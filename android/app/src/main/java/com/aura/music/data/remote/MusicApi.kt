package com.aura.music.data.remote

import com.aura.music.data.remote.dto.AlbumDetailResponse
import com.aura.music.data.remote.dto.ApiResponse
import com.aura.music.data.remote.dto.ArtistDetailResponse
import com.aura.music.data.remote.dto.DailyMixResponse
import com.aura.music.data.remote.dto.HealthDto
import com.aura.music.data.remote.dto.HomeResponseDto
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.MoodCategoriesResponse
import com.aura.music.data.remote.dto.MoodPlaylistsResponse
import com.aura.music.data.remote.dto.PlaylistDetailResponse
import com.aura.music.data.remote.dto.PlaylistDto
import com.aura.music.data.remote.dto.RecommendationResponse
import com.aura.music.data.remote.dto.RegisterRequest
import com.aura.music.data.remote.dto.SearchResponseDto
import com.aura.music.data.remote.dto.SearchSuggestionsDto
import com.aura.music.data.remote.dto.SongDto
import com.aura.music.data.remote.dto.TopArtistsResponse
import com.aura.music.data.remote.dto.TrendingPlaylistsResponse
import com.aura.music.data.remote.dto.UserDto
import com.aura.music.data.remote.dto.YTMusicPlaylistDetailResponse
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

    // Search - Multi-category filtered search
    @GET("search")
    suspend fun searchAllCategories(
        @Query("query") query: String
    ): SearchResponseDto

    // Search Suggestions
    @GET("search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("q") query: String
    ): SearchSuggestionsDto

    // Album Details
    @GET("album/{browseId}")
    suspend fun getAlbumDetails(@Path("browseId") browseId: String): AlbumDetailResponse

    // Artist Details
    @GET("artist/{browseId}")
    suspend fun getArtistDetails(@Path("browseId") browseId: String): ArtistDetailResponse

    // YTMusic Playlist Details
    @GET("playlist/{browseId}")
    suspend fun getYTPlaylistDetails(@Path("browseId") browseId: String): PlaylistDetailResponse

    // Song details
    @GET("song/{videoId}")
    suspend fun getSong(@Path("videoId") videoId: String): ApiResponse<SongDto>

    // Trending
    @GET("trending")
    suspend fun getTrending(@Query("limit") limit: Int = 20): ApiResponse<SongDto>

    // Home (trending + recommendations + collaborative filtering)
    @GET("home")
    suspend fun getHome(
        @Query("uid") uid: String? = null
    ): HomeResponseDto

    // User Recommendations
    @GET("recommendations")
    suspend fun getRecommendations(
        @Query("uid") uid: String
    ): RecommendationResponse

    // Home: Trending Playlists
    @GET("home/trending-playlists")
    suspend fun getTrendingPlaylists(
        @Query("limit") limit: Int = 10
    ): TrendingPlaylistsResponse

    // Home: Mood Categories
    @GET("home/moods")
    suspend fun getMoodCategories(): MoodCategoriesResponse

    // Home: Mood Playlists
    @GET("home/mood-playlists")
    suspend fun getMoodPlaylists(
        @Query("params") params: String,
        @Query("limit") limit: Int = 10
    ): MoodPlaylistsResponse

    // Home: Top Artists (personalized)
    @GET("home/top-artists")
    suspend fun getTopArtists(
        @Query("uid") uid: String,
        @Query("limit") limit: Int = 10
    ): TopArtistsResponse

    // Daily Mixes - 4 personalized playlists: Favorites, Similar Artists, Discover, Mood
    @GET("daily-mixes")
    suspend fun getDailyMixes(
        @Query("uid") uid: String,
        @Query("refresh") refresh: Boolean = false
    ): DailyMixResponse

    // YTMusic Playlist Songs
    @GET("playlist/{playlistId}/songs")
    suspend fun getYTMusicPlaylistSongs(
        @Path("playlistId") playlistId: String,
        @Query("limit") limit: Int = 50
    ): YTMusicPlaylistDetailResponse

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

