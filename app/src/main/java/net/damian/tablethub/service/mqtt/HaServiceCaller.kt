package net.damian.tablethub.service.mqtt

import android.util.Log
import kotlinx.coroutines.flow.first
import net.damian.tablethub.data.local.entity.ButtonEntity
import net.damian.tablethub.data.preferences.SettingsDataStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes Home Assistant service calls via MQTT.
 *
 * Publishes to: homeassistant/service/<domain>/<service>
 * With payload: {"entity_id": "light.living_room", ...}
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
     * Call an HA service based on button configuration.
     */
    suspend fun callService(button: ButtonEntity) {
        val domain = when (button.actionType) {
            ButtonEntity.ActionType.SCENE -> "scene"
            ButtonEntity.ActionType.SCRIPT -> "script"
            else -> button.domain
        }

        val service = when (button.actionType) {
            ButtonEntity.ActionType.SCENE -> "turn_on"
            ButtonEntity.ActionType.SCRIPT -> "turn_on"
            ButtonEntity.ActionType.TOGGLE_ENTITY -> "toggle"
            else -> button.service
        }

        callService(domain, service, button.entityId, button.serviceData)
    }

    /**
     * Call an HA service directly.
     */
    suspend fun callService(
        domain: String,
        service: String,
        entityId: String? = null,
        serviceData: String? = null
    ) {
        if (domain.isBlank() || service.isBlank()) {
            Log.w(TAG, "Cannot call service: domain or service is blank")
            return
        }

        // Build the payload
        val payload = buildPayload(entityId, serviceData)

        // Publish to HA service topic
        // Format: homeassistant/service/<domain>/<service>
        val topic = "homeassistant/$domain/$service"

        Log.d(TAG, "Calling service: $topic with payload: $payload")
        mqttManager.publish(topic, payload, qos = 1, retained = false)
    }

    private fun buildPayload(entityId: String?, serviceData: String?): String {
        val parts = mutableListOf<String>()

        if (!entityId.isNullOrBlank()) {
            parts.add("\"entity_id\":\"$entityId\"")
        }

        // If there's additional service data, try to merge it
        if (!serviceData.isNullOrBlank()) {
            // Remove outer braces if present and add the content
            val trimmed = serviceData.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val inner = trimmed.substring(1, trimmed.length - 1).trim()
                if (inner.isNotBlank()) {
                    parts.add(inner)
                }
            }
        }

        return "{${parts.joinToString(",")}}"
    }
}
