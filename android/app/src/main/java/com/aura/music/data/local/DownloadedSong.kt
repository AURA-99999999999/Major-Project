package com.aura.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_songs")
data class DownloadedSong(
    @PrimaryKey val videoId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val thumbnail: String?,
    val duration: String?,
    val filePath: String,
    val fileSize: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
)

typealias DownloadedSongEntity = DownloadedSong
