package net.damian.tablethub.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.damian.tablethub.plex.PlexAuthState
import net.damian.tablethub.ui.screens.music.PlexAuthScreen
import net.damian.tablethub.ui.screens.music.PlexAuthViewModel

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    authViewModel: PlexAuthViewModel = hiltViewModel()
) {
    val authState by authViewModel.authState.collectAsStateWithLifecycle()
    var showMusicLibrary by remember { mutableStateOf(false) }

    when {
        authState is PlexAuthState.Ready || showMusicLibrary -> {
            // Show music library (placeholder for now)
            MusicLibraryPlaceholder(
                serverName = (authState as? PlexAuthState.Ready)?.serverName ?: "Plex",
                onLogout = { authViewModel.logout() },
                modifier = modifier
            )
        }
        else -> {
            PlexAuthScreen(
                onAuthenticated = { showMusicLibrary = true }
            )
        }
    }
}

@Composable
private fun MusicLibraryPlaceholder(
    serverName: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Connected to $serverName",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Music library browsing coming soon",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
