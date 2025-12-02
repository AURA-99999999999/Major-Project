package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.mapper.toPlaylist
import com.aura.music.data.mapper.toPlaylists
import com.aura.music.data.mapper.toSong
import com.aura.music.data.mapper.toSongDtoMap
import com.aura.music.data.mapper.toSongs
import com.aura.music.data.mapper.toUser
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.Song
import com.aura.music.data.model.User
import com.aura.music.data.remote.MusicApi
import com.aura.music.data.remote.dto.LoginRequest
import com.aura.music.data.remote.dto.RegisterRequest

class MusicRepository(
    private val api: MusicApi
) {
    suspend fun searchSongs(query: String, limit: Int = 20): Result<List<Song>> {
        return try {
            safeLog { Log.d(TAG, "searchSongs() query=$query, limit=$limit") }
            val response = api.searchSongs(query, limit)
            if (response.success && response.results != null) {
                safeLog { Log.d(TAG, "searchSongs() success count=${response.results.size}") }
                Result.success(response.results.toSongs())
            } else {
                val error = response.error ?: "Search failed"
                safeLog { Log.w(TAG, "searchSongs() failed: $error") }
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            safeLog { Log.e(TAG, "searchSongs() exception", e) }
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

        private inline fun safeLog(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // Ignore when android.util.Log is not mocked (unit tests)
            }
        }
    }
}

