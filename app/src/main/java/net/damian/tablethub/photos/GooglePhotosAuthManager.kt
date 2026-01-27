package net.damian.tablethub.photos

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.damian.tablethub.photos.model.GooglePhotosAuthState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePhotosAuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "GooglePhotosAuthManager"
        private const val PREFS_NAME = "google_photos_secure_prefs"
        private const val KEY_ACCOUNT_NAME = "account_name"
        private const val KEY_EMAIL = "email"

        // Google Photos Library API scope
        const val PHOTOS_LIBRARY_READONLY_SCOPE = "https://www.googleapis.com/auth/photoslibrary.readonly"
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

    private val _authState = MutableStateFlow<GooglePhotosAuthState>(GooglePhotosAuthState.NotAuthenticated)
    val authState: StateFlow<GooglePhotosAuthState> = _authState.asStateFlow()

    val isAuthenticated: Boolean
        get() = _authState.value is GooglePhotosAuthState.Authenticated

    val userEmail: String?
        get() = encryptedPrefs.getString(KEY_EMAIL, null)

    private val accountName: String?
        get() = encryptedPrefs.getString(KEY_ACCOUNT_NAME, null)

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(PHOTOS_LIBRARY_READONLY_SCOPE))
            .build()

        GoogleSignIn.getClient(context, gso)
    }

    private val credential: GoogleAccountCredential by lazy {
        GoogleAccountCredential.usingOAuth2(
            context,
            listOf(PHOTOS_LIBRARY_READONLY_SCOPE)
        )
    }

    init {
        checkExistingAuth()
    }

    private fun checkExistingAuth() {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        val savedAccountName = accountName

        if (account != null &&
            savedAccountName != null &&
            GoogleSignIn.hasPermissions(account, Scope(PHOTOS_LIBRARY_READONLY_SCOPE))
        ) {
            val email = account.email ?: savedAccountName
            credential.selectedAccountName = savedAccountName
            _authState.value = GooglePhotosAuthState.Authenticated(email, "")
        }
    }

    /**
     * Get the sign-in intent to launch.
     */
    fun getSignInIntent(): Intent {
        _authState.value = GooglePhotosAuthState.Authenticating
        return googleSignInClient.signInIntent
    }

    /**
     * Handle the result from the sign-in activity.
     */
    suspend fun handleSignInResult(result: ActivityResult): Boolean {
        if (result.resultCode != Activity.RESULT_OK) {
            Log.d(TAG, "Sign in cancelled or failed: ${result.resultCode}")
            _authState.value = GooglePhotosAuthState.NotAuthenticated
            return false
        }

        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            handleSignedInAccount(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Sign in failed with code: ${e.statusCode}", e)
            _authState.value = GooglePhotosAuthState.Error("Sign in failed: ${e.statusMessage ?: "Unknown error"}")
            false
        }
    }

    private suspend fun handleSignedInAccount(account: GoogleSignInAccount): Boolean {
        val email = account.email ?: return false
        val accountName = account.account?.name ?: email

        Log.d(TAG, "Signed in as: $email")

        // Set up the credential
        credential.selectedAccountName = accountName

        // Verify we can get an access token
        return try {
            val token = getAccessToken()
            if (token != null) {
                saveCredentials(accountName, email)
                _authState.value = GooglePhotosAuthState.Authenticated(email, token)
                true
            } else {
                _authState.value = GooglePhotosAuthState.Error("Failed to get access token")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            _authState.value = GooglePhotosAuthState.Error(e.message ?: "Unknown error")
            false
        }
    }

    /**
     * Get a fresh access token for the Photos Library API.
     * This must be called on a background thread.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        try {
            if (credential.selectedAccountName == null) {
                val savedAccountName = accountName
                if (savedAccountName != null) {
                    credential.selectedAccountName = savedAccountName
                } else {
                    return@withContext null
                }
            }
            credential.token
        } catch (e: Exception) {
            Log.e(TAG, "Error getting access token", e)
            null
        }
    }

    /**
     * Get the Account object for API calls.
     */
    fun getAccount(): Account? {
        val name = credential.selectedAccountName ?: accountName ?: return null
        return Account(name, "com.google")
    }

    /**
     * Sign out and clear all credentials.
     */
    suspend fun signOut() {
        try {
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out", e)
        }

        credential.selectedAccountName = null

        encryptedPrefs.edit()
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_EMAIL)
            .apply()

        _authState.value = GooglePhotosAuthState.NotAuthenticated
    }

    /**
     * Revoke access and sign out.
     */
    suspend fun revokeAccess() {
        try {
            googleSignInClient.revokeAccess().await()
        } catch (e: Exception) {
            Log.e(TAG, "Error revoking access", e)
        }

        credential.selectedAccountName = null

        encryptedPrefs.edit()
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_EMAIL)
            .apply()

        _authState.value = GooglePhotosAuthState.NotAuthenticated
    }

    private fun saveCredentials(accountName: String, email: String) {
        encryptedPrefs.edit()
            .putString(KEY_ACCOUNT_NAME, accountName)
            .putString(KEY_EMAIL, email)
            .apply()
    }
}
