package net.damian.tablethub.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import net.damian.tablethub.plex.PlexRepository
import net.damian.tablethub.service.music.PlaybackManager
import net.damian.tablethub.service.music.PlaybackState
import javax.inject.Inject

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackManager: PlaybackManager,
    private val plexRepository: PlexRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaybackState()
        )

    fun getArtworkUrl(thumb: String?): String? {
        return plexRepository.getArtworkUrl(thumb)
    }

    fun playPause() {
        playbackManager.togglePlayPause()
    }

    fun skipNext() {
        playbackManager.skipToNext()
    }

    fun skipPrevious() {
        playbackManager.skipToPrevious()
    }

    fun getCurrentPosition(): Long {
        return playbackManager.getCurrentPosition()
    }

    fun getDuration(): Long {
        return playbackManager.getDuration()
    }
}
