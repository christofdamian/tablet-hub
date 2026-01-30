package net.damian.tablethub.plex

import android.util.Log
import net.damian.tablethub.di.PlexServerApiFactory
import net.damian.tablethub.plex.model.PlexDirectory
import net.damian.tablethub.plex.model.PlexMetadata
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of a paginated API call.
 */
data class PagedResult<T>(
    val items: List<T>,
    val offset: Int,
    val totalSize: Int,
    val hasMore: Boolean
)

@Singleton
class PlexRepository @Inject constructor(
    private val plexAuthManager: PlexAuthManager,
    private val serverApiFactory: PlexServerApiFactory
) {
    companion object {
        private const val TAG = "PlexRepository"
        const val DEFAULT_PAGE_SIZE = 50
    }

    private val serverApi: PlexServerApi?
        get() {
            val url = plexAuthManager.serverUrl ?: return null
            return serverApiFactory.create(url)
        }

    private val token: String?
        get() = plexAuthManager.authToken

    /**
     * Get all library sections (Music, Movies, etc.)
     */
    suspend fun getLibrarySections(): Result<List<PlexDirectory>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getLibrarySections(authToken)
            if (response.isSuccessful) {
                val directories = response.body()?.mediaContainer?.directories ?: emptyList()
                Log.d(TAG, "Found ${directories.size} library sections")
                Result.success(directories)
            } else {
                Result.failure(Exception("Failed to get libraries: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting library sections", e)
            Result.failure(e)
        }
    }

    /**
     * Get music library sections only.
     */
    suspend fun getMusicLibraries(): Result<List<PlexDirectory>> {
        return getLibrarySections().map { sections ->
            sections.filter { it.type == "artist" }
        }
    }

    /**
     * Get artists in a music library with pagination.
     * @param start Offset to start from (default 0)
     * @param size Number of items to fetch (default 50)
     */
    suspend fun getArtists(
        sectionId: String,
        start: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE
    ): Result<PagedResult<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getArtists(sectionId, authToken, start = start, size = size)
            if (response.isSuccessful) {
                val container = response.body()?.mediaContainer
                val artists = container?.metadata ?: emptyList()
                val totalSize = container?.totalSize ?: container?.size ?: artists.size
                val hasMore = start + artists.size < totalSize
                Log.d(TAG, "Found ${artists.size} artists (offset=$start, total=$totalSize, hasMore=$hasMore)")
                Result.success(PagedResult(artists, start, totalSize, hasMore))
            } else {
                Result.failure(Exception("Failed to get artists: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting artists", e)
            Result.failure(e)
        }
    }

    /**
     * Get albums for an artist.
     */
    suspend fun getAlbumsForArtist(artistRatingKey: String): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getChildren(artistRatingKey, authToken)
            if (response.isSuccessful) {
                val albums = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${albums.size} albums for artist $artistRatingKey")
                Result.success(albums)
            } else {
                Result.failure(Exception("Failed to get albums: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting albums", e)
            Result.failure(e)
        }
    }

    /**
     * Get tracks for an album.
     */
    suspend fun getTracksForAlbum(albumRatingKey: String): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getChildren(albumRatingKey, authToken)
            if (response.isSuccessful) {
                val tracks = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${tracks.size} tracks for album $albumRatingKey")
                Result.success(tracks)
            } else {
                Result.failure(Exception("Failed to get tracks: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting tracks", e)
            Result.failure(e)
        }
    }

    /**
     * Get all playlists.
     */
    suspend fun getPlaylists(): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getPlaylists(authToken)
            if (response.isSuccessful) {
                val playlists = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${playlists.size} playlists")
                Result.success(playlists)
            } else {
                Result.failure(Exception("Failed to get playlists: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting playlists", e)
            Result.failure(e)
        }
    }

    /**
     * Get tracks in a playlist.
     * @param limit Maximum number of tracks to fetch (default 100)
     */
    suspend fun getPlaylistTracks(playlistRatingKey: String, limit: Int = 100): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getPlaylistItems(playlistRatingKey, authToken, start = 0, size = limit)
            if (response.isSuccessful) {
                val tracks = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${tracks.size} tracks in playlist $playlistRatingKey (requested $limit)")
                Result.success(tracks)
            } else {
                Result.failure(Exception("Failed to get playlist tracks: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting playlist tracks", e)
            Result.failure(e)
        }
    }

    /**
     * Get albums in a music library with pagination.
     * @param start Offset to start from (default 0)
     * @param size Number of items to fetch (default 50)
     */
    suspend fun getAllAlbums(
        sectionId: String,
        start: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE
    ): Result<PagedResult<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getAlbums(sectionId, authToken, start = start, size = size)
            if (response.isSuccessful) {
                val container = response.body()?.mediaContainer
                val albums = container?.metadata ?: emptyList()
                val totalSize = container?.totalSize ?: container?.size ?: albums.size
                val hasMore = start + albums.size < totalSize
                Log.d(TAG, "Found ${albums.size} albums (offset=$start, total=$totalSize, hasMore=$hasMore)")
                Result.success(PagedResult(albums, start, totalSize, hasMore))
            } else {
                Result.failure(Exception("Failed to get albums: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting albums", e)
            Result.failure(e)
        }
    }

    /**
     * Get recently added items with pagination.
     * @param start Offset to start from (default 0)
     * @param size Number of items to fetch (default 50)
     */
    suspend fun getRecentlyAdded(
        sectionId: String,
        start: Int = 0,
        size: Int = DEFAULT_PAGE_SIZE
    ): Result<PagedResult<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getRecentlyAdded(sectionId, authToken, start = start, size = size)
            if (response.isSuccessful) {
                val container = response.body()?.mediaContainer
                val items = container?.metadata ?: emptyList()
                val totalSize = container?.totalSize ?: items.size
                val hasMore = start + items.size < totalSize
                Log.d(TAG, "Found ${items.size} recently added items (offset=$start, total=$totalSize)")
                Result.success(PagedResult(items, start, totalSize, hasMore))
            } else {
                Result.failure(Exception("Failed to get recently added: ${response.code()}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting recently added", e)
            Result.failure(e)
        }
    }

    /**
     * Build the full URL for a media stream.
     */
    fun getStreamUrl(partKey: String): String? {
        val serverUrl = plexAuthManager.serverUrl ?: return null
        val authToken = token ?: return null
        return "$serverUrl$partKey?X-Plex-Token=$authToken"
    }

    /**
     * Build the full URL for artwork.
     */
    fun getArtworkUrl(thumb: String?): String? {
        if (thumb == null) return null
        val serverUrl = plexAuthManager.serverUrl ?: return null
        val authToken = token ?: return null
        return "$serverUrl$thumb?X-Plex-Token=$authToken"
    }
}
