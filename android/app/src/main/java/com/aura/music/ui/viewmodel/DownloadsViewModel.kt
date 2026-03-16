package com.aura.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.local.DownloadedSong
import com.aura.music.data.model.Song
import com.aura.music.data.repository.DownloadProgress
import com.aura.music.data.repository.DownloadsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class DownloadsEvent {
    data class ShowMessage(val message: String) : DownloadsEvent()
}

class DownloadsViewModel(
    private val repository: DownloadsRepository,
    application: Application
) : AndroidViewModel(application) {

    val downloads: StateFlow<List<DownloadedSong>> = repository.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<Map<String, DownloadProgress>> = repository.activeDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _events = Channel<DownloadsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            if (repository.isDownloaded(song.videoId)) {
                _events.send(DownloadsEvent.ShowMessage("\"${song.title}\" is already downloaded"))
                return@launch
            }
            val success = repository.downloadSong(song)
            if (success) {
                _events.send(DownloadsEvent.ShowMessage("Downloading \"${song.title}\""))
            } else {
                _events.send(DownloadsEvent.ShowMessage("Download failed — no stream URL available"))
            }
        }
    }

    fun deleteSong(videoId: String) {
        viewModelScope.launch {
            repository.deleteSong(videoId)
            _events.send(DownloadsEvent.ShowMessage("Download removed"))
        }
    }
}
