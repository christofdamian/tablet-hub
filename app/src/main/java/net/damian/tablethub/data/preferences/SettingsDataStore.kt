package net.damian.tablethub.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.damian.tablethub.photos.model.SlideshowConfig
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // MQTT Settings
    private val mqttHostKey = stringPreferencesKey("mqtt_host")
    private val mqttPortKey = intPreferencesKey("mqtt_port")
    private val mqttUsernameKey = stringPreferencesKey("mqtt_username")
    private val mqttPasswordKey = stringPreferencesKey("mqtt_password")
    private val mqttClientIdKey = stringPreferencesKey("mqtt_client_id")
    private val mqttEnabledKey = booleanPreferencesKey("mqtt_enabled")
    private val mqttUseTlsKey = booleanPreferencesKey("mqtt_use_tls")

    // Device Settings
    private val deviceIdKey = stringPreferencesKey("device_id")
    private val deviceNameKey = stringPreferencesKey("device_name")

    // Night Mode Settings
    private val nightModeManualEnabledKey = booleanPreferencesKey("night_mode_manual_enabled")
    private val nightModeAutoEnabledKey = booleanPreferencesKey("night_mode_auto_enabled")
    private val nightModeLuxThresholdKey = intPreferencesKey("night_mode_lux_threshold")
    private val nightModeLuxHysteresisKey = intPreferencesKey("night_mode_lux_hysteresis")
    private val nightModeBrightnessKey = intPreferencesKey("night_mode_brightness")

    // Slideshow Settings
    private val slideshowRotationIntervalKey = intPreferencesKey("slideshow_rotation_interval")
    private val slideshowKenBurnsEnabledKey = booleanPreferencesKey("slideshow_ken_burns_enabled")
    private val slideshowClockOverlayEnabledKey = booleanPreferencesKey("slideshow_clock_overlay_enabled")
    private val slideshowAlbumIdKey = stringPreferencesKey("slideshow_album_id")
    private val slideshowAlbumTitleKey = stringPreferencesKey("slideshow_album_title")

    // TODO: Remove hardcoded values and use settings UI
    val mqttConfig: Flow<MqttConfig> = context.dataStore.data.map { preferences ->
        MqttConfig(
            host = preferences[mqttHostKey] ?: "homeassistant.lan",
            port = preferences[mqttPortKey] ?: 1883,
            username = preferences[mqttUsernameKey] ?: "tablethub",
            password = preferences[mqttPasswordKey] ?: "yourpassword",
            clientId = preferences[mqttClientIdKey] ?: "tablethub_android",
            enabled = preferences[mqttEnabledKey] ?: true,
            useTls = preferences[mqttUseTlsKey] ?: false
        )
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[deviceIdKey] ?: "tablethub"
    }

    val deviceName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[deviceNameKey] ?: "TabletHub"
    }

    val nightModeConfig: Flow<NightModeConfig> = context.dataStore.data.map { preferences ->
        NightModeConfig(
            manualEnabled = preferences[nightModeManualEnabledKey] ?: false,
            autoEnabled = preferences[nightModeAutoEnabledKey] ?: true,
            luxThreshold = preferences[nightModeLuxThresholdKey] ?: 15,
            luxHysteresis = preferences[nightModeLuxHysteresisKey] ?: 5,
            nightBrightness = preferences[nightModeBrightnessKey] ?: 5
        )
    }

    val slideshowConfig: Flow<SlideshowConfig> = context.dataStore.data.map { preferences ->
        SlideshowConfig(
            rotationIntervalSeconds = preferences[slideshowRotationIntervalKey] ?: 30,
            kenBurnsEnabled = preferences[slideshowKenBurnsEnabledKey] ?: true,
            clockOverlayEnabled = preferences[slideshowClockOverlayEnabledKey] ?: true,
            selectedAlbumId = preferences[slideshowAlbumIdKey],
            selectedAlbumTitle = preferences[slideshowAlbumTitleKey]
        )
    }

    suspend fun updateMqttConfig(config: MqttConfig) {
        context.dataStore.edit { preferences ->
            preferences[mqttHostKey] = config.host
            preferences[mqttPortKey] = config.port
            preferences[mqttUsernameKey] = config.username
            preferences[mqttPasswordKey] = config.password
            preferences[mqttClientIdKey] = config.clientId
            preferences[mqttEnabledKey] = config.enabled
            preferences[mqttUseTlsKey] = config.useTls
        }
    }

    suspend fun updateDeviceInfo(deviceId: String, deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[deviceIdKey] = deviceId
            preferences[deviceNameKey] = deviceName
        }
    }

    suspend fun setMqttEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[mqttEnabledKey] = enabled
        }
    }

    suspend fun updateNightModeConfig(config: NightModeConfig) {
        context.dataStore.edit { preferences ->
            preferences[nightModeManualEnabledKey] = config.manualEnabled
            preferences[nightModeAutoEnabledKey] = config.autoEnabled
            preferences[nightModeLuxThresholdKey] = config.luxThreshold
            preferences[nightModeLuxHysteresisKey] = config.luxHysteresis
            preferences[nightModeBrightnessKey] = config.nightBrightness
        }
    }

    suspend fun setNightModeManualEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[nightModeManualEnabledKey] = enabled
        }
    }

    suspend fun setNightModeAutoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[nightModeAutoEnabledKey] = enabled
        }
    }

    suspend fun updateSlideshowConfig(config: SlideshowConfig) {
        context.dataStore.edit { preferences ->
            preferences[slideshowRotationIntervalKey] = config.rotationIntervalSeconds
            preferences[slideshowKenBurnsEnabledKey] = config.kenBurnsEnabled
            preferences[slideshowClockOverlayEnabledKey] = config.clockOverlayEnabled
            config.selectedAlbumId?.let { preferences[slideshowAlbumIdKey] = it }
            config.selectedAlbumTitle?.let { preferences[slideshowAlbumTitleKey] = it }
        }
    }

    suspend fun updateSlideshowAlbum(albumId: String, albumTitle: String) {
        context.dataStore.edit { preferences ->
            preferences[slideshowAlbumIdKey] = albumId
            preferences[slideshowAlbumTitleKey] = albumTitle
        }
    }

    suspend fun clearSlideshowAlbum() {
        context.dataStore.edit { preferences ->
            preferences.remove(slideshowAlbumIdKey)
            preferences.remove(slideshowAlbumTitleKey)
        }
    }

    suspend fun setSlideshowRotationInterval(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[slideshowRotationIntervalKey] = seconds
        }
    }

    suspend fun setSlideshowKenBurnsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[slideshowKenBurnsEnabledKey] = enabled
        }
    }

    suspend fun setSlideshowClockOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[slideshowClockOverlayEnabledKey] = enabled
        }
    }
}

data class MqttConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val clientId: String,
    val enabled: Boolean,
    val useTls: Boolean
) {
    val isValid: Boolean
        get() = host.isNotBlank()

    val serverUri: String
        get() = "${if (useTls) "ssl" else "tcp"}://$host:$port"
}

data class NightModeConfig(
    val manualEnabled: Boolean = false,
    val autoEnabled: Boolean = true,
    val luxThreshold: Int = 15,
    val luxHysteresis: Int = 5,
    val nightBrightness: Int = 5
)
