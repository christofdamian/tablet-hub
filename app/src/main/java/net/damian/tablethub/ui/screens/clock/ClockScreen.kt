package net.damian.tablethub.ui.screens.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.damian.tablethub.ui.components.ClockDisplay
import net.damian.tablethub.ui.components.NextAlarmDisplay

@Composable
fun ClockScreen(
    modifier: Modifier = Modifier,
    viewModel: ClockViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    val nextAlarmText by viewModel.nextAlarmText.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()
    val showAlarmEditor by viewModel.showAlarmEditor.collectAsState()

    var showAlarmList by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable { showAlarmList = true },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ClockDisplay()

            NextAlarmDisplay(
                nextAlarmText = nextAlarmText,
                modifier = Modifier.padding(top = 32.dp)
            )
        }

        FloatingActionButton(
            onClick = { showAlarmList = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 80.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Manage alarms",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    if (showAlarmList) {
        AlarmListSheet(
            alarms = alarms,
            onDismiss = { showAlarmList = false },
            onAlarmClick = { alarm ->
                viewModel.editAlarm(alarm)
            },
            onAlarmToggle = { alarm ->
                viewModel.toggleAlarmEnabled(alarm)
            },
            onAlarmDelete = { alarm ->
                viewModel.deleteAlarm(alarm)
            },
            onAddAlarm = {
                viewModel.createNewAlarm()
            }
        )
    }

    if (showAlarmEditor && editingAlarm != null) {
        AlarmEditorDialog(
            alarm = editingAlarm!!,
            onDismiss = { viewModel.dismissEditor() },
            onSave = { alarm ->
                viewModel.saveAlarm(alarm)
            }
        )
    }
}
