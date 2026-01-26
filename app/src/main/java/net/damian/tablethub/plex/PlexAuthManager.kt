package net.damian.tablethub.plex

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.damian.tablethub.plex.model.PlexConnection
import net.damian.tablethub.plex.model.PlexDevice
import net.damian.tablethub.plex.model.PlexPinResponse
import net.damian.tablethub.plex.model.PlexUser
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlexAuthState {
    data object NotAuthenticated : PlexAuthState()
    data class WaitingForPin(val pin: String, val pinId: Long) : PlexAuthState()
    data object Authenticating : PlexAuthState()
    data class Authenticated(val user: PlexUser) : PlexAuthState()
    data class SelectServer(val servers: List<PlexDevice>) : PlexAuthState()
    data class Ready(val user: PlexUser, val serverUrl: String, val serverName: String) : PlexAuthState()
    data class Error(val message: String) : PlexAuthState()
}

@Singleton
class PlexAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plexAuthApi: PlexAuthApi
) {
    companion object {
        private const val TAG = "PlexAuthManager"
        private const val PREFS_NAME = "plex_secure_prefs"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_NAME = "server_name"
        private const val KEY_USERNAME = "username"
        private const val PIN_CHECK_INTERVAL_MS = 2000L
        private const val PIN_CHECK_TIMEOUT_MS = 300000L // 5 minutes
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val _authState = MutableStateFlow<PlexAuthState>(PlexAuthState.NotAuthenticated)
    val authState: StateFlow<PlexAuthState> = _authState.asStateFlow()

    val isAuthenticated: Boolean
        get() = _authState.value is PlexAuthState.Ready

    val authToken: String?
        get() = encryptedPrefs.getString(KEY_AUTH_TOKEN, null)

    val serverUrl: String?
        get() = encryptedPrefs.getString(KEY_SERVER_URL, null)

    val clientId: String
        get() = encryptedPrefs.getString(KEY_CLIENT_ID, null) ?: run {
            val newId = UUID.randomUUID().toString()
            encryptedPrefs.edit().putString(KEY_CLIENT_ID, newId).apply()
            newId
        }

    init {
        // Check for existing credentials on init
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val token = authToken
        val url = serverUrl
        val serverName = encryptedPrefs.getString(KEY_SERVER_NAME, null)
        val username = encryptedPrefs.getString(KEY_USERNAME, null)

        if (token != null && url != null && serverName != null && username != null) {
            _authState.value = PlexAuthState.Ready(
                user = PlexUser(0, username, "", null, token),
                serverUrl = url,
                serverName = serverName
            )
        }
    }

    /**
     * Start PIN-based authentication flow.
     * Returns the PIN code for user to enter at plex.tv/link
     */
    suspend fun startPinAuth(): String? {
        try {
            _authState.value = PlexAuthState.Authenticating

            val response = plexAuthApi.requestPin(clientId = clientId)
            if (response.isSuccessful) {
                val pin = response.body()
                if (pin != null) {
                    _authState.value = PlexAuthState.WaitingForPin(pin.code, pin.id)
                    return pin.code
                }
            }

            _authState.value = PlexAuthState.Error("Failed to get PIN")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting PIN", e)
            _authState.value = PlexAuthState.Error(e.message ?: "Unknown error")
            return null
        }
    }

    /**
     * Poll for PIN claim. Call this after startPinAuth.
     * Returns true when authenticated.
     */
    suspend fun waitForPinClaim(): Boolean {
        val state = _authState.value
        if (state !is PlexAuthState.WaitingForPin) return false

        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < PIN_CHECK_TIMEOUT_MS) {
            try {
                val response = plexAuthApi.checkPin(state.pinId, clientId)
                Log.d(TAG, "PIN check response: ${response.code()}")
                if (response.isSuccessful) {
                    val pin = response.body()
                    Log.d(TAG, "PIN response body: id=${pin?.id}, code=${pin?.code}, authToken=${pin?.authToken?.take(10)}...")
                    if (pin?.authToken != null) {
                        // PIN was claimed, we have a token
                        Log.d(TAG, "PIN claimed! Got auth token")
                        saveAuthToken(pin.authToken)
                        return fetchUserAndServers(pin.authToken)
                    }
                } else {
                    Log.w(TAG, "PIN check failed: ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking PIN", e)
            }

            delay(PIN_CHECK_INTERVAL_MS)
        }

        _authState.value = PlexAuthState.Error("PIN expired")
        return false
    }

    private suspend fun fetchUserAndServers(token: String): Boolean {
        try {
            _authState.value = PlexAuthState.Authenticating

            // Get user info
            val userResponse = plexAuthApi.getUser(token)
            val user = userResponse.body()
            if (user == null) {
                _authState.value = PlexAuthState.Error("Failed to get user info")
                return false
            }

            saveUsername(user.username)

            // Get available servers
            val resourcesResponse = plexAuthApi.getResources(token)
            val allDevices = resourcesResponse.body() ?: emptyList()
            Log.d(TAG, "Found ${allDevices.size} devices")
            allDevices.forEach { device ->
                Log.d(TAG, "Device: ${device.name}, provides: ${device.provides}")
            }

            val servers = allDevices.filter {
                it.provides?.contains("server") == true
            }
            Log.d(TAG, "Found ${servers.size} servers")

            if (servers.isEmpty()) {
                _authState.value = PlexAuthState.Error("No Plex servers found")
                return false
            }

            if (servers.size == 1) {
                // Auto-select the only server
                return selectServer(servers.first(), user)
            }

            _authState.value = PlexAuthState.SelectServer(servers)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user/servers", e)
            _authState.value = PlexAuthState.Error(e.message ?: "Unknown error")
            return false
        }
    }

    /**
     * Select a server from the available servers.
     */
    suspend fun selectServer(server: PlexDevice, user: PlexUser? = null): Boolean {
        val currentUser = user ?: when (val state = _authState.value) {
            is PlexAuthState.SelectServer -> {
                val token = authToken ?: return false
                PlexUser(0, encryptedPrefs.getString(KEY_USERNAME, "") ?: "", "", null, token)
            }
            is PlexAuthState.Authenticated -> state.user
            else -> return false
        }

        // Find the best connection (prefer local)
        val connection = server.connections
            ?.sortedByDescending { it.local }
            ?.firstOrNull()

        if (connection == null) {
            _authState.value = PlexAuthState.Error("No connection available for server")
            return false
        }

        saveServerInfo(connection.uri, server.name)

        _authState.value = PlexAuthState.Ready(
            user = currentUser,
            serverUrl = connection.uri,
            serverName = server.name
        )

        return true
    }

    /**
     * Logout and clear all credentials.
     */
    fun logout() {
        encryptedPrefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_NAME)
            .remove(KEY_USERNAME)
            .apply()

        _authState.value = PlexAuthState.NotAuthenticated
    }

    private fun saveAuthToken(token: String) {
        encryptedPrefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    private fun saveUsername(username: String) {
        encryptedPrefs.edit().putString(KEY_USERNAME, username).apply()
    }

    private fun saveServerInfo(url: String, name: String) {
        encryptedPrefs.edit()
            .putString(KEY_SERVER_URL, url)
            .putString(KEY_SERVER_NAME, name)
            .apply()
    }
}
