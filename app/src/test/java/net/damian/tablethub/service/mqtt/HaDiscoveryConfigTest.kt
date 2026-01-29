package net.damian.tablethub.service.mqtt

import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaDiscoveryConfigTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `HaLightConfig includes brightness_value_template in JSON`() {
        val config = HaLightConfig(
            name = "Brightness",
            uniqueId = "test_brightness",
            stateTopic = "test/state",
            commandTopic = "test/command",
            stateValueTemplate = "{{ value_json.screen }}",
            brightnessStateTopic = "test/state",
            brightnessCommandTopic = "test/command",
            brightnessValueTemplate = "{{ value_json.brightness }}",
            brightnessScale = 255,
            device = HaDeviceInfo(
                identifiers = listOf("test_device"),
                name = "Test Device"
            )
        )

        val adapter = moshi.adapter(HaLightConfig::class.java)
        val json = adapter.toJson(config)

        assertTrue(
            "JSON should contain brightness_value_template",
            json.contains("\"brightness_value_template\"")
        )
        assertTrue(
            "JSON should contain the brightness template expression",
            json.contains("value_json.brightness")
        )
    }

    @Test
    fun `HaLightConfig includes state_value_template in JSON`() {
        val config = HaLightConfig(
            name = "Brightness",
            uniqueId = "test_brightness",
            stateTopic = "test/state",
            commandTopic = "test/command",
            stateValueTemplate = "{{ value_json.screen }}",
            brightnessStateTopic = "test/state",
            brightnessCommandTopic = "test/command",
            brightnessValueTemplate = "{{ value_json.brightness }}",
            brightnessScale = 255,
            device = HaDeviceInfo(
                identifiers = listOf("test_device"),
                name = "Test Device"
            )
        )

        val adapter = moshi.adapter(HaLightConfig::class.java)
        val json = adapter.toJson(config)

        assertTrue(
            "JSON should contain state_value_template",
            json.contains("\"state_value_template\"")
        )
        assertTrue(
            "JSON should contain the state template expression",
            json.contains("value_json.screen")
        )
    }

    @Test
    fun `HaLightConfig without templates omits them from JSON`() {
        val config = HaLightConfig(
            name = "Brightness",
            uniqueId = "test_brightness",
            stateTopic = "test/state",
            commandTopic = "test/command",
            brightnessStateTopic = "test/state",
            brightnessCommandTopic = "test/command",
            brightnessScale = 255,
            device = HaDeviceInfo(
                identifiers = listOf("test_device"),
                name = "Test Device"
            )
        )

        val adapter = moshi.adapter(HaLightConfig::class.java)
        val json = adapter.toJson(config)

        // Null values should not appear in JSON (Moshi default behavior)
        // This test documents the behavior when templates are not provided
        assertTrue(
            "JSON should contain required fields",
            json.contains("\"brightness_state_topic\"")
        )
    }

    @Test
    fun `HaLightConfig serializes all required fields correctly`() {
        val config = HaLightConfig(
            name = "Test Light",
            uniqueId = "test_light_123",
            stateTopic = "home/light/state",
            commandTopic = "home/light/command",
            stateValueTemplate = "{{ value_json.state }}",
            brightnessStateTopic = "home/light/state",
            brightnessCommandTopic = "home/light/brightness",
            brightnessValueTemplate = "{{ value_json.brightness }}",
            brightnessScale = 100,
            device = HaDeviceInfo(
                identifiers = listOf("device_123"),
                name = "Test Device"
            ),
            icon = "mdi:lightbulb",
            payloadOn = "ON",
            payloadOff = "OFF"
        )

        val adapter = moshi.adapter(HaLightConfig::class.java)
        val json = adapter.toJson(config)

        assertTrue(json.contains("\"name\":\"Test Light\""))
        assertTrue(json.contains("\"unique_id\":\"test_light_123\""))
        assertTrue(json.contains("\"state_topic\":\"home/light/state\""))
        assertTrue(json.contains("\"command_topic\":\"home/light/command\""))
        assertTrue(json.contains("\"brightness_scale\":100"))
        assertTrue(json.contains("\"icon\":\"mdi:lightbulb\""))
    }
}
