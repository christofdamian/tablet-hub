package net.damian.tablethub.service.music

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.damian.tablethub.plex.PlexRepository
import net.damian.tablethub.plex.model.PlexMetadata
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentTrack: PlexMetadata? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val playbackState: Int = Player.STATE_IDLE,
    val queue: List<PlexMetadata> = emptyList(),
    val currentIndex: Int = -1
)

@Singleton
class PlaybackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plexRepository: PlexRepository
) {
    companion object {
        private const val TAG = "PlaybackManager"
    }

    private var player: Player? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentQueue: List<PlexMetadata> = emptyList()

    fun initialize(player: Player) {
        this.player = player
        Log.d(TAG, "PlaybackManager initialized with player")
    }

    fun connectToService() {
        if (mediaControllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicPlaybackService::class.java)
        )

        mediaControllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            try {
                val controller = mediaControllerFuture?.get()
                if (controller != null) {
                    player = controller
                    Log.d(TAG, "Connected to MediaController")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    fun playTrack(track: PlexMetadata, queue: List<PlexMetadata> = listOf(track)) {
        val streamUrl = plexRepository.getStreamUrl(track.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: return)
            ?: return

        Log.d(TAG, "Playing track: ${track.title}, URL: $streamUrl")

        currentQueue = queue
        val currentIndex = queue.indexOf(track).coerceAtLeast(0)

        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            queue = queue,
            currentIndex = currentIndex
        )

        val artworkUrl = plexRepository.getArtworkUrl(track.thumb)

        val mediaItem = MediaItem.Builder()
            .setUri(streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.grandparentTitle ?: track.parentTitle)
                    .setAlbumTitle(track.parentTitle)
                    .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                    .build()
            )
            .build()

        player?.let { p ->
            p.setMediaItem(mediaItem)
            p.prepare()
            p.play()
        }
    }

    fun playQueue(tracks: List<PlexMetadata>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return

        currentQueue = tracks
        val mediaItems = tracks.mapNotNull { track ->
            val streamUrl = plexRepository.getStreamUrl(
                track.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: return@mapNotNull null
            ) ?: return@mapNotNull null

            val artworkUrl = plexRepository.getArtworkUrl(track.thumb)

            MediaItem.Builder()
                .setUri(streamUrl)
                .setMediaId(track.ratingKey)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.grandparentTitle ?: track.parentTitle)
                        .setAlbumTitle(track.parentTitle)
                        .setArtworkUri(artworkUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }

        if (mediaItems.isEmpty()) return

        _playbackState.value = _playbackState.value.copy(
            currentTrack = tracks.getOrNull(startIndex),
            queue = tracks,
            currentIndex = startIndex
        )

        player?.let { p ->
            p.setMediaItems(mediaItems, startIndex, 0L)
            p.prepare()
            p.play()
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun togglePlayPause() {
        player?.let { p ->
            if (p.isPlaying) {
                p.pause()
            } else {
                p.play()
            }
        }
    }

    fun skipToNext() {
        player?.let { p ->
            if (p.hasNextMediaItem()) {
                p.seekToNextMediaItem()
            }
        }
    }

    fun skipToPrevious() {
        player?.let { p ->
            if (p.currentPosition > 3000) {
                // If more than 3 seconds in, restart current track
                p.seekTo(0)
            } else if (p.hasPreviousMediaItem()) {
                p.seekToPreviousMediaItem()
            } else {
                p.seekTo(0)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    fun stop() {
        player?.stop()
        _playbackState.value = PlaybackState()
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return player?.duration ?: 0L
    }

    // Called by the service
    fun onPlaybackStateChanged(state: Int) {
        _playbackState.value = _playbackState.value.copy(playbackState = state)
    }

    fun onIsPlayingChanged(isPlaying: Boolean) {
        _playbackState.value = _playbackState.value.copy(isPlaying = isPlaying)
    }

    fun onMediaItemTransition(mediaItem: MediaItem?) {
        val index = player?.currentMediaItemIndex ?: -1
        val track = currentQueue.getOrNull(index)
        _playbackState.value = _playbackState.value.copy(
            currentTrack = track,
            currentIndex = index
        )
    }

    fun release() {
        mediaControllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        mediaControllerFuture = null
        player = null
    }
}
