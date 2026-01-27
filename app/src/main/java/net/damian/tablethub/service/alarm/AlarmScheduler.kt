package net.damian.tablethub.service.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import net.damian.tablethub.data.local.entity.AlarmEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(alarm: AlarmEntity) {
        if (!alarm.enabled) {
            cancelAlarm(alarm)
            return
        }

        val nextTriggerTime = calculateNextTriggerTime(alarm) ?: return
        val triggerMillis = nextTriggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

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
    }

    fun cancelAlarm(alarm: AlarmEntity) {
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
