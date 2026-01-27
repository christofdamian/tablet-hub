package net.damian.tablethub.plex

import android.util.Log
import net.damian.tablethub.di.PlexServerApiFactory
import net.damian.tablethub.plex.model.PlexDirectory
import net.damian.tablethub.plex.model.PlexMetadata
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlexRepository @Inject constructor(
    private val plexAuthManager: PlexAuthManager,
    private val serverApiFactory: PlexServerApiFactory
) {
    companion object {
        private const val TAG = "PlexRepository"
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
     * Get all artists in a music library.
     */
    suspend fun getArtists(sectionId: String): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getArtists(sectionId, authToken)
            if (response.isSuccessful) {
                val artists = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${artists.size} artists")
                Result.success(artists)
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
     */
    suspend fun getPlaylistTracks(playlistRatingKey: String): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getPlaylistItems(playlistRatingKey, authToken)
            if (response.isSuccessful) {
                val tracks = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${tracks.size} tracks in playlist $playlistRatingKey")
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
     * Get recently added items.
     */
    suspend fun getRecentlyAdded(sectionId: String): Result<List<PlexMetadata>> {
        val api = serverApi ?: return Result.failure(Exception("Not connected to server"))
        val authToken = token ?: return Result.failure(Exception("Not authenticated"))

        return try {
            val response = api.getRecentlyAdded(sectionId, authToken)
            if (response.isSuccessful) {
                val items = response.body()?.mediaContainer?.metadata ?: emptyList()
                Log.d(TAG, "Found ${items.size} recently added items")
                Result.success(items)
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
