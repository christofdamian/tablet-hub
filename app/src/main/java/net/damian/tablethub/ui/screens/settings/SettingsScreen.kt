package net.damian.tablethub.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.damian.tablethub.service.mqtt.MqttConnectionState
import net.damian.tablethub.ui.theme.Dimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mqttConnectionState by viewModel.mqttConnectionState.collectAsState()
    val currentLux by viewModel.currentLux.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Settings saved")
            viewModel.clearSaveSuccess()
        }
    }

    LaunchedEffect(uiState.connectionTestResult) {
        when (val result = uiState.connectionTestResult) {
            is TestResult.Success -> {
                snackbarHostState.showSnackbar("Connection successful")
                viewModel.clearTestResult()
            }
            is TestResult.Failure -> {
                snackbarHostState.showSnackbar("Connection failed: ${result.message}")
                viewModel.clearTestResult()
            }
            else -> {}
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(Dimensions.IconButtonSize)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier.size(Dimensions.IconSizeDefault)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveSettings() },
                        modifier = Modifier.size(Dimensions.IconButtonSize)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(Dimensions.IconSizeDefault)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection Section
            SettingsSection(title = "Connection") {
                MqttConnectionStatus(
                    state = mqttConnectionState,
                    onReconnect = { viewModel.reconnectMqtt() }
                )

                Spacer(modifier = Modifier.height(8.dp))

                SwitchSetting(
                    label = "MQTT Enabled",
                    checked = uiState.mqttConfig.enabled,
                    onCheckedChange = { viewModel.updateMqttEnabled(it) }
                )

                OutlinedTextField(
                    value = uiState.mqttConfig.host,
                    onValueChange = { viewModel.updateMqttHost(it) },
                    label = { Text("MQTT Host") },
                    placeholder = { Text("homeassistant.local") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.mqttConfig.port.toString(),
                    onValueChange = { viewModel.updateMqttPort(it) },
                    label = { Text("MQTT Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                OutlinedTextField(
                    value = uiState.mqttConfig.username,
                    onValueChange = { viewModel.updateMqttUsername(it) },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.mqttConfig.password,
                    onValueChange = { viewModel.updateMqttPassword(it) },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )

                SwitchSetting(
                    label = "Use TLS",
                    checked = uiState.mqttConfig.useTls,
                    onCheckedChange = { viewModel.updateMqttUseTls(it) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.testMqttConnection() },
                        modifier = Modifier.weight(1f),
                        enabled = uiState.connectionTestResult !is TestResult.Testing
                    ) {
                        if (uiState.connectionTestResult is TestResult.Testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }
                }
            }

            // Device Section
            SettingsSection(title = "Device") {
                OutlinedTextField(
                    value = uiState.deviceId,
                    onValueChange = { viewModel.updateDeviceId(it) },
                    label = { Text("Device ID") },
                    placeholder = { Text("tablethub") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Used in MQTT topics and HA entity IDs") }
                )

                OutlinedTextField(
                    value = uiState.deviceName,
                    onValueChange = { viewModel.updateDeviceName(it) },
                    label = { Text("Device Name") },
                    placeholder = { Text("TabletHub") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Displayed in Home Assistant") }
                )
            }

            // Night Mode Section
            SettingsSection(title = "Night Mode") {
                SwitchSetting(
                    label = "Auto Night Mode",
                    checked = uiState.nightModeConfig.autoEnabled,
                    onCheckedChange = { viewModel.updateNightModeAutoEnabled(it) },
                    subtitle = "Automatically enable based on ambient light"
                )

                OutlinedTextField(
                    value = uiState.nightModeConfig.luxThreshold.toString(),
                    onValueChange = { viewModel.updateNightModeLuxThreshold(it) },
                    label = { Text("Lux Threshold") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Enter night mode when light falls below this (default: 15)") }
                )

                // Calibration row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current light: ${currentLux.toInt()} lux",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { viewModel.calibrateLuxThreshold() }
                    ) {
                        Text("Use Current")
                    }
                }

                OutlinedTextField(
                    value = uiState.nightModeConfig.luxHysteresis.toString(),
                    onValueChange = { viewModel.updateNightModeLuxHysteresis(it) },
                    label = { Text("Lux Hysteresis") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Exit night mode when light rises above threshold + hysteresis (default: 5)") }
                )

                OutlinedTextField(
                    value = uiState.nightModeConfig.nightBrightness.toString(),
                    onValueChange = { viewModel.updateNightModeBrightness(it) },
                    label = { Text("Night Brightness") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Screen brightness in night mode (1-255, default: 5)") }
                )
            }

            // Alarm Section
            SettingsSection(title = "Alarms") {
                SwitchSetting(
                    label = "On-device alarm sound",
                    checked = uiState.alarmSoundEnabled,
                    onCheckedChange = { viewModel.updateAlarmSoundEnabled(it) },
                    subtitle = "Disable to let Home Assistant control wake-up (music, lights)"
                )

                OutlinedTextField(
                    value = uiState.snoozeDurationMinutes.toString(),
                    onValueChange = { viewModel.updateSnoozeDuration(it) },
                    label = { Text("Snooze Duration (minutes)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("How long to snooze when you tap Snooze (1-60, default: 9)") }
                )
            }

            // Save Button
            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Settings")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun MqttConnectionStatus(
    state: MqttConnectionState,
    onReconnect: () -> Unit
) {
    val (statusText, statusColor, icon) = when (state) {
        is MqttConnectionState.Connected -> Triple(
            "Connected",
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle
        )
        is MqttConnectionState.Connecting -> Triple(
            "Connecting...",
            MaterialTheme.colorScheme.tertiary,
            null
        )
        is MqttConnectionState.Disconnected -> Triple(
            "Disconnected",
            MaterialTheme.colorScheme.outline,
            null
        )
        is MqttConnectionState.Reconnecting -> Triple(
            "Reconnecting (${state.attempt}/${state.maxAttempts})...",
            MaterialTheme.colorScheme.tertiary,
            null
        )
        is MqttConnectionState.WaitingForNetwork -> Triple(
            "Waiting for network...",
            MaterialTheme.colorScheme.outline,
            null
        )
        is MqttConnectionState.Error -> Triple(
            state.message,
            MaterialTheme.colorScheme.error,
            Icons.Default.Error
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Status:", style = MaterialTheme.typography.bodyMedium)
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (state is MqttConnectionState.Connecting ||
                    state is MqttConnectionState.Reconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
        }

        // Show reconnect button when not connected
        if (state is MqttConnectionState.Error ||
            state is MqttConnectionState.Disconnected) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reconnect Now")
            }
        }
    }
}
