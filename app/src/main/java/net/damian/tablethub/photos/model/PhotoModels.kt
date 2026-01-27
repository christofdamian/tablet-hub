package net.damian.tablethub.photos.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// Google Photos API Response Models

@JsonClass(generateAdapter = true)
data class AlbumsResponse(
    @Json(name = "albums") val albums: List<GoogleAlbum>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleAlbum(
    @Json(name = "id") val id: String,
    @Json(name = "title") val title: String? = null,
    @Json(name = "productUrl") val productUrl: String? = null,
    @Json(name = "mediaItemsCount") val mediaItemsCount: String? = null,
    @Json(name = "coverPhotoBaseUrl") val coverPhotoBaseUrl: String? = null,
    @Json(name = "coverPhotoMediaItemId") val coverPhotoMediaItemId: String? = null
) {
    val itemCount: Int
        get() = mediaItemsCount?.toIntOrNull() ?: 0

    fun getCoverPhotoUrl(width: Int = 300, height: Int = 300): String? {
        return coverPhotoBaseUrl?.let { "$it=w$width-h$height-c" }
    }
}

@JsonClass(generateAdapter = true)
data class MediaItemsResponse(
    @Json(name = "mediaItems") val mediaItems: List<GoogleMediaItem>? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

@JsonClass(generateAdapter = true)
data class GoogleMediaItem(
    @Json(name = "id") val id: String,
    @Json(name = "productUrl") val productUrl: String? = null,
    @Json(name = "baseUrl") val baseUrl: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "mediaMetadata") val mediaMetadata: MediaMetadata? = null,
    @Json(name = "filename") val filename: String? = null
) {
    val isPhoto: Boolean
        get() = mimeType?.startsWith("image/") == true

    val isVideo: Boolean
        get() = mimeType?.startsWith("video/") == true

    fun getPhotoUrl(width: Int = 2048, height: Int = 1024): String? {
        return baseUrl?.let { "$it=w$width-h$height" }
    }

    fun getThumbnailUrl(width: Int = 300, height: Int = 300, crop: Boolean = true): String? {
        val suffix = if (crop) "-c" else ""
        return baseUrl?.let { "$it=w$width-h$height$suffix" }
    }
}

@JsonClass(generateAdapter = true)
data class MediaMetadata(
    @Json(name = "creationTime") val creationTime: String? = null,
    @Json(name = "width") val width: String? = null,
    @Json(name = "height") val height: String? = null,
    @Json(name = "photo") val photo: PhotoMetadata? = null,
    @Json(name = "video") val video: VideoMetadata? = null
)

@JsonClass(generateAdapter = true)
data class PhotoMetadata(
    @Json(name = "cameraMake") val cameraMake: String? = null,
    @Json(name = "cameraModel") val cameraModel: String? = null,
    @Json(name = "focalLength") val focalLength: Double? = null,
    @Json(name = "apertureFNumber") val apertureFNumber: Double? = null,
    @Json(name = "isoEquivalent") val isoEquivalent: Int? = null
)

@JsonClass(generateAdapter = true)
data class VideoMetadata(
    @Json(name = "cameraMake") val cameraMake: String? = null,
    @Json(name = "cameraModel") val cameraModel: String? = null,
    @Json(name = "fps") val fps: Double? = null,
    @Json(name = "status") val status: String? = null
)

@JsonClass(generateAdapter = true)
data class SearchMediaItemsRequest(
    @Json(name = "albumId") val albumId: String,
    @Json(name = "pageSize") val pageSize: Int = 100,
    @Json(name = "pageToken") val pageToken: String? = null
)

// Authentication State

sealed class GooglePhotosAuthState {
    data object NotAuthenticated : GooglePhotosAuthState()
    data object Authenticating : GooglePhotosAuthState()
    data class Authenticated(val email: String, val accessToken: String) : GooglePhotosAuthState()
    data class Error(val message: String) : GooglePhotosAuthState()
}

// Slideshow State and Config

data class SlideshowConfig(
    val rotationIntervalSeconds: Int = 30,
    val kenBurnsEnabled: Boolean = true,
    val clockOverlayEnabled: Boolean = true,
    val selectedAlbumId: String? = null,
    val selectedAlbumTitle: String? = null
)

data class SlideshowState(
    val photos: List<GoogleMediaItem> = emptyList(),
    val currentIndex: Int = 0,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val albums: List<GoogleAlbum> = emptyList(),
    val showAlbumPicker: Boolean = false
) {
    val currentPhoto: GoogleMediaItem?
        get() = photos.getOrNull(currentIndex)

    val hasPhotos: Boolean
        get() = photos.isNotEmpty()
}
