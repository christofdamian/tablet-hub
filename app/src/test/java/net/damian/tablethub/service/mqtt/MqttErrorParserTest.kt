package net.damian.tablethub.service.mqtt

import org.eclipse.paho.client.mqttv3.MqttException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MqttErrorParserTest {

    // parseErrorMessage tests

    @Test
    fun `parseErrorMessage returns Unknown error for null`() {
        assertEquals("Unknown error", MqttErrorParser.parseErrorMessage(null))
    }

    @Test
    fun `parseErrorMessage returns Connection failed for CLIENT_EXCEPTION`() {
        val exception = MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION.toInt())
        assertEquals("Connection failed", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Invalid client ID for INVALID_CLIENT_ID`() {
        val exception = MqttException(MqttException.REASON_CODE_INVALID_CLIENT_ID.toInt())
        assertEquals("Invalid client ID", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Broker unavailable for BROKER_UNAVAILABLE`() {
        val exception = MqttException(MqttException.REASON_CODE_BROKER_UNAVAILABLE.toInt())
        assertEquals("Broker unavailable", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Authentication failed for FAILED_AUTHENTICATION`() {
        val exception = MqttException(MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt())
        assertEquals("Authentication failed", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Not authorized for NOT_AUTHORIZED`() {
        val exception = MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())
        assertEquals("Not authorized", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Connection timeout for CLIENT_TIMEOUT`() {
        val exception = MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt())
        assertEquals("Connection timeout", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns SSL configuration error for SSL_CONFIG_ERROR`() {
        val exception = MqttException(MqttException.REASON_CODE_SSL_CONFIG_ERROR.toInt())
        assertEquals("SSL configuration error", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Cannot resolve host for UnknownHostException message`() {
        val exception = Exception("java.net.UnknownHostException: Unable to resolve host")
        assertEquals("Cannot resolve host", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Connection refused for ConnectException message`() {
        val exception = Exception("java.net.ConnectException: Connection refused")
        assertEquals("Connection refused", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Connection timeout for SocketTimeoutException message`() {
        val exception = Exception("java.net.SocketTimeoutException: connect timed out")
        assertEquals("Connection timeout", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns SSL handshake failed for SSLHandshakeException message`() {
        val exception = Exception("javax.net.ssl.SSLHandshakeException: certificate error")
        assertEquals("SSL handshake failed", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns exception message for unknown errors`() {
        val exception = Exception("Some custom error message")
        assertEquals("Some custom error message", MqttErrorParser.parseErrorMessage(exception))
    }

    @Test
    fun `parseErrorMessage returns Unknown error for exception with null message`() {
        val exception = Exception(null as String?)
        assertEquals("Unknown error", MqttErrorParser.parseErrorMessage(exception))
    }

    // isRecoverableError tests

    @Test
    fun `isRecoverableError returns true for null`() {
        assertTrue(MqttErrorParser.isRecoverableError(null))
    }

    @Test
    fun `isRecoverableError returns false for INVALID_CLIENT_ID`() {
        val exception = MqttException(MqttException.REASON_CODE_INVALID_CLIENT_ID.toInt())
        assertFalse(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns false for FAILED_AUTHENTICATION`() {
        val exception = MqttException(MqttException.REASON_CODE_FAILED_AUTHENTICATION.toInt())
        assertFalse(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns false for NOT_AUTHORIZED`() {
        val exception = MqttException(MqttException.REASON_CODE_NOT_AUTHORIZED.toInt())
        assertFalse(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns true for CLIENT_EXCEPTION`() {
        val exception = MqttException(MqttException.REASON_CODE_CLIENT_EXCEPTION.toInt())
        assertTrue(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns true for BROKER_UNAVAILABLE`() {
        val exception = MqttException(MqttException.REASON_CODE_BROKER_UNAVAILABLE.toInt())
        assertTrue(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns true for CLIENT_TIMEOUT`() {
        val exception = MqttException(MqttException.REASON_CODE_CLIENT_TIMEOUT.toInt())
        assertTrue(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns true for SERVER_CONNECT_ERROR`() {
        val exception = MqttException(MqttException.REASON_CODE_SERVER_CONNECT_ERROR.toInt())
        assertTrue(MqttErrorParser.isRecoverableError(exception))
    }

    @Test
    fun `isRecoverableError returns true for non-MQTT exceptions`() {
        val exception = Exception("Network error")
        assertTrue(MqttErrorParser.isRecoverableError(exception))
    }
}
