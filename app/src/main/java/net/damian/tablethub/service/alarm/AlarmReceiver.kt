package net.damian.tablethub.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import net.damian.tablethub.service.mqtt.HaStatePublisher
import javax.inject.Inject

@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_ALARM_FIRED = "net.damian.tablethub.ACTION_ALARM_FIRED"
        const val ACTION_PRE_ALARM_FIRED = "net.damian.tablethub.ACTION_PRE_ALARM_FIRED"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_ALARM_TIME = "extra_alarm_time"
        const val EXTRA_PRE_ALARM_MINUTES = "extra_pre_alarm_minutes"
        const val EXTRA_PLEX_PLAYLIST_ID = "extra_plex_playlist_id"
    }

    @Inject
    lateinit var haStatePublisher: HaStatePublisher

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_FIRED -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
                val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
                val plexPlaylistId = intent.getStringExtra(EXTRA_PLEX_PLAYLIST_ID)

                Log.d(TAG, "Alarm fired: id=$alarmId, label=$alarmLabel")

                // Start the alarm service
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_START_ALARM
                    putExtra(EXTRA_ALARM_ID, alarmId)
                    putExtra(EXTRA_ALARM_LABEL, alarmLabel)
                    plexPlaylistId?.let { putExtra(AlarmService.EXTRA_PLEX_PLAYLIST_ID, it) }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }

            ACTION_PRE_ALARM_FIRED -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
                val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
                val alarmTime = intent.getStringExtra(EXTRA_ALARM_TIME) ?: ""
                val minutesUntil = intent.getIntExtra(EXTRA_PRE_ALARM_MINUTES, 0)

                Log.d(TAG, "Pre-alarm fired: id=$alarmId, time=$alarmTime, minutes=$minutesUntil")

                // Publish pre-alarm event to MQTT for HA automations
                haStatePublisher.publishPreAlarmEvent(
                    alarmId = alarmId,
                    alarmTime = alarmTime,
                    alarmLabel = alarmLabel,
                    minutesUntil = minutesUntil
                )
            }

            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule alarms after reboot
                // This will be handled by a WorkManager job or similar
                Log.d(TAG, "Boot completed - alarms should be rescheduled")
            }
        }
    }
}
