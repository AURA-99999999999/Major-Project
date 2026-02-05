package com.aura.music.data.remote

import com.aura.music.data.remote.dto.ApiResponse
import com.aura.music.data.remote.dto.HealthDto
import com.aura.music.data.remote.dto.HomeResponseDto
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.PlaylistDto
import com.aura.music.data.remote.dto.RegisterRequest
import com.aura.music.data.remote.dto.SongDto
import com.aura.music.data.remote.dto.UserDto
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApi {
    @GET("health")
    suspend fun getHealth(): HealthDto

    // Search
    @GET("search")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("filter") filter: String = "songs"
    ): ApiResponse<SongDto>

    // Song details
    @GET("song/{videoId}")
    suspend fun getSong(@Path("videoId") videoId: String): ApiResponse<SongDto>

    // Trending
    @GET("trending")
    suspend fun getTrending(@Query("limit") limit: Int = 20): ApiResponse<SongDto>

    // Home (trending + recommendations)
    @GET("home")
    suspend fun getHome(): HomeResponseDto

    // Artist
    @GET("artist/{artistId}")
    suspend fun getArtist(@Path("artistId") artistId: String): ApiResponse<Any>

    // Playlists
    @GET("playlists")
    suspend fun getPlaylists(@Query("userId") userId: String): ApiResponse<PlaylistDto>

    @POST("playlists")
    suspend fun createPlaylist(
        @Query("userId") userId: String,
        @Body body: Map<String, String>
    ): ApiResponse<PlaylistDto>

    @GET("playlists/{playlistId}")
    suspend fun getPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String
    ): ApiResponse<PlaylistDto>

    @PUT("playlists/{playlistId}")
    suspend fun updatePlaylist(
        @Path("playlistId") playlistId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<PlaylistDto>

    @DELETE("playlists/{playlistId}")
    suspend fun deletePlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String
    ): ApiResponse<Any>

    @POST("playlists/{playlistId}/songs")
    suspend fun addSongToPlaylist(
        @Path("playlistId") playlistId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<PlaylistDto>

    @DELETE("playlists/{playlistId}/songs/{videoId}")
    suspend fun removeSongFromPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String,
        @Path("videoId") videoId: String
    ): ApiResponse<PlaylistDto>

    // Auth
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<UserDto>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<UserDto>

    // User
    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): ApiResponse<UserDto>

    @GET("users/{userId}/liked")
    suspend fun getLikedSongs(@Path("userId") userId: String): ApiResponse<SongDto>

    @POST("users/{userId}/liked")
    suspend fun addLikedSong(
        @Path("userId") userId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<Any>

    @DELETE("users/{userId}/liked/{videoId}")
    suspend fun removeLikedSong(
        @Path("userId") userId: String,
        @Path("videoId") videoId: String
    ): ApiResponse<Any>

    @GET("users/{userId}/recent")
    suspend fun getRecentlyPlayed(@Path("userId") userId: String): ApiResponse<SongDto>

    @POST("users/{userId}/recent")
    suspend fun addRecentlyPlayed(
        @Path("userId") userId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<Any>
}

