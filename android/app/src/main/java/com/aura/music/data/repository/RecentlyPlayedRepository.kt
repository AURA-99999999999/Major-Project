package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.local.RecentlyPlayedDao
import com.aura.music.data.local.RecentlyPlayedEntity
import com.aura.music.data.model.Song
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RecentlyPlayedRepository(
    private val recentlyPlayedDao: RecentlyPlayedDao,
    private val firestoreRepository: FirestoreRepository = FirestoreRepository()
) {
    private val auth = FirebaseAuth.getInstance()

    /**
     * Get recently played tracks for the current logged-in user.
     * 
     * Flow:
     * 1. Returns local cache immediately (fast load)
     * 2. Syncs from Firestore in background if available
     * 3. Updates local cache with Firestore data
     * 
     * Returns empty list if no user is authenticated.
     */
    fun getRecentTracks(limit: Int = 6): Flow<List<Song>> {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) {
            return flowOf(emptyList())
        }
        
        return recentlyPlayedDao.getRecentTracks(currentUserId, limit).map { tracks ->
            tracks.map { it.toSong() }
        }
    }

    /**
     * Sync recently played from Firestore to local cache.
     * 
     * Called on login to hydrate local cache with cloud data.
     * This ensures:
     * - Latest tracks from other devices are visible locally
     * - Offline mode has recent history available
     * - Cross-device consistency
     */
    suspend fun syncRecentlyPlayedFromFirestore(limit: Int = 6, forceRefresh: Boolean = false) {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) {
            Log.w(TAG, "Cannot sync: no authenticated user")
            return
        }

        try {
            Log.d(TAG, "Syncing recently played from Firestore for user '$currentUserId' forceRefresh=$forceRefresh")
            
            val firestoreResult = firestoreRepository.getRecentlyPlayedSongs(limit)
            firestoreResult.fold(
                onSuccess = { songs ->
                    // Convert Firestore Songs to local entities and batch insert
                    val entities = songs.map { song ->
                        RecentlyPlayedEntity(
                            userId = currentUserId,
                            id = song.videoId,
                            title = song.title,
                            artist = song.getArtistString(),
                            artworkUrl = song.thumbnail,
                            audioUrl = song.url,
                            playedAt = System.currentTimeMillis()
                        )
                    }

                    // Clear old local cache and insert fresh data from Firestore
                    recentlyPlayedDao.clearUserHistory(currentUserId)
                    entities.forEach { entity ->
                        recentlyPlayedDao.insertTrack(entity)
                    }

                    Log.d(TAG, "✓ Synced ${entities.size} recently played tracks from Firestore")
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to sync from Firestore: ${error.message}")
                    // Silently fail - local cache will be used as fallback
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error syncing recently played: ${e.message}")
        }
    }

    /**
     * Record a track as played for the current logged-in user.
     * 
     * Flow:
     * 1. Update Firestore plays collection (logSongPlay called by MusicService)
     * 2. Store in local cache for instant retrieval
     * 3. Enforce max 6 tracks per user
     * 
     * Note: Firestore update is handled by MusicService calling firestoreRepository.logSongPlay()
     * This method only maintains local cache.
     */
    suspend fun onTrackPlayed(song: Song) {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) {
            Log.w(TAG, "Cannot save recently played track: no authenticated user")
            return
        }

        val trackId = song.videoId.ifBlank {
            buildString {
                append(song.title)
                append("|")
                append(song.getArtistString())
            }
        }

        val thumbnailUrl = song.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: buildThumbnailUrl(song.videoId)

        val entity = RecentlyPlayedEntity(
            userId = currentUserId,
            id = trackId,
            title = song.title,
            artist = song.getArtistString(),
            artworkUrl = thumbnailUrl,
            audioUrl = song.url,
            playedAt = System.currentTimeMillis()
        )

        // Dedup: remove if exists (composite key: userId + id)
        recentlyPlayedDao.deleteTrackById(currentUserId, trackId)
        // Insert at top (newest)
        recentlyPlayedDao.insertTrack(entity)
        // Enforce max 6 per user
        recentlyPlayedDao.clearOverflow(currentUserId)
        
        Log.d(TAG, "Recently played cached locally: '$trackId' for user '$currentUserId'")
    }

    /**
     * Clear all recently played history for the current user.
     * Called on logout to prevent data persistence.
     */
    suspend fun clearCurrentUserHistory() {
        val currentUserId = getCurrentUserId()
        if (currentUserId.isBlank()) return
        
        recentlyPlayedDao.clearUserHistory(currentUserId)
        Log.d(TAG, "Recently played history cleared for user '$currentUserId'")
    }

    private fun getCurrentUserId(): String {
        return auth.currentUser?.uid ?: ""
    }

    /**
     * Constructs YouTube thumbnail URL from videoId if not provided.
     * 
     * Primary: https://img.youtube.com/vi/{videoId}/maxresdefault.jpg (1280x720)
     * Falls back to hqdefault at maxres unavailable (480x360)
     */
    private fun buildThumbnailUrl(videoId: String): String {
        return "https://img.youtube.com/vi/${videoId}/maxresdefault.jpg"
    }

    private fun RecentlyPlayedEntity.toSong(): Song {
        // Ensure fallback thumbnail for legacy records without artwork URL
        val finalThumbnail = artworkUrl.takeIf { !it.isNullOrBlank() }
            ?: buildThumbnailUrl(id)

        return Song(
            videoId = id,
            title = title,
            artist = artist,
            artists = artist?.let { listOf(it) },
            thumbnail = finalThumbnail,
            url = audioUrl
        )
    }

    companion object {
        private const val TAG = "RecentlyPlayedRepository"
    }
}
