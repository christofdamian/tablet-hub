package net.damian.tablethub.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "",
    val enabled: Boolean = true,
    val monday: Boolean = false,
    val tuesday: Boolean = false,
    val wednesday: Boolean = false,
    val thursday: Boolean = false,
    val friday: Boolean = false,
    val saturday: Boolean = false,
    val sunday: Boolean = false,
    val preAlarmMinutes: Int = 15,
    val snoozeDurationMinutes: Int = 9
) {
    val activeDays: List<Boolean>
        get() = listOf(monday, tuesday, wednesday, thursday, friday, saturday, sunday)

    val isRepeating: Boolean
        get() = activeDays.any { it }

    fun getActiveDaysText(): String {
        if (!isRepeating) return "Once"

        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val activeDayNames = activeDays.mapIndexedNotNull { index, active ->
            if (active) days[index] else null
        }

        return when {
            activeDayNames.size == 7 -> "Every day"
            activeDayNames == listOf("Mon", "Tue", "Wed", "Thu", "Fri") -> "Weekdays"
            activeDayNames == listOf("Sat", "Sun") -> "Weekends"
            else -> activeDayNames.joinToString(", ")
        }
    }

    fun getTimeString(): String {
        return "%02d:%02d".format(hour, minute)
    }
}
