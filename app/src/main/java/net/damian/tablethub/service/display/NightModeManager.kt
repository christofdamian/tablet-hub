package net.damian.tablethub.service.display

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val _nightModeState = MutableStateFlow(NightModeState())
    val nightModeState: StateFlow<NightModeState> = _nightModeState.asStateFlow()

    // Callback for notifying HA state publisher
    var onNightModeChanged: ((Boolean) -> Unit)? = null

    init {
        // Load initial config from DataStore
        scope.launch {
            settingsDataStore.nightModeConfig.collect { config ->
                _nightModeState.update { state ->
                    state.copy(
                        isManualEnabled = config.manualEnabled,
                        isAutoEnabled = config.autoEnabled,
                        luxThreshold = config.luxThreshold.toFloat(),
                        luxHysteresis = config.luxHysteresis.toFloat(),
                        nightBrightness = config.nightBrightness
                    )
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
     * If currently active (by any means), turn off manual mode.
     * If not active, turn on manual mode.
     */
    fun toggleNightMode() {
        val currentState = _nightModeState.value
        val newManualEnabled = !currentState.isActive
        setManualNightMode(newManualEnabled)
    }

    /**
     * Update the isActive state based on manual and auto settings.
     * Manual mode takes priority over auto sensing.
     */
    private fun updateActiveState() {
        val state = _nightModeState.value
        val wasActive = state.isActive

        val shouldBeActive = when {
            // Manual mode takes priority
            state.isManualEnabled -> true
            // Auto mode with hysteresis
            state.isAutoEnabled -> {
                val enterThreshold = state.luxThreshold
                val exitThreshold = state.luxThreshold + state.luxHysteresis

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
            _nightModeState.update { it.copy(isActive = shouldBeActive) }

            // Apply night mode brightness
            if (shouldBeActive) {
                ScreenManager.setBrightness(state.nightBrightness)
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
    val isAutoEnabled: Boolean = true,
    val isActive: Boolean = false,
    val currentLux: Float = 100f,
    val luxThreshold: Float = 15f,
    val luxHysteresis: Float = 5f,
    val nightBrightness: Int = 5
)
