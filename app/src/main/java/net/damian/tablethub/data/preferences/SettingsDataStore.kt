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

    val mqttConfig: Flow<MqttConfig> = context.dataStore.data.map { preferences ->
        MqttConfig(
            host = preferences[mqttHostKey] ?: "",
            port = preferences[mqttPortKey] ?: 1883,
            username = preferences[mqttUsernameKey] ?: "",
            password = preferences[mqttPasswordKey] ?: "",
            clientId = preferences[mqttClientIdKey] ?: "tablethub_${System.currentTimeMillis()}",
            enabled = preferences[mqttEnabledKey] ?: false,
            useTls = preferences[mqttUseTlsKey] ?: false
        )
    }

    val deviceId: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[deviceIdKey] ?: "tablethub"
    }

    val deviceName: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[deviceNameKey] ?: "TabletHub"
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
