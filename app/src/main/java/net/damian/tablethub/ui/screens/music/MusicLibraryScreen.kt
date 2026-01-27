package net.damian.tablethub.ui.screens.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import net.damian.tablethub.plex.model.PlexMetadata

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    onTrackSelected: (PlexMetadata) -> Unit,
    onLogout: () -> Unit,
    viewModel: MusicLibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar with back navigation when in detail views
        if (state.selectedArtist != null || state.selectedAlbum != null) {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedAlbum?.title
                            ?: state.selectedArtist?.title
                            ?: "Music",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.selectedAlbum != null) {
                                viewModel.clearAlbumSelection()
                            } else {
                                viewModel.clearArtistSelection()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        } else {
            // Tab row for main navigation
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                edgePadding = 16.dp
            ) {
                MusicTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tab.name) }
                    )
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.error != null -> {
                    Text(
                        text = state.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.selectedAlbum != null -> {
                    TrackList(
                        tracks = state.tracks,
                        onTrackClick = onTrackSelected,
                        getArtworkUrl = { viewModel.getArtworkUrl(it) }
                    )
                }

                state.selectedArtist != null -> {
                    AlbumGrid(
                        albums = state.albums,
                        onAlbumClick = { viewModel.selectAlbum(it) },
                        getArtworkUrl = { viewModel.getArtworkUrl(it) }
                    )
                }

                else -> {
                    when (selectedTab) {
                        MusicTab.Artists -> {
                            ArtistGrid(
                                artists = state.artists,
                                onArtistClick = { viewModel.selectArtist(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) }
                            )
                        }

                        MusicTab.Albums -> {
                            AlbumGrid(
                                albums = state.recentlyAdded.filter { it.type == "album" },
                                onAlbumClick = { viewModel.selectAlbum(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) }
                            )
                        }

                        MusicTab.Playlists -> {
                            PlaylistList(
                                playlists = state.playlists,
                                onPlaylistClick = { /* TODO: Load playlist tracks */ },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) }
                            )
                        }

                        MusicTab.Recent -> {
                            AlbumGrid(
                                albums = state.recentlyAdded,
                                onAlbumClick = { viewModel.selectAlbum(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistGrid(
    artists: List<PlexMetadata>,
    onArtistClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?
) {
    if (artists.isEmpty()) {
        EmptyState("No artists found", Icons.Default.Person)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.ratingKey }) { artist ->
            MediaCard(
                title = artist.title,
                subtitle = null,
                imageUrl = getArtworkUrl(artist.thumb),
                onClick = { onArtistClick(artist) },
                placeholder = Icons.Default.Person
            )
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<PlexMetadata>,
    onAlbumClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?
) {
    if (albums.isEmpty()) {
        EmptyState("No albums found", Icons.Default.Album)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.ratingKey }) { album ->
            MediaCard(
                title = album.title,
                subtitle = album.parentTitle ?: album.year?.toString(),
                imageUrl = getArtworkUrl(album.thumb),
                onClick = { onAlbumClick(album) },
                placeholder = Icons.Default.Album
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<PlexMetadata>,
    onTrackClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?
) {
    if (tracks.isEmpty()) {
        EmptyState("No tracks found", Icons.Default.MusicNote)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks, key = { it.ratingKey }) { track ->
            TrackItem(
                track = track,
                onClick = { onTrackClick(track) },
                imageUrl = getArtworkUrl(track.thumb)
            )
        }
    }
}

@Composable
private fun TrackItem(
    track: PlexMetadata,
    onClick: () -> Unit,
    imageUrl: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track number
            Text(
                text = track.index?.toString() ?: "-",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(32.dp)
            )

            // Album art (small)
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (track.grandparentTitle != null) {
                    Text(
                        text = track.grandparentTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Duration
            track.duration?.let { duration ->
                Text(
                    text = formatDuration(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play button
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<PlexMetadata>,
    onPlaylistClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?
) {
    if (playlists.isEmpty()) {
        EmptyState("No playlists found", Icons.Default.PlaylistPlay)
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(playlists, key = { it.ratingKey }) { playlist ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPlaylistClick(playlist) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = getArtworkUrl(playlist.thumb),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlist.leafCount ?: 0} tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    placeholder: ImageVector
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = placeholder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    icon: ImageVector
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
