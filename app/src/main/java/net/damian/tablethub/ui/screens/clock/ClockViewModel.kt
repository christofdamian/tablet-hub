package net.damian.tablethub.ui.screens.clock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.damian.tablethub.data.local.entity.AlarmEntity
import net.damian.tablethub.data.repository.AlarmRepository
import net.damian.tablethub.service.alarm.AlarmScheduler
import net.damian.tablethub.service.mqtt.HaStatePublisher
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class ClockViewModel @Inject constructor(
    private val alarmRepository: AlarmRepository,
    private val alarmScheduler: AlarmScheduler,
    private val haStatePublisher: HaStatePublisher
) : ViewModel() {

    val alarms: StateFlow<List<AlarmEntity>> = alarmRepository.getAllAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nextAlarmText: StateFlow<String?> = alarmRepository.getEnabledAlarms()
        .map { alarms -> calculateNextAlarm(alarms) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Observe alarms and publish state updates to HA
        viewModelScope.launch {
            alarms.collect { alarmList ->
                haStatePublisher.updateAlarms(alarmList)
            }
        }
        viewModelScope.launch {
            alarmRepository.getEnabledAlarms().collect { enabledAlarms ->
                val nextAlarmInfo = calculateNextAlarmInfo(enabledAlarms)
                haStatePublisher.updateNextAlarm(
                    alarmTime = nextAlarmInfo?.first?.getTimeString(),
                    alarmLabel = nextAlarmInfo?.first?.label,
                    alarmId = nextAlarmInfo?.first?.id
                )
            }
        }
    }

    private val _editingAlarm = MutableStateFlow<AlarmEntity?>(null)
    val editingAlarm: StateFlow<AlarmEntity?> = _editingAlarm.asStateFlow()

    private val _showAlarmEditor = MutableStateFlow(false)
    val showAlarmEditor: StateFlow<Boolean> = _showAlarmEditor.asStateFlow()

    fun createNewAlarm() {
        val now = LocalTime.now()
        _editingAlarm.value = AlarmEntity(
            hour = now.hour,
            minute = now.minute
        )
        _showAlarmEditor.value = true
    }

    fun editAlarm(alarm: AlarmEntity) {
        _editingAlarm.value = alarm
        _showAlarmEditor.value = true
    }

    fun dismissEditor() {
        _showAlarmEditor.value = false
        _editingAlarm.value = null
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            val savedAlarm = if (alarm.id == 0L) {
                val newId = alarmRepository.insertAlarm(alarm)
                alarm.copy(id = newId)
            } else {
                alarmRepository.updateAlarm(alarm)
                alarm
            }
            alarmScheduler.scheduleAlarm(savedAlarm)
            dismissEditor()
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            alarmScheduler.cancelAlarm(alarm)
            alarmRepository.deleteAlarm(alarm)
        }
    }

    fun toggleAlarmEnabled(alarm: AlarmEntity) {
        viewModelScope.launch {
            val newEnabled = !alarm.enabled
            alarmRepository.setAlarmEnabled(alarm.id, newEnabled)
            val updatedAlarm = alarm.copy(enabled = newEnabled)
            alarmScheduler.scheduleAlarm(updatedAlarm)
        }
    }

    private fun calculateNextAlarmInfo(alarms: List<AlarmEntity>): Pair<AlarmEntity, Int>? {
        if (alarms.isEmpty()) return null

        val now = LocalTime.now()
        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek

        data class AlarmCandidate(val alarm: AlarmEntity, val daysUntil: Int)

        val candidates = alarms.flatMap { alarm ->
            val alarmTime = LocalTime.of(alarm.hour, alarm.minute)

            if (!alarm.isRepeating) {
                val daysUntil = if (alarmTime.isAfter(now)) 0 else 1
                listOf(AlarmCandidate(alarm, daysUntil))
            } else {
                (0..6).mapNotNull { dayOffset ->
                    val targetDay = currentDayOfWeek.plus(dayOffset.toLong())
                    val dayIndex = (targetDay.value - 1)
                    val isActive = alarm.activeDays[dayIndex]

                    if (isActive) {
                        val daysUntil = if (dayOffset == 0 && alarmTime.isAfter(now)) {
                            0
                        } else if (dayOffset == 0) {
                            7
                        } else {
                            dayOffset
                        }
                        AlarmCandidate(alarm, daysUntil)
                    } else null
                }
            }
        }

        val nextCandidate = candidates.minByOrNull { candidate ->
            val alarmTime = LocalTime.of(candidate.alarm.hour, candidate.alarm.minute)
            candidate.daysUntil * 24 * 60 + alarmTime.hour * 60 + alarmTime.minute
        } ?: return null

        return Pair(nextCandidate.alarm, nextCandidate.daysUntil)
    }

    private fun calculateNextAlarm(alarms: List<AlarmEntity>): String? {
        val nextAlarmInfo = calculateNextAlarmInfo(alarms) ?: return null
        val nextAlarm = nextAlarmInfo.first
        val daysUntil = nextAlarmInfo.second

        val today = LocalDate.now()
        val currentDayOfWeek = today.dayOfWeek

        val dayText = when (daysUntil) {
            0 -> "Today"
            1 -> "Tomorrow"
            else -> {
                val targetDay = currentDayOfWeek.plus(daysUntil.toLong())
                targetDay.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
            }
        }

        return "${nextAlarm.getTimeString()} $dayText"
    }
}
