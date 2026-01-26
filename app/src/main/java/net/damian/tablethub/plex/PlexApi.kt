package net.damian.tablethub.plex

import net.damian.tablethub.plex.model.PlexMediaContainer
import net.damian.tablethub.plex.model.PlexPinResponse
import net.damian.tablethub.plex.model.PlexResourcesResponse
import net.damian.tablethub.plex.model.PlexUser
import retrofit2.Response
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Plex.tv API for authentication and resources.
 */
interface PlexAuthApi {

    /**
     * Request a PIN for device linking.
     * User enters this PIN at https://plex.tv/link
     */
    @FormUrlEncoded
    @POST("pins")
    suspend fun requestPin(
        @Field("strong") strong: Boolean = false,
        @Header("X-Plex-Product") product: String = "TabletHub",
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexPinResponse>

    /**
     * Check if PIN has been claimed (user entered it on plex.tv/link).
     * Returns auth_token when claimed.
     */
    @GET("pins/{id}")
    suspend fun checkPin(
        @Path("id") pinId: Long,
        @Header("X-Plex-Client-Identifier") clientId: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexPinResponse>

    /**
     * Get user info using auth token.
     */
    @GET("user")
    suspend fun getUser(
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexUser>

    /**
     * Get available Plex servers/resources.
     */
    @GET("resources")
    suspend fun getResources(
        @Header("X-Plex-Token") token: String,
        @Query("includeHttps") includeHttps: Int = 1,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexResourcesResponse>
}

/**
 * Plex Media Server API for library browsing and playback.
 */
interface PlexServerApi {

    /**
     * Get library sections (Music, Movies, etc.)
     */
    @GET("library/sections")
    suspend fun getLibrarySections(
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get contents of a library section.
     */
    @GET("library/sections/{sectionId}/all")
    suspend fun getSectionContents(
        @Path("sectionId") sectionId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int? = null, // 8=artist, 9=album, 10=track
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get artists in a music library.
     */
    @GET("library/sections/{sectionId}/all")
    suspend fun getArtists(
        @Path("sectionId") sectionId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 8, // artist
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get albums (optionally filtered by artist).
     */
    @GET("library/sections/{sectionId}/all")
    suspend fun getAlbums(
        @Path("sectionId") sectionId: String,
        @Header("X-Plex-Token") token: String,
        @Query("type") type: Int = 9, // album
        @Query("artist.id") artistId: String? = null,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get children of an item (e.g., albums of an artist, tracks of an album).
     */
    @GET("library/metadata/{ratingKey}/children")
    suspend fun getChildren(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get metadata for a specific item.
     */
    @GET("library/metadata/{ratingKey}")
    suspend fun getMetadata(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get all playlists.
     */
    @GET("playlists")
    suspend fun getPlaylists(
        @Header("X-Plex-Token") token: String,
        @Query("playlistType") playlistType: String = "audio",
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get tracks in a playlist.
     */
    @GET("playlists/{ratingKey}/items")
    suspend fun getPlaylistItems(
        @Path("ratingKey") ratingKey: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Search the library.
     */
    @GET("library/sections/{sectionId}/search")
    suspend fun search(
        @Path("sectionId") sectionId: String,
        @Header("X-Plex-Token") token: String,
        @Query("query") query: String,
        @Query("type") type: Int? = null,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>

    /**
     * Get recently added items.
     */
    @GET("library/sections/{sectionId}/recentlyAdded")
    suspend fun getRecentlyAdded(
        @Path("sectionId") sectionId: String,
        @Header("X-Plex-Token") token: String,
        @Header("Accept") accept: String = "application/json"
    ): Response<PlexMediaContainer>
}
