package net.damian.tablethub.service.mqtt

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.damian.tablethub.data.local.entity.AlarmEntity
import net.damian.tablethub.data.preferences.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes TabletHub state to Home Assistant via MQTT.
 * State is published as a JSON object to tablethub/<device_id>/state
 */
@Singleton
class HaStatePublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttManager: MqttManager,
    private val settingsDataStore: SettingsDataStore,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "HaStatePublisher"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var deviceId: String = "tablethub"
    private val stateTopic: String get() = "tablethub/$deviceId/state"
    private val eventTopic: String get() = "tablethub/$deviceId/event"

    // Current state
    private val currentState = MutableStateFlow(TabletHubState())

    init {
        scope.launch {
            deviceId = settingsDataStore.deviceId.first()
        }
    }

    /**
     * Publish the full current state to MQTT.
     */
    fun publishState() {
        scope.launch {
            try {
                deviceId = settingsDataStore.deviceId.first()
                val state = currentState.value
                val adapter = moshi.adapter(TabletHubState::class.java)
                val json = adapter.toJson(state)

                mqttManager.publish(stateTopic, json, qos = 1, retained = true)
                Log.d(TAG, "Published state: $json")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish state", e)
            }
        }
    }

    /**
     * Update next alarm info and publish state.
     */
    fun updateNextAlarm(alarmTime: String?, alarmLabel: String?, alarmId: Long?, minutesUntil: Int? = null) {
        currentState.update { state ->
            state.copy(
                nextAlarm = alarmTime ?: "none",
                nextAlarmLabel = alarmLabel ?: "",
                nextAlarmId = alarmId?.toString() ?: "",
                alarmCountdown = minutesUntil ?: -1
            )
        }
        publishState()
    }

    /**
     * Update alarm ringing state and publish.
     */
    fun updateAlarmRinging(isRinging: Boolean, alarmId: Long? = null) {
        currentState.update { state ->
            state.copy(
                alarmRinging = if (isRinging) "ON" else "OFF",
                ringingAlarmId = alarmId?.toString() ?: ""
            )
        }
        publishState()
    }

    /**
     * Update screen state and publish.
     */
    fun updateScreenState(isOn: Boolean, brightness: Int) {
        currentState.update { state ->
            state.copy(
                screen = if (isOn) "ON" else "OFF",
                brightness = brightness
            )
        }
        publishState()
    }

    /**
     * Update battery info and publish.
     */
    fun updateBattery() {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        batteryStatus?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale)
            } else {
                0
            }

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            currentState.update { state ->
                state.copy(
                    battery = batteryPct,
                    batteryCharging = isCharging
                )
            }
        }
        publishState()
    }

    /**
     * Update alarms map and publish.
     * Call this when alarms are loaded or changed.
     */
    fun updateAlarms(alarms: List<AlarmEntity>) {
        val alarmsMap = alarms.associate { alarm ->
            alarm.id.toString() to AlarmState(
                enabled = alarm.enabled,
                time = alarm.getTimeString(),
                label = alarm.label,
                days = alarm.getDaysString()
            )
        }

        currentState.update { state ->
            state.copy(alarms = alarmsMap)
        }
        publishState()
    }

    /**
     * Update night mode state and publish.
     */
    fun updateNightModeState(isActive: Boolean) {
        currentState.update { state ->
            state.copy(nightMode = if (isActive) "ON" else "OFF")
        }
        publishState()
    }

    /**
     * Publish pre-alarm event for Home Assistant automations.
     * This is published as a separate event (not retained) to the event topic.
     */
    fun publishPreAlarmEvent(alarmId: Long, alarmTime: String, alarmLabel: String, minutesUntil: Int) {
        scope.launch {
            try {
                deviceId = settingsDataStore.deviceId.first()
                val event = PreAlarmEvent(
                    eventType = "tablethub_pre_alarm",
                    alarmId = alarmId.toString(),
                    alarmTime = alarmTime,
                    alarmLabel = alarmLabel,
                    minutesUntil = minutesUntil
                )
                val adapter = moshi.adapter(PreAlarmEvent::class.java)
                val json = adapter.toJson(event)

                // Publish event (not retained - events are transient)
                mqttManager.publish(eventTopic, json, qos = 1, retained = false)
                Log.d(TAG, "Published pre-alarm event: $json")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish pre-alarm event", e)
            }
        }
    }

    /**
     * Force refresh all state values and publish.
     */
    fun refreshAndPublish() {
        updateBattery()
        // Screen state would need to be tracked separately
        // For now, assume screen is on
        currentState.update { state ->
            state.copy(screen = "ON")
        }
        publishState()
    }
}

/**
 * Full state object published to MQTT.
 */
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class TabletHubState(
    @com.squareup.moshi.Json(name = "next_alarm") val nextAlarm: String = "none",
    @com.squareup.moshi.Json(name = "next_alarm_label") val nextAlarmLabel: String = "",
    @com.squareup.moshi.Json(name = "next_alarm_id") val nextAlarmId: String = "",
    @com.squareup.moshi.Json(name = "alarm_countdown") val alarmCountdown: Int = -1,
    @com.squareup.moshi.Json(name = "alarm_ringing") val alarmRinging: String = "OFF",
    @com.squareup.moshi.Json(name = "ringing_alarm_id") val ringingAlarmId: String = "",
    val screen: String = "ON",
    val brightness: Int = 255,
    val battery: Int = 100,
    @com.squareup.moshi.Json(name = "battery_charging") val batteryCharging: Boolean = false,
    @com.squareup.moshi.Json(name = "night_mode") val nightMode: String = "OFF",
    val alarms: Map<String, AlarmState> = emptyMap()
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class AlarmState(
    val enabled: Boolean,
    val time: String,
    val label: String,
    val days: String
)

/**
 * Pre-alarm event published to MQTT for HA automations.
 */
@com.squareup.moshi.JsonClass(generateAdapter = true)
data class PreAlarmEvent(
    @com.squareup.moshi.Json(name = "event_type") val eventType: String,
    @com.squareup.moshi.Json(name = "alarm_id") val alarmId: String,
    @com.squareup.moshi.Json(name = "alarm_time") val alarmTime: String,
    @com.squareup.moshi.Json(name = "alarm_label") val alarmLabel: String,
    @com.squareup.moshi.Json(name = "minutes_until") val minutesUntil: Int
)
