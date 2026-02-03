package net.damian.tablethub.ui.screens.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import net.damian.tablethub.service.mqtt.MqttConnectionState

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

    var showMqttDialog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
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
                .verticalScroll(rememberScrollState())
        ) {
            // Connection Section
            SettingsSectionHeader("Connection")

            MqttConnectionStatusItem(
                state = mqttConnectionState,
                onReconnect = { viewModel.reconnectMqtt() }
            )

            SettingsSwitchItem(
                title = "MQTT enabled",
                subtitle = if (uiState.mqttConfig.enabled) "Connected to Home Assistant" else "Disabled",
                checked = uiState.mqttConfig.enabled,
                onCheckedChange = { viewModel.updateMqttEnabled(it) }
            )

            SettingsClickableItem(
                title = "MQTT server",
                subtitle = "${uiState.mqttConfig.host}:${uiState.mqttConfig.port}",
                onClick = { showMqttDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Device Section
            SettingsSectionHeader("Device")

            SettingsClickableItem(
                title = "Device identity",
                subtitle = "${uiState.deviceName} (${uiState.deviceId})",
                onClick = { showDeviceDialog = true }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Night Mode Section
            SettingsSectionHeader("Night mode")

            SettingsSwitchItem(
                title = "Auto night mode",
                subtitle = "Enable based on ambient light",
                checked = uiState.nightModeConfig.autoEnabled,
                onCheckedChange = { viewModel.updateNightModeAutoEnabled(it) }
            )

            SettingsSliderItem(
                title = "Lux threshold",
                value = uiState.nightModeConfig.luxThreshold,
                valueRange = 1..500,
                subtitle = "Current: ${currentLux.toInt()} lux",
                onValueChange = { viewModel.updateNightModeLuxThreshold(it) },
                onCalibrate = { viewModel.calibrateLuxThreshold() }
            )

            SettingsSliderItem(
                title = "Lux hysteresis",
                value = uiState.nightModeConfig.luxHysteresis,
                valueRange = 1..50,
                subtitle = "Exit threshold: ${uiState.nightModeConfig.luxThreshold + uiState.nightModeConfig.luxHysteresis} lux",
                onValueChange = { viewModel.updateNightModeLuxHysteresis(it) }
            )

            SettingsSliderItem(
                title = "Night brightness",
                value = uiState.nightModeConfig.nightBrightness,
                valueRange = 1..255,
                onValueChange = { viewModel.updateNightModeBrightness(it) }
            )

            SettingsSliderItem(
                title = "Extra dim overlay",
                value = uiState.nightModeConfig.dimOverlay,
                valueRange = 0..90,
                valueSuffix = "%",
                subtitle = "Dims beyond minimum brightness",
                onValueChange = { viewModel.updateNightModeDimOverlay(it) }
            )

            SettingsSliderItem(
                title = "Wake duration",
                value = uiState.nightModeConfig.wakeDurationSeconds,
                valueRange = 5..300,
                valueSuffix = "s",
                subtitle = "Stay awake after tapping night clock",
                onValueChange = { viewModel.updateNightModeWakeDuration(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Alarms Section
            SettingsSectionHeader("Alarms")

            SettingsSwitchItem(
                title = "On-device alarm sound",
                subtitle = "Disable to let Home Assistant control wake-up",
                checked = uiState.alarmSoundEnabled,
                onCheckedChange = { viewModel.updateAlarmSoundEnabled(it) }
            )

            SettingsSliderItem(
                title = "Snooze duration",
                value = uiState.snoozeDurationMinutes,
                valueRange = 1..30,
                valueSuffix = " min",
                onValueChange = { viewModel.updateSnoozeDuration(it) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // MQTT Configuration Dialog
    if (showMqttDialog) {
        MqttConfigDialog(
            config = uiState.mqttConfig,
            onDismiss = { showMqttDialog = false },
            onHostChange = viewModel::updateMqttHost,
            onPortChange = viewModel::updateMqttPort,
            onUsernameChange = viewModel::updateMqttUsername,
            onPasswordChange = viewModel::updateMqttPassword,
            onUseTlsChange = viewModel::updateMqttUseTls,
            onTestConnection = viewModel::testMqttConnection,
            testResult = uiState.connectionTestResult
        )
    }

    // Device Identity Dialog
    if (showDeviceDialog) {
        DeviceIdentityDialog(
            deviceId = uiState.deviceId,
            deviceName = uiState.deviceName,
            onDismiss = { showDeviceDialog = false },
            onDeviceIdChange = viewModel::updateDeviceId,
            onDeviceNameChange = viewModel::updateDeviceName
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun SettingsClickableItem(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSliderItem(
    title: String,
    value: Int,
    valueRange: IntRange,
    valueSuffix: String = "",
    subtitle: String? = null,
    onValueChange: (Int) -> Unit,
    onCalibrate: (() -> Unit)? = null
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "$value$valueSuffix",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            modifier = Modifier.fillMaxWidth(),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(24.dp),
                    thumbTrackGapSize = 4.dp
                )
            }
        )

        if (subtitle != null || onCalibrate != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (onCalibrate != null) {
                    OutlinedButton(onClick = onCalibrate) {
                        Text("Use current")
                    }
                }
            }
        }
    }
}

@Composable
private fun MqttConnectionStatusItem(
    state: MqttConnectionState,
    onReconnect: () -> Unit
) {
    val (statusText, statusColor, icon, showReconnect) = when (state) {
        is MqttConnectionState.Connected -> Quadruple(
            "Connected",
            MaterialTheme.colorScheme.primary,
            Icons.Default.CheckCircle,
            false
        )
        is MqttConnectionState.Connecting -> Quadruple(
            "Connecting...",
            MaterialTheme.colorScheme.tertiary,
            null,
            false
        )
        is MqttConnectionState.Disconnected -> Quadruple(
            "Disconnected",
            MaterialTheme.colorScheme.outline,
            null,
            true
        )
        is MqttConnectionState.Reconnecting -> Quadruple(
            "Reconnecting (${state.attempt}/${state.maxAttempts})...",
            MaterialTheme.colorScheme.tertiary,
            null,
            false
        )
        is MqttConnectionState.WaitingForNetwork -> Quadruple(
            "Waiting for network...",
            MaterialTheme.colorScheme.outline,
            null,
            false
        )
        is MqttConnectionState.Error -> Quadruple(
            state.message,
            MaterialTheme.colorScheme.error,
            Icons.Default.Error,
            true
        )
    }

    ListItem(
        headlineContent = { Text("Connection status") },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                } else if (state is MqttConnectionState.Connecting ||
                    state is MqttConnectionState.Reconnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(statusText, color = statusColor)
            }
        },
        trailingContent = if (showReconnect) {
            {
                IconButton(onClick = onReconnect) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reconnect")
                }
            }
        } else null
    )
}

@Composable
private fun MqttConfigDialog(
    config: net.damian.tablethub.data.preferences.MqttConfig,
    onDismiss: () -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onUseTlsChange: (Boolean) -> Unit,
    onTestConnection: () -> Unit,
    testResult: TestResult?
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("MQTT Server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = config.host,
                    onValueChange = onHostChange,
                    label = { Text("Host") },
                    placeholder = { Text("homeassistant.local") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.port.toString(),
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = config.username,
                    onValueChange = onUsernameChange,
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = config.password,
                    onValueChange = onPasswordChange,
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Use TLS")
                    Switch(
                        checked = config.useTls,
                        onCheckedChange = onUseTlsChange
                    )
                }
                OutlinedButton(
                    onClick = onTestConnection,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = testResult !is TestResult.Testing
                ) {
                    if (testResult is TestResult.Testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Test connection")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
private fun DeviceIdentityDialog(
    deviceId: String,
    deviceName: String,
    onDismiss: () -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Device Identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = onDeviceIdChange,
                    label = { Text("Device ID") },
                    placeholder = { Text("tablethub") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Used in MQTT topics and HA entity IDs") }
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = onDeviceNameChange,
                    label = { Text("Device name") },
                    placeholder = { Text("TabletHub") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text("Displayed in Home Assistant") }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
