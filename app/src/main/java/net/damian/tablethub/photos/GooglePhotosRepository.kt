package net.damian.tablethub.photos

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import net.damian.tablethub.photos.model.GoogleAlbum
import net.damian.tablethub.photos.model.GoogleMediaItem
import net.damian.tablethub.photos.model.SearchMediaItemsRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GooglePhotosRepository @Inject constructor(
    private val api: GooglePhotosApi,
    private val authManager: GooglePhotosAuthManager
) {
    companion object {
        private const val TAG = "GooglePhotosRepository"
    }

    /**
     * Get all albums from the user's library.
     */
    fun getAlbums(): Flow<Result<List<GoogleAlbum>>> = flow {
        try {
            val token = authManager.getAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated")))
                return@flow
            }

            val authHeader = "Bearer $token"
            val albums = mutableListOf<GoogleAlbum>()
            var pageToken: String? = null

            do {
                val response = api.listAlbums(authHeader, pageToken = pageToken)

                if (response.isSuccessful) {
                    val body = response.body()
                    body?.albums?.let { albums.addAll(it) }
                    pageToken = body?.nextPageToken
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error fetching albums: ${response.code()} - $errorBody")
                    emit(Result.failure(Exception("Failed to fetch albums: ${response.code()}")))
                    return@flow
                }
            } while (pageToken != null)

            // Also fetch shared albums
            pageToken = null
            do {
                val response = api.listSharedAlbums(authHeader, pageToken = pageToken)

                if (response.isSuccessful) {
                    val body = response.body()
                    body?.albums?.let { sharedAlbums ->
                        // Avoid duplicates
                        val existingIds = albums.map { it.id }.toSet()
                        albums.addAll(sharedAlbums.filter { it.id !in existingIds })
                    }
                    pageToken = body?.nextPageToken
                } else {
                    // Non-fatal: shared albums might not be available
                    Log.w(TAG, "Error fetching shared albums: ${response.code()}")
                    break
                }
            } while (pageToken != null)

            Log.d(TAG, "Fetched ${albums.size} albums")
            emit(Result.success(albums.sortedBy { it.title?.lowercase() }))

        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching albums", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get photos from a specific album.
     */
    fun getPhotosFromAlbum(albumId: String): Flow<Result<List<GoogleMediaItem>>> = flow {
        try {
            val token = authManager.getAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated")))
                return@flow
            }

            val authHeader = "Bearer $token"
            val photos = mutableListOf<GoogleMediaItem>()
            var pageToken: String? = null

            do {
                val request = SearchMediaItemsRequest(
                    albumId = albumId,
                    pageSize = 100,
                    pageToken = pageToken
                )

                val response = api.searchMediaItems(authHeader, request)

                if (response.isSuccessful) {
                    val body = response.body()
                    body?.mediaItems?.let { items ->
                        // Filter to only include photos (not videos)
                        photos.addAll(items.filter { it.isPhoto })
                    }
                    pageToken = body?.nextPageToken
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error fetching photos: ${response.code()} - $errorBody")
                    emit(Result.failure(Exception("Failed to fetch photos: ${response.code()}")))
                    return@flow
                }
            } while (pageToken != null)

            Log.d(TAG, "Fetched ${photos.size} photos from album")
            emit(Result.success(photos))

        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching photos", e)
            emit(Result.failure(e))
        }
    }

    /**
     * Get all photos from the user's library (no album filter).
     */
    fun getAllPhotos(maxItems: Int = 500): Flow<Result<List<GoogleMediaItem>>> = flow {
        try {
            val token = authManager.getAccessToken()
            if (token == null) {
                emit(Result.failure(Exception("Not authenticated")))
                return@flow
            }

            val authHeader = "Bearer $token"
            val photos = mutableListOf<GoogleMediaItem>()
            var pageToken: String? = null

            do {
                val response = api.listMediaItems(authHeader, pageSize = 100, pageToken = pageToken)

                if (response.isSuccessful) {
                    val body = response.body()
                    body?.mediaItems?.let { items ->
                        photos.addAll(items.filter { it.isPhoto })
                    }
                    pageToken = body?.nextPageToken

                    // Stop if we've reached the max items
                    if (photos.size >= maxItems) {
                        pageToken = null
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "Error fetching photos: ${response.code()} - $errorBody")
                    emit(Result.failure(Exception("Failed to fetch photos: ${response.code()}")))
                    return@flow
                }
            } while (pageToken != null)

            Log.d(TAG, "Fetched ${photos.size} photos from library")
            emit(Result.success(photos.take(maxItems)))

        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching photos", e)
            emit(Result.failure(e))
        }
    }
}
