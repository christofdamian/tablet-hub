package net.damian.tablethub.service.mqtt

import android.util.Log
import net.damian.tablethub.data.local.entity.ButtonEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends button press events to Home Assistant via MQTT.
 *
 * Publishes to: tablethub/button/press
 * With payload: {"identifier": "button_1"}
 */
@Singleton
class HaServiceCaller @Inject constructor(
    private val mqttManager: MqttManager
) {
    companion object {
        private const val TAG = "HaServiceCaller"
        private const val BUTTON_PRESS_TOPIC = "tablethub/button/press"
    }

    /**
     * Send a button press event to HA.
     */
    suspend fun sendButtonPress(button: ButtonEntity) {
        val identifier = button.identifier.ifBlank {
            ButtonEntity.defaultIdentifier(button.position)
        }
        val payload = """{"identifier":"$identifier"}"""

        Log.d(TAG, "Button press: $identifier")
        mqttManager.publish(BUTTON_PRESS_TOPIC, payload, qos = 1, retained = false)
    }
}
