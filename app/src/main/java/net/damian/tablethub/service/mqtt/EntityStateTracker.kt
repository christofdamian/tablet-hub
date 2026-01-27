package net.damian.tablethub.service.mqtt

import android.util.Log
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks Home Assistant entity states via MQTT.
 *
 * Subscribes to state topics and maintains a map of entity_id -> state.
 * Buttons can query this to show visual state indication (on/off).
 *
 * Supports two approaches:
 * 1. MQTT Statestream: homeassistant/statestream/<domain>/<entity>/state
 * 2. Direct state requests via MQTT service calls
 */
@Singleton
class EntityStateTracker @Inject constructor(
    private val mqttManager: MqttManager,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "EntityStateTracker"
        private const val STATESTREAM_TOPIC = "homeassistant/statestream/#"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Map of entity_id -> EntityState
    private val _entityStates = MutableStateFlow<Map<String, EntityState>>(emptyMap())
    val entityStates: StateFlow<Map<String, EntityState>> = _entityStates.asStateFlow()

    // Set of entity IDs we're tracking
    private val trackedEntities = mutableSetOf<String>()

    private var isListening = false

    /**
     * Start listening for entity state updates.
     */
    fun startListening() {
        if (isListening) return
        isListening = true

        // Subscribe to statestream topic (if HA has it configured)
        mqttManager.subscribe(STATESTREAM_TOPIC)

        // Listen for incoming messages
        mqttManager.incomingMessages
            .onEach { message -> handleMessage(message) }
            .launchIn(scope)

        Log.d(TAG, "Started listening for entity states")
    }

    /**
     * Add an entity to track.
     */
    fun trackEntity(entityId: String) {
        if (entityId.isBlank()) return
        trackedEntities.add(entityId)
        Log.d(TAG, "Now tracking entity: $entityId")
    }

    /**
     * Remove an entity from tracking.
     */
    fun untrackEntity(entityId: String) {
        trackedEntities.remove(entityId)
        _entityStates.update { it - entityId }
    }

    /**
     * Get the state of a specific entity.
     */
    fun getEntityState(entityId: String): EntityState? {
        return _entityStates.value[entityId]
    }

    /**
     * Check if an entity is "on" (for binary states like lights, switches).
     */
    fun isEntityOn(entityId: String): Boolean {
        val state = _entityStates.value[entityId] ?: return false
        return state.state.lowercase() in listOf("on", "true", "home", "open", "playing")
    }

    /**
     * Manually update an entity's state (e.g., from optimistic updates after button press).
     */
    fun setEntityState(entityId: String, state: String) {
        _entityStates.update { states ->
            states + (entityId to EntityState(
                entityId = entityId,
                state = state,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Toggle an entity's optimistic state (for immediate UI feedback).
     */
    fun toggleEntityOptimistic(entityId: String) {
        val currentState = _entityStates.value[entityId]
        val newState = if (currentState?.state?.lowercase() == "on") "off" else "on"
        setEntityState(entityId, newState)
    }

    private fun handleMessage(message: MqttIncomingMessage) {
        // Parse statestream messages
        // Format: homeassistant/statestream/<domain>/<object_id>/state
        if (message.topic.startsWith("homeassistant/statestream/")) {
            parseStatestreamMessage(message)
            return
        }

        // Also handle direct state messages for entities we're tracking
        // Format varies by integration
    }

    private fun parseStatestreamMessage(message: MqttIncomingMessage) {
        try {
            val parts = message.topic.split("/")
            if (parts.size < 4) return

            // Extract domain and entity name
            val domain = parts[2]
            val entityName = parts[3]
            val entityId = "$domain.$entityName"

            // Only process if we're tracking this entity
            if (entityId !in trackedEntities && trackedEntities.isNotEmpty()) {
                return
            }

            // The payload could be just the state value or a JSON object
            val state = parseStatePayload(message.payload)

            _entityStates.update { states ->
                states + (entityId to EntityState(
                    entityId = entityId,
                    state = state,
                    lastUpdated = System.currentTimeMillis()
                ))
            }

            Log.d(TAG, "Updated state for $entityId: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing statestream message", e)
        }
    }

    private fun parseStatePayload(payload: String): String {
        // Try to parse as JSON first
        return try {
            if (payload.trim().startsWith("{")) {
                // Extract "state" field from JSON
                val adapter = moshi.adapter(StatePayload::class.java)
                adapter.fromJson(payload)?.state ?: payload
            } else {
                // Plain text state
                payload.trim()
            }
        } catch (e: Exception) {
            payload.trim()
        }
    }
}

/**
 * Represents the state of a Home Assistant entity.
 */
data class EntityState(
    val entityId: String,
    val state: String,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isOn: Boolean
        get() = state.lowercase() in listOf("on", "true", "home", "open", "playing", "unlocked")

    val isOff: Boolean
        get() = state.lowercase() in listOf("off", "false", "away", "closed", "paused", "locked", "unavailable")
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
internal data class StatePayload(
    val state: String? = null
)
