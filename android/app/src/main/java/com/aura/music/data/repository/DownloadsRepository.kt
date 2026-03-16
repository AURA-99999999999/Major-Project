package com.aura.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.aura.music.data.local.DownloadedSong
import com.aura.music.data.local.DownloadedSongDao
import com.aura.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class DownloadsRepository(
    private val dao: DownloadedSongDao,
    private val context: Context,
    private val downloadManagerService: DownloadManagerService = DownloadManagerService(context)
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = downloadManagerService.activeDownloads

    fun getAllDownloads(): Flow<List<DownloadedSong>> = dao.getAllDownloads()

    suspend fun isDownloaded(videoId: String): Boolean = dao.findByVideoId(videoId) != null

    suspend fun getLocalFilePath(videoId: String): String? = dao.findByVideoId(videoId)?.filePath

    suspend fun downloadSong(song: Song): Boolean {
        if (isDownloaded(song.videoId)) return false

        val url = song.url ?: return false

        val musicDir = downloadManagerService.resolveDownloadsDirectory()
            ?: context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            ?: return false
        val fileName = "${song.videoId}.mp4"
        val destFile = File(musicDir, fileName)

        val entity = DownloadedSong(
            videoId = song.videoId,
            title = song.title,
            artist = song.artist ?: song.artists?.firstOrNull() ?: "Unknown Artist",
            album = song.album,
            thumbnail = song.thumbnail,
            duration = song.duration,
            filePath = destFile.absolutePath,
            fileSize = 0L
        )

        dao.upsert(entity)

        val downloadable = song.copy(url = url)
        downloadManagerService.startDownload(downloadable, destFile) { result ->
            repositoryScope.launch {
                when (result) {
                    is DownloadResult.Success -> {
                        dao.upsert(
                            entity.copy(
                                filePath = result.filePath,
                                fileSize = result.fileSize,
                                downloadedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    is DownloadResult.Failed -> {
                        // Cleanup stale metadata if the download fails permanently.
                        dao.deleteByVideoId(song.videoId)
                    }
                }
            }
        }

        return true
    }

    suspend fun deleteSong(videoId: String) {
        val record = dao.findByVideoId(videoId) ?: return
        File(record.filePath).takeIf { it.exists() }?.delete()
        dao.deleteByVideoId(videoId)
    }
}
