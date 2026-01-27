package net.damian.tablethub.service.mqtt

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
import net.damian.tablethub.service.network.NetworkMonitor
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

@Singleton
class MqttManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "MqttManager"
        private const val INITIAL_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 60000L
        private const val MAX_RECONNECT_ATTEMPTS = 20
        private const val RETRY_QUEUE_MAX_SIZE = 100
        private const val CONNECTION_TEST_TIMEOUT_MS = 10000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mqttClient: MqttAsyncClient? = null
    private var currentConfig: MqttConfig? = null
    private var reconnectAttempts = 0
    private var shouldReconnect = true
    private var reconnectJob: Job? = null
    private var networkObserverJob: Job? = null

    private val _connectionState = MutableStateFlow<MqttConnectionState>(MqttConnectionState.Disconnected)
    val connectionState: StateFlow<MqttConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<MqttIncomingMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<MqttIncomingMessage> = _incomingMessages.asSharedFlow()

    private val subscriptions = mutableSetOf<String>()

    // Queue for messages that failed to send (will retry on reconnect)
    private val pendingMessages = ConcurrentLinkedQueue<PendingMessage>()

    fun connect(config: MqttConfig) {
        if (!config.isValid) {
            _connectionState.value = MqttConnectionState.Error(
                "Invalid MQTT configuration",
                isRecoverable = false
            )
            return
        }

        scope.launch {
            try {
                disconnect()
                currentConfig = config
                shouldReconnect = true
                reconnectAttempts = 0

                // Start network monitoring
                startNetworkMonitoring()

                // Check network before connecting
                if (!networkMonitor.checkCurrentConnectivity()) {
                    Log.d(TAG, "No network connectivity, waiting...")
                    _connectionState.value = MqttConnectionState.WaitingForNetwork(0)
                    return@launch
                }

                doConnect(config)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting connection", e)
                _connectionState.value = MqttConnectionState.Error(
                    e.message ?: "Connection failed",
                    e
                )
            }
        }
    }

    private fun startNetworkMonitoring() {
        networkObserverJob?.cancel()
        networkMonitor.startMonitoring()

        networkObserverJob = scope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                Log.d(TAG, "Network state changed: connected=$isConnected")

                if (isConnected) {
                    val currentState = _connectionState.value
                    if (currentState is MqttConnectionState.WaitingForNetwork) {
                        Log.d(TAG, "Network restored, attempting to connect")
                        currentConfig?.let { doConnect(it) }
                    }
                } else {
                    // Network lost
                    val currentState = _connectionState.value
                    if (currentState is MqttConnectionState.Connected ||
                        currentState is MqttConnectionState.Reconnecting) {
                        _connectionState.value = MqttConnectionState.WaitingForNetwork(reconnectAttempts)
                        reconnectJob?.cancel()
                    }
                }
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
            handleConnectionFailure(e)
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        networkObserverJob?.cancel()
        networkMonitor.stopMonitoring()

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
                    Log.w(TAG, "Cannot publish - not connected, queueing message")
                    queueMessage(topic, payload, qos, retained)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error publishing to $topic", e)
                queueMessage(topic, payload, qos, retained)
            }
        }
    }

    private fun queueMessage(topic: String, payload: String, qos: Int, retained: Boolean) {
        if (pendingMessages.size >= RETRY_QUEUE_MAX_SIZE) {
            // Remove oldest message if queue is full
            pendingMessages.poll()
        }
        pendingMessages.offer(PendingMessage(topic, payload, qos, retained, System.currentTimeMillis()))
    }

    private fun flushPendingMessages() {
        val client = mqttClient ?: return
        if (!client.isConnected) return

        var message = pendingMessages.poll()
        while (message != null) {
            try {
                val mqttMessage = MqttMessage(message.payload.toByteArray()).apply {
                    this.qos = message.qos
                    this.isRetained = message.retained
                }
                client.publish(message.topic, mqttMessage)
                Log.d(TAG, "Flushed pending message to ${message.topic}")
            } catch (e: Exception) {
                Log.e(TAG, "Error flushing message to ${message.topic}", e)
                // Re-queue if still failing
                pendingMessages.offer(message)
                break
            }
            message = pendingMessages.poll()
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

    private fun handleConnectionFailure(throwable: Throwable?) {
        val errorMessage = MqttErrorParser.parseErrorMessage(throwable)
        val isRecoverable = MqttErrorParser.isRecoverableError(throwable)

        if (!isRecoverable) {
            _connectionState.value = MqttConnectionState.Error(
                errorMessage,
                throwable,
                isRecoverable = false
            )
            return
        }

        scheduleReconnect(errorMessage)
    }

    private fun scheduleReconnect(lastError: String? = null) {
        if (!shouldReconnect) return

        // Check network first
        if (!networkMonitor.checkCurrentConnectivity()) {
            _connectionState.value = MqttConnectionState.WaitingForNetwork(reconnectAttempts)
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached")
            _connectionState.value = MqttConnectionState.Error(
                "Max reconnect attempts reached. Last error: ${lastError ?: "unknown"}",
                isRecoverable = true
            )
            return
        }

        reconnectAttempts++

        // Exponential backoff with jitter
        val baseDelay = INITIAL_RECONNECT_DELAY_MS * 2.0.pow(reconnectAttempts - 1).toLong()
        val delayMs = min(baseDelay, MAX_RECONNECT_DELAY_MS)
        val jitter = (Math.random() * 0.2 * delayMs).toLong() // Add up to 20% jitter
        val finalDelay = delayMs + jitter

        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempts in ${finalDelay}ms")
        _connectionState.value = MqttConnectionState.Reconnecting(
            attempt = reconnectAttempts,
            maxAttempts = MAX_RECONNECT_ATTEMPTS,
            nextRetryInSeconds = (finalDelay / 1000).toInt()
        )

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(finalDelay)
            currentConfig?.let { config ->
                if (shouldReconnect && networkMonitor.checkCurrentConnectivity()) {
                    doConnect(config)
                }
            }
        }
    }

    /**
     * Test connection to MQTT broker with the given config.
     * Returns a result indicating success or failure with error message.
     */
    suspend fun testConnection(config: MqttConfig): ConnectionTestResult {
        if (!config.isValid) {
            return ConnectionTestResult.Failure("Invalid configuration")
        }

        if (!networkMonitor.checkCurrentConnectivity()) {
            return ConnectionTestResult.Failure("No network connectivity")
        }

        return try {
            val testClient = MqttAsyncClient(
                config.serverUri,
                "${config.clientId}_test",
                MemoryPersistence()
            )

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = (CONNECTION_TEST_TIMEOUT_MS / 1000)
                if (config.username.isNotBlank()) {
                    userName = config.username
                    password = config.password.toCharArray()
                }
            }

            var result: ConnectionTestResult = ConnectionTestResult.Failure("Timeout")

            val latch = java.util.concurrent.CountDownLatch(1)

            testClient.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    result = ConnectionTestResult.Success
                    try {
                        testClient.disconnect()
                        testClient.close()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                    latch.countDown()
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    result = ConnectionTestResult.Failure(MqttErrorParser.parseErrorMessage(exception))
                    try {
                        testClient.close()
                    } catch (e: Exception) {
                        // Ignore cleanup errors
                    }
                    latch.countDown()
                }
            })

            // Wait for result
            latch.await(CONNECTION_TEST_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            result
        } catch (e: Exception) {
            ConnectionTestResult.Failure(MqttErrorParser.parseErrorMessage(e))
        }
    }

    /**
     * Manually trigger a reconnection attempt.
     */
    fun reconnectNow() {
        if (_connectionState.value is MqttConnectionState.Connected) return

        reconnectAttempts = 0
        currentConfig?.let { config ->
            scope.launch {
                if (networkMonitor.checkCurrentConnectivity()) {
                    doConnect(config)
                } else {
                    _connectionState.value = MqttConnectionState.WaitingForNetwork(0)
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
            flushPendingMessages()
        }

        override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
            Log.e(TAG, "Connection failed", exception)
            handleConnectionFailure(exception)
        }
    }

    private val mqttCallback = object : MqttCallback {
        override fun connectionLost(cause: Throwable?) {
            Log.w(TAG, "Connection lost", cause)
            handleConnectionFailure(cause)
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

data class PendingMessage(
    val topic: String,
    val payload: String,
    val qos: Int,
    val retained: Boolean,
    val timestamp: Long
)

sealed class ConnectionTestResult {
    data object Success : ConnectionTestResult()
    data class Failure(val message: String) : ConnectionTestResult()
}
