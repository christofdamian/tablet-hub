package net.damian.tablethub.service.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ALARM_FIRED -> {
                val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1)
                val alarmLabel = intent.getStringExtra(EXTRA_ALARM_LABEL) ?: ""
                val plexPlaylistId = intent.getStringExtra(EXTRA_PLEX_PLAYLIST_ID)

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

            Intent.ACTION_BOOT_COMPLETED -> {
                // Re-schedule alarms after reboot
                // This will be handled by a WorkManager job or similar
            }
        }
    }

    companion object {
        const val ACTION_ALARM_FIRED = "net.damian.tablethub.ACTION_ALARM_FIRED"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_LABEL = "extra_alarm_label"
        const val EXTRA_PLEX_PLAYLIST_ID = "extra_plex_playlist_id"
    }
}
