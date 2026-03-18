package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.mapper.toPlaylist
import com.aura.music.data.mapper.toPlaylists
import com.aura.music.data.mapper.toSaavnArtists
import com.aura.music.data.mapper.toSearchResults
import com.aura.music.data.mapper.toSong
import com.aura.music.data.mapper.toSongDtoMap
import com.aura.music.data.mapper.toSongs
import com.aura.music.data.mapper.toUser
import com.aura.music.data.model.CollaborativeSection
import com.aura.music.data.model.HomeData
import com.aura.music.data.model.JioSaavnPlaylist
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.SearchResults
import com.aura.music.data.model.Song
import com.aura.music.data.model.TrendingData
import com.aura.music.data.model.User
import com.aura.music.data.remote.MusicApi
import com.aura.music.data.remote.dto.AddSongToPlaylistRequest
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.PlaylistSongRequest
import com.aura.music.data.remote.dto.RegisterRequest
import com.aura.music.data.remote.dto.TrackEventRequest
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MusicRepository(
    private val api: MusicApi,
    private val firestoreLogger: FirestoreLogger = NoOpFirestoreLogger
) {
    /**
     * Fetch metadata for all daily mixes (no songs)
     */
    suspend fun getDailyMixesMeta(): Result<List<com.aura.music.data.model.MixCardMeta>> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank() || uid == "guest") {
                Result.failure(Exception("User not authenticated"))
            } else {
                val response = api.getDailyMixesMeta(uid = uid)
                val metaList = response
                    .filter { it.key != null && it.name != null }
                    .map { meta ->
                        com.aura.music.data.model.MixCardMeta(
                            key = meta.key ?: "unknown",
                            name = meta.name ?: "",
                            description = meta.description ?: "",
                            icon = meta.icon ?: "\uD83C\uDFB5", // Default music note
                            color = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(meta.color ?: "#9B87F5")) } catch (_: Exception) { androidx.compose.ui.graphics.Color(0xFF9B87F5) }
                        )
                    }
                android.util.Log.d("DailyMix", "Response: $metaList")
                Result.success(metaList)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetch songs for a single daily mix by type (e.g., "favorites", "similar", "discover", "mood")
     */
    suspend fun getDailyMixSongs(type: String, refresh: Boolean = false): Result<com.aura.music.data.model.MixCardData> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank() || uid == "guest") {
                Result.failure(Exception("User not authenticated"))
            } else {
                val mix = api.getDailyMix(type = type, uid = uid, refresh = refresh)
                Result.success(
                    com.aura.music.data.model.MixCardData(
                        key = type,
                        name = mix.name,
                        description = mix.description,
                        icon = when (type) {
                            "favorites" -> "\uD83C\uDFB5"
                            "similar" -> "\uD83C\uDFB6"
                            "discover" -> "\u2728"
                            "mood" -> "\uD83C\uDF19"
                            else -> "\uD83C\uDFB5"
                        },
                        color = when (type) {
                            "favorites" -> androidx.compose.ui.graphics.Color(0xFF9B87F5)
                            "similar" -> androidx.compose.ui.graphics.Color(0xFF87F5E0)
                            "discover" -> androidx.compose.ui.graphics.Color(0xFFF5B787)
                            "mood" -> androidx.compose.ui.graphics.Color(0xFFF587B2)
                            else -> androidx.compose.ui.graphics.Color(0xFF9B87F5)
                        },
                        songs = mix.songs?.map { it.toSong() } ?: emptyList()
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    private fun toTrackingSongPayload(song: Song): Map<String, Any> {
        val artistValue = song.artist?.takeIf { it.isNotBlank() }
            ?: song.artists?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: ""

        val yearValue = song.year.takeIf { it.isNotBlank() && it != "unknown" } ?: ""

        return mapOf(
            "id" to song.videoId,
            "title" to song.title,
            "album" to (song.album ?: ""),
            "artist" to artistValue,
            "language" to song.language,
            "global_play_count" to song.playCount,
            "starring" to (song.starring ?: ""),
            "image" to (song.thumbnail ?: ""),
            "url" to (song.url ?: ""),
            "year" to yearValue,
        )
    }

    suspend fun trackPlayEvent(song: Song): Result<Unit> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                return Result.failure(Exception("User not authenticated"))
            }

            val response = api.trackPlay(
                TrackEventRequest(
                    uid = uid,
                    song = toTrackingSongPayload(song)
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Failed to track play"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun trackLikeEvent(song: Song): Result<Unit> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) return Result.failure(Exception("User not authenticated"))

            val response = api.trackLike(
                TrackEventRequest(
                    uid = uid,
                    song = toTrackingSongPayload(song)
                )
            )

            if (response.success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception(response.error ?: "Failed to track like"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchSongs(query: String, limit: Int = 20): Result<List<Song>> {
        return try {
            val response = api.searchAllCategories(query)
            if (response.success) {
                Result.success(response.songs?.toSongs().orEmpty().take(limit))
            } else {
                Result.failure(Exception(response.error ?: "Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchAllCategories(query: String): Result<SearchResults> {
        return try {
            val response = api.searchAllCategories(query)
            val saavnArtists = try {
                val saavnResponse = api.searchArtists(query)
                if (saavnResponse.success) {
                    saavnResponse.data.results.toSaavnArtists()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }

            if (!response.success) {
                return Result.failure(Exception(response.error ?: "Search failed"))
            }

            val baseResults = response.toSearchResults()
            Result.success(
                SearchResults(
                    songs = baseResults.songs,
                    albums = baseResults.albums,
                    artists = saavnArtists.take(SEARCH_CATEGORY_LIMIT),
                    playlists = baseResults.playlists,
                    count = baseResults.songs.size + baseResults.albums.size + saavnArtists.take(SEARCH_CATEGORY_LIMIT).size + baseResults.playlists.size,
                    query = query,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun limitAndDedupeSearchResults(results: SearchResults): SearchResults {
        val songs = results.songs
            .distinctBy { song ->
                val id = song.videoId.trim()
                if (id.isNotEmpty()) id else "${song.title}|${song.artist.orEmpty()}"
            }
            .take(SEARCH_CATEGORY_LIMIT)

        val albums = results.albums
            .distinctBy { album ->
                val id = album.browseId.trim()
                if (id.isNotEmpty()) id else "${album.title}|${album.artists.joinToString(",")}"
            }
            .take(SEARCH_CATEGORY_LIMIT)

        val artists = results.artists
            .distinctBy { artist ->
                val id = artist.browseId.trim()
                if (id.isNotEmpty()) id else artist.name
            }
            .take(SEARCH_CATEGORY_LIMIT)

        val playlists = results.playlists
            .distinctBy { playlist ->
                val id = playlist.playlistId.trim()
                if (id.isNotEmpty()) id else playlist.browseId.trim().ifEmpty { playlist.title }
            }
            .take(SEARCH_CATEGORY_LIMIT)

        return results.copy(
            songs = songs,
            albums = albums,
            artists = artists,
            playlists = playlists,
            count = songs.size + albums.size + artists.size + playlists.size
        )
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
            val mappedSongs = response.songs.map { song ->
                com.aura.music.data.model.Song(
                    videoId = song.id,
                    title = song.name,
                    artist = song.primaryArtists,
                    artists = song.primaryArtists?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
                    thumbnail = song.image,
                    duration = null,
                    url = song.mediaUrl,
                    album = song.album,
                    artistId = browseId
                )
            }

            val mappedAlbums = response.albums.map { album ->
                com.aura.music.data.remote.dto.ArtistAlbumDto(
                    browseId = album.query ?: "",
                    title = album.title,
                    thumbnail = album.image ?: "",
                    year = null,
                    type = "album"
                )
            }

            val mappedArtist = com.aura.music.data.remote.dto.ArtistDetailDto(
                artistId = browseId,
                name = response.artist,
                thumbnail = mappedSongs.firstOrNull()?.thumbnail ?: "",
                subscribers = null,
                description = null,
                topSongs = mappedSongs,
                albums = mappedAlbums
            )

            safeLog {
                Log.d(TAG, "getArtistDetails() success: ${mappedArtist.name}")
                Log.d(TAG, "getArtistDetails() songs=${mappedArtist.topSongs.size} albums=${mappedArtist.albums.size}")
            }
            Result.success(mappedArtist)
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

    /**
     * Fetches trending songs grouped by language (Hindi, Telugu, English)
     * Matches web app.js implementation using JioSaavn search queries.
     */
    suspend fun getTrendingByLanguage(): Result<TrendingData> = Result.success(TrendingData())

    /**
     * Legacy getTrending() for backward compatibility.
     * Returns all trending songs combined.
     */
    suspend fun getTrending(limit: Int = 20): Result<List<Song>> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val response = api.getTrending(limit, uid)
            if (response.success && response.results != null) {
                Result.success(response.results.toSongs())
            } else {
                Result.failure(Exception(response.error ?: "Trending failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFreshPicks(limit: Int = 15): Result<List<Song>> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val response = api.getFreshPicks(uid = uid, limit = limit)
            val songs = when {
                response.songs != null -> response.songs.toSongs()
                response.results != null -> response.results.toSongs()
                else -> emptyList()
            }

            if (response.success) {
                Result.success(songs)
            } else {
                Result.failure(Exception(response.error ?: "Fresh picks failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHomeData(forceRefresh: Boolean = false): Result<HomeData> {
        return try {
            val freshPicksResult = getFreshPicks(limit = 15)
            val response = api.getHome(uid = FirebaseAuth.getInstance().currentUser?.uid)
            val trending = freshPicksResult.getOrDefault(response.trending?.toSongs().orEmpty())
            val recommendations = response.recommendations?.toSongs().orEmpty()
            val collaborative = response.collaborative?.toSongs().orEmpty()

            Result.success(
                HomeData(
                    trendingData = TrendingData(),
                    trending = trending,
                    recommendations = recommendations,
                    collaborative = collaborative,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchUserRecommendations(): List<Song> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
            api.getRecommendations(uid).results.toSongs()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchCollaborativeRecommendations(limit: Int = 12): Result<CollaborativeSection> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank() || uid == "guest") {
                return Result.success(
                    CollaborativeSection(
                        title = "Users Like You Also Listen To",
                        tracks = emptyList(),
                        count = 0
                    )
                )
            }

            val response = api.getCollaborativeRecommendations(uid = uid, limit = limit)
            val tracks = response.results.toSongs()
            Result.success(
                CollaborativeSection(
                    title = "Users Like You Also Listen To",
                    tracks = tracks,
                    count = tracks.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTopArtists(limit: Int = 10, forceRefresh: Boolean = false): Result<List<com.aura.music.data.remote.dto.TopArtistDto>> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                Result.success(emptyList())
            } else {
                safeLog { Log.d(TAG, "getTopArtists() uid=$uid limit=$limit") }
                val response = api.getTopArtists(uid = uid, limit = limit)
                if (response.success) {
                    Result.success(response.artists)
                } else {
                    Result.failure(Exception(response.error ?: "Failed to get top artists"))
                }
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getTopArtists() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getDailyMixes(refresh: Boolean = false): Result<com.aura.music.data.remote.dto.DailyMixResponse> {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank() || uid == "guest") {
                Result.failure(Exception("User not authenticated"))
            } else {
                safeLog { Log.d(TAG, "getDailyMixes() uid=$uid refresh=$refresh") }
                Result.success(api.getDailyMixes(uid = uid, refresh = refresh))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getDailyMixes() exception", e) }
            Result.failure(e)
        }
    }

    suspend fun getTrendingPlaylists(limit: Int = 10, forceRefresh: Boolean = false): Result<List<JioSaavnPlaylist>> {
        return Result.success(emptyList())
    }

    suspend fun getMoodCategories(): Result<List<com.aura.music.data.model.MoodCategory>> {
        return Result.success(
            listOf(
                com.aura.music.data.model.MoodCategory("Chill", "Chill"),
                com.aura.music.data.model.MoodCategory("Workout", "Workout"),
                com.aura.music.data.model.MoodCategory("Party", "Party"),
                com.aura.music.data.model.MoodCategory("Focus", "Focus"),
                com.aura.music.data.model.MoodCategory("Romantic", "Romantic"),
                com.aura.music.data.model.MoodCategory("Energetic", "Energetic"),
                com.aura.music.data.model.MoodCategory("Sad", "Sad"),
                com.aura.music.data.model.MoodCategory("Happy", "Happy"),
                com.aura.music.data.model.MoodCategory("Meditation", "Meditation"),
                com.aura.music.data.model.MoodCategory("Sleep", "Sleep"),
                com.aura.music.data.model.MoodCategory("Study", "Study"),
                com.aura.music.data.model.MoodCategory("Driving", "Driving"),
            )
        )
    }

    suspend fun getMoodPlaylists(mood: String, limit: Int = 10): Result<List<JioSaavnPlaylist>> {
        return try {
            safeLog { Log.d(TAG, "getMoodPlaylists() mood=$mood limit=$limit") }
            val response = api.getMoodPlaylists(mood, limit)
            if (response.success) {
                val playlists = response.playlists.map { it.toJioSaavnPlaylist() }
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

    suspend fun getYTMusicPlaylistSongs(playlistId: String, limit: Int = 50): Result<com.aura.music.data.model.PlaylistDetail> {
        return try {
            if (playlistId.startsWith("http", ignoreCase = true)) {
                return getJioSaavnPlaylistSongs(playlistId)
            }
            safeLog { Log.d(TAG, "getYTMusicPlaylistSongs() playlistId=$playlistId limit=$limit") }
            // For ID-based playlists, use the detail endpoint because it already returns
            // playlist metadata + songs in the schema the app expects.
            val response = api.getYTPlaylistDetails(playlistId)
            if (response.success && response.playlist != null) {
                val playlistDto = response.playlist
                val playlistDetail = com.aura.music.data.model.PlaylistDetail(
                    id = playlistDto.playlistId,
                    title = playlistDto.title,
                    description = playlistDto.description ?: "",
                    thumbnail = playlistDto.thumbnail,
                    author = playlistDto.author,
                    songCount = playlistDto.trackCount,
                    songs = playlistDto.songs.take(limit)
                )
                safeLog { Log.d(TAG, "getYTMusicPlaylistSongs() success songCount=${playlistDetail.songs.size}") }
                Result.success(playlistDetail)
            } else {
                val error = response.error ?: "Failed to get playlist songs"
                safeLog { Log.w(TAG, "getYTMusicPlaylistSongs() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getYTMusicPlaylistSongs() exception", e) }
            Result.failure(e)
        }
    }

    private suspend fun getJioSaavnPlaylistSongs(playlistUrl: String): Result<com.aura.music.data.model.PlaylistDetail> {
        return try {
            safeLog { Log.d(TAG, "getJioSaavnPlaylistSongs() playlist_url=$playlistUrl") }
            val response = api.getPlaylistSongsByUrl(playlistUrl)
            if (!response.success) {
                return Result.failure(Exception(response.error ?: "Failed to get playlist songs"))
            }

            val songs = response.data.orEmpty().map { it.toSong() }

            val playlistDetail = com.aura.music.data.model.PlaylistDetail(
                id = playlistUrl,
                title = "Playlist",
                description = "",
                thumbnail = songs.firstOrNull()?.thumbnail.orEmpty(),
                author = "JioSaavn",
                songCount = songs.size,
                songs = songs
            )

            safeLog { Log.d(TAG, "getJioSaavnPlaylistSongs() success songCount=${songs.size}") }
            Result.success(playlistDetail)
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "getJioSaavnPlaylistSongs() exception", e) }
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
            val artistValue = song.artist?.takeIf { it.isNotBlank() }
                ?: song.artists?.firstOrNull()?.takeIf { it.isNotBlank() }
                ?: ""

            val body = AddSongToPlaylistRequest(
                userId = userId,
                song = PlaylistSongRequest(
                    videoId = song.videoId,
                    title = song.title,
                    artist = artistValue,
                    artists = song.artists ?: emptyList(),
                    starring = song.starring ?: "",
                    thumbnail = song.thumbnail ?: "",
                    duration = song.duration ?: "",
                    url = song.url ?: "",
                    album = song.album ?: "",
                    artistId = song.artistId ?: ""
                )
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
        private const val SEARCH_CATEGORY_LIMIT = 5
        
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

