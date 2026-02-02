package net.damian.tablethub.ui.screens.clock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Brightness2
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import net.damian.tablethub.ui.components.NowPlayingBar
import net.damian.tablethub.ui.components.SnoozeStatusDisplay
import net.damian.tablethub.ui.components.WeatherWidget
import net.damian.tablethub.ui.theme.Dimensions

@Composable
fun ClockScreen(
    modifier: Modifier = Modifier,
    isNightModeActive: Boolean = false,
    onNightModeToggle: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: ClockViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsState()
    val nextAlarmText by viewModel.nextAlarmText.collectAsState()
    val editingAlarm by viewModel.editingAlarm.collectAsState()
    val showAlarmEditor by viewModel.showAlarmEditor.collectAsState()
    val snoozeInfo by viewModel.snoozeInfo.collectAsState()

    var showAlarmList by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        // Main content area
        Box(
            modifier = Modifier.weight(1f)
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

                SnoozeStatusDisplay(
                    snoozeEndTime = snoozeInfo?.snoozeEndTime,
                    onCancel = { viewModel.cancelSnooze() },
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            // Settings button (top-left)
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 16.dp)
                    .size(Dimensions.IconButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier.size(Dimensions.IconSizeDefault),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            // Weather widget and night mode toggle (top-right)
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherWidget()

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = onNightModeToggle,
                    modifier = Modifier.size(Dimensions.IconButtonSize)
                ) {
                    Icon(
                        imageVector = if (isNightModeActive) Icons.Default.Brightness7 else Icons.Default.Brightness2,
                        contentDescription = if (isNightModeActive) "Exit night mode" else "Enter night mode",
                        modifier = Modifier.size(Dimensions.IconSizeDefault),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Alarm FAB (bottom-left)
            FloatingActionButton(
                onClick = { showAlarmList = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = "Manage alarms",
                    modifier = Modifier.size(Dimensions.FabIconSize),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Now Playing Bar at bottom (self-contained component)
        NowPlayingBar()
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
