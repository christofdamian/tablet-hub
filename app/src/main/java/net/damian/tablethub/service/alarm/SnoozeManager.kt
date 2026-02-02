package net.damian.tablethub.service.alarm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class SnoozeInfo(
    val alarmId: Long,
    val alarmLabel: String,
    val snoozeEndTime: LocalDateTime
)

@Singleton
class SnoozeManager @Inject constructor() {

    private val _snoozeState = MutableStateFlow<SnoozeInfo?>(null)
    val snoozeState: StateFlow<SnoozeInfo?> = _snoozeState.asStateFlow()

    fun setSnooze(alarmId: Long, alarmLabel: String, snoozeEndTime: LocalDateTime) {
        _snoozeState.value = SnoozeInfo(alarmId, alarmLabel, snoozeEndTime)
    }

    fun clearSnooze() {
        _snoozeState.value = null
    }

    fun clearSnoozeForAlarm(alarmId: Long) {
        if (_snoozeState.value?.alarmId == alarmId) {
            _snoozeState.value = null
        }
    }
}
