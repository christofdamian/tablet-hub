package net.damian.tablethub.plex.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ===== Authentication Models =====

@JsonClass(generateAdapter = true)
data class PlexPinResponse(
    val id: Long,
    val code: String,
    val authToken: String?,
    val expiresAt: String?
)

@JsonClass(generateAdapter = true)
data class PlexUser(
    val id: Long,
    val username: String,
    val email: String,
    val thumb: String?,
    @Json(name = "authToken") val authToken: String?
)

@JsonClass(generateAdapter = true)
data class PlexResourcesResponse(
    @Json(name = "MediaContainer") val mediaContainer: PlexResourcesContainer?
)

@JsonClass(generateAdapter = true)
data class PlexResourcesContainer(
    @Json(name = "Device") val devices: List<PlexDevice>?
)

@JsonClass(generateAdapter = true)
data class PlexDevice(
    val name: String = "",
    val product: String = "",
    val productVersion: String? = null,
    val platform: String? = null,
    val clientIdentifier: String = "",
    val provides: String? = null, // "server", "client", etc.
    val connections: List<PlexConnection>? = null
)

@JsonClass(generateAdapter = true)
data class PlexConnection(
    val protocol: String,
    val address: String,
    val port: Int,
    val uri: String,
    val local: Boolean
)

// ===== Library Models =====

@JsonClass(generateAdapter = true)
data class PlexMediaContainer(
    @Json(name = "MediaContainer") val mediaContainer: PlexContainer?
)

@JsonClass(generateAdapter = true)
data class PlexContainer(
    val size: Int?,
    val title1: String?,
    val title2: String?,
    @Json(name = "Directory") val directories: List<PlexDirectory>?,
    @Json(name = "Metadata") val metadata: List<PlexMetadata>?
)

@JsonClass(generateAdapter = true)
data class PlexDirectory(
    val key: String,
    val title: String,
    val type: String?,
    val thumb: String?,
    val art: String?
)

@JsonClass(generateAdapter = true)
data class PlexMetadata(
    val ratingKey: String,
    val key: String,
    val type: String, // "artist", "album", "track", "playlist"
    val title: String,
    val parentTitle: String?, // Album name for tracks
    val grandparentTitle: String?, // Artist name for tracks
    val summary: String?,
    val thumb: String?,
    val art: String?,
    val duration: Long?, // in milliseconds
    val index: Int?, // Track number
    val parentIndex: Int?, // Disc number
    val year: Int?,
    val addedAt: Long?,
    @Json(name = "Media") val media: List<PlexMedia>?
)

@JsonClass(generateAdapter = true)
data class PlexMedia(
    val id: Long,
    val duration: Long?,
    val bitrate: Int?,
    val audioChannels: Int?,
    val audioCodec: String?,
    val container: String?,
    @Json(name = "Part") val parts: List<PlexPart>?
)

@JsonClass(generateAdapter = true)
data class PlexPart(
    val id: Long,
    val key: String, // URL path to stream
    val duration: Long?,
    val file: String?,
    val size: Long?,
    val container: String?
)

// ===== Playlist Models =====

@JsonClass(generateAdapter = true)
data class PlexPlaylist(
    val ratingKey: String,
    val key: String,
    val title: String,
    val type: String,
    val smart: Boolean?,
    val playlistType: String?, // "audio", "video"
    val thumb: String?,
    val duration: Long?,
    val leafCount: Int? // Number of items
)
