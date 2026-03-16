package com.aura.music.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import com.aura.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

data class DownloadProgress(
    val videoId: String,
    val downloadId: Long,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val status: Int,
    val progressPercent: Int,
    val retryCount: Int = 0
)

sealed class DownloadResult {
    data class Success(val filePath: String, val fileSize: Long) : DownloadResult()
    data class Failed(val reason: String) : DownloadResult()
}

class DownloadManagerService(
    private val context: Context
) {
    companion object {
        private const val POLL_DELAY_MS = 1000L
        private const val MAX_RETRIES = 2
    }

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = _activeDownloads.asStateFlow()

    fun startDownload(song: Song, destinationFile: File, onComplete: (DownloadResult) -> Unit): Long {
        val initialId = enqueue(song, destinationFile)
        trackDownload(song.videoId, initialId, song, destinationFile, 0, onComplete)
        return initialId
    }

    private fun enqueue(song: Song, destinationFile: File): Long {
        val request = DownloadManager.Request(Uri.parse(song.url!!)).apply {
            setTitle(song.title)
            setDescription("Downloading for offline playback")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("audio/mp4")
        }
        return downloadManager.enqueue(request)
    }

    private fun trackDownload(
        videoId: String,
        downloadId: Long,
        song: Song,
        destinationFile: File,
        retryCount: Int,
        onComplete: (DownloadResult) -> Unit
    ) {
        serviceScope.launch {
            while (true) {
                val snapshot = queryDownload(downloadId)
                if (snapshot == null) {
                    updateProgress(videoId, null)
                    onComplete(DownloadResult.Failed("Download not found"))
                    return@launch
                }

                val progress = DownloadProgress(
                    videoId = videoId,
                    downloadId = downloadId,
                    bytesDownloaded = snapshot.bytesDownloaded,
                    totalBytes = snapshot.totalBytes,
                    status = snapshot.status,
                    progressPercent = progressPercent(snapshot.bytesDownloaded, snapshot.totalBytes),
                    retryCount = retryCount
                )
                updateProgress(videoId, progress)

                when (snapshot.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        updateProgress(videoId, null)
                        val size = destinationFile.length().coerceAtLeast(snapshot.totalBytes)
                        onComplete(DownloadResult.Success(destinationFile.absolutePath, size))
                        return@launch
                    }

                    DownloadManager.STATUS_FAILED -> {
                        if (retryCount < MAX_RETRIES) {
                            val nextId = enqueue(song, destinationFile)
                            trackDownload(videoId, nextId, song, destinationFile, retryCount + 1, onComplete)
                        } else {
                            updateProgress(videoId, null)
                            onComplete(DownloadResult.Failed("Download failed after retries"))
                        }
                        return@launch
                    }
                }

                delay(POLL_DELAY_MS)
            }
        }
    }

    private fun updateProgress(videoId: String, progress: DownloadProgress?) {
        val mutable = _activeDownloads.value.toMutableMap()
        if (progress == null) {
            mutable.remove(videoId)
        } else {
            mutable[videoId] = progress
        }
        _activeDownloads.value = mutable
    }

    private fun progressPercent(downloaded: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
    }

    private fun queryDownload(downloadId: Long): DownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.toSnapshot()
        }
    }

    private fun Cursor.toSnapshot(): DownloadSnapshot {
        val status = getInt(getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val bytesDownloaded = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val totalBytes = getLong(getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        return DownloadSnapshot(status, bytesDownloaded, totalBytes)
    }

    private data class DownloadSnapshot(
        val status: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    )

    fun resolveDownloadsDirectory(): File? {
        val base = context.getExternalFilesDir(null) ?: return null
        val downloadsDir = File(base, "downloads")
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        return downloadsDir.takeIf { it.exists() }
    }
}
