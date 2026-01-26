package net.damian.tablethub.ui.screens.clock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.damian.tablethub.data.local.entity.AlarmEntity

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorDialog(
    alarm: AlarmEntity,
    onDismiss: () -> Unit,
    onSave: (AlarmEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val timePickerState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = true
    )

    var label by remember { mutableStateOf(alarm.label) }
    var monday by remember { mutableStateOf(alarm.monday) }
    var tuesday by remember { mutableStateOf(alarm.tuesday) }
    var wednesday by remember { mutableStateOf(alarm.wednesday) }
    var thursday by remember { mutableStateOf(alarm.thursday) }
    var friday by remember { mutableStateOf(alarm.friday) }
    var saturday by remember { mutableStateOf(alarm.saturday) }
    var sunday by remember { mutableStateOf(alarm.sunday) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(text = if (alarm.id == 0L) "New Alarm" else "Edit Alarm")
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel")
                            }
                        },
                        actions = {
                            Button(
                                onClick = {
                                    onSave(
                                        alarm.copy(
                                            hour = timePickerState.hour,
                                            minute = timePickerState.minute,
                                            label = label,
                                            monday = monday,
                                            tuesday = tuesday,
                                            wednesday = wednesday,
                                            thursday = thursday,
                                            friday = friday,
                                            saturday = saturday,
                                            sunday = sunday
                                        )
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text("Save")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    TimePicker(state = timePickerState)

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Label (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Repeat",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DayChip("Mon", monday) { monday = it }
                        DayChip("Tue", tuesday) { tuesday = it }
                        DayChip("Wed", wednesday) { wednesday = it }
                        DayChip("Thu", thursday) { thursday = it }
                        DayChip("Fri", friday) { friday = it }
                        DayChip("Sat", saturday) { saturday = it }
                        DayChip("Sun", sunday) { sunday = it }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                monday = true
                                tuesday = true
                                wednesday = true
                                thursday = true
                                friday = true
                                saturday = false
                                sunday = false
                            }
                        ) {
                            Text("Weekdays")
                        }
                        TextButton(
                            onClick = {
                                monday = false
                                tuesday = false
                                wednesday = false
                                thursday = false
                                friday = false
                                saturday = true
                                sunday = true
                            }
                        ) {
                            Text("Weekends")
                        }
                        TextButton(
                            onClick = {
                                monday = true
                                tuesday = true
                                wednesday = true
                                thursday = true
                                friday = true
                                saturday = true
                                sunday = true
                            }
                        ) {
                            Text("Every day")
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onSelectedChange(!selected) },
        label = { Text(label) }
    )
}
