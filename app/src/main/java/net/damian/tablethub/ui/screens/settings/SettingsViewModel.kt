package net.damian.tablethub.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.damian.tablethub.data.preferences.MqttConfig
import net.damian.tablethub.data.preferences.NightModeConfig
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.service.display.NightModeManager
import net.damian.tablethub.service.mqtt.ConnectionTestResult
import net.damian.tablethub.service.mqtt.MqttConnectionState
import net.damian.tablethub.service.mqtt.MqttManager
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
    private val mqttManager: MqttManager,
    private val nightModeManager: NightModeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val mqttConnectionState: StateFlow<MqttConnectionState> = mqttManager.connectionState

    // Expose current lux for calibration display
    val currentLux: StateFlow<Float> = nightModeManager.nightModeState
        .let { flow ->
            val luxFlow = MutableStateFlow(0f)
            viewModelScope.launch {
                flow.collect { state ->
                    luxFlow.value = state.currentLux
                }
            }
            luxFlow.asStateFlow()
        }

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            // Load MQTT config
            settingsDataStore.mqttConfig.collect { config ->
                _uiState.value = _uiState.value.copy(mqttConfig = config)
            }
        }

        viewModelScope.launch {
            // Load device info
            val deviceId = settingsDataStore.deviceId.first()
            val deviceName = settingsDataStore.deviceName.first()
            _uiState.value = _uiState.value.copy(
                deviceId = deviceId,
                deviceName = deviceName
            )
        }

        viewModelScope.launch {
            // Load night mode config
            settingsDataStore.nightModeConfig.collect { config ->
                _uiState.value = _uiState.value.copy(nightModeConfig = config)
            }
        }

        viewModelScope.launch {
            // Load alarm settings
            settingsDataStore.alarmSoundEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(alarmSoundEnabled = enabled)
            }
        }
    }

    fun updateMqttHost(host: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(host = host)
        )
    }

    fun updateMqttPort(port: String) {
        val portInt = port.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(port = portInt)
        )
    }

    fun updateMqttUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(username = username)
        )
    }

    fun updateMqttPassword(password: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(password = password)
        )
    }

    fun updateMqttUseTls(useTls: Boolean) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(useTls = useTls)
        )
    }

    fun updateMqttEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(enabled = enabled)
        )
    }

    fun updateDeviceId(deviceId: String) {
        _uiState.value = _uiState.value.copy(deviceId = deviceId)
    }

    fun updateDeviceName(deviceName: String) {
        _uiState.value = _uiState.value.copy(deviceName = deviceName)
    }

    fun updateNightModeAutoEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(autoEnabled = enabled)
        )
    }

    fun updateNightModeLuxThreshold(threshold: String) {
        val thresholdInt = threshold.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxThreshold = thresholdInt)
        )
    }

    fun updateNightModeLuxHysteresis(hysteresis: String) {
        val hysteresisInt = hysteresis.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxHysteresis = hysteresisInt)
        )
    }

    fun updateNightModeBrightness(brightness: String) {
        val brightnessInt = brightness.toIntOrNull()?.coerceIn(1, 255) ?: return
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(nightBrightness = brightnessInt)
        )
    }

    /**
     * Calibrate the lux threshold using the current ambient light reading.
     * Sets the threshold to the current lux value.
     */
    fun calibrateLuxThreshold() {
        val currentLuxValue = nightModeManager.nightModeState.value.currentLux.toInt()
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxThreshold = currentLuxValue)
        )
    }

    fun updateAlarmSoundEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alarmSoundEnabled = enabled)
        // Save immediately since this is a simple toggle
        viewModelScope.launch {
            settingsDataStore.setAlarmSoundEnabled(enabled)
        }
    }

    fun saveSettings() {
        viewModelScope.launch {
            val state = _uiState.value

            // Save MQTT config
            settingsDataStore.updateMqttConfig(state.mqttConfig)

            // Save device info
            settingsDataStore.updateDeviceInfo(state.deviceId, state.deviceName)

            // Save night mode config
            settingsDataStore.updateNightModeConfig(state.nightModeConfig)

            // Reconnect MQTT if enabled
            if (state.mqttConfig.enabled && state.mqttConfig.isValid) {
                mqttManager.connect(state.mqttConfig)
            }

            _uiState.value = _uiState.value.copy(saveSuccess = true)
        }
    }

    fun clearSaveSuccess() {
        _uiState.value = _uiState.value.copy(saveSuccess = false)
    }

    fun testMqttConnection() {
        viewModelScope.launch {
            val config = _uiState.value.mqttConfig
            if (!config.isValid) {
                _uiState.value = _uiState.value.copy(
                    connectionTestResult = TestResult.Failure("Invalid configuration")
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                connectionTestResult = TestResult.Testing
            )

            val result = mqttManager.testConnection(config)
            _uiState.value = _uiState.value.copy(
                connectionTestResult = when (result) {
                    is ConnectionTestResult.Success -> TestResult.Success
                    is ConnectionTestResult.Failure -> TestResult.Failure(result.message)
                }
            )
        }
    }

    fun clearTestResult() {
        _uiState.value = _uiState.value.copy(connectionTestResult = null)
    }

    fun reconnectMqtt() {
        mqttManager.reconnectNow()
    }
}

data class SettingsUiState(
    val mqttConfig: MqttConfig = MqttConfig(
        host = "",
        port = 1883,
        username = "",
        password = "",
        clientId = "tablethub_android",
        enabled = true,
        useTls = false
    ),
    val deviceId: String = "tablethub",
    val deviceName: String = "TabletHub",
    val nightModeConfig: NightModeConfig = NightModeConfig(),
    val alarmSoundEnabled: Boolean = true,
    val saveSuccess: Boolean = false,
    val connectionTestResult: TestResult? = null
)

sealed class TestResult {
    data object Testing : TestResult()
    data object Success : TestResult()
    data class Failure(val message: String) : TestResult()
}
