package net.damian.tablethub.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.damian.tablethub.plex.PlexRepository
import net.damian.tablethub.plex.model.PlexDirectory
import net.damian.tablethub.plex.model.PlexMetadata
import javax.inject.Inject

data class MusicLibraryState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val musicLibraries: List<PlexDirectory> = emptyList(),
    val selectedLibraryId: String? = null,
    val artists: List<PlexMetadata> = emptyList(),
    val selectedArtist: PlexMetadata? = null,
    val albums: List<PlexMetadata> = emptyList(),
    val selectedAlbum: PlexMetadata? = null,
    val tracks: List<PlexMetadata> = emptyList(),
    val playlists: List<PlexMetadata> = emptyList(),
    val recentlyAdded: List<PlexMetadata> = emptyList()
)

enum class MusicTab {
    Artists, Albums, Playlists, Recent
}

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val plexRepository: PlexRepository
) : ViewModel() {

    private val _state = MutableStateFlow(MusicLibraryState())
    val state: StateFlow<MusicLibraryState> = _state.asStateFlow()

    private val _selectedTab = MutableStateFlow(MusicTab.Artists)
    val selectedTab: StateFlow<MusicTab> = _selectedTab.asStateFlow()

    init {
        loadMusicLibraries()
    }

    fun selectTab(tab: MusicTab) {
        _selectedTab.value = tab
        when (tab) {
            MusicTab.Artists -> if (_state.value.artists.isEmpty()) loadArtists()
            MusicTab.Albums -> {} // Albums shown per artist
            MusicTab.Playlists -> if (_state.value.playlists.isEmpty()) loadPlaylists()
            MusicTab.Recent -> if (_state.value.recentlyAdded.isEmpty()) loadRecentlyAdded()
        }
    }

    private fun loadMusicLibraries() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            plexRepository.getMusicLibraries()
                .onSuccess { libraries ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        musicLibraries = libraries
                    )
                    // Auto-select first music library
                    libraries.firstOrNull()?.let { library ->
                        selectLibrary(library.key)
                    }
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun selectLibrary(libraryId: String) {
        _state.value = _state.value.copy(selectedLibraryId = libraryId)
        loadArtists()
        loadRecentlyAdded()
    }

    private fun loadArtists() {
        val libraryId = _state.value.selectedLibraryId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            plexRepository.getArtists(libraryId)
                .onSuccess { artists ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        artists = artists
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun selectArtist(artist: PlexMetadata) {
        _state.value = _state.value.copy(
            selectedArtist = artist,
            selectedAlbum = null,
            albums = emptyList(),
            tracks = emptyList()
        )
        loadAlbumsForArtist(artist.ratingKey)
    }

    private fun loadAlbumsForArtist(artistRatingKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            plexRepository.getAlbumsForArtist(artistRatingKey)
                .onSuccess { albums ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        albums = albums
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    fun selectAlbum(album: PlexMetadata) {
        _state.value = _state.value.copy(
            selectedAlbum = album,
            tracks = emptyList()
        )
        loadTracksForAlbum(album.ratingKey)
    }

    private fun loadTracksForAlbum(albumRatingKey: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            plexRepository.getTracksForAlbum(albumRatingKey)
                .onSuccess { tracks ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        tracks = tracks
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            plexRepository.getPlaylists()
                .onSuccess { playlists ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        playlists = playlists
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
        }
    }

    private fun loadRecentlyAdded() {
        val libraryId = _state.value.selectedLibraryId ?: return
        viewModelScope.launch {
            plexRepository.getRecentlyAdded(libraryId)
                .onSuccess { items ->
                    _state.value = _state.value.copy(recentlyAdded = items)
                }
        }
    }

    fun clearArtistSelection() {
        _state.value = _state.value.copy(
            selectedArtist = null,
            selectedAlbum = null,
            albums = emptyList(),
            tracks = emptyList()
        )
    }

    fun clearAlbumSelection() {
        _state.value = _state.value.copy(
            selectedAlbum = null,
            tracks = emptyList()
        )
    }

    fun getArtworkUrl(thumb: String?): String? {
        return plexRepository.getArtworkUrl(thumb)
    }

    fun getStreamUrl(track: PlexMetadata): String? {
        val partKey = track.media?.firstOrNull()?.parts?.firstOrNull()?.key ?: return null
        return plexRepository.getStreamUrl(partKey)
    }
}
