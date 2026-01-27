package net.damian.tablethub.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.damian.tablethub.plex.PlexAuthState
import net.damian.tablethub.plex.model.PlexMetadata
import net.damian.tablethub.ui.screens.music.MusicLibraryScreen
import net.damian.tablethub.ui.screens.music.PlexAuthScreen
import net.damian.tablethub.ui.screens.music.PlexAuthViewModel

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    authViewModel: PlexAuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var showMusicLibrary by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<PlexMetadata?>(null) }

    when {
        authState is PlexAuthState.Ready || showMusicLibrary -> {
            MusicLibraryScreen(
                onTrackSelected = { track ->
                    selectedTrack = track
                    // TODO: Start playback
                },
                onLogout = { authViewModel.logout() }
            )
        }
        else -> {
            PlexAuthScreen(
                onAuthenticated = { showMusicLibrary = true }
            )
        }
    }
}
