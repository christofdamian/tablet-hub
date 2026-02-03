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
            settingsDataStore.mqttConfig.collect { config ->
                _uiState.value = _uiState.value.copy(mqttConfig = config)
            }
        }

        viewModelScope.launch {
            val deviceId = settingsDataStore.deviceId.first()
            val deviceName = settingsDataStore.deviceName.first()
            _uiState.value = _uiState.value.copy(
                deviceId = deviceId,
                deviceName = deviceName
            )
        }

        viewModelScope.launch {
            settingsDataStore.nightModeConfig.collect { config ->
                _uiState.value = _uiState.value.copy(nightModeConfig = config)
            }
        }

        viewModelScope.launch {
            settingsDataStore.alarmSoundEnabled.collect { enabled ->
                _uiState.value = _uiState.value.copy(alarmSoundEnabled = enabled)
            }
        }

        viewModelScope.launch {
            settingsDataStore.snoozeDuration.collect { minutes ->
                _uiState.value = _uiState.value.copy(snoozeDurationMinutes = minutes)
            }
        }
    }

    // MQTT Settings - auto-save on change

    fun updateMqttHost(host: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(host = host)
        )
        saveMqttConfig()
    }

    fun updateMqttPort(port: String) {
        val portInt = port.toIntOrNull() ?: return
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(port = portInt)
        )
        saveMqttConfig()
    }

    fun updateMqttUsername(username: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(username = username)
        )
        saveMqttConfig()
    }

    fun updateMqttPassword(password: String) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(password = password)
        )
        saveMqttConfig()
    }

    fun updateMqttUseTls(useTls: Boolean) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(useTls = useTls)
        )
        saveMqttConfig()
    }

    fun updateMqttEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            mqttConfig = _uiState.value.mqttConfig.copy(enabled = enabled)
        )
        saveMqttConfig()
        // Reconnect if enabling
        if (enabled && _uiState.value.mqttConfig.isValid) {
            mqttManager.connect(_uiState.value.mqttConfig)
        }
    }

    private fun saveMqttConfig() {
        viewModelScope.launch {
            settingsDataStore.updateMqttConfig(_uiState.value.mqttConfig)
        }
    }

    // Device Settings - auto-save on change

    fun updateDeviceId(deviceId: String) {
        _uiState.value = _uiState.value.copy(deviceId = deviceId)
        saveDeviceInfo()
    }

    fun updateDeviceName(deviceName: String) {
        _uiState.value = _uiState.value.copy(deviceName = deviceName)
        saveDeviceInfo()
    }

    private fun saveDeviceInfo() {
        viewModelScope.launch {
            settingsDataStore.updateDeviceInfo(_uiState.value.deviceId, _uiState.value.deviceName)
        }
    }

    // Night Mode Settings - auto-save on change

    fun updateNightModeAutoEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(autoEnabled = enabled)
        )
        saveNightModeConfig()
    }

    fun updateNightModeLuxThreshold(threshold: Int) {
        val thresholdInt = threshold.coerceIn(1, 500)
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxThreshold = thresholdInt)
        )
        saveNightModeConfig()
    }

    fun updateNightModeLuxHysteresis(hysteresis: Int) {
        val hysteresisInt = hysteresis.coerceIn(1, 50)
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxHysteresis = hysteresisInt)
        )
        saveNightModeConfig()
    }

    fun updateNightModeBrightness(brightness: Int) {
        val brightnessInt = brightness.coerceIn(1, 255)
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(nightBrightness = brightnessInt)
        )
        saveNightModeConfig()
    }

    fun updateNightModeDimOverlay(dimOverlay: Int) {
        val dimOverlayInt = dimOverlay.coerceIn(0, 90)
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(dimOverlay = dimOverlayInt)
        )
        saveNightModeConfig()
    }

    fun updateNightModeWakeDuration(seconds: Int) {
        val secondsInt = seconds.coerceIn(5, 300)
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(wakeDurationSeconds = secondsInt)
        )
        saveNightModeConfig()
    }

    fun calibrateLuxThreshold() {
        val currentLuxValue = nightModeManager.nightModeState.value.currentLux.toInt()
        _uiState.value = _uiState.value.copy(
            nightModeConfig = _uiState.value.nightModeConfig.copy(luxThreshold = currentLuxValue)
        )
        saveNightModeConfig()
    }

    private fun saveNightModeConfig() {
        viewModelScope.launch {
            settingsDataStore.updateNightModeConfig(_uiState.value.nightModeConfig)
        }
    }

    // Alarm Settings - auto-save on change

    fun updateAlarmSoundEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(alarmSoundEnabled = enabled)
        viewModelScope.launch {
            settingsDataStore.setAlarmSoundEnabled(enabled)
        }
    }

    fun updateSnoozeDuration(minutes: Int) {
        val minutesInt = minutes.coerceIn(1, 30)
        _uiState.value = _uiState.value.copy(snoozeDurationMinutes = minutesInt)
        viewModelScope.launch {
            settingsDataStore.setSnoozeDuration(minutesInt)
        }
    }

    // Connection testing

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

            // Save config before testing
            settingsDataStore.updateMqttConfig(config)

            val result = mqttManager.testConnection(config)
            _uiState.value = _uiState.value.copy(
                connectionTestResult = when (result) {
                    is ConnectionTestResult.Success -> TestResult.Success
                    is ConnectionTestResult.Failure -> TestResult.Failure(result.message)
                }
            )

            // If test succeeded and MQTT is enabled, reconnect
            if (result is ConnectionTestResult.Success && config.enabled) {
                mqttManager.connect(config)
            }
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
    val snoozeDurationMinutes: Int = 9,
    val connectionTestResult: TestResult? = null
)

sealed class TestResult {
    data object Testing : TestResult()
    data object Success : TestResult()
    data class Failure(val message: String) : TestResult()
}
