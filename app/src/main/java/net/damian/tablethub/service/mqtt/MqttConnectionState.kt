package net.damian.tablethub.service.mqtt

/**
 * Represents the current state of the MQTT connection.
 */
sealed class MqttConnectionState {
    data object Disconnected : MqttConnectionState()

    data object Connecting : MqttConnectionState()

    data object Connected : MqttConnectionState()

    data class Reconnecting(
        val attempt: Int,
        val maxAttempts: Int,
        val nextRetryInSeconds: Int
    ) : MqttConnectionState()

    data class WaitingForNetwork(
        val attempt: Int
    ) : MqttConnectionState()

    data class Error(
        val message: String,
        val throwable: Throwable? = null,
        val isRecoverable: Boolean = true
    ) : MqttConnectionState()

    val isConnected: Boolean
        get() = this is Connected

    val statusText: String
        get() = when (this) {
            is Disconnected -> "Disconnected"
            is Connecting -> "Connecting..."
            is Connected -> "Connected"
            is Reconnecting -> "Reconnecting ($attempt/$maxAttempts)..."
            is WaitingForNetwork -> "Waiting for network..."
            is Error -> "Error: $message"
        }
}
