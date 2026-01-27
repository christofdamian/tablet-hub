package net.damian.tablethub.service.mqtt

import org.eclipse.paho.client.mqttv3.MqttException

/**
 * Parses MQTT exceptions into user-friendly messages and determines recoverability.
 */
object MqttErrorParser {

    /**
     * Convert a throwable to a user-friendly error message.
     */
    fun parseErrorMessage(throwable: Throwable?): String {
        if (throwable == null) return "Unknown error"

        return when {
            throwable is MqttException -> parseMqttException(throwable)
            throwable.message?.contains("UnknownHostException", ignoreCase = true) == true ->
                "Cannot resolve host"
            throwable.message?.contains("ConnectException", ignoreCase = true) == true ->
                "Connection refused"
            throwable.message?.contains("SocketTimeoutException", ignoreCase = true) == true ->
                "Connection timeout"
            throwable.message?.contains("SSLHandshakeException", ignoreCase = true) == true ->
                "SSL handshake failed"
            else -> throwable.message ?: "Unknown error"
        }
    }

    private fun parseMqttException(exception: MqttException): String {
        return when (exception.reasonCode.toShort()) {
            MqttException.REASON_CODE_CLIENT_EXCEPTION -> "Connection failed"
            MqttException.REASON_CODE_INVALID_CLIENT_ID -> "Invalid client ID"
            MqttException.REASON_CODE_BROKER_UNAVAILABLE -> "Broker unavailable"
            MqttException.REASON_CODE_FAILED_AUTHENTICATION -> "Authentication failed"
            MqttException.REASON_CODE_NOT_AUTHORIZED -> "Not authorized"
            MqttException.REASON_CODE_CONNECT_IN_PROGRESS -> "Connection in progress"
            MqttException.REASON_CODE_CLIENT_CONNECTED -> "Already connected"
            MqttException.REASON_CODE_CLIENT_DISCONNECTING -> "Disconnecting"
            MqttException.REASON_CODE_SERVER_CONNECT_ERROR -> "Server connection error"
            MqttException.REASON_CODE_CLIENT_TIMEOUT -> "Connection timeout"
            MqttException.REASON_CODE_SOCKET_FACTORY_MISMATCH -> "SSL configuration error"
            MqttException.REASON_CODE_SSL_CONFIG_ERROR -> "SSL configuration error"
            else -> exception.message ?: "MQTT error (${exception.reasonCode})"
        }
    }

    /**
     * Determine if an error is recoverable (should trigger reconnection attempts).
     * Non-recoverable errors are typically configuration issues that won't be fixed by retrying.
     */
    fun isRecoverableError(throwable: Throwable?): Boolean {
        if (throwable == null) return true

        if (throwable is MqttException) {
            return when (throwable.reasonCode.toShort()) {
                MqttException.REASON_CODE_INVALID_CLIENT_ID,
                MqttException.REASON_CODE_FAILED_AUTHENTICATION,
                MqttException.REASON_CODE_NOT_AUTHORIZED -> false
                else -> true
            }
        }
        return true
    }
}
