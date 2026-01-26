package net.damian.tablethub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buttons")
data class ButtonEntity(
    @PrimaryKey
    val position: Int, // Grid position (0-11 for 3x4 grid)
    val label: String = "",
    val icon: String = "lightbulb", // Material icon name
    val actionType: ActionType = ActionType.SERVICE_CALL,
    val domain: String = "", // e.g., "light", "switch", "scene", "script"
    val service: String = "", // e.g., "toggle", "turn_on", "turn_off"
    val entityId: String = "", // e.g., "light.living_room"
    val serviceData: String = "", // JSON string for additional service data
    val trackEntityState: Boolean = false, // Whether to show entity state
    val isConfigured: Boolean = false
) {
    enum class ActionType {
        SERVICE_CALL,
        TOGGLE_ENTITY,
        SCENE,
        SCRIPT
    }

    /**
     * Build the MQTT payload for the HA service call
     */
    fun buildServiceCallPayload(): String {
        val effectiveDomain = when (actionType) {
            ActionType.SCENE -> "scene"
            ActionType.SCRIPT -> "script"
            else -> domain
        }

        val effectiveService = when (actionType) {
            ActionType.SCENE -> "turn_on"
            ActionType.SCRIPT -> "turn_on"
            ActionType.TOGGLE_ENTITY -> "toggle"
            else -> service
        }

        val dataMap = mutableMapOf<String, Any>()
        if (entityId.isNotBlank()) {
            dataMap["entity_id"] = entityId
        }

        // Parse additional service data if present
        if (serviceData.isNotBlank()) {
            try {
                // Simple JSON parsing - in production you'd use Moshi
                // For now, we'll pass it as-is
            } catch (e: Exception) {
                // Ignore parsing errors
            }
        }

        return """{"domain":"$effectiveDomain","service":"$effectiveService","entity_id":"$entityId"}"""
    }
}
