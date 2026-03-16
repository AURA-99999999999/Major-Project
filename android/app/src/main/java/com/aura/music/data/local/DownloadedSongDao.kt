package com.aura.music.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedSongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: DownloadedSong)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: DownloadedSong)

    @Query("DELETE FROM downloaded_songs WHERE videoId = :videoId")
    suspend fun deleteByVideoId(videoId: String)

    @Query("UPDATE downloaded_songs SET fileSize = :fileSize WHERE videoId = :videoId")
    suspend fun updateFileSize(videoId: String, fileSize: Long)

    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedSong>>

    @Query("SELECT * FROM downloaded_songs WHERE videoId = :videoId LIMIT 1")
    suspend fun findByVideoId(videoId: String): DownloadedSong?
}
