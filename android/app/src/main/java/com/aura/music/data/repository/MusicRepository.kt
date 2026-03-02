package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.mapper.toPlaylist
import com.aura.music.data.mapper.toPlaylists
import com.aura.music.data.mapper.toSearchResults
import com.aura.music.data.mapper.toSong
import com.aura.music.data.mapper.toSongDtoMap
import com.aura.music.data.mapper.toSongs
import com.aura.music.data.mapper.toUser
import com.aura.music.data.model.CollaborativeSection
import com.aura.music.data.model.HomeData
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.SearchResults
import com.aura.music.data.model.Song
import com.aura.music.data.model.User
import com.aura.music.data.remote.MusicApi
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.RegisterRequest
import com.google.firebase.auth.FirebaseAuth

class MusicRepository(
    private val api: MusicApi,
    private val firestoreLogger: FirestoreLogger = NoOpFirestoreLogger
) {
    suspend fun searchSongs(query: String, limit: Int = 20): Result<List<Song>> {
        return try {
            safeLog { 
                Log.d(TAG, "========================================")
                Log.d(TAG, "searchSongs() called (DEPRECATED - use searchAllCategories)")
                Log.d(TAG, "Query: $query")
                Log.d(TAG, "Limit: $limit")
                Log.d(TAG, "========================================")
            }
            try {
                firestoreLogger.logSearch(query)
                    .onFailure { error ->
                        safeLog { Log.w(TAG, "searchSongs() - Firestore logSearch failed", error) }
                    }
            } catch (e: Exception) {
                safeLog { Log.w(TAG, "searchSongs() - Firestore logSearch threw", e) }
            }
            
            // Use new multi-category search but only return songs for backward compatibility
            val response = api.searchAllCategories(query)
            if (response.success && response.songs != null) {
                safeLog { 
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "searchSongs() SUCCESS")
                    Log.d(TAG, "Results count: ${response.songs.size}")
                    Log.d(TAG, "========================================")
                }
                Result.success(response.songs.toSongs())
            } else {
                val error = response.error ?: "Search failed"
                safeLog { 
                    Log.w(TAG, "========================================")
                    Log.w(TAG, "searchSongs() API ERROR")
                    Log.w(TAG, "Error: $error")
                    Log.w(TAG, "========================================")
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { 
                Log.e(TAG, "========================================")
                Log.e(TAG, "searchSongs() EXCEPTION")
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stack trace:", e)
                Log.e(TAG, "========================================")
            }
            Result.failure(e)
        }
    }

    suspend fun searchAllCategories(query: String): Result<SearchResults> {
        return try {
            safeLog { 
                Log.d(TAG, "========================================")
                Log.d(TAG, "searchAllCategories() called")
                Log.d(TAG, "Query: $query")
                Log.d(TAG, "========================================")
            }
            try {
                firestoreLogger.logSearch(query)
                    .onFailure { error ->
                        safeLog { Log.w(TAG, "searchAllCategories() - Firestore logSearch failed", error) }
                    }
            } catch (e: Exception) {
                safeLog { Log.w(TAG, "searchAllCategories() - Firestore logSearch threw", e) }
            }
            
            val response = api.searchAllCategories(query)
            if (response.success) {
                val searchResults = response.toSearchResults()
                safeLog { 
                    Log.d(TAG, "========================================")
                    Log.d(TAG, "searchAllCategories() SUCCESS")
                    Log.d(TAG, "Songs: ${searchResults.songs.size}")
                    Log.d(TAG, "Albums: ${searchResults.albums.size}")
                    Log.d(TAG, "Artists: ${searchResults.artists.size}")
                    Log.d(TAG, "Playlists: ${searchResults.playlists.size}")
                    Log.d(TAG, "Total: ${searchResults.count}")
                    Log.d(TAG, "========================================")
                }
                Result.success(searchResults)
            } else {
                val error = response.error ?: "Search failed"
                safeLog { 
                    Log.w(TAG, "========================================")
                    Log.w(TAG, "searchAllCategories() API ERROR")
                    Log.w(TAG, "Error: $error")
                    Log.w(TAG, "========================================")
                }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { 
                Log.e(TAG, "========================================")
                Log.e(TAG, "searchAllCategories() EXCEPTION")
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                Log.e(TAG, "Stack trace:", e)
                Log.e(TAG, "========================================")
            }
            Result.failure(e)
        }
    }

    suspend fun getSearchSuggestions(query: String): Result<List<String>> {
        return try {
            safeLog { Log.d(TAG, "getSearchSuggestions() query=$query") }
            val response = api.getSearchSuggestions(query)
            if (response.success && response.suggestions != null) {
                safeLog { Log.d(TAG, "getSearchSuggestions() success: ${response.suggestions.size} suggestions") }
                Result.success(response.suggestions)
            } else {
                val error = response.error ?: "Failed to get suggestions"
                safeLog { Log.w(TAG, "getSearchSuggestions() error: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getSearchSuggestions() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getAlbumDetails(browseId: String): Result<com.aura.music.data.remote.dto.AlbumDetailDto> {
        return try {
            safeLog { Log.d(TAG, "getAlbumDetails() browseId=$browseId") }
            val response = api.getAlbumDetails(browseId)
            if (response.success && response.album != null) {
                safeLog { Log.d(TAG, "getAlbumDetails() success: ${response.album.title}") }
                Result.success(response.album)
            } else {
                val error = response.error ?: "Failed to get album details"
                safeLog { Log.w(TAG, "getAlbumDetails() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getAlbumDetails() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getArtistDetails(browseId: String): Result<com.aura.music.data.remote.dto.ArtistDetailDto> {
        return try {
            safeLog { Log.d(TAG, "getArtistDetails() browseId=$browseId") }
            val response = api.getArtistDetails(browseId)
            if (response.success && response.artist != null) {
                safeLog { Log.d(TAG, "getArtistDetails() success: ${response.artist.name}") }
                Result.success(response.artist)
            } else {
                val error = response.error ?: "Failed to get artist details"
                safeLog { Log.w(TAG, "getArtistDetails() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getArtistDetails() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getYTPlaylistDetails(browseId: String): Result<com.aura.music.data.remote.dto.PlaylistDetailDto> {
        return try {
            safeLog { Log.d(TAG, "getYTPlaylistDetails() browseId=$browseId") }
            val response = api.getYTPlaylistDetails(browseId)
            if (response.success && response.playlist != null) {
                safeLog { Log.d(TAG, "getYTPlaylistDetails() success: ${response.playlist.title}") }
                Result.success(response.playlist)
            } else {
                val error = response.error ?: "Failed to get playlist details"
                safeLog { Log.w(TAG, "getYTPlaylistDetails() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getYTPlaylistDetails() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getSong(videoId: String): Result<Song> {
        return try {
            safeLog { Log.d(TAG, "getSong() videoId=$videoId") }
            val response = api.getSong(videoId)
            if (response.success && response.data != null) {
                safeLog { Log.d(TAG, "getSong() success for $videoId") }
                Result.success(response.data.toSong())
            } else {
                val error = response.error ?: "Failed to get song"
                safeLog { Log.w(TAG, "getSong() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getSong() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getTrending(limit: Int = 20): Result<List<Song>> {
        return try {
            safeLog { Log.d(TAG, "getTrending() limit=$limit") }
            val response = api.getTrending(limit)
            if (response.success && response.results != null) {
                safeLog { Log.d(TAG, "getTrending() success count=${response.results.size}") }
                Result.success(response.results.toSongs())
            } else {
                val error = response.error ?: "Failed to get trending"
                safeLog { Log.w(TAG, "getTrending() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getTrending() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getHomeData(): Result<HomeData> {
        return try {
            // STEP 1: Get current user ID for CF recommendations
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            safeLog { Log.d(TAG, "[HOME_API] Fetching home data with uid=${uid ?: "null"}") }
            
            // STEP 2: Make API call with uid parameter
            val response = api.getHome(uid = uid)
            
            // STEP 3: Log raw response structure
            safeLog { 
                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "[HOME_API] Raw Response from /api/home:")
                Log.d(TAG, "[HOME_API]   - trending size: ${response.trending?.size ?: 0}")
                Log.d(TAG, "[HOME_API]   - recommendations size: ${response.recommendations?.size ?: 0}")
                Log.d(TAG, "[HOME_API]   - collaborative size: ${response.collaborative?.size ?: 0}")
                Log.d(TAG, "[HOME_API]   - CF section exists: ${response.collaborativeRecommendations != null}")
                if (response.collaborativeRecommendations != null) {
                    Log.d(TAG, "[HOME_API]   - CF tracks size: ${response.collaborativeRecommendations.tracks?.size ?: 0}")
                    Log.d(TAG, "[HOME_API]   - CF title: ${response.collaborativeRecommendations.title}")
                }
                Log.d(TAG, "════════════════════════════════════════")
            }
            
            val trending = response.trending?.toSongs().orEmpty()
            val recommendations = response.recommendations?.toSongs().orEmpty()
            
            // STEP 4: Extract collaborative recommendations if available
            val collaborative = response.collaborative?.toSongs().orEmpty()
            safeLog { Log.d(TAG, "[HOME_REPO] ✓ Collaborative (flat): ${collaborative.size} songs") }
            
            // STEP 4b: Extract nested collaborative recommendations if available (legacy)
            val collaborativeSection = response.collaborativeRecommendations?.let { cfDto ->
                val tracks = cfDto.tracks?.toSongs() ?: emptyList()
                safeLog { Log.d(TAG, "[HOME_PARSE] ✓ CF nested section: ${tracks.size} songs") }
                
                CollaborativeSection(
                    title = cfDto.title ?: "Users like you also listen to",
                    tracks = tracks,
                    count = cfDto.count ?: 0
                )
            }
            
            // STEP 5: Log parsed data and validation
            safeLog { 
                Log.d(TAG, "════════════════════════════════════════")
                Log.d(TAG, "[HOME_REPO] Parsed HomeData:")
                Log.d(TAG, "[HOME_REPO]   ✓ Trending: ${trending.size}")
                Log.d(TAG, "[HOME_REPO]   ✓ Recommendations: ${recommendations.size}")
                Log.d(TAG, "[HOME_REPO]   ✓ Collaborative (flat): ${collaborative.size}")
                Log.d(TAG, "[HOME_REPO]   ✓ CF Section: ${collaborativeSection?.tracks?.size ?: 0} (nested)")
                Log.d(TAG, "════════════════════════════════════════")
            }
            
            // STEP 6: Validation - Check for swapped fields
            if (recommendations.isEmpty() && collaborative.isNotEmpty()) {
                Log.w(TAG, "[HOME_REPO] ⚠ WARNING: recommendations is empty but collaborative has data")
            }
            if (recommendations.isNotEmpty() && collaborative.isEmpty()) {
                Log.w(TAG, "[HOME_REPO] ⚠ WARNING: recommendations has data but collaborative is empty")
            }
            
            Result.success(HomeData(
                trending = trending,
                recommendations = recommendations,
                collaborativeRecommendations = collaborativeSection,
                collaborative = collaborative
            ))
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "[HOME_REPO] Exception in getHomeData()", e) }
            Result.failure(e)
        }
    }

    suspend fun fetchUserRecommendations(): List<Song> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?: return emptyList()

        return try {
            val response = api.getRecommendations(uid)
            response.results.toSongs()
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "fetchUserRecommendations() exception", e) }
            emptyList()
        }
    }

    suspend fun getTopArtists(limit: Int = 10): Result<List<com.aura.music.data.remote.dto.TopArtistDto>> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            safeLog { Log.w(TAG, "getTopArtists() - No authenticated user") }
            return Result.success(emptyList())
        }

        return try {
            safeLog { Log.d(TAG, "getTopArtists() uid=$uid limit=$limit") }
            val response = api.getTopArtists(uid, limit)
            if (response.success) {
                safeLog { Log.d(TAG, "getTopArtists() success: ${response.count} artists") }
                Result.success(response.artists)
            } else {
                val error = response.error ?: "Failed to fetch top artists"
                safeLog { Log.w(TAG, "getTopArtists() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getTopArtists() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getDailyMixes(refresh: Boolean = false): Result<com.aura.music.data.remote.dto.DailyMixResponse> {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            safeLog { Log.w(TAG, "getDailyMixes() - No authenticated user") }
            return Result.failure(Exception("User not authenticated"))
        }

        return try {
            safeLog { Log.d(TAG, "getDailyMixes() uid=$uid refresh=$refresh") }
            val response = api.getDailyMixes(uid, refresh)
            
            safeLog { 
                Log.d(TAG, "getDailyMixes() success - " +
                    "daily1=${response.mixes?.dailyMix1?.count ?: 0}, " +
                    "daily2=${response.mixes?.dailyMix2?.count ?: 0}, " +
                    "discover=${response.mixes?.discoverMix?.count ?: 0}, " +
                    "mood=${response.mixes?.moodMix?.count ?: 0}")
            }
            Result.success(response)
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getDailyMixes() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getTrendingPlaylists(limit: Int = 10): Result<List<com.aura.music.data.model.YTMusicPlaylist>> {
        return try {
            safeLog { Log.d(TAG, "getTrendingPlaylists() limit=$limit") }
            val response = api.getTrendingPlaylists(limit)
            if (response.success) {
                val playlists = response.playlists.map { it.toYTMusicPlaylist() }
                safeLog { Log.d(TAG, "getTrendingPlaylists() success count=${playlists.size}") }
                Result.success(playlists)
            } else {
                safeLog { Log.w(TAG, "getTrendingPlaylists() failed") }
                Result.failure(Exception("Failed to get trending playlists"))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getTrendingPlaylists() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getMoodCategories(): Result<List<com.aura.music.data.model.MoodCategory>> {
        return try {
            safeLog { Log.d(TAG, "getMoodCategories()") }
            val response = api.getMoodCategories()
            if (response.success) {
                val categories = response.categories.map { it.toMoodCategory() }
                val filteredCategories = filterSupportedMoods(categories)
                safeLog { 
                    Log.d(TAG, "getMoodCategories() total=${categories.size} filtered=${filteredCategories.size}") 
                }
                Result.success(filteredCategories)
            } else {
                safeLog { Log.w(TAG, "getMoodCategories() failed") }
                Result.failure(Exception("Failed to get mood categories"))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getMoodCategories() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getMoodPlaylists(params: String, limit: Int = 10): Result<List<com.aura.music.data.model.YTMusicPlaylist>> {
        return try {
            safeLog { Log.d(TAG, "getMoodPlaylists() params=$params limit=$limit") }
            val response = api.getMoodPlaylists(params, limit)
            if (response.success) {
                val playlists = response.playlists.map { it.toYTMusicPlaylist() }
                safeLog { Log.d(TAG, "getMoodPlaylists() success count=${playlists.size}") }
                Result.success(playlists)
            } else {
                safeLog { Log.w(TAG, "getMoodPlaylists() failed") }
                Result.failure(Exception("Failed to get mood playlists"))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getMoodPlaylists() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getYTMusicPlaylistSongs(playlistId: String, limit: Int = 50): Result<com.aura.music.data.model.YTMusicPlaylistDetail> {
        return try {
            safeLog { Log.d(TAG, "getYTMusicPlaylistSongs() playlistId=$playlistId limit=$limit") }
            val response = api.getYTMusicPlaylistSongs(playlistId, limit)
            if (response.success) {
                val playlistDetail = com.aura.music.data.model.YTMusicPlaylistDetail(
                    id = response.playlist.id,
                    title = response.playlist.title,
                    description = response.playlist.description ?: "",
                    thumbnail = response.playlist.thumbnail ?: "",
                    author = response.playlist.author ?: "YouTube Music",
                    songCount = response.playlist.songCount,
                    songs = response.songs.toSongs()
                )
                safeLog { Log.d(TAG, "getYTMusicPlaylistSongs() success songCount=${playlistDetail.songs.size}") }
                Result.success(playlistDetail)
            } else {
                safeLog { Log.w(TAG, "getYTMusicPlaylistSongs() failed") }
                Result.failure(Exception("Failed to get playlist songs"))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getYTMusicPlaylistSongs() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getPlaylists(userId: String): Result<List<Playlist>> {
        return try {
            val response = api.getPlaylists(userId)
            if (response.success && response.playlists != null) {
                Result.success(response.playlists.toPlaylists())
            } else {
                Result.failure(Exception(response.error ?: "Failed to get playlists"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPlaylist(
        name: String,
        description: String?,
        userId: String
    ): Result<Playlist> {
        return try {
            val body = mutableMapOf<String, String>(
                "name" to name,
                "userId" to userId
            )
            description?.let { body["description"] = it }
            
            val response = api.createPlaylist(userId, body)
            if (response.success && response.playlist != null) {
                Result.success(response.playlist.toPlaylist())
            } else {
                Result.failure(Exception(response.error ?: "Failed to create playlist"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylist(playlistId: String, userId: String): Result<Playlist> {
        return try {
            val response = api.getPlaylist(playlistId, userId)
            if (response.success && response.playlist != null) {
                Result.success(response.playlist.toPlaylist())
            } else {
                Result.failure(Exception(response.error ?: "Playlist not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePlaylist(
        playlistId: String,
        name: String?,
        description: String?,
        userId: String
    ): Result<Playlist> {
        return try {
            val body = mutableMapOf<String, Any>(
                "userId" to userId
            )
            name?.let { body["name"] = it }
            description?.let { body["description"] = it }
            
            val response = api.updatePlaylist(playlistId, body)
            if (response.success && response.playlist != null) {
                Result.success(response.playlist.toPlaylist())
            } else {
                Result.failure(Exception(response.error ?: "Failed to update playlist"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String, userId: String): Result<Boolean> {
        return try {
            val response = api.deletePlaylist(playlistId, userId)
            Result.success(response.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addSongToPlaylist(
        playlistId: String,
        song: Song,
        userId: String
    ): Result<Playlist> {
        return try {
            val body = mapOf(
                "userId" to userId,
                "song" to song.toSongDtoMap()
            )
            val response = api.addSongToPlaylist(playlistId, body)
            if (response.success && response.playlist != null) {
                Result.success(response.playlist.toPlaylist())
            } else {
                Result.failure(Exception(response.error ?: "Failed to add song"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSongFromPlaylist(
        playlistId: String,
        videoId: String,
        userId: String
    ): Result<Playlist> {
        return try {
            val response = api.removeSongFromPlaylist(playlistId, userId, videoId)
            if (response.success && response.playlist != null) {
                Result.success(response.playlist.toPlaylist())
            } else {
                Result.failure(Exception(response.error ?: "Failed to remove song"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<User> {
        return try {
            val response = api.register(RegisterRequest(username, email, password))
            if (response.success && response.user != null) {
                Result.success(response.user.toUser())
            } else {
                Result.failure(Exception(response.error ?: "Registration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(email: String, password: String): Result<User> {
        return try {
            val response = api.login(LoginRequest(email, password))
            if (response.success && response.user != null) {
                Result.success(response.user.toUser())
            } else {
                Result.failure(Exception(response.error ?: "Invalid credentials"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUser(userId: String): Result<User> {
        return try {
            val response = api.getUser(userId)
            if (response.success && response.user != null) {
                Result.success(response.user.toUser())
            } else {
                Result.failure(Exception(response.error ?: "User not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLikedSongs(userId: String): Result<List<Song>> {
        return try {
            val response = api.getLikedSongs(userId)
            if (response.success && response.songs != null) {
                Result.success(response.songs.toSongs())
            } else {
                Result.failure(Exception(response.error ?: "Failed to get liked songs"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addLikedSong(userId: String, song: Song): Result<Boolean> {
        return try {
            val body = mapOf("song" to song.toSongDtoMap())
            val response = api.addLikedSong(userId, body)
            Result.success(response.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeLikedSong(userId: String, videoId: String): Result<Boolean> {
        return try {
            val response = api.removeLikedSong(userId, videoId)
            Result.success(response.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentlyPlayed(userId: String): Result<List<Song>> {
        return try {
            val response = api.getRecentlyPlayed(userId)
            if (response.success && response.songs != null) {
                Result.success(response.songs.toSongs())
            } else {
                Result.failure(Exception(response.error ?: "Failed to get recently played"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addRecentlyPlayed(userId: String, song: Song): Result<Boolean> {
        return try {
            val body = mapOf("song" to song.toSongDtoMap())
            val response = api.addRecentlyPlayed(userId, body)
            Result.success(response.success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pingBackend(): Result<Boolean> {
        return try {
            val response = api.getHealth()
            val isHealthy = response.status?.equals("healthy", ignoreCase = true) == true
            Result.success(isHealthy)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val TAG = "MusicRepository"
        
        /**
         * Supported mood categories that have API backend support.
         * Language-based moods (Hindi, Telugu, Tamil, English, etc.) are excluded
         * as the API does not return playlists for them.
         */
        private val SUPPORTED_MOODS = setOf(
            "chill",
            "relax",
            "relaxed",
            "calm",
            "happy",
            "joy",
            "joyful",
            "cheerful",
            "sad",
            "melancholy",
            "blue",
            "energetic",
            "energy",
            "pump up",
            "energize",
            "workout",
            "gym",
            "fitness",
            "focus",
            "concentration",
            "study",
            "party",
            "celebration",
            "dance",
            "romantic",
            "love",
            "romance",
            "sleep",
            "sleepy",
            "bedtime",
            "night",
            "commute",
            "travel",
            "feel good",
            "feel-good",
            "gaming",
            "game",
            "angry",
            "rage",
            "nostalgic",
            "nostalgia",
            "excited",
            "excitement",
            "groovy",
            "funky",
            "decades",
            "decade",
            "classic",
            "classics"
        )
        
        /**
         * Filters mood categories to include only those supported by the API.
         * Language-based categories are automatically excluded.
         */
        private fun filterSupportedMoods(moods: List<com.aura.music.data.model.MoodCategory>): List<com.aura.music.data.model.MoodCategory> {
            return moods.filter { mood ->
                SUPPORTED_MOODS.contains(mood.title.lowercase().trim())
            }
        }

        private inline fun safeLog(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // Ignore when android.util.Log is not mocked (unit tests)
            }
        }
    }
}

