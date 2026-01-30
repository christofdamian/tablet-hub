package net.damian.tablethub.service.mqtt

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import net.damian.tablethub.data.preferences.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes Home Assistant MQTT Discovery messages to auto-register
 * TabletHub entities in Home Assistant.
 *
 * Discovery topic format: homeassistant/<component>/<device_id>/<object_id>/config
 * State topic format: tablethub/<device_id>/state
 * Command topic format: tablethub/<device_id>/command
 */
@Singleton
class HaDiscovery @Inject constructor(
    private val mqttManager: MqttManager,
    private val settingsDataStore: SettingsDataStore,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "HaDiscovery"
        private const val HA_DISCOVERY_PREFIX = "homeassistant"
    }

    private var deviceId: String = "tablethub"
    private var deviceName: String = "TabletHub"

    private val stateTopic: String get() = "tablethub/$deviceId/state"
    private val commandTopic: String get() = "tablethub/$deviceId/command"
    private val mediaStateTopic: String get() = "tablethub/$deviceId/media/state"
    private val mediaCommandTopic: String get() = "tablethub/$deviceId/media/command"
    private val buttonPressTopic: String get() = "tablethub/$deviceId/button/press"

    private val deviceInfo: HaDeviceInfo
        get() = HaDeviceInfo(
            identifiers = listOf("tablethub_$deviceId"),
            name = deviceName
        )

    suspend fun publishAllDiscoveryMessages() {
        // Load device info from settings
        deviceId = settingsDataStore.deviceId.first()
        deviceName = settingsDataStore.deviceName.first()

        Log.d(TAG, "Publishing discovery messages for device: $deviceId")

        publishNextAlarmSensor()
        publishAlarmCountdownSensor()
        publishAlarmRingingSensor()
        publishScreenSwitch()
        publishBrightnessLight()
        publishBatterySensor()
        publishNightModeSwitch()
        publishDismissAlarmButton()
        publishTriggerAlarmButton()

        Log.d(TAG, "Discovery messages published")
    }

    fun removeAllDiscoveryMessages() {
        // Publish empty payloads to remove entities
        val topics = listOf(
            "$HA_DISCOVERY_PREFIX/sensor/${deviceId}/next_alarm/config",
            "$HA_DISCOVERY_PREFIX/sensor/${deviceId}/alarm_countdown/config",
            "$HA_DISCOVERY_PREFIX/binary_sensor/${deviceId}/alarm_ringing/config",
            "$HA_DISCOVERY_PREFIX/switch/${deviceId}/screen/config",
            "$HA_DISCOVERY_PREFIX/light/${deviceId}/brightness/config",
            "$HA_DISCOVERY_PREFIX/sensor/${deviceId}/battery/config",
            "$HA_DISCOVERY_PREFIX/switch/${deviceId}/night_mode/config",
            "$HA_DISCOVERY_PREFIX/button/${deviceId}/dismiss_alarm/config",
            "$HA_DISCOVERY_PREFIX/button/${deviceId}/trigger_alarm/config"
        )

        topics.forEach { topic ->
            mqttManager.publish(topic, "", retained = true)
        }

        Log.d(TAG, "Discovery messages removed")
    }

    private fun publishNextAlarmSensor() {
        val config = HaSensorConfig(
            name = "Next Alarm",
            uniqueId = "tablethub_${deviceId}_next_alarm",
            stateTopic = stateTopic,
            valueTemplate = "{{ value_json.next_alarm }}",
            device = deviceInfo,
            icon = "mdi:alarm",
            jsonAttributesTopic = stateTopic,
            jsonAttributesTemplate = """{{ {"alarm_label": value_json.next_alarm_label, "alarm_id": value_json.next_alarm_id} | tojson }}"""
        )

        publishConfig("sensor", "next_alarm", config)
    }

    private fun publishAlarmCountdownSensor() {
        val config = HaSensorConfig(
            name = "Alarm Countdown",
            uniqueId = "tablethub_${deviceId}_alarm_countdown",
            stateTopic = stateTopic,
            valueTemplate = "{{ value_json.alarm_countdown if value_json.alarm_countdown >= 0 else 'unknown' }}",
            device = deviceInfo,
            icon = "mdi:timer-sand",
            unitOfMeasurement = "min"
        )

        publishConfig("sensor", "alarm_countdown", config)
    }

    private fun publishAlarmRingingSensor() {
        val config = HaBinarySensorConfig(
            name = "Alarm Ringing",
            uniqueId = "tablethub_${deviceId}_alarm_ringing",
            stateTopic = stateTopic,
            valueTemplate = "{{ value_json.alarm_ringing }}",
            device = deviceInfo,
            icon = "mdi:alarm-light",
            payloadOn = "ON",
            payloadOff = "OFF"
        )

        publishConfig("binary_sensor", "alarm_ringing", config)
    }

    private fun publishScreenSwitch() {
        val config = HaSwitchConfig(
            name = "Screen",
            uniqueId = "tablethub_${deviceId}_screen",
            stateTopic = stateTopic,
            commandTopic = commandTopic,
            valueTemplate = "{{ value_json.screen }}",
            device = deviceInfo,
            icon = "mdi:tablet"
        )

        publishConfig("switch", "screen", config)
    }

    private fun publishBrightnessLight() {
        val config = HaLightConfig(
            name = "Brightness",
            uniqueId = "tablethub_${deviceId}_brightness",
            stateTopic = stateTopic,
            commandTopic = commandTopic,
            stateValueTemplate = "{{ value_json.screen }}",
            brightnessStateTopic = stateTopic,
            brightnessCommandTopic = commandTopic,
            brightnessValueTemplate = "{{ value_json.brightness }}",
            brightnessScale = 255,
            device = deviceInfo,
            icon = "mdi:brightness-6"
        )

        publishConfig("light", "brightness", config)
    }

    private fun publishBatterySensor() {
        val config = HaSensorConfig(
            name = "Battery",
            uniqueId = "tablethub_${deviceId}_battery",
            stateTopic = stateTopic,
            valueTemplate = "{{ value_json.battery }}",
            device = deviceInfo,
            icon = "mdi:battery",
            deviceClass = "battery",
            unitOfMeasurement = "%",
            jsonAttributesTopic = stateTopic,
            jsonAttributesTemplate = """{{ {"charging": value_json.battery_charging} | tojson }}"""
        )

        publishConfig("sensor", "battery", config)
    }

    private fun publishNightModeSwitch() {
        val config = HaSwitchConfig(
            name = "Night Mode",
            uniqueId = "tablethub_${deviceId}_night_mode",
            stateTopic = stateTopic,
            commandTopic = commandTopic,
            valueTemplate = "{{ value_json.night_mode }}",
            device = deviceInfo,
            icon = "mdi:weather-night"
        )

        publishConfig("switch", "night_mode", config)
    }

    private fun publishDismissAlarmButton() {
        val config = HaButtonConfig(
            name = "Dismiss Alarm",
            uniqueId = "tablethub_${deviceId}_dismiss_alarm",
            commandTopic = commandTopic,
            device = deviceInfo,
            icon = "mdi:alarm-off",
            payloadPress = """{"command": "dismiss_alarm"}"""
        )

        publishConfig("button", "dismiss_alarm", config)
    }

    private fun publishTriggerAlarmButton() {
        val config = HaButtonConfig(
            name = "Trigger Alarm",
            uniqueId = "tablethub_${deviceId}_trigger_alarm",
            commandTopic = commandTopic,
            device = deviceInfo,
            icon = "mdi:alarm-bell",
            payloadPress = """{"command": "trigger_alarm"}"""
        )

        publishConfig("button", "trigger_alarm", config)
    }

    /**
     * Publish discovery config for an alarm switch.
     * Call this when alarms are created/updated.
     */
    fun publishAlarmSwitch(alarmId: Long, alarmLabel: String, alarmTime: String) {
        val config = HaSwitchConfig(
            name = alarmLabel.ifEmpty { "Alarm $alarmId" },
            uniqueId = "tablethub_${deviceId}_alarm_$alarmId",
            stateTopic = stateTopic,
            commandTopic = commandTopic,
            valueTemplate = "{{ value_json.alarms['$alarmId'].enabled }}",
            device = deviceInfo,
            icon = "mdi:alarm",
            payloadOn = """{"command": "enable_alarm", "alarm_id": $alarmId}""",
            payloadOff = """{"command": "disable_alarm", "alarm_id": $alarmId}""",
            stateOn = "true",
            stateOff = "false",
            jsonAttributesTopic = stateTopic,
            jsonAttributesTemplate = """{{ {"time": value_json.alarms['$alarmId'].time, "days": value_json.alarms['$alarmId'].days} | tojson }}"""
        )

        publishConfig("switch", "alarm_$alarmId", config)
    }

    /**
     * Remove discovery config for an alarm switch.
     * Call this when alarms are deleted.
     */
    fun removeAlarmSwitch(alarmId: Long) {
        val topic = "$HA_DISCOVERY_PREFIX/switch/$deviceId/alarm_$alarmId/config"
        mqttManager.publish(topic, "", retained = true)
    }

    /**
     * Publish discovery config for a shortcut button trigger.
     * Call this when shortcut buttons are configured.
     */
    fun publishShortcutButtonTrigger(identifier: String, label: String) {
        val config = HaDeviceTriggerConfig(
            automationType = "trigger",
            type = "button_short_press",
            subtype = label.ifEmpty { identifier },
            topic = buttonPressTopic,
            payload = """{"identifier":"$identifier"}""",
            device = deviceInfo
        )

        publishConfig("device_automation", "shortcut_$identifier", config)
        Log.d(TAG, "Published shortcut button trigger: $identifier")
    }

    /**
     * Remove discovery config for a shortcut button trigger.
     * Call this when shortcut buttons are deleted.
     */
    fun removeShortcutButtonTrigger(identifier: String) {
        val topic = "$HA_DISCOVERY_PREFIX/device_automation/$deviceId/shortcut_$identifier/config"
        mqttManager.publish(topic, "", retained = true)
        Log.d(TAG, "Removed shortcut button trigger: $identifier")
    }

    private inline fun <reified T> publishConfig(component: String, objectId: String, config: T) {
        val adapter = moshi.adapter(T::class.java)
        val json = adapter.toJson(config)
        val topic = "$HA_DISCOVERY_PREFIX/$component/$deviceId/$objectId/config"

        mqttManager.publish(topic, json, qos = 1, retained = true)
        Log.d(TAG, "Published $component/$objectId discovery")
    }
}
