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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import net.damian.tablethub.ui.components.NowPlayingBar
import net.damian.tablethub.ui.theme.Dimensions

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
        if (state.selectedArtist != null || state.selectedAlbum != null || state.selectedPlaylist != null) {
            TopAppBar(
                title = {
                    Text(
                        text = state.selectedAlbum?.title
                            ?: state.selectedPlaylist?.title
                            ?: state.selectedArtist?.title
                            ?: "Music",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            when {
                                state.selectedAlbum != null -> viewModel.clearAlbumSelection()
                                state.selectedPlaylist != null -> viewModel.clearPlaylistSelection()
                                else -> viewModel.clearArtistSelection()
                            }
                        },
                        modifier = Modifier.size(Dimensions.IconButtonSize)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(Dimensions.IconSizeDefault)
                        )
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
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        text = {
                            Text(
                                text = tab.name,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
                }
            }
        }

        // Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                        onTrackClick = { viewModel.playTrack(it) },
                        getArtworkUrl = { viewModel.getArtworkUrl(it) }
                    )
                }

                state.selectedPlaylist != null -> {
                    TrackList(
                        tracks = state.playlistTracks,
                        onTrackClick = { viewModel.playPlaylistTrack(it) },
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
                                getArtworkUrl = { viewModel.getArtworkUrl(it) },
                                hasMore = state.artistsHasMore,
                                isLoadingMore = state.isLoadingMoreArtists,
                                onLoadMore = { viewModel.loadMoreArtists() },
                                totalCount = state.artistsTotal
                            )
                        }

                        MusicTab.Albums -> {
                            AlbumGrid(
                                albums = state.allAlbums,
                                onAlbumClick = { viewModel.selectAlbum(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) },
                                hasMore = state.allAlbumsHasMore,
                                isLoadingMore = state.isLoadingMoreAlbums,
                                onLoadMore = { viewModel.loadMoreAlbums() },
                                totalCount = state.allAlbumsTotal
                            )
                        }

                        MusicTab.Playlists -> {
                            PlaylistList(
                                playlists = state.playlists,
                                onPlaylistClick = { viewModel.selectPlaylist(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) }
                            )
                        }

                        MusicTab.Recent -> {
                            AlbumGrid(
                                albums = state.recentlyAdded,
                                onAlbumClick = { viewModel.selectAlbum(it) },
                                getArtworkUrl = { viewModel.getArtworkUrl(it) },
                                hasMore = state.recentlyAddedHasMore,
                                isLoadingMore = state.isLoadingMoreRecent,
                                onLoadMore = { viewModel.loadMoreRecent() },
                                totalCount = state.recentlyAddedTotal
                            )
                        }
                    }
                }
            }
        }

        // Now Playing Bar (self-contained component)
        NowPlayingBar()
    }
}

@Composable
private fun ArtistGrid(
    artists: List<PlexMetadata>,
    onArtistClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    totalCount: Int = 0
) {
    if (artists.isEmpty()) {
        EmptyState("No artists found", Icons.Default.Person)
        return
    }

    val gridState = rememberLazyGridState()

    // Detect when scrolled near the end and trigger load more
    LaunchedEffect(gridState, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 10
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(artists, key = { it.ratingKey }) { artist ->
            MediaCard(
                title = artist.title,
                subtitle = null,
                imageUrl = getArtworkUrl(artist.effectiveThumb),
                onClick = { onArtistClick(artist) },
                placeholder = Icons.Default.Person
            )
        }

        // Loading indicator at the bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        // Item count indicator
        if (totalCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Showing ${artists.size} of $totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AlbumGrid(
    albums: List<PlexMetadata>,
    onAlbumClick: (PlexMetadata) -> Unit,
    getArtworkUrl: (String?) -> String?,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    totalCount: Int = 0
) {
    if (albums.isEmpty()) {
        EmptyState("No albums found", Icons.Default.Album)
        return
    }

    val gridState = rememberLazyGridState()

    // Detect when scrolled near the end and trigger load more
    LaunchedEffect(gridState, hasMore, isLoadingMore) {
        snapshotFlow {
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = gridState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 10
        }.collect { nearEnd ->
            if (nearEnd && hasMore && !isLoadingMore) {
                onLoadMore()
            }
        }
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(albums, key = { it.ratingKey }) { album ->
            MediaCard(
                title = album.title,
                subtitle = album.parentTitle ?: album.year?.toString(),
                imageUrl = getArtworkUrl(album.effectiveThumb),
                onClick = { onAlbumClick(album) },
                placeholder = Icons.Default.Album
            )
        }

        // Loading indicator at the bottom
        if (isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        // Item count indicator
        if (totalCount > 0) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Showing ${albums.size} of $totalCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
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
                imageUrl = getArtworkUrl(track.effectiveThumb)
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
        EmptyState("No playlists found", Icons.AutoMirrored.Filled.PlaylistPlay)
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
                        model = getArtworkUrl(playlist.effectiveThumb),
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
                        imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
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
