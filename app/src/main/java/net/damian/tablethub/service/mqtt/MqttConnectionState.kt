package net.damian.tablethub.service.mqtt

sealed class MqttConnectionState {
    data object Disconnected : MqttConnectionState()
    data object Connecting : MqttConnectionState()
    data object Connected : MqttConnectionState()
    data class Error(val message: String, val throwable: Throwable? = null) : MqttConnectionState()
}
