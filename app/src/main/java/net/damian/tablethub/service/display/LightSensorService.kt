package net.damian.tablethub.service.display

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that monitors the ambient light sensor and reports smoothed lux values
 * to NightModeManager for automatic night mode switching.
 *
 * Battery optimization: Only actively listens when auto-sensing is enabled.
 */
@Singleton
class LightSensorService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nightModeManager: NightModeManager
) : SensorEventListener {

    companion object {
        private const val TAG = "LightSensorService"
        private const val SMOOTHING_WINDOW_SIZE = 5
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val lightSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val luxReadings = mutableListOf<Float>()
    private var isListening = false
    private var isActivityResumed = false

    init {
        // Observe auto-sensing state and start/stop sensor accordingly
        scope.launch {
            nightModeManager.nightModeState
                .map { it.isAutoEnabled }
                .distinctUntilChanged()
                .collect { autoEnabled ->
                    Log.d(TAG, "Auto-sensing changed: $autoEnabled, activity resumed: $isActivityResumed")
                    if (autoEnabled && isActivityResumed) {
                        startSensorListening()
                    } else {
                        stopSensorListening()
                    }
                }
        }
    }

    /**
     * Called when activity resumes. Enables sensor if auto-sensing is on.
     */
    fun onActivityResumed() {
        isActivityResumed = true
        if (nightModeManager.nightModeState.value.isAutoEnabled) {
            startSensorListening()
        }
    }

    /**
     * Called when activity pauses. Always stops sensor to save battery.
     */
    fun onActivityPaused() {
        isActivityResumed = false
        stopSensorListening()
    }

    private fun startSensorListening() {
        if (isListening) return

        if (lightSensor == null) {
            Log.w(TAG, "No light sensor available on this device")
            return
        }

        val registered = sensorManager.registerListener(
            this,
            lightSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )

        if (registered) {
            isListening = true
            Log.d(TAG, "Started listening to light sensor")
        } else {
            Log.e(TAG, "Failed to register light sensor listener")
        }
    }

    private fun stopSensorListening() {
        if (!isListening) return

        sensorManager.unregisterListener(this)
        isListening = false
        luxReadings.clear()
        Log.d(TAG, "Stopped listening to light sensor")
    }

    // Deprecated: use onActivityResumed() instead
    @Deprecated("Use onActivityResumed() for lifecycle-aware sensor management")
    fun startListening() {
        onActivityResumed()
    }

    // Deprecated: use onActivityPaused() instead
    @Deprecated("Use onActivityPaused() for lifecycle-aware sensor management")
    fun stopListening() {
        onActivityPaused()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LIGHT) return

        val lux = event.values[0]

        // Add to readings window
        luxReadings.add(lux)

        // Keep only the last N readings
        while (luxReadings.size > SMOOTHING_WINDOW_SIZE) {
            luxReadings.removeAt(0)
        }

        // Calculate smoothed average
        val smoothedLux = if (luxReadings.isNotEmpty()) {
            luxReadings.average().toFloat()
        } else {
            lux
        }

        // Report to night mode manager
        nightModeManager.updateAmbientLux(smoothedLux)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed for light sensor
    }
}
