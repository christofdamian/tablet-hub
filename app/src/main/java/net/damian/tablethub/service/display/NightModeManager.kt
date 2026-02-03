package net.damian.tablethub.service.display

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.damian.tablethub.data.preferences.NightModeConfig
import net.damian.tablethub.data.preferences.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for night mode state coordination.
 * Handles manual toggle, automatic sensor-based switching, and HA integration.
 */
@Singleton
class NightModeManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "NightModeManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeTimerJob: Job? = null

    private val _nightModeState = MutableStateFlow(NightModeState())
    val nightModeState: StateFlow<NightModeState> = _nightModeState.asStateFlow()

    // Callback for notifying HA state publisher
    var onNightModeChanged: ((Boolean) -> Unit)? = null

    // Track if initial config has been loaded
    private var initialConfigLoaded = false

    init {
        // Load initial config from DataStore
        scope.launch {
            settingsDataStore.nightModeConfig.collect { config ->
                _nightModeState.update { state ->
                    if (!initialConfigLoaded) {
                        // First load: restore all settings including manual enabled state
                        initialConfigLoaded = true
                        state.copy(
                            isManualEnabled = config.manualEnabled,
                            isAutoEnabled = config.autoEnabled,
                            luxThreshold = config.luxThreshold.toFloat(),
                            luxHysteresis = config.luxHysteresis.toFloat(),
                            nightBrightness = config.nightBrightness,
                            dimOverlay = config.dimOverlay,
                            wakeDurationSeconds = config.wakeDurationSeconds
                        )
                    } else {
                        // Subsequent updates: only update config values, not manual state
                        // (manual state is already set in-memory by setManualNightMode)
                        state.copy(
                            isAutoEnabled = config.autoEnabled,
                            luxThreshold = config.luxThreshold.toFloat(),
                            luxHysteresis = config.luxHysteresis.toFloat(),
                            nightBrightness = config.nightBrightness,
                            dimOverlay = config.dimOverlay,
                            wakeDurationSeconds = config.wakeDurationSeconds
                        )
                    }
                }
                updateActiveState()
            }
        }
    }

    /**
     * Toggle manual night mode on/off.
     * When manual is enabled, it overrides auto-sensing.
     */
    fun setManualNightMode(enabled: Boolean) {
        Log.d(TAG, "Setting manual night mode: $enabled")
        _nightModeState.update { it.copy(isManualEnabled = enabled) }
        updateActiveState()

        scope.launch {
            settingsDataStore.setNightModeManualEnabled(enabled)
        }
    }

    /**
     * Toggle automatic night mode sensing on/off.
     */
    fun setAutoNightModeEnabled(enabled: Boolean) {
        Log.d(TAG, "Setting auto night mode: $enabled")
        _nightModeState.update { it.copy(isAutoEnabled = enabled) }
        updateActiveState()

        scope.launch {
            settingsDataStore.setNightModeAutoEnabled(enabled)
        }
    }

    /**
     * Update ambient light level from sensor.
     * Called by LightSensorService with smoothed lux values.
     */
    fun updateAmbientLux(lux: Float) {
        _nightModeState.update { it.copy(currentLux = lux) }
        updateActiveState()
    }

    /**
     * Set night mode from Home Assistant command.
     * This sets manual mode to the requested state.
     */
    fun setNightModeFromHa(enabled: Boolean) {
        Log.d(TAG, "Setting night mode from HA: $enabled")
        setManualNightMode(enabled)
    }

    /**
     * Toggle night mode (for UI button).
     * If currently active due to auto-sensing, start a wake timer to temporarily
     * suppress night mode. After the timer expires, auto-sensing resumes.
     * If manually enabled, just turn it off.
     * If not active, turn on manual mode.
     */
    fun toggleNightMode() {
        val currentState = _nightModeState.value
        if (currentState.isActive) {
            // Exiting night mode
            if (currentState.isManualEnabled) {
                // Was manually enabled, just turn it off
                setManualNightMode(false)
            } else if (currentState.isAutoEnabled) {
                // Was auto-enabled, start wake timer
                startWakeTimer()
            }
        } else {
            // Entering night mode - cancel any wake timer and enable manual mode
            cancelWakeTimer()
            setManualNightMode(true)
        }
    }

    /**
     * Start a temporary wake timer that suppresses auto night mode.
     * After the timer expires, auto-sensing can re-enable night mode.
     */
    private fun startWakeTimer() {
        wakeTimerJob?.cancel()
        val durationSeconds = _nightModeState.value.wakeDurationSeconds
        Log.d(TAG, "Starting wake timer for $durationSeconds seconds")
        _nightModeState.update { it.copy(isWakeTimerActive = true) }
        updateActiveState()

        wakeTimerJob = scope.launch {
            delay(durationSeconds * 1000L)
            Log.d(TAG, "Wake timer expired")
            _nightModeState.update { it.copy(isWakeTimerActive = false) }
            updateActiveState()
        }
    }

    /**
     * Cancel the wake timer if active.
     */
    private fun cancelWakeTimer() {
        if (wakeTimerJob?.isActive == true) {
            Log.d(TAG, "Cancelling wake timer")
            wakeTimerJob?.cancel()
            _nightModeState.update { it.copy(isWakeTimerActive = false) }
        }
    }

    /**
     * Reset the wake timer on user interaction.
     * Called when user touches the screen while wake timer is active.
     */
    fun onUserInteraction() {
        if (_nightModeState.value.isWakeTimerActive) {
            Log.d(TAG, "User interaction - resetting wake timer")
            startWakeTimer()
        }
    }

    /**
     * Update the isActive state based on manual and auto settings.
     * Manual mode takes priority over auto sensing.
     * Wake timer temporarily suppresses auto night mode.
     */
    private fun updateActiveState() {
        val state = _nightModeState.value
        val wasActive = state.isActive
        val exitThreshold = state.luxThreshold + state.luxHysteresis

        val shouldBeActive = when {
            // Manual mode takes priority
            state.isManualEnabled -> true
            // Wake timer suppresses auto night mode temporarily
            state.isWakeTimerActive -> false
            // Auto mode with hysteresis
            state.isAutoEnabled -> {
                val enterThreshold = state.luxThreshold

                when {
                    // Currently active, exit when above exit threshold
                    state.isActive && state.currentLux > exitThreshold -> false
                    // Currently active, stay active when below exit threshold
                    state.isActive -> true
                    // Not active, enter when below enter threshold
                    state.currentLux <= enterThreshold -> true
                    // Not active, stay inactive
                    else -> false
                }
            }
            else -> false
        }

        if (shouldBeActive != state.isActive) {
            Log.d(TAG, "Night mode active changed: $shouldBeActive (lux=${state.currentLux})")

            if (shouldBeActive) {
                // Entering night mode - store current brightness first, then apply night brightness
                val currentBrightness = ScreenManager.getBrightness()
                _nightModeState.update { it.copy(isActive = true, preNightBrightness = currentBrightness) }
                ScreenManager.setBrightness(state.nightBrightness)
                Log.d(TAG, "Saved pre-night brightness: $currentBrightness, set night brightness: ${state.nightBrightness}")
            } else {
                // Exiting night mode - restore brightness
                val savedBrightness = state.preNightBrightness
                _nightModeState.update { it.copy(isActive = false, preNightBrightness = null) }
                savedBrightness?.let {
                    ScreenManager.setBrightness(it)
                    Log.d(TAG, "Restored pre-night brightness: $it")
                }
            }

            // Notify HA
            onNightModeChanged?.invoke(shouldBeActive)
        }
    }
}

/**
 * State object for night mode.
 */
data class NightModeState(
    val isManualEnabled: Boolean = false,
    val isAutoEnabled: Boolean = false,  // Default to false to avoid sensor issues
    val isActive: Boolean = false,
    val currentLux: Float = 100f,
    val luxThreshold: Float = 15f,
    val luxHysteresis: Float = 5f,
    val nightBrightness: Int = 5,
    val dimOverlay: Int = 0,  // Extra dim overlay percentage (0-90)
    val preNightBrightness: Int? = null,  // Brightness level before entering night mode
    val isWakeTimerActive: Boolean = false,  // Temporary wake timer suppresses auto night mode
    val wakeDurationSeconds: Int = 30  // How long to stay awake after tapping night clock
)
