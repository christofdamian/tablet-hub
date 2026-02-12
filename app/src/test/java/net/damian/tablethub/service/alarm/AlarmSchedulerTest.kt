package net.damian.tablethub.service.alarm

import net.damian.tablethub.data.local.entity.AlarmEntity
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class AlarmSchedulerTest {

    /**
     * Regression test for the alarm rescheduling bug:
     * When a repeating alarm fires and scheduleAlarm is called again,
     * calculateNextTriggerTime must return a future time (the next occurrence),
     * not null or the just-passed time.
     */
    @Test
    fun `repeating alarm calculates next future occurrence after firing`() {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val dayOfWeek = today.dayOfWeek

        // Create a repeating alarm for today's day-of-week, set to 1 minute ago
        // (simulating that the alarm just fired)
        val pastTime = now.minusMinutes(1)
        val alarm = createAlarmForDay(pastTime.hour, pastTime.minute, dayOfWeek)

        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)

        assertNotNull("Repeating alarm should have a next trigger time after firing", nextTrigger)
        assertTrue(
            "Next trigger time should be in the future (next week), got $nextTrigger",
            nextTrigger!!.isAfter(now)
        )
    }

    @Test
    fun `repeating alarm with multiple days schedules next active day`() {
        val now = LocalDateTime.now()
        val today = LocalDate.now()
        val todayDow = today.dayOfWeek
        val tomorrowDow = today.plusDays(1).dayOfWeek

        // Create alarm for today (past) and tomorrow, set to 1 minute ago
        val pastTime = now.minusMinutes(1)
        val alarm = createAlarmForDays(pastTime.hour, pastTime.minute, listOf(todayDow, tomorrowDow))

        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)

        assertNotNull("Should find next active day", nextTrigger)
        assertTrue("Next trigger should be tomorrow", nextTrigger!!.isAfter(now))
        // Should be tomorrow, not next week
        assertTrue(
            "Next trigger should be within 2 days",
            nextTrigger.isBefore(now.plusDays(2))
        )
    }

    @Test
    fun `one-time alarm past today schedules for tomorrow`() {
        val now = LocalDateTime.now()
        val pastTime = now.minusMinutes(1)

        // One-time alarm (no days selected) set to 1 minute ago
        val alarm = AlarmEntity(
            id = 1,
            hour = pastTime.hour,
            minute = pastTime.minute,
            enabled = true
        )

        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)

        assertNotNull("One-time alarm should schedule for tomorrow", nextTrigger)
        assertTrue("Should be in the future", nextTrigger!!.isAfter(now))
    }

    @Test
    fun `future alarm schedules for today`() {
        val now = LocalDateTime.now()
        val futureTime = now.plusMinutes(30)

        val alarm = AlarmEntity(
            id = 1,
            hour = futureTime.hour,
            minute = futureTime.minute,
            enabled = true
        )

        val nextTrigger = AlarmScheduler.calculateNextTriggerTime(alarm)

        assertNotNull(nextTrigger)
        assertTrue("Should be today", nextTrigger!!.toLocalDate() == LocalDate.now())
    }

    private fun createAlarmForDay(hour: Int, minute: Int, day: DayOfWeek): AlarmEntity {
        return createAlarmForDays(hour, minute, listOf(day))
    }

    private fun createAlarmForDays(hour: Int, minute: Int, days: List<DayOfWeek>): AlarmEntity {
        return AlarmEntity(
            id = 1,
            hour = hour,
            minute = minute,
            enabled = true,
            monday = DayOfWeek.MONDAY in days,
            tuesday = DayOfWeek.TUESDAY in days,
            wednesday = DayOfWeek.WEDNESDAY in days,
            thursday = DayOfWeek.THURSDAY in days,
            friday = DayOfWeek.FRIDAY in days,
            saturday = DayOfWeek.SATURDAY in days,
            sunday = DayOfWeek.SUNDAY in days
        )
    }
}
