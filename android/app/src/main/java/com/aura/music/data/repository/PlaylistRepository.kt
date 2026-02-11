package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.model.PlaylistSong
import com.aura.music.data.model.Song
import com.aura.music.data.model.UserPlaylist
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class PlaylistRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "PlaylistRepository"
        private const val COLLECTION_USERS = "users"
        private const val SUBCOLLECTION_PLAYLISTS = "playlists"
        private const val SUBCOLLECTION_SONGS = "songs"

        private const val FIELD_NAME = "name"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_SONG_COUNT = "songCount"

        private const val FIELD_TITLE = "title"
        private const val FIELD_ALBUM = "album"
        private const val FIELD_ARTISTS = "artists"
        private const val FIELD_THUMBNAIL = "thumbnail"
        private const val FIELD_VIDEO_ID = "videoId"
        private const val FIELD_ADDED_AT = "addedAt"
    }

    fun observePlaylists(): Flow<List<UserPlaylist>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val playlistsRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_PLAYLISTS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)

        val listener = playlistsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "observePlaylists() failed", error)
                close(error)
                return@addSnapshotListener
            }

            val playlists = snapshot?.documents
                ?.mapNotNull { it.toUserPlaylist() }
                ?: emptyList()
            trySend(playlists)
        }

        awaitClose { listener.remove() }
    }

    fun observePlaylist(playlistId: String): Flow<UserPlaylist?> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val playlistRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_PLAYLISTS)
            .document(playlistId)

        val listener = playlistRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "observePlaylist() failed", error)
                close(error)
                return@addSnapshotListener
            }

            trySend(snapshot?.toUserPlaylist())
        }

        awaitClose { listener.remove() }
    }

    fun observePlaylistSongs(playlistId: String): Flow<List<PlaylistSong>> = callbackFlow {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val songsRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(SUBCOLLECTION_PLAYLISTS)
            .document(playlistId)
            .collection(SUBCOLLECTION_SONGS)
            .orderBy(FIELD_ADDED_AT, Query.Direction.DESCENDING)

        val listener = songsRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "observePlaylistSongs() failed", error)
                close(error)
                return@addSnapshotListener
            }

            val songs = snapshot?.documents
                ?.mapNotNull { it.toPlaylistSong() }
                ?: emptyList()
            trySend(songs)
        }

        awaitClose { listener.remove() }
    }

    suspend fun createPlaylist(name: String): Result<String> {
        return try {
            val userId = requireUserId()
            val playlistRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYLISTS)
                .document()

            val payload = mapOf(
                FIELD_NAME to name.trim(),
                FIELD_CREATED_AT to FieldValue.serverTimestamp(),
                FIELD_SONG_COUNT to 0
            )

            playlistRef.set(payload).await()
            Result.success(playlistRef.id)
        } catch (e: Exception) {
            Log.e(TAG, "createPlaylist() failed", e)
            Result.failure(e)
        }
    }

    suspend fun renamePlaylist(playlistId: String, name: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val playlistRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYLISTS)
                .document(playlistId)

            playlistRef.update(FIELD_NAME, name.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "renamePlaylist() failed", e)
            Result.failure(e)
        }
    }

    suspend fun deletePlaylist(playlistId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val playlistRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYLISTS)
                .document(playlistId)

            deleteSubcollectionInBatches(playlistRef.collection(SUBCOLLECTION_SONGS))
            playlistRef.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "deletePlaylist() failed", e)
            Result.failure(e)
        }
    }

    suspend fun addSongToPlaylist(playlistId: String, song: Song): Result<Unit> {
        return try {
            val userId = requireUserId()
            val playlistRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYLISTS)
                .document(playlistId)

            val songRef = playlistRef.collection(SUBCOLLECTION_SONGS)
                .document(song.videoId)

            firestore.runTransaction { transaction ->
                val existingSong = transaction.get(songRef)
                if (existingSong.exists()) {
                    throw IllegalStateException("Song already in playlist")
                }

                val artists = when {
                    !song.artists.isNullOrEmpty() -> song.artists
                    !song.artist.isNullOrBlank() -> listOf(song.artist)
                    else -> emptyList()
                }

                val payload = mapOf(
                    FIELD_VIDEO_ID to song.videoId,
                    FIELD_TITLE to song.title,
                    FIELD_ALBUM to (song.album ?: ""),
                    FIELD_ARTISTS to artists,
                    FIELD_THUMBNAIL to (song.thumbnail ?: ""),
                    FIELD_ADDED_AT to FieldValue.serverTimestamp()
                )

                transaction.set(songRef, payload, SetOptions.merge())
                transaction.update(playlistRef, FIELD_SONG_COUNT, FieldValue.increment(1))
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "addSongToPlaylist() failed", e)
            Result.failure(e)
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: String, videoId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val playlistRef = firestore.collection(COLLECTION_USERS)
                .document(userId)
                .collection(SUBCOLLECTION_PLAYLISTS)
                .document(playlistId)

            val songRef = playlistRef.collection(SUBCOLLECTION_SONGS)
                .document(videoId)

            firestore.runTransaction { transaction ->
                val songSnapshot = transaction.get(songRef)
                if (!songSnapshot.exists()) return@runTransaction

                val playlistSnapshot = transaction.get(playlistRef)
                val currentCount = playlistSnapshot.getLong(FIELD_SONG_COUNT) ?: 0L
                val newCount = (currentCount - 1).coerceAtLeast(0L)

                transaction.delete(songRef)
                transaction.update(playlistRef, FIELD_SONG_COUNT, newCount)
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "removeSongFromPlaylist() failed", e)
            Result.failure(e)
        }
    }

    private fun requireUserId(): String {
        val userId = auth.currentUser?.uid
        require(!userId.isNullOrBlank()) { "User not authenticated" }
        return userId
    }

    private suspend fun deleteSubcollectionInBatches(query: Query) {
        val documents = query.get().await().documents
        if (documents.isEmpty()) return

        var batch = firestore.batch()
        var count = 0

        documents.forEach { doc ->
            batch.delete(doc.reference)
            count += 1
            if (count == 450) {
                batch.commit().await()
                batch = firestore.batch()
                count = 0
            }
        }

        if (count > 0) {
            batch.commit().await()
        }
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toUserPlaylist(): UserPlaylist? {
    val name = getString("name") ?: return null
    val createdAt = getTimestamp("createdAt")?.toDate()?.time
    val songCount = getLong("songCount")?.toInt() ?: 0

    return UserPlaylist(
        id = id,
        name = name,
        songCount = songCount,
        createdAt = createdAt
    )
}

private fun com.google.firebase.firestore.DocumentSnapshot.toPlaylistSong(): PlaylistSong? {
    val title = getString("title") ?: return null
    val videoId = getString("videoId") ?: id
    val album = getString("album")
    val thumbnail = getString("thumbnail")
    val artists = (get("artists") as? List<*>)
        ?.mapNotNull { it as? String }
        ?: emptyList()
    val addedAt = getTimestamp("addedAt")?.toDate()?.time

    return PlaylistSong(
        videoId = videoId,
        title = title,
        album = album,
        artists = artists,
        thumbnail = thumbnail,
        addedAt = addedAt
    )
}
