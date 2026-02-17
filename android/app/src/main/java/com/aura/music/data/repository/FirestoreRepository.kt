package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.model.Song
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface FirestoreLogger {
    suspend fun logSearch(query: String): Result<Unit>

    suspend fun logSongPlay(
        videoId: String,
        songName: String,
        albumName: String,
        artists: List<String>,
        source: String
    ): Result<Unit>
}

object NoOpFirestoreLogger : FirestoreLogger {
    override suspend fun logSearch(query: String): Result<Unit> = Result.success(Unit)

    override suspend fun logSongPlay(
        videoId: String,
        songName: String,
        albumName: String,
        artists: List<String>,
        source: String
    ): Result<Unit> = Result.success(Unit)
}

/**
 * Repository for managing all Firestore operations.
 *
 * Responsibilities:
 * - User document creation and updates
 * - Search query logging per user
 * - Song play tracking per user
 *
 * Architecture:
 * - Called from ViewModels only (never from UI)
 * - Uses authenticated Firebase user UID
 * - All operations use server timestamps
 * - Idempotent and production-ready
 */
class FirestoreRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : FirestoreLogger {

    companion object {
        private const val TAG = "FirestoreRepository"
        // Collection names
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_SEARCHES = "searches"
        private const val SUBCOLLECTION_PLAYS = "plays"
        private const val SUBCOLLECTION_LIKED_SONGS = "likedSongs"
        
        // Field names - users
        private const val FIELD_EMAIL = "email"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_LAST_LOGIN = "lastLogin"
        
        // Field names - searches
        private const val FIELD_QUERY = "query"
        private const val FIELD_TIMESTAMP = "timestamp"
        
        // Field names - plays
        private const val FIELD_VIDEO_ID = "videoId"
        private const val FIELD_SONG_NAME = "title"
        private const val FIELD_ARTISTS = "artists"
        private const val FIELD_ALBUM = "album"
        private const val FIELD_SOURCE = "source"
        private const val FIELD_FIRST_PLAYED_AT = "firstPlayedAt"
        private const val FIELD_LAST_PLAYED_AT = "lastPlayedAt"
        private const val FIELD_PLAY_COUNT = "playCount"

        // Field names - liked songs
        private const val FIELD_THUMBNAIL = "thumbnail"
        private const val FIELD_DURATION = "duration"
        private const val FIELD_LIKED_AT = "likedAt"
    }

    suspend fun addToLikedSongs(userId: String, song: Song): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                return Result.failure(IllegalStateException("User not authenticated"))
            }

            val normalizedVideoId = song.videoId.trim()
            if (normalizedVideoId.isBlank()) {
                return Result.failure(IllegalArgumentException("Invalid videoId"))
            }

            val songRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_LIKED_SONGS)
                .document(normalizedVideoId)

            val payload = mapOf(
                FIELD_VIDEO_ID to normalizedVideoId,
                FIELD_SONG_NAME to song.title.trim(),
                FIELD_ALBUM to (song.album ?: ""),
                FIELD_ARTISTS to resolveArtists(song),
                FIELD_THUMBNAIL to (song.thumbnail ?: ""),
                FIELD_DURATION to (song.duration ?: ""),
                FIELD_LIKED_AT to FieldValue.serverTimestamp()
            )

            firestore.runTransaction { transaction ->
                val existing = transaction.get(songRef)
                if (!existing.exists()) {
                    transaction.set(songRef, payload)
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addToLikedSongs() failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeFromLikedSongs(userId: String, videoId: String): Result<Unit> {
        return try {
            if (userId.isBlank()) {
                return Result.failure(IllegalStateException("User not authenticated"))
            }

            val normalizedVideoId = videoId.trim()
            if (normalizedVideoId.isBlank()) {
                return Result.failure(IllegalArgumentException("Invalid videoId"))
            }

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_LIKED_SONGS)
                .document(normalizedVideoId)
                .delete()
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeFromLikedSongs() failed", e)
            Result.failure(e)
        }
    }

    fun getLikedSongs(userId: String): Flow<List<Song>> = callbackFlow {
        if (userId.isBlank()) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val likedRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_LIKED_SONGS)
            .orderBy(FIELD_LIKED_AT, Query.Direction.DESCENDING)

        val listener = likedRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "getLikedSongs() failed", error)
                close(error)
                return@addSnapshotListener
            }

            val songs = snapshot?.documents
                ?.mapNotNull { it.toLikedSong() }
                ?: emptyList()

            trySend(songs)
        }

        awaitClose { listener.remove() }
    }

    fun isSongLiked(userId: String, videoId: String): Flow<Boolean> = callbackFlow {
        if (userId.isBlank() || videoId.isBlank()) {
            trySend(false)
            close()
            return@callbackFlow
        }

        val songRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_LIKED_SONGS)
            .document(videoId)

        val listener = songRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "isSongLiked() failed", error)
                close(error)
                return@addSnapshotListener
            }

            trySend(snapshot?.exists() == true)
        }

        awaitClose { listener.remove() }
    }

    private fun resolveArtists(song: Song): List<String> {
        return when {
            !song.artists.isNullOrEmpty() -> song.artists
            !song.artist.isNullOrBlank() -> listOf(song.artist)
            else -> emptyList()
        }
    }

    /**
     * Creates or updates the user document in Firestore.
     * 
     * Called when: User successfully logs in via Firebase Authentication
     * 
     * Behavior:
     * - If user document doesn't exist: Creates it with email, createdAt, lastLogin
     * - If user document exists: Only updates lastLogin timestamp
     * 
     * Uses SetOptions.merge() to ensure createdAt is never overwritten.
     * 
     * @return Result indicating success or failure
     */
    suspend fun createOrUpdateUser(): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "createOrUpdateUser() - No authenticated user")
                return Result.failure(Exception("User not authenticated"))
            }

            val userId = currentUser.uid
            val userEmail = currentUser.email ?: ""
            
            Log.d(TAG, "createOrUpdateUser() for userId=$userId")

            val userDocRef = firestore.collection(COLLECTION_USERS).document(userId)
            
            // Check if document exists to determine if this is first login
            val documentSnapshot = userDocRef.get().await()
            
            val userData = if (documentSnapshot.exists()) {
                // Existing user - only update lastLogin
                Log.d(TAG, "Updating existing user lastLogin")
                hashMapOf<String, Any>(
                    FIELD_LAST_LOGIN to FieldValue.serverTimestamp()
                )
            } else {
                // New user - set all fields including createdAt
                Log.d(TAG, "Creating new user document")
                hashMapOf<String, Any>(
                    FIELD_EMAIL to userEmail,
                    FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                    FIELD_LAST_LOGIN to FieldValue.serverTimestamp()
                )
            }

            // Use merge to safely update without overwriting existing fields
            userDocRef.set(userData, SetOptions.merge()).await()
            
            Log.d(TAG, "✓ User document created/updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to create/update user document", e)
            Result.failure(e)
        }
    }

    /**
     * Logs a search query to the user's searches subcollection.
     * 
     * Called when: User performs a search in SearchViewModel
     * 
     * Storage location: users/{uid}/searches/{auto-generated-id}
     * 
     * @param query The search query string entered by the user
     * @return Result indicating success or failure
     */
    override suspend fun logSearch(query: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "logSearch() - No authenticated user")
                return Result.success(Unit) // Fail silently when not logged in
            }

            if (query.isBlank()) {
                Log.d(TAG, "logSearch() - Skipping blank query")
                return Result.success(Unit)
            }

            val userId = currentUser.uid
            Log.d(TAG, "logSearch() userId=$userId query='$query'")

            val searchData = hashMapOf(
                FIELD_QUERY to query.trim(),
                FIELD_TIMESTAMP to FieldValue.serverTimestamp()
            )

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_SEARCHES)
                .add(searchData)
                .await()

            Log.d(TAG, "✓ Search logged successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to log search", e)
            Result.failure(e)
        }
    }

    /**
     * Logs a song play to the user's plays subcollection.
     * 
     * Called when: User plays a song via MusicService
     * 
     * Storage location: users/{uid}/plays/{auto-generated-id}
     * 
     * @param song The Song object containing all track information
     * @return Result indicating success or failure
     */
    override suspend fun logSongPlay(
        videoId: String,
        songName: String,
        albumName: String,
        artists: List<String>,
        source: String
    ): Result<Unit> {
        return try {
            Log.d(
                TAG,
                "logSongPlay() called with videoId='${videoId}', songName='${songName}', albumName='${albumName}', artists=${artists.size}, source='${source}'"
            )
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "logSongPlay() - No authenticated user")
                return Result.success(Unit) // Fail silently when not logged in
            }

            val userId = currentUser.uid
            val normalizedVideoId = videoId.trim()
            val normalizedSongName = songName.trim()
            val normalizedAlbumName = albumName.trim()
            val normalizedArtists = artists
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            val normalizedSource = source.trim().ifBlank { "unknown" }

            if (normalizedVideoId.isBlank() || normalizedSongName.isBlank()) {
                Log.w(
                    TAG,
                    "logSongPlay() - Skipping write due to incomplete metadata: " +
                        "videoId='${normalizedVideoId}', songName='${normalizedSongName}', albumName='${normalizedAlbumName}', artists=${normalizedArtists.size}"
                )
                return Result.success(Unit)
            }

            Log.d(TAG, "logSongPlay() userId=$userId videoId='$normalizedVideoId' song='$normalizedSongName'")

            val playDocRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYS)
                .document(normalizedVideoId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(playDocRef)
                if (snapshot.exists()) {
                    transaction.update(
                        playDocRef,
                        mapOf(
                            FIELD_SONG_NAME to normalizedSongName,
                            FIELD_ARTISTS to normalizedArtists,
                            FIELD_ALBUM to normalizedAlbumName,
                            FIELD_SOURCE to normalizedSource,
                            FIELD_LAST_PLAYED_AT to FieldValue.serverTimestamp(),
                            FIELD_PLAY_COUNT to FieldValue.increment(1)
                        )
                    )
                } else {
                    transaction.set(
                        playDocRef,
                        mapOf(
                            FIELD_VIDEO_ID to normalizedVideoId,
                            FIELD_SONG_NAME to normalizedSongName,
                            FIELD_ARTISTS to normalizedArtists,
                            FIELD_ALBUM to normalizedAlbumName,
                            FIELD_SOURCE to normalizedSource,
                            FIELD_FIRST_PLAYED_AT to FieldValue.serverTimestamp(),
                            FIELD_LAST_PLAYED_AT to FieldValue.serverTimestamp(),
                            FIELD_PLAY_COUNT to 1
                        ),
                        SetOptions.merge()
                    )
                }
            }.await()

            Log.d(TAG, "✓ Song play logged successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to log song play", e)
            Result.failure(e)
        }
    }

    /**
     * Updates the lastPlayed document in the user's profile.
     * 
     * Called when: A song starts playing via MusicService
     * 
     * Storage location: users/{uid}/lastPlayed (single document)
     * 
     * Behavior:
     * - Saves minimal metadata needed for mini player display
     * - Does NOT auto-play song (just stores for reference)
     * - Overwrites previous lastPlayed data
     * 
     * @param song The Song object being played
     * @return Result indicating success or failure
     */
    suspend fun updateLastPlayedSong(song: Song): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "updateLastPlayedSong() - No authenticated user")
                return Result.success(Unit) // Fail silently
            }

            val userId = currentUser.uid
            val normalizedVideoId = song.videoId.trim()
            val normalizedTitle = song.title.trim()
            val normalizedArtists = resolveArtists(song)
            val normalizedThumbnail = song.thumbnail ?: ""

            if (normalizedVideoId.isBlank() || normalizedTitle.isBlank()) {
                Log.w(
                    TAG,
                    "updateLastPlayedSong() - Skipping due to incomplete metadata: " +
                        "videoId='$normalizedVideoId', title='$normalizedTitle'"
                )
                return Result.success(Unit)
            }

            Log.d(TAG, "updateLastPlayedSong() userId=$userId videoId='$normalizedVideoId' title='$normalizedTitle'")

            val lastPlayedData = mapOf(
                FIELD_VIDEO_ID to normalizedVideoId,
                FIELD_SONG_NAME to normalizedTitle,
                FIELD_ARTISTS to normalizedArtists,
                FIELD_ALBUM to (song.album ?: ""),
                FIELD_THUMBNAIL to normalizedThumbnail,
                FIELD_DURATION to (song.duration ?: ""),
                FIELD_LAST_PLAYED_AT to FieldValue.serverTimestamp()
            )

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection("lastPlayed")
                .document("current")
                .set(lastPlayedData, SetOptions.merge())
                .await()

            Log.d(TAG, "✓ Last played song updated successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to update last played song", e)
            Result.failure(e)
        }
    }

    /**
     * Retrieves the lastPlayed song for the current user.
     * 
     * Called when: App launches or user logs in
     * 
     * Storage location: users/{uid}/lastPlayed/current
     * 
     * Behavior:
     * - Returns null if no lastPlayed document exists
     * - Returns Song object with minimal metadata
     * - Does NOT auto-play (just restoration)
     * 
     * @return Song? The last played song or null
     */
    suspend fun getLastPlayedSong(): Song? {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.w(TAG, "getLastPlayedSong() - No authenticated user")
                return null
            }

            val userId = currentUser.uid
            Log.d(TAG, "getLastPlayedSong() userId=$userId")

            val documentSnapshot = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection("lastPlayed")
                .document("current")
                .get()
                .await()

            if (!documentSnapshot.exists()) {
                Log.d(TAG, "No lastPlayed document found")
                return null
            }

            val song = documentSnapshot.toLikedSong()
            Log.d(TAG, "✓ Last played song retrieved: ${song?.title}")
            song
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to retrieve last played song", e)
            null
        }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toLikedSong(): Song? {
    val videoId = getString("videoId") ?: id
    val title = getString("title") ?: return null
    val album = getString("album")
    val thumbnail = getString("thumbnail")
    val duration = getString("duration")
    val artists = (get("artists") as? List<*>)
        ?.mapNotNull { it as? String }
        ?: emptyList()

    return Song(
        videoId = videoId,
        title = title,
        artist = artists.firstOrNull(),
        artists = if (artists.isNotEmpty()) artists else null,
        thumbnail = thumbnail,
        duration = duration,
        album = album
    )
}
