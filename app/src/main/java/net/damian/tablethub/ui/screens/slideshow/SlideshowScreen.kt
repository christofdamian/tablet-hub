package net.damian.tablethub.ui.screens.slideshow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.damian.tablethub.photos.model.GooglePhotosAuthState
import net.damian.tablethub.ui.screens.slideshow.components.AlbumPickerDialog
import net.damian.tablethub.ui.screens.slideshow.components.ClockOverlay
import net.damian.tablethub.ui.screens.slideshow.components.KenBurnsImage
import kotlin.math.abs

@Composable
fun SlideshowScreen(
    modifier: Modifier = Modifier,
    viewModel: SlideshowViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val slideshowState by viewModel.slideshowState.collectAsStateWithLifecycle()
    val config by viewModel.slideshowConfig.collectAsStateWithLifecycle()

    when {
        authState !is GooglePhotosAuthState.Authenticated -> {
            GooglePhotosAuthScreen(
                onAuthenticated = { /* ViewModel observes auth state */ },
                viewModel = viewModel
            )
        }

        config.selectedAlbumId == null || slideshowState.showAlbumPicker -> {
            // No album selected, show album picker
            AlbumSelectionScreen(
                albums = slideshowState.albums,
                selectedAlbumId = config.selectedAlbumId,
                isLoading = slideshowState.isLoading,
                onAlbumSelected = { viewModel.selectAlbum(it) },
                onRefresh = { viewModel.loadAlbums() }
            )

            if (slideshowState.showAlbumPicker) {
                AlbumPickerDialog(
                    albums = slideshowState.albums,
                    selectedAlbumId = config.selectedAlbumId,
                    isLoading = slideshowState.isLoading,
                    onAlbumSelected = { viewModel.selectAlbum(it) },
                    onDismiss = { viewModel.hideAlbumPicker() }
                )
            }
        }

        else -> {
            SlideshowContent(
                viewModel = viewModel,
                modifier = modifier
            )

            if (slideshowState.showAlbumPicker) {
                AlbumPickerDialog(
                    albums = slideshowState.albums,
                    selectedAlbumId = config.selectedAlbumId,
                    isLoading = slideshowState.isLoading,
                    onAlbumSelected = { viewModel.selectAlbum(it) },
                    onDismiss = { viewModel.hideAlbumPicker() }
                )
            }
        }
    }
}

@Composable
private fun AlbumSelectionScreen(
    albums: List<net.damian.tablethub.photos.model.GoogleAlbum>,
    selectedAlbumId: String?,
    isLoading: Boolean,
    onAlbumSelected: (net.damian.tablethub.photos.model.GoogleAlbum) -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading && albums.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading albums...",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else if (albums.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Select an Album",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Choose a Google Photos album to display as a slideshow",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onRefresh) {
                    Text("Load Albums")
                }
            }
        } else {
            // Albums loaded - show in dialog
            AlbumPickerDialog(
                albums = albums,
                selectedAlbumId = selectedAlbumId,
                isLoading = isLoading,
                onAlbumSelected = onAlbumSelected,
                onDismiss = { }
            )
        }
    }
}

@Composable
private fun SlideshowContent(
    viewModel: SlideshowViewModel,
    modifier: Modifier = Modifier
) {
    val slideshowState by viewModel.slideshowState.collectAsStateWithLifecycle()
    val config by viewModel.slideshowConfig.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    // Auto-hide controls after a delay
    fun showControlsTemporarily() {
        showControls = true
        scope.launch {
            delay(3000L)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        viewModel.togglePause()
                        showControlsTemporarily()
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onDragEnd = {
                        if (abs(totalDrag) > 100) {
                            if (totalDrag > 0) {
                                viewModel.previousPhoto()
                            } else {
                                viewModel.nextPhoto()
                            }
                        }
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDrag += dragAmount
                    }
                )
            }
    ) {
        when {
            slideshowState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading photos...",
                            color = Color.White
                        )
                    }
                }
            }

            slideshowState.error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = slideshowState.error ?: "",
                            color = Color.White.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.showAlbumPicker() }) {
                            Text("Select Different Album")
                        }
                    }
                }
            }

            !slideshowState.hasPhotos -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoAlbum,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No photos in this album",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.showAlbumPicker() }) {
                            Text("Select Different Album")
                        }
                    }
                }
            }

            else -> {
                // Display current photo with Ken Burns effect
                val currentPhoto = slideshowState.currentPhoto
                if (currentPhoto != null) {
                    val photoUrl = currentPhoto.getPhotoUrl(2048, 1536)

                    Crossfade(
                        targetState = slideshowState.currentIndex,
                        label = "photo_crossfade"
                    ) { index ->
                        val photo = slideshowState.photos.getOrNull(index)
                        if (photo != null) {
                            val url = photo.getPhotoUrl(2048, 1536)
                            if (url != null) {
                                KenBurnsImage(
                                    imageUrl = url,
                                    durationMs = config.rotationIntervalSeconds * 1000L,
                                    enabled = config.kenBurnsEnabled && !slideshowState.isPaused,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                // Clock overlay
                if (config.clockOverlayEnabled) {
                    ClockOverlay(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(24.dp)
                    )
                }

                // Pause indicator and controls
                AnimatedVisibility(
                    visible = showControls || slideshowState.isPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePause() },
                            modifier = Modifier
                                .size(80.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.5f),
                                    MaterialTheme.shapes.medium
                                )
                        ) {
                            Icon(
                                imageVector = if (slideshowState.isPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                                contentDescription = if (slideshowState.isPaused) "Play" else "Pause",
                                modifier = Modifier.size(48.dp),
                                tint = Color.White
                            )
                        }
                    }
                }

                // Photo counter
                AnimatedVisibility(
                    visible = showControls || slideshowState.isPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Text(
                        text = "${slideshowState.currentIndex + 1} / ${slideshowState.photos.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                // Change album button
                AnimatedVisibility(
                    visible = showControls || slideshowState.isPaused,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.showAlbumPicker() },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                MaterialTheme.shapes.small
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoAlbum,
                            contentDescription = "Change Album",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
