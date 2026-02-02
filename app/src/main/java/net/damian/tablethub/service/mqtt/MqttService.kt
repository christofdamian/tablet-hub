package net.damian.tablethub.service.mqtt

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.damian.tablethub.MainActivity
import net.damian.tablethub.R
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.data.repository.ButtonRepository
import net.damian.tablethub.service.display.NightModeManager
import net.damian.tablethub.service.tts.TtsManager
import javax.inject.Inject

@AndroidEntryPoint
class MqttService : Service() {

    companion object {
        private const val TAG = "MqttService"
        private const val CHANNEL_ID = "mqtt_service_channel"
        private const val NOTIFICATION_ID = 2001

        const val ACTION_START = "net.damian.tablethub.MQTT_START"
        const val ACTION_STOP = "net.damian.tablethub.MQTT_STOP"
        const val ACTION_RECONNECT = "net.damian.tablethub.MQTT_RECONNECT"

        fun start(context: Context) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MqttService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var mqttManager: MqttManager

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var haDiscovery: HaDiscovery

    @Inject
    lateinit var haStatePublisher: HaStatePublisher

    @Inject
    lateinit var commandHandler: MqttCommandHandler

    @Inject
    lateinit var mediaPlayerPublisher: HaMediaPlayerPublisher

    @Inject
    lateinit var nightModeManager: NightModeManager

    @Inject
    lateinit var entityStateTracker: EntityStateTracker

    @Inject
    lateinit var buttonRepository: ButtonRepository

    @Inject
    lateinit var ttsManager: TtsManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var discoveryPublished = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification("Connecting..."))
                ttsManager.initialize()
                startMqttConnection()
            }
            ACTION_STOP -> {
                stopMqttConnection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_RECONNECT -> {
                reconnect()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        mqttManager.disconnect()
        ttsManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMqttConnection() {
        serviceScope.launch {
            // Observe connection state and update notification
            launch {
                mqttManager.connectionState.collectLatest { state ->
                    val statusText = when (state) {
                        is MqttConnectionState.Connected -> {
                            // Publish HA discovery messages on first connection
                            if (!discoveryPublished) {
                                publishDiscoveryMessages()
                            }
                            "Connected"
                        }
                        is MqttConnectionState.Connecting -> "Connecting..."
                        is MqttConnectionState.Disconnected -> "Disconnected"
                        is MqttConnectionState.Reconnecting ->
                            "Reconnecting (${state.attempt}/${state.maxAttempts})..."
                        is MqttConnectionState.WaitingForNetwork -> "Waiting for network..."
                        is MqttConnectionState.Error -> "Error: ${state.message}"
                    }
                    updateNotification(statusText)
                }
            }

            // Get config and connect
            val config = settingsDataStore.mqttConfig.first()
            if (config.isValid && config.enabled) {
                mqttManager.connect(config)
            } else {
                Log.d(TAG, "MQTT not configured or disabled")
                updateNotification("Not configured")
            }
        }
    }

    private fun publishDiscoveryMessages() {
        serviceScope.launch {
            try {
                haDiscovery.publishAllDiscoveryMessages()
                buttonRepository.publishAllButtonDiscovery()
                discoveryPublished = true
                Log.d(TAG, "HA discovery messages published")

                // Publish initial state
                haStatePublisher.refreshAndPublish()

                // Wire up night mode state changes to HA publisher
                nightModeManager.onNightModeChanged = { isActive ->
                    haStatePublisher.updateNightModeState(isActive)
                }

                // Start media player publisher
                mediaPlayerPublisher.startPublishing()

                // Subscribe to command topics and start listening
                val deviceId = settingsDataStore.deviceId.first()
                mqttManager.subscribe("tablethub/$deviceId/command")
                mqttManager.subscribe("tablethub/$deviceId/media_player/set")
                // Subscribe to media player command topics (for bkbilly/mqtt_media_player)
                mqttManager.subscribe("tablethub/$deviceId/media/cmd/#")
                // Subscribe to night mode command topic
                mqttManager.subscribe("tablethub/$deviceId/night_mode/set")
                // Subscribe to color temperature command topic
                mqttManager.subscribe("tablethub/$deviceId/color_temp/set")
                // Subscribe to TTS topic
                mqttManager.subscribe("tablethub/$deviceId/tts")
                commandHandler.startListening()
                Log.d(TAG, "Subscribed to command topics")

                // Start entity state tracking for button state reflection
                entityStateTracker.startListening()
                Log.d(TAG, "Entity state tracker started")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to publish discovery messages", e)
            }
        }
    }

    private fun stopMqttConnection() {
        mqttManager.disconnect()
    }

    private fun reconnect() {
        serviceScope.launch {
            val config = settingsDataStore.mqttConfig.first()
            if (config.isValid && config.enabled) {
                mqttManager.connect(config)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MQTT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains connection to Home Assistant"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TabletHub")
            .setContentText("MQTT: $statusText")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(statusText))
    }
}
