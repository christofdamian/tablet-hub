package net.damian.tablethub.photos

import net.damian.tablethub.photos.model.AlbumsResponse
import net.damian.tablethub.photos.model.MediaItemsResponse
import net.damian.tablethub.photos.model.SearchMediaItemsRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for Google Photos Library API.
 * Base URL: https://photoslibrary.googleapis.com/
 */
interface GooglePhotosApi {

    /**
     * List all albums in the user's library.
     */
    @GET("v1/albums")
    suspend fun listAlbums(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<AlbumsResponse>

    /**
     * List all shared albums the user has joined.
     */
    @GET("v1/sharedAlbums")
    suspend fun listSharedAlbums(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): Response<AlbumsResponse>

    /**
     * Search for media items in an album.
     * Note: This is a POST request as per the Google Photos API specification.
     */
    @POST("v1/mediaItems:search")
    suspend fun searchMediaItems(
        @Header("Authorization") authHeader: String,
        @Body request: SearchMediaItemsRequest
    ): Response<MediaItemsResponse>

    /**
     * List all media items in the user's library.
     */
    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): Response<MediaItemsResponse>
}
