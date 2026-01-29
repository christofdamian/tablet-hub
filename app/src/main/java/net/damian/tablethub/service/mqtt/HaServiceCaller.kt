package net.damian.tablethub.service.mqtt

import android.util.Log
import kotlinx.coroutines.flow.first
import net.damian.tablethub.data.local.entity.ButtonEntity
import net.damian.tablethub.data.preferences.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends button press events to Home Assistant via MQTT.
 *
 * Publishes to: tablethub/<device_id>/button/press
 * With payload: {"identifier": "button_1"}
 */
@Singleton
class HaServiceCaller @Inject constructor(
    private val mqttManager: MqttManager,
    private val settingsDataStore: SettingsDataStore
) {
    companion object {
        private const val TAG = "HaServiceCaller"
    }

    /**
     * Send a button press event to HA.
     */
    suspend fun sendButtonPress(button: ButtonEntity) {
        val deviceId = settingsDataStore.deviceId.first()
        val identifier = button.identifier.ifBlank {
            ButtonEntity.defaultIdentifier(button.position)
        }
        val payload = """{"identifier":"$identifier"}"""
        val topic = "tablethub/$deviceId/button/press"

        Log.d(TAG, "Button press: $identifier")
        mqttManager.publish(topic, payload, qos = 1, retained = false)
    }
}
