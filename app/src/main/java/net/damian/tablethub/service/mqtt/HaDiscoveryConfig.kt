package net.damian.tablethub.service.mqtt

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Home Assistant MQTT Discovery configuration classes.
 * These are serialized to JSON and published to homeassistant/<component>/<device_id>/<object_id>/config
 */

@JsonClass(generateAdapter = true)
data class HaDeviceInfo(
    val identifiers: List<String>,
    val name: String,
    val model: String = "TabletHub",
    val manufacturer: String = "DIY",
    @Json(name = "sw_version") val swVersion: String = "1.0"
)

@JsonClass(generateAdapter = true)
data class HaSensorConfig(
    val name: String,
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "state_topic") val stateTopic: String,
    @Json(name = "value_template") val valueTemplate: String,
    val device: HaDeviceInfo,
    val icon: String? = null,
    @Json(name = "device_class") val deviceClass: String? = null,
    @Json(name = "unit_of_measurement") val unitOfMeasurement: String? = null,
    @Json(name = "json_attributes_topic") val jsonAttributesTopic: String? = null,
    @Json(name = "json_attributes_template") val jsonAttributesTemplate: String? = null
)

@JsonClass(generateAdapter = true)
data class HaBinarySensorConfig(
    val name: String,
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "state_topic") val stateTopic: String,
    @Json(name = "value_template") val valueTemplate: String,
    val device: HaDeviceInfo,
    val icon: String? = null,
    @Json(name = "device_class") val deviceClass: String? = null,
    @Json(name = "payload_on") val payloadOn: String = "ON",
    @Json(name = "payload_off") val payloadOff: String = "OFF"
)

@JsonClass(generateAdapter = true)
data class HaSwitchConfig(
    val name: String,
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "state_topic") val stateTopic: String,
    @Json(name = "command_topic") val commandTopic: String,
    @Json(name = "value_template") val valueTemplate: String? = null,
    val device: HaDeviceInfo,
    val icon: String? = null,
    @Json(name = "payload_on") val payloadOn: String = "ON",
    @Json(name = "payload_off") val payloadOff: String = "OFF",
    @Json(name = "state_on") val stateOn: String = "ON",
    @Json(name = "state_off") val stateOff: String = "OFF",
    @Json(name = "json_attributes_topic") val jsonAttributesTopic: String? = null,
    @Json(name = "json_attributes_template") val jsonAttributesTemplate: String? = null
)

@JsonClass(generateAdapter = true)
data class HaLightConfig(
    val name: String,
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "state_topic") val stateTopic: String,
    @Json(name = "command_topic") val commandTopic: String,
    @Json(name = "state_value_template") val stateValueTemplate: String? = null,
    @Json(name = "brightness_state_topic") val brightnessStateTopic: String,
    @Json(name = "brightness_command_topic") val brightnessCommandTopic: String,
    @Json(name = "brightness_value_template") val brightnessValueTemplate: String? = null,
    @Json(name = "brightness_scale") val brightnessScale: Int = 255,
    val device: HaDeviceInfo,
    val icon: String? = null,
    @Json(name = "payload_on") val payloadOn: String = "ON",
    @Json(name = "payload_off") val payloadOff: String = "OFF"
)

@JsonClass(generateAdapter = true)
data class HaButtonConfig(
    val name: String,
    @Json(name = "unique_id") val uniqueId: String,
    @Json(name = "command_topic") val commandTopic: String,
    val device: HaDeviceInfo,
    val icon: String? = null,
    @Json(name = "payload_press") val payloadPress: String = "PRESS"
)

@JsonClass(generateAdapter = true)
data class HaDeviceTriggerConfig(
    @Json(name = "automation_type") val automationType: String = "trigger",
    val type: String = "button_short_press",
    val subtype: String,
    val topic: String,
    val payload: String,
    val device: HaDeviceInfo
)
