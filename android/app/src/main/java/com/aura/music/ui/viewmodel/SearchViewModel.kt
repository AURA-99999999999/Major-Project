package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.BuildConfig
import com.aura.music.data.model.Song
import com.aura.music.data.model.withFallbackMetadata
import com.aura.music.data.repository.MusicRepository
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val isPlaybackPreparing: Boolean = false,
    val error: String? = null,
    val lastPlayedSongId: String? = null
)

sealed interface SearchEvent {
    data class PlaySong(val song: Song) : SearchEvent
    data class ShowMessage(val message: String) : SearchEvent
}

class SearchViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SearchEvent> = _events

    private var searchJob: Job? = null

    fun search(rawQuery: String) {
        _uiState.update { it.copy(query = rawQuery) }

        val query = rawQuery.trim()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isBlank()) {
                logDebug("search() - blank query, clearing results")
                _uiState.update { it.copy(results = emptyList(), isLoading = false, error = null) }
                return@launch
            }

            delay(350) // Debounce typing noise
            _uiState.update { it.copy(isLoading = true, error = null) }

            repository.searchSongs(query, 50)
                .onSuccess { songs ->
                    logDebug("search() success for '$query' with ${songs.size} songs")
                    _uiState.update { it.copy(results = songs, isLoading = false) }

                    if (songs.isEmpty()) {
                        _events.emit(SearchEvent.ShowMessage("No results for \"$query\""))
                    }
                }
                .onFailure { e ->
                    val message = mapNetworkError(e)
                    logError("search() failed for '$query': $message", e)
                    _uiState.update { it.copy(isLoading = false, error = message) }
                    _events.emit(SearchEvent.ShowMessage(message))
                }
        }
    }

    fun prepareSongForPlayback(song: Song) {
        viewModelScope.launch {
            logDebug("prepareSongForPlayback() videoId=${song.videoId}")
            _uiState.update { it.copy(isPlaybackPreparing = true, error = null) }

            val resolvedSong = if (song.url.isNullOrBlank()) {
                repository.getSong(song.videoId)
                    .onFailure { throwable ->
                        val message = mapNetworkError(throwable)
                        logError("prepareSongForPlayback() failed", throwable)
                        _events.emit(SearchEvent.ShowMessage(message))
                    }
                    .getOrNull()
                    ?.withFallbackMetadata(song)
            } else {
                song
            }

            if (resolvedSong == null || resolvedSong.url.isNullOrBlank()) {
                _uiState.update { it.copy(isPlaybackPreparing = false) }
                if (resolvedSong == null) return@launch
                _events.emit(SearchEvent.ShowMessage("Stream URL missing for ${song.title}"))
                return@launch
            }

            _uiState.update {
                it.copy(
                    isPlaybackPreparing = false,
                    lastPlayedSongId = resolvedSong.videoId
                )
            }
            _events.emit(SearchEvent.PlaySong(resolvedSong))
        }
    }

    fun clearSearch() {
        logDebug("clearSearch() called")
        searchJob?.cancel()
        _uiState.value = SearchUiState()
    }

    fun verifyBackendConnection() {
        viewModelScope.launch {
            repository.pingBackend()
                .onFailure { error ->
                    _events.emit(SearchEvent.ShowMessage(mapNetworkError(error)))
                }
        }
    }

    private fun mapNetworkError(throwable: Throwable): String {
        val baseUrl = BuildConfig.API_BASE_URL
        val env = BuildConfig.API_ENV
        
        Log.e(TAG, "========================================")
        Log.e(TAG, "NETWORK ERROR DETAILS")
        Log.e(TAG, "Exception type: ${throwable.javaClass.simpleName}")
        Log.e(TAG, "Exception message: ${throwable.message}")
        Log.e(TAG, "Base URL: $baseUrl")
        Log.e(TAG, "Environment: $env")
        Log.e(TAG, "Full stack trace:", throwable)
        Log.e(TAG, "========================================")
        
        return when (throwable) {
            is UnknownHostException -> {
                "Cannot resolve host. Check:\n• Flask server is running\n• Correct IP in local.properties\n• Same Wi-Fi network (device)"
            }
            is ConnectException -> {
                "Cannot connect to server at $baseUrl\n\nPlease verify:\n• Flask server running (python app.py)\n• Server accessible from this device\n• Check Logcat for details"
            }
            is SocketTimeoutException ->
                "Connection timeout. Server took too long to respond."
            is java.net.SocketException -> {
                "Network error. Check:\n• Device/emulator network connection\n• Server is reachable\n• Check Logcat for details"
            }
            is HttpException -> {
                val code = throwable.code()
                val message = throwable.message()
                "HTTP Error $code: $message"
            }
            else -> {
                val errorMsg = throwable.message ?: "Network error occurred"
                Log.e(TAG, "Unexpected error type: ${throwable.javaClass.simpleName}", throwable)
                "Error: $errorMsg\n\nCheck Logcat for full details."
            }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"

        private inline fun safeLog(block: () -> Unit) {
            try {
                block()
            } catch (_: Throwable) {
                // Ignore when android.util.Log is not mocked
            }
        }

        private fun logDebug(message: String) = safeLog { Log.d(TAG, message) }

        private fun logError(message: String, throwable: Throwable? = null) =
            safeLog { Log.e(TAG, message, throwable) }
    }
}

