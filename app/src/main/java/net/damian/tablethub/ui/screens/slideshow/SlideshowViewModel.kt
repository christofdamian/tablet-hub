package net.damian.tablethub.ui.screens.slideshow

import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.damian.tablethub.data.preferences.SettingsDataStore
import net.damian.tablethub.photos.GooglePhotosAuthManager
import net.damian.tablethub.photos.GooglePhotosRepository
import net.damian.tablethub.photos.model.GoogleAlbum
import net.damian.tablethub.photos.model.GooglePhotosAuthState
import net.damian.tablethub.photos.model.SlideshowConfig
import net.damian.tablethub.photos.model.SlideshowState
import javax.inject.Inject

@HiltViewModel
class SlideshowViewModel @Inject constructor(
    private val authManager: GooglePhotosAuthManager,
    private val repository: GooglePhotosRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    companion object {
        private const val TAG = "SlideshowViewModel"
    }

    val authState: StateFlow<GooglePhotosAuthState> = authManager.authState

    private val _slideshowState = MutableStateFlow(SlideshowState())
    val slideshowState: StateFlow<SlideshowState> = _slideshowState.asStateFlow()

    val slideshowConfig: StateFlow<SlideshowConfig> = settingsDataStore.slideshowConfig
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            SlideshowConfig()
        )

    private var rotationJob: Job? = null

    init {
        // Load slideshow state when authenticated
        viewModelScope.launch {
            authManager.authState.collect { state ->
                if (state is GooglePhotosAuthState.Authenticated) {
                    loadAlbumsIfNeeded()
                }
            }
        }

        // Start/restart slideshow when config changes
        viewModelScope.launch {
            slideshowConfig.collect { config ->
                if (config.selectedAlbumId != null && authManager.isAuthenticated) {
                    loadPhotosFromAlbum(config.selectedAlbumId)
                }
            }
        }
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    suspend fun handleSignInResult(result: ActivityResult) {
        authManager.handleSignInResult(result)
    }

    fun loadAlbums() {
        viewModelScope.launch {
            _slideshowState.value = _slideshowState.value.copy(isLoading = true, error = null)

            repository.getAlbums().collect { result ->
                result.fold(
                    onSuccess = { albums ->
                        _slideshowState.value = _slideshowState.value.copy(
                            albums = albums,
                            isLoading = false,
                            error = null
                        )
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error loading albums", e)
                        _slideshowState.value = _slideshowState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load albums"
                        )
                    }
                )
            }
        }
    }

    private fun loadAlbumsIfNeeded() {
        if (_slideshowState.value.albums.isEmpty()) {
            loadAlbums()
        }
    }

    fun selectAlbum(album: GoogleAlbum) {
        viewModelScope.launch {
            settingsDataStore.updateSlideshowAlbum(album.id, album.title ?: "Untitled")
            _slideshowState.value = _slideshowState.value.copy(showAlbumPicker = false)
            loadPhotosFromAlbum(album.id)
        }
    }

    private fun loadPhotosFromAlbum(albumId: String) {
        viewModelScope.launch {
            _slideshowState.value = _slideshowState.value.copy(isLoading = true, error = null)

            repository.getPhotosFromAlbum(albumId).collect { result ->
                result.fold(
                    onSuccess = { photos ->
                        _slideshowState.value = _slideshowState.value.copy(
                            photos = photos.shuffled(), // Shuffle for variety
                            currentIndex = 0,
                            isLoading = false,
                            error = null
                        )
                        startSlideshow()
                    },
                    onFailure = { e ->
                        Log.e(TAG, "Error loading photos", e)
                        _slideshowState.value = _slideshowState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load photos"
                        )
                    }
                )
            }
        }
    }

    fun showAlbumPicker() {
        _slideshowState.value = _slideshowState.value.copy(showAlbumPicker = true)
        if (_slideshowState.value.albums.isEmpty()) {
            loadAlbums()
        }
    }

    fun hideAlbumPicker() {
        _slideshowState.value = _slideshowState.value.copy(showAlbumPicker = false)
    }

    fun togglePause() {
        val current = _slideshowState.value
        _slideshowState.value = current.copy(isPaused = !current.isPaused)

        if (!current.isPaused) {
            // Was playing, now paused - cancel job
            rotationJob?.cancel()
        } else {
            // Was paused, now playing - restart slideshow
            startSlideshow()
        }
    }

    fun nextPhoto() {
        val current = _slideshowState.value
        if (current.photos.isEmpty()) return

        val nextIndex = (current.currentIndex + 1) % current.photos.size
        _slideshowState.value = current.copy(currentIndex = nextIndex)
    }

    fun previousPhoto() {
        val current = _slideshowState.value
        if (current.photos.isEmpty()) return

        val prevIndex = if (current.currentIndex > 0) {
            current.currentIndex - 1
        } else {
            current.photos.size - 1
        }
        _slideshowState.value = current.copy(currentIndex = prevIndex)
    }

    private fun startSlideshow() {
        rotationJob?.cancel()

        rotationJob = viewModelScope.launch {
            while (true) {
                val config = slideshowConfig.value
                val state = _slideshowState.value

                if (!state.isPaused && state.hasPhotos) {
                    delay(config.rotationIntervalSeconds * 1000L)

                    // Check again after delay
                    if (!_slideshowState.value.isPaused) {
                        nextPhoto()
                    }
                } else {
                    // Wait a bit before checking again
                    delay(1000L)
                }
            }
        }
    }

    suspend fun signOut() {
        rotationJob?.cancel()
        _slideshowState.value = SlideshowState()
        authManager.signOut()
        settingsDataStore.clearSlideshowAlbum()
    }

    override fun onCleared() {
        super.onCleared()
        rotationJob?.cancel()
    }
}
