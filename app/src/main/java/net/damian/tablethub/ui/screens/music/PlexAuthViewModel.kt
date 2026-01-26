package net.damian.tablethub.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.damian.tablethub.plex.PlexAuthManager
import net.damian.tablethub.plex.PlexAuthState
import net.damian.tablethub.plex.model.PlexDevice
import javax.inject.Inject

@HiltViewModel
class PlexAuthViewModel @Inject constructor(
    private val plexAuthManager: PlexAuthManager
) : ViewModel() {

    val authState = plexAuthManager.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlexAuthState.NotAuthenticated
        )

    fun startAuthentication() {
        viewModelScope.launch {
            val pin = plexAuthManager.startPinAuth()
            if (pin != null) {
                // Start polling for PIN claim
                plexAuthManager.waitForPinClaim()
            }
        }
    }

    fun selectServer(server: PlexDevice) {
        viewModelScope.launch {
            plexAuthManager.selectServer(server)
        }
    }

    fun logout() {
        plexAuthManager.logout()
    }
}
