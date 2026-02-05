package com.aura.music.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

interface FirestoreLogger {
    suspend fun logSearch(query: String): Result<Unit>

    suspend fun logSongPlay(
        videoId: String,
        songName: String,
        albumName: String,
        artists: List<String>
    ): Result<Unit>
}

object NoOpFirestoreLogger : FirestoreLogger {
    override suspend fun logSearch(query: String): Result<Unit> = Result.success(Unit)

    override suspend fun logSongPlay(
        videoId: String,
        songName: String,
        albumName: String,
        artists: List<String>
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
        private const val PLAY_DEDUP_WINDOW_MS = 30_000L
        
        // Collection names
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_SEARCHES = "searches"
        private const val SUBCOLLECTION_PLAYS = "plays"
        
        // Field names - users
        private const val FIELD_EMAIL = "email"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_LAST_LOGIN = "lastLogin"
        
        // Field names - searches
        private const val FIELD_QUERY = "query"
        private const val FIELD_TIMESTAMP = "timestamp"
        
        // Field names - plays
        private const val FIELD_VIDEO_ID = "videoId"
        private const val FIELD_SONG_NAME = "songName"
        private const val FIELD_ARTISTS = "artists"
        private const val FIELD_ALBUM = "albumName"
        private const val FIELD_PLAYED_AT = "playedAt"
    }

    // In-memory de-duplication to avoid rapid duplicate writes for same song
    // Keyed by userId + normalized song metadata
    private val recentPlayCache: MutableMap<String, Long> = mutableMapOf()

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
        artists: List<String>
    ): Result<Unit> {
        return try {
            Log.d(
                TAG,
                "logSongPlay() called with videoId='${videoId}', songName='${songName}', albumName='${albumName}', artists=${artists.size}"
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

            if (normalizedVideoId.isBlank() ||
                normalizedSongName.isBlank() ||
                normalizedAlbumName.isBlank() ||
                normalizedArtists.isEmpty()
            ) {
                Log.w(
                    TAG,
                    "logSongPlay() - Skipping write due to incomplete metadata: " +
                        "videoId='${normalizedVideoId}', songName='${normalizedSongName}', albumName='${normalizedAlbumName}', artists=${normalizedArtists.size}"
                )
                return Result.success(Unit)
            }

            Log.d(TAG, "logSongPlay() userId=$userId videoId='$normalizedVideoId' song='$normalizedSongName'")

            val dedupeKey = buildString {
                append(userId)
                append('|')
                append(normalizedVideoId.lowercase())
                append('|')
                append(normalizedSongName.lowercase())
                append('|')
                append(normalizedAlbumName.lowercase())
                append('|')
                append(normalizedArtists.joinToString("|") { it.lowercase() })
            }

            val now = System.currentTimeMillis()
            val shouldSkip = synchronized(recentPlayCache) {
                val lastPlayedAt = recentPlayCache[dedupeKey]
                if (lastPlayedAt != null && now - lastPlayedAt < PLAY_DEDUP_WINDOW_MS) {
                    true
                } else {
                    recentPlayCache[dedupeKey] = now
                    false
                }
            }

            if (shouldSkip) {
                Log.d(TAG, "logSongPlay() - Skipping duplicate play within window")
                return Result.success(Unit)
            }

            val playData = hashMapOf(
                FIELD_VIDEO_ID to normalizedVideoId,
                FIELD_SONG_NAME to normalizedSongName,
                FIELD_ARTISTS to normalizedArtists,
                FIELD_ALBUM to normalizedAlbumName,
                FIELD_PLAYED_AT to FieldValue.serverTimestamp()
            )

            firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYS)
                .add(playData)
                .await()

            Log.d(TAG, "✓ Song play logged successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "✗ Failed to log song play", e)
            Result.failure(e)
        }
    }
}
