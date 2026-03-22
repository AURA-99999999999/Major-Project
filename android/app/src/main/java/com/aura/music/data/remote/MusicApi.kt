package com.aura.music.data.remote

import com.aura.music.data.remote.dto.AlbumDetailResponse
import com.aura.music.data.remote.dto.ArtistNameDetailResponse
import com.aura.music.data.remote.dto.ApiResponse
import com.aura.music.data.remote.dto.HealthDto
import com.aura.music.data.remote.dto.HomeResponseDto
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.MoodCategoriesResponse
import com.aura.music.data.remote.dto.MoodPlaylistsResponse
import com.aura.music.data.remote.dto.PlaylistDetailResponse
import com.aura.music.data.remote.dto.PlaylistDto
import com.aura.music.data.remote.dto.PlaylistSongsResponseDto
import com.aura.music.data.remote.dto.RecommendationResponse
import com.aura.music.data.remote.dto.CollaborativeRecommendationResponse
import com.aura.music.data.remote.dto.RegisterRequest
import com.aura.music.data.remote.dto.SaavnArtistResponse
import com.aura.music.data.remote.dto.SearchResponseDto
import com.aura.music.data.remote.dto.SearchSuggestionsDto
import com.aura.music.data.remote.dto.SongDto
import com.aura.music.data.remote.dto.TopArtistsResponse
import com.aura.music.data.remote.dto.TrendingPlaylistsResponse
import com.aura.music.data.remote.dto.TrackEventRequest
import com.aura.music.data.remote.dto.AddSongToPlaylistRequest
import com.aura.music.data.remote.dto.UserDto
import com.aura.music.data.remote.dto.JioSaavnPlaylistDetailResponse
import com.aura.music.data.remote.dto.YTMusicPlaylistDetailResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApi {
    @GET("api/health")
    suspend fun getHealth(): HealthDto

    // Search - Multi-category filtered search
    @GET("api/search")
    suspend fun searchAllCategories(
        @Query("query") query: String
    ): SearchResponseDto

    // Search Suggestions
    @GET("api/search/suggestions")
    suspend fun getSearchSuggestions(
        @Query("q") query: String
    ): SearchSuggestionsDto

    // Artist search through backend
    @GET("api/artists/search")
    suspend fun searchArtists(
        @Query("query") query: String
    ): SaavnArtistResponse

    // Album Details
    @GET("api/album/{browseId}")
    suspend fun getAlbumDetails(@Path("browseId") browseId: String): AlbumDetailResponse

    // Artist songs through backend
    @GET("api/artists/{browseId}/songs")
    suspend fun getArtistDetails(@Path("browseId") browseId: String): ArtistNameDetailResponse

    // YTMusic Playlist Details
    @GET("api/playlist/{browseId}")
    suspend fun getYTPlaylistDetails(@Path("browseId") browseId: String): PlaylistDetailResponse

    // Song details
    @GET("api/song/{videoId}")
    suspend fun getSong(@Path("videoId") videoId: String): ApiResponse<SongDto>

    // Trending
    @GET("api/trending")
    suspend fun getTrending(
        @Query("limit") limit: Int = 20,
        @Query("uid") uid: String? = null
    ): ApiResponse<SongDto>

    // Fresh Hits For You (playlist + discovery query blend ranked on backend)
    @GET("api/fresh-picks")
    suspend fun getFreshPicks(
        @Query("uid") uid: String? = null,
        @Query("limit") limit: Int = 15
    ): ApiResponse<SongDto>

    // Home (trending + recommendations + collaborative filtering)
    @GET("api/home")
    suspend fun getHome(
        @Query("uid") uid: String? = null
    ): HomeResponseDto

    // User Recommendations
    @GET("api/recommendations")
    suspend fun getRecommendations(
        @Query("uid") uid: String
    ): RecommendationResponse

    // Collaborative Recommendations (lazy-loaded for Home)
    @GET("api/recommendations/collaborative")
    suspend fun getCollaborativeRecommendations(
        @Query("uid") uid: String,
        @Query("limit") limit: Int = 12
    ): CollaborativeRecommendationResponse

    // Home: Trending Playlists
    @GET("api/home/trending-playlists")
    suspend fun getTrendingPlaylists(
        @Query("limit") limit: Int = 10
    ): TrendingPlaylistsResponse

    // Home: Mood Categories
    @GET("api/home/moods")
    suspend fun getMoodCategories(): MoodCategoriesResponse

    // Mood playlists through backend
    @GET("api/moods/playlists")
    suspend fun getMoodPlaylists(
        @Query("mood") mood: String,
        @Query("limit") limit: Int = 10
    ): MoodPlaylistsResponse

    // Home: Top Artists (personalized)
    @GET("api/home/top-artists")
    suspend fun getTopArtists(
        @Query("uid") uid: String,
        @Query("limit") limit: Int = 10
    ): TopArtistsResponse

    // Daily Mix metadata (lightweight)
    @GET("api/daily-mixes/meta")
    suspend fun getDailyMixesMeta(): List<com.aura.music.data.remote.dto.DailyMixMetaDto>

    // Per-mix endpoint (songs loaded on demand)
    @GET("api/daily-mixes/{type}")
    suspend fun getDailyMix(
        @Path("type") type: String,
        @Query("uid") uid: String,
        @Query("refresh") refresh: Boolean = false
    ): com.aura.music.data.remote.dto.MixContainer

    // YTMusic Playlist Songs
    @GET("api/playlist/{playlistId}/songs")
    suspend fun getYTMusicPlaylistSongs(
        @Path("playlistId") playlistId: String,
        @Query("limit") limit: Int = 50
    ): YTMusicPlaylistDetailResponse

    // JioSaavn playlist songs by playlist URL
    @GET("api/playlists/songs")
    suspend fun getPlaylistSongsByUrl(
        @Query("playlist_url") playlistUrl: String
    ): PlaylistSongsResponseDto

    // Artist
    @GET("api/artist-info/{artistId}")
    suspend fun getArtist(@Path("artistId") artistId: String): ApiResponse<Any>

    // Playlists
    @GET("api/playlists")
    suspend fun getPlaylists(@Query("userId") userId: String): ApiResponse<PlaylistDto>

    @POST("api/playlists")
    suspend fun createPlaylist(
        @Query("userId") userId: String,
        @Body body: Map<String, String>
    ): ApiResponse<PlaylistDto>

    @GET("api/playlists/{playlistId}")
    suspend fun getPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String
    ): ApiResponse<PlaylistDto>

    @PUT("api/playlists/{playlistId}")
    suspend fun updatePlaylist(
        @Path("playlistId") playlistId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<PlaylistDto>

    @DELETE("api/playlists/{playlistId}")
    suspend fun deletePlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String
    ): ApiResponse<Any>

    @POST("api/playlists/{playlistId}/songs")
    suspend fun addSongToPlaylist(
        @Path("playlistId") playlistId: String,
        @Body body: AddSongToPlaylistRequest
    ): ApiResponse<PlaylistDto>

    @DELETE("api/playlists/{playlistId}/songs/{videoId}")
    suspend fun removeSongFromPlaylist(
        @Path("playlistId") playlistId: String,
        @Query("userId") userId: String,
        @Path("videoId") videoId: String
    ): ApiResponse<PlaylistDto>

    // Auth
    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<UserDto>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<UserDto>

    // User
    @GET("api/users/{userId}")
    suspend fun getUser(@Path("userId") userId: String): ApiResponse<UserDto>

    @GET("api/users/{userId}/liked")
    suspend fun getLikedSongs(@Path("userId") userId: String): ApiResponse<SongDto>

    @POST("api/users/{userId}/liked")
    suspend fun addLikedSong(
        @Path("userId") userId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<Any>

    @DELETE("api/users/{userId}/liked/{videoId}")
    suspend fun removeLikedSong(
        @Path("userId") userId: String,
        @Path("videoId") videoId: String
    ): ApiResponse<Any>

    @GET("api/users/{userId}/recent")
    suspend fun getRecentlyPlayed(@Path("userId") userId: String): ApiResponse<SongDto>

    @POST("api/users/{userId}/recent")
    suspend fun addRecentlyPlayed(
        @Path("userId") userId: String,
        @Body body: Map<String, Any>
    ): ApiResponse<Any>

    @POST("api/play")
    suspend fun trackPlay(
        @Body body: TrackEventRequest
    ): ApiResponse<Any>

    @POST("api/like")
    suspend fun trackLike(
        @Body body: TrackEventRequest
    ): ApiResponse<Any>

    // Language Preferences
    @GET("api/user/{uid}/languages")
    suspend fun getUserLanguages(@Path("uid") uid: String): com.aura.music.data.remote.dto.LanguagePreferenceResponse

    @POST("api/user/{uid}/languages")
    suspend fun updateUserLanguages(
        @Path("uid") uid: String,
        @Body request: com.aura.music.data.remote.dto.UpdateLanguagePreferenceRequest
    ): com.aura.music.data.remote.dto.UpdateLanguagePreferenceResponse

    @GET("api/languages/supported")
    suspend fun getSupportedLanguages(): com.aura.music.data.remote.dto.SupportedLanguagesResponse
}

