package net.damian.tablethub.service.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.damian.tablethub.AlarmFiringActivity
import net.damian.tablethub.R
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.plex.PlexRepository
import net.damian.tablethub.service.mqtt.HaStatePublisher
import net.damian.tablethub.service.music.PlaybackManager
import javax.inject.Inject

@AndroidEntryPoint
class AlarmService : Service() {

    companion object {
        private const val TAG = "AlarmService"
        const val ACTION_START_ALARM = "net.damian.tablethub.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "net.damian.tablethub.ACTION_STOP_ALARM"
        const val ACTION_SNOOZE_ALARM = "net.damian.tablethub.ACTION_SNOOZE_ALARM"

        const val EXTRA_PLEX_PLAYLIST_ID = "plex_playlist_id"

        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
        const val SNOOZE_DURATION_MINUTES = 9
    }

    @Inject
    lateinit var haStatePublisher: HaStatePublisher

    @Inject
    lateinit var playbackManager: PlaybackManager

    @Inject
    lateinit var plexRepository: PlexRepository

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    @Inject
    lateinit var alarmScheduler: AlarmScheduler

    @Inject
    lateinit var snoozeManager: SnoozeManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaPlayer: MediaPlayer? = null
    private var usingPlexPlayback: Boolean = false
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Long = -1
    private var currentAlarmLabel: String = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
                val alarmLabel = intent.getStringExtra(AlarmReceiver.EXTRA_ALARM_LABEL) ?: ""
                val plexPlaylistId = intent.getStringExtra(EXTRA_PLEX_PLAYLIST_ID)
                currentAlarmId = alarmId
                currentAlarmLabel = alarmLabel

                // Clear any active snooze since the alarm is now firing
                snoozeManager.clearSnoozeForAlarm(alarmId)

                startForeground(NOTIFICATION_ID, createNotification(alarmLabel))

                // Check if on-device alarm sound is enabled
                val soundEnabled = runBlocking { settingsDataStore.alarmSoundEnabled.first() }
                if (soundEnabled) {
                    if (plexPlaylistId != null) {
                        startPlexPlaylistAlarm(plexPlaylistId)
                    } else {
                        startAlarmSound()
                    }
                    startVibration()
                }
                // Always show the alarm UI (snooze/dismiss)
                launchAlarmActivity(alarmId, alarmLabel)

                // Publish alarm ringing state to HA
                haStatePublisher.updateAlarmRinging(true, alarmId)
            }

            ACTION_STOP_ALARM -> {
                stopAlarm()
                haStatePublisher.updateAlarmRinging(false)
                stopSelf()
            }

            ACTION_SNOOZE_ALARM -> {
                val alarmId = intent.getLongExtra(AlarmReceiver.EXTRA_ALARM_ID, -1)
                val snoozeMinutes = snoozeAlarm(alarmId)
                stopAlarm()
                haStatePublisher.updateAlarmRinging(false)
                haStatePublisher.publishSnoozeEvent(alarmId, snoozeMinutes)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarm notifications"
                setSound(null, null) // We handle sound separately
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(label: String): Notification {
        val dismissIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_STOP_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            0,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, AlarmFiringActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarm")
            .setContentText(label.ifEmpty { "Alarm is ringing" })
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun startAlarmSound() {
        usingPlexPlayback = false
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlexPlaylistAlarm(playlistId: String) {
        usingPlexPlayback = true
        Log.d(TAG, "Starting Plex playlist alarm: $playlistId")

        serviceScope.launch {
            try {
                val result = plexRepository.getPlaylistTracks(playlistId)
                result.onSuccess { tracks ->
                    if (tracks.isNotEmpty()) {
                        Log.d(TAG, "Loaded ${tracks.size} tracks from playlist")
                        // Shuffle the playlist and play
                        val shuffledTracks = tracks.shuffled()
                        playbackManager.playQueue(shuffledTracks, 0)
                    } else {
                        Log.w(TAG, "Playlist is empty, falling back to default alarm")
                        startAlarmSound()
                    }
                }.onFailure { error ->
                    Log.e(TAG, "Failed to load playlist: ${error.message}")
                    startAlarmSound()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Plex playlist alarm", e)
                startAlarmSound()
            }
        }
    }

    private fun startVibration() {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val pattern = longArrayOf(0, 500, 500) // Wait 0ms, vibrate 500ms, pause 500ms
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        // Stop MediaPlayer if using default alarm sound
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null

        // Stop Plex playback if using playlist
        if (usingPlexPlayback) {
            playbackManager.stop()
            usingPlexPlayback = false
        }

        vibrator?.cancel()
        vibrator = null
    }

    private fun launchAlarmActivity(alarmId: Long, alarmLabel: String) {
        val intent = Intent(this, AlarmFiringActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarmLabel)
        }
        startActivity(intent)
    }

    private fun snoozeAlarm(alarmId: Long): Int {
        val snoozeMinutes = SNOOZE_DURATION_MINUTES
        alarmScheduler.scheduleSnooze(alarmId, currentAlarmLabel, snoozeMinutes)
        Log.d(TAG, "Alarm $alarmId snoozed for $snoozeMinutes minutes")
        return snoozeMinutes
    }
}
