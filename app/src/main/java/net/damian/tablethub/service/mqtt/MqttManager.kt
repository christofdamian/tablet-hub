package net.damian.tablethub.service.mqtt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.damian.tablethub.data.preferences.MqttConfig
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "MqttManager"
        private const val RECONNECT_DELAY_MS = 5000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mqttClient: MqttAsyncClient? = null
    private var currentConfig: MqttConfig? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    private val _connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Disconnected)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MqttIncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MqttIncomingMessage> = _incomingMessages.asSharedFlow()

    private val subscriptions = mutableSetOf<String>()

    fun connect(config: MqttConfig) {
        if (!config.isValid) {
            _connectionState.value = MqttConnectionState.Error("Invalid MQTT configuration")
            return
        }

        scope.launch {
            try {
                disconnect()
                currentConfig = config
                shouldReconnect = true
                reconnectAttempts = 0
                doConnect(config)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting connection", e)
                _connectionState.value = MqttConnectionState.Error(e.message ?: "Connection failed", e)
            }
        }
    }

    private fun doConnect(config: MqttConfig) {
        _connectionState.value = MqttConnectionState.Connecting

        try {
            val persistence = MemoryPersistence()
            mqttClient = MqttAsyncClient(config.serverUri, config.clientId, persistence).apply {
                setCallback(mqttCallback)
            }

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 30
                keepAliveInterval = 60
                isAutomaticReconnect = false // We handle reconnection ourselves

                if (config.username.isNotBlank()) {
                    userName = config.username
                    password = config.password.toCharArray()
                }
            }

            mqttClient?.connect(options, null, connectCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MQTT client", e)
            _connectionState.value = MqttConnectionState.Error(e.message ?: "Failed to create client", e)
            scheduleReconnect()
        }
    }

    fun disconnect() {
        shouldReconnect = false
        try {
            mqttClient?.let { client ->
                if (client.isConnected) {
                    client.disconnect()
                }
                client.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        mqttClient = null
        _connectionState.value = MqttConnectionState.Disconnected
    }

    fun publish(topic: String, payload: String, qos: Int = 1, retained: Boolean = false) {
        scope.launch {
            try {
                val client = mqttClient
                if (client?.isConnected == true) {
                    val message = MqttMessage(payload.toByteArray()).apply {
                        this.qos = qos
                        this.isRetained = retained
                    }
                    client.publish(topic, message)
                    Log.d(TAG, "Published to $topic: $payload")
                } else {
                    Log.w(TAG, "Cannot publish - not connected")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing to $topic", e)
            }
        }
    }

    fun subscribe(topic: String, qos: Int = 1) {
        subscriptions.add(topic)
        scope.launch {
            try {
                val client = mqttClient
                if (client?.isConnected == true) {
                    client.subscribe(topic, qos)
                    Log.d(TAG, "Subscribed to $topic")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error subscribing to $topic", e)
            }
        }
    }

    fun unsubscribe(topic: String) {
        subscriptions.remove(topic)
        scope.launch {
            try {
                val client = mqttClient
                if (client?.isConnected == true) {
                    client.unsubscribe(topic)
                    Log.d(TAG, "Unsubscribed from $topic")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error unsubscribing from $topic", e)
            }
        }
    }

    private fun resubscribeAll() {
        subscriptions.forEach { topic ->
            try {
                mqttClient?.subscribe(topic, 1)
                Log.d(TAG, "Resubscribed to $topic")
            } catch (e: Exception) {
                Log.e(TAG, "Error resubscribing to $topic", e)
            }
        }
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            _connectionState.value = MqttConnectionState.Error("Max reconnect attempts reached")
            return
        }

        reconnectAttempts++
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${RECONNECT_DELAY_MS}ms")

        scope.launch {
            delay(RECONNECT_DELAY_MS)
            currentConfig?.let { config ->
                if (shouldReconnect) {
                    doConnect(config)
                }
            }
        }
    }

    private val connectCallback = object : IMqttActionListener {
        override fun onSuccess(asyncActionToken: IMqttToken?) {
            Log.d(TAG, "Connected successfully")
            reconnectAttempts = 0
            _connectionState.value = MqttConnectionState.Connected
            resubscribeAll()
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(TAG, "Connection failed", exception)
            _connectionState.value = MqttConnectionState.Error(
                exception?.message ?: "Connection failed",
                exception as? Exception
            )
            scheduleReconnect()
        }
    }

    private val mqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            Log.w(TAG, "Connection lost", cause)
            _connectionState.value = MqttConnectionState.Error(
                cause?.message ?: "Connection lost",
                cause as? Exception
            )
            scheduleReconnect()
        }

        override fun messageArrived(topic: String, message: MqttMessage) {
            val payload = String(message.payload)
            Log.d(TAG, "Message received on $topic: $payload")
            scope.launch {
                _incomingMessages.emit(MqttIncomingMessage(topic, payload))
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {
            // Not used for our purposes
        }
    }
}

data class MqttIncomingMessage(
    val topic: String,
    val payload: String
)
