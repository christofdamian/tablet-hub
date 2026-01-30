package net.damian.tablethub.service.mqtt

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.damian.tablethub.data.repository.AlarmRepository
import net.damian.tablethub.service.alarm.AlarmReceiver
import net.damian.tablethub.service.alarm.AlarmService
import net.damian.tablethub.service.display.NightModeManager
import net.damian.tablethub.service.display.ScreenManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles incoming MQTT commands from Home Assistant.
 *
 * Command format (JSON):
 * {"command": "screen", "value": "ON"}
 * {"command": "brightness", "value": 128}
 * {"command": "trigger_alarm"}
 * {"command": "dismiss_alarm"}
 * {"command": "enable_alarm", "alarm_id": 1}
 * {"command": "disable_alarm", "alarm_id": 1}
 * {"command": "media_play"}
 * {"command": "media_pause"}
 * {"command": "media_next"}
 * {"command": "media_previous"}
 */
@Singleton
class MqttCommandHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mqttManager: MqttManager,
    private val alarmRepository: AlarmRepository,
    private val haStatePublisher: HaStatePublisher,
    private val nightModeManager: NightModeManager,
    private val mediaPlayerPublisher: dagger.Lazy<HaMediaPlayerPublisher>,
    private val moshi: Moshi
) {
    companion object {
        private const val TAG = "MqttCommandHandler"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startListening() {
        mqttManager.incomingMessages
            .onEach { message -> handleMessage(message) }
            .launchIn(scope)

        Log.d(TAG, "Started listening for commands")
    }

    private fun handleMessage(message: MqttIncomingMessage) {
        // Handle media player commands (legacy format)
        if (message.topic.endsWith("/media_player/set")) {
            handleMediaPlayerCommand(message.payload)
            return
        }

        // Handle media player commands (bkbilly/mqtt_media_player format)
        if (message.topic.contains("/media/cmd/")) {
            val command = message.topic.substringAfterLast("/")
            handleMediaPlayerCommandWithPayload(command, message.payload)
            return
        }

        if (!message.topic.endsWith("/command")) return

        Log.d(TAG, "Received command: ${message.payload}")

        try {
            val adapter = moshi.adapter(MqttCommand::class.java)
            val command = adapter.fromJson(message.payload) ?: return

            when (command.command) {
                "screen", "ON", "OFF" -> handleScreenCommand(command)
                "brightness" -> handleBrightnessCommand(command)
                "night_mode" -> handleNightModeCommand(command)
                "trigger_alarm" -> handleTriggerAlarm()
                "dismiss_alarm" -> handleDismissAlarm()
                "enable_alarm" -> handleEnableAlarm(command, true)
                "disable_alarm" -> handleEnableAlarm(command, false)
                "media_play", "media_pause", "media_stop",
                "media_next", "media_previous", "media_play_pause" -> handleMediaCommand(command.command)
                else -> Log.w(TAG, "Unknown command: ${command.command}")
            }
        } catch (e: Exception) {
            // Try parsing as simple ON/OFF for switch compatibility
            when (message.payload.trim().uppercase()) {
                "ON" -> handleScreenOn()
                "OFF" -> handleScreenOff()
                else -> Log.e(TAG, "Failed to parse command: ${message.payload}", e)
            }
        }
    }

    private fun handleMediaPlayerCommand(payload: String) {
        Log.d(TAG, "Received media player command: $payload")
        // Payload can be simple command like "play", "pause", etc.
        mediaPlayerPublisher.get().handleCommand(payload, null)
    }

    private fun handleMediaPlayerCommandWithPayload(command: String, payload: String) {
        Log.d(TAG, "Received media player command: $command, payload: $payload")
        mediaPlayerPublisher.get().handleCommand(command, payload)
    }

    private fun handleMediaCommand(command: String) {
        val mediaCommand = command.removePrefix("media_")
        mediaPlayerPublisher.get().handleCommand(mediaCommand, null)
    }

    private fun handleScreenCommand(command: MqttCommand) {
        val value = command.value?.toString()?.uppercase() ?: command.command.uppercase()
        when (value) {
            "ON" -> handleScreenOn()
            "OFF" -> handleScreenOff()
        }
    }

    private fun handleScreenOn() {
        Log.d(TAG, "Turning screen ON")
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        @Suppress("DEPRECATION")
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "TabletHub:ScreenOn"
        )
        wakeLock.acquire(1000)
        wakeLock.release()

        ScreenManager.setBrightness(255)
        haStatePublisher.updateScreenState(true, 255)
    }

    private fun handleScreenOff() {
        Log.d(TAG, "Turning screen OFF (setting minimum brightness)")
        // Android doesn't allow turning off screen programmatically without device admin
        // Instead, we set brightness to minimum
        ScreenManager.setBrightness(0)
        haStatePublisher.updateScreenState(false, 0)
    }

    private fun handleBrightnessCommand(command: MqttCommand) {
        val brightness = when (val value = command.value) {
            is Number -> value.toInt().coerceIn(0, 255)
            is String -> value.toIntOrNull()?.coerceIn(0, 255) ?: return
            else -> return
        }

        Log.d(TAG, "Setting brightness to $brightness")
        ScreenManager.setBrightness(brightness)
        haStatePublisher.updateScreenState(brightness > 0, brightness)
    }

    private fun handleNightModeCommand(command: MqttCommand) {
        val value = command.value?.toString()?.uppercase() ?: return
        when (value) {
            "ON" -> {
                Log.d(TAG, "Enabling night mode from HA")
                nightModeManager.setNightModeFromHa(true)
            }
            "OFF" -> {
                Log.d(TAG, "Disabling night mode from HA")
                nightModeManager.setNightModeFromHa(false)
            }
        }
    }

    private fun handleTriggerAlarm() {
        Log.d(TAG, "Triggering alarm from HA")
        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START_ALARM
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, -1L)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, "Triggered from Home Assistant")
        }
        context.startForegroundService(intent)
    }

    private fun handleDismissAlarm() {
        Log.d(TAG, "Dismissing alarm from HA")
        val intent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP_ALARM
        }
        context.startService(intent)
    }

    private fun handleEnableAlarm(command: MqttCommand, enabled: Boolean) {
        val alarmId = when (val id = command.alarmId) {
            is Number -> id.toLong()
            is String -> id.toLongOrNull() ?: return
            else -> return
        }

        Log.d(TAG, "${if (enabled) "Enabling" else "Disabling"} alarm $alarmId")
        scope.launch {
            alarmRepository.setAlarmEnabled(alarmId, enabled)
        }
    }
}

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class MqttCommand(
    val command: String,
    val value: Any? = null,
    @com.squareup.moshi.Json(name = "alarm_id") val alarmId: Any? = null
)
