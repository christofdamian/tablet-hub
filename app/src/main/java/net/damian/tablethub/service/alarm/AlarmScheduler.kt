package net.damian.tablethub.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import net.damian.tablethub.data.local.entity.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "AlarmScheduler"
        // Use a different request code range for pre-alarms to avoid conflicts
        private const val PRE_ALARM_REQUEST_CODE_OFFSET = 100000
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun scheduleAlarm(alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancelAlarm(alarm)
            return
        }

        val nextTriggerTime = calculateNextTriggerTime(alarm) ?: return
        val triggerMillis = nextTriggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Schedule main alarm
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            alarm.plexPlaylistId?.let { putExtra(AlarmReceiver.EXTRA_PLEX_PLAYLIST_ID, it) }
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setAlarmClock for highest priority (shows in status bar)
        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            triggerMillis,
            pendingIntent
        )

        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
        Log.d(TAG, "Scheduled alarm ${alarm.id} for $nextTriggerTime")

        // Schedule pre-alarm trigger if configured
        if (alarm.preAlarmMinutes > 0) {
            schedulePreAlarm(alarm, nextTriggerTime)
        }
    }

    /**
     * Schedule the pre-alarm trigger that fires N minutes before the actual alarm.
     * This publishes an MQTT event for Home Assistant automations (e.g., sunrise lights).
     */
    private fun schedulePreAlarm(alarm: AlarmEntity, alarmTime: LocalDateTime) {
        val preAlarmTime = alarmTime.minusMinutes(alarm.preAlarmMinutes.toLong())
        val now = LocalDateTime.now()

        // Only schedule if pre-alarm time is in the future
        if (preAlarmTime.isBefore(now) || preAlarmTime.isEqual(now)) {
            Log.d(TAG, "Pre-alarm time already passed for alarm ${alarm.id}, skipping")
            return
        }

        val preAlarmMillis = preAlarmTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val alarmTimeStr = alarmTime.toLocalTime().format(timeFormatter)

        val preAlarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PRE_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmReceiver.EXTRA_ALARM_LABEL, alarm.label)
            putExtra(AlarmReceiver.EXTRA_ALARM_TIME, alarmTimeStr)
            putExtra(AlarmReceiver.EXTRA_PRE_ALARM_MINUTES, alarm.preAlarmMinutes)
        }

        val preAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            PRE_ALARM_REQUEST_CODE_OFFSET + alarm.id.toInt(),
            preAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Use setExactAndAllowWhileIdle for pre-alarm (doesn't need to show in status bar)
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            preAlarmMillis,
            preAlarmPendingIntent
        )

        Log.d(TAG, "Scheduled pre-alarm for alarm ${alarm.id}: $preAlarmTime (${alarm.preAlarmMinutes} min before)")
    }

    fun cancelAlarm(alarm: AlarmEntity) {
        // Cancel main alarm
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        // Cancel pre-alarm
        cancelPreAlarm(alarm)

        Log.d(TAG, "Cancelled alarm ${alarm.id}")
    }

    private fun cancelPreAlarm(alarm: AlarmEntity) {
        val preAlarmIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_PRE_ALARM_FIRED
        }

        val preAlarmPendingIntent = PendingIntent.getBroadcast(
            context,
            PRE_ALARM_REQUEST_CODE_OFFSET + alarm.id.toInt(),
            preAlarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(preAlarmPendingIntent)
    }

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun calculateNextTriggerTime(alarm: AlarmEntity): LocalDateTime? {
        val now = LocalDateTime.now()
        val alarmTime = LocalTime.of(alarm.hour, alarm.minute)

        if (!alarm.isRepeating) {
            // One-time alarm
            val todayAlarm = LocalDateTime.of(LocalDate.now(), alarmTime)
            return if (todayAlarm.isAfter(now)) {
                todayAlarm
            } else {
                todayAlarm.plusDays(1)
            }
        }

        // Repeating alarm - find next active day
        for (dayOffset in 0..7) {
            val targetDate = LocalDate.now().plusDays(dayOffset.toLong())
            val targetDateTime = LocalDateTime.of(targetDate, alarmTime)

            if (targetDateTime.isBefore(now) || targetDateTime.isEqual(now)) {
                continue
            }

            val dayOfWeek = targetDate.dayOfWeek
            if (isDayActive(alarm, dayOfWeek)) {
                return targetDateTime
            }
        }

        return null
    }

    private fun isDayActive(alarm: AlarmEntity, dayOfWeek: DayOfWeek): Boolean {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> alarm.monday
            DayOfWeek.TUESDAY -> alarm.tuesday
            DayOfWeek.WEDNESDAY -> alarm.wednesday
            DayOfWeek.THURSDAY -> alarm.thursday
            DayOfWeek.FRIDAY -> alarm.friday
            DayOfWeek.SATURDAY -> alarm.saturday
            DayOfWeek.SUNDAY -> alarm.sunday
        }
    }
}
