package com.aura.music.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.BuildConfig
import com.aura.music.data.model.SearchResults
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Sealed class representing search states
 * Only shows errors on final requests, not during typing
 */
sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(val results: SearchResults) : SearchState()
    object Empty : SearchState()
    data class Error(val message: String) : SearchState()
}

data class SearchUiState(
    val query: String = "",
    val searchState: SearchState = SearchState.Idle,
    val suggestions: List<String> = emptyList(),
    val isPlaybackPreparing: Boolean = false,
    val lastPlayedSongId: String? = null
)

sealed interface SearchEvent {
    data class PlayQueue(val songs: List<Song>, val startIndex: Int) : SearchEvent
    data class ShowMessage(val message: String) : SearchEvent
}

class SearchViewModel(
    private val repository: MusicRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SearchEvent> = _events

    // Query flow for debouncing and distinct filtering
    private val _queryFlow = MutableStateFlow("")
    
    // Suggestions flow with faster debounce (300ms)
    private val _suggestionsFlow = MutableStateFlow("")

    init {
        // Production-grade search pipeline with:
        // - 400ms debounce (prevents API spam)
        // - Min length filter (>=2 chars)
        // - Distinct filtering (prevents duplicate queries)
        // - flatMapLatest (auto-cancels old requests)
        viewModelScope.launch {
            _queryFlow
                .debounce(400) // Wait 400ms after user stops typing
                .filter { it.trim().length >= 2 } // Minimum 2 characters
                .distinctUntilChanged() // Only trigger if query actually changed
                .flatMapLatest { query -> // Auto-cancel previous search
                    flow {
                        logDebug("Searching for: '$query'")
                        emit(SearchState.Loading)
                        
                        try {
                            val result = repository.searchAllCategories(query.trim())
                            result.fold(
                                onSuccess = { searchResults ->
                                    logDebug("Search success: ${searchResults.count} total results for '$query'")
                                    emit(
                                        if (searchResults.isEmpty()) SearchState.Empty
                                        else SearchState.Success(searchResults)
                                    )
                                },
                                onFailure = { e ->
                                    // Silent failure during typing - only log
                                    logError("Search failed for '$query'", e)
                                    emit(SearchState.Error(mapNetworkError(e)))
                                }
                            )
                        } catch (e: Exception) {
                            logError("Search exception for '$query'", e)
                            emit(SearchState.Error("Search failed. Please try again."))
                        }
                    }
                }
                .catch { e ->
                    logError("Flow error", e)
                    emit(SearchState.Error("Unexpected error occurred"))
                }
                .collect { state ->
                    _uiState.update { it.copy(searchState = state) }
                }
        }
        
        // Suggestions pipeline - faster debounce (300ms)
        viewModelScope.launch {
            _suggestionsFlow
                .debounce(300)
                .filter { it.trim().length >= 2 }
                .distinctUntilChanged()
                .flatMapLatest { query ->
                    flow {
                        try {
                            val result = repository.getSearchSuggestions(query.trim())
                            result.fold(
                                onSuccess = { suggestions ->
                                    emit(suggestions)
                                },
                                onFailure = {
                                    emit(emptyList())
                                }
                            )
                        } catch (e: Exception) {
                            emit(emptyList())
                        }
                    }
                }
                .catch {
                    emit(emptyList())
                }
                .collect { suggestions ->
                    _uiState.update { it.copy(suggestions = suggestions) }
                }
        }
    }

    fun search(rawQuery: String) {
        val trimmed = rawQuery.trim()
        _uiState.update { it.copy(query = rawQuery) }
        
        // Clear results immediately if query is too short
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(searchState = SearchState.Idle, suggestions = emptyList()) }
            _queryFlow.value = ""
            _suggestionsFlow.value = ""
        } else if (trimmed.length == 1) {
            // Don't search, but don't clear either - just wait
            _uiState.update { it.copy(searchState = SearchState.Idle, suggestions = emptyList()) }
            _queryFlow.value = trimmed
            _suggestionsFlow.value = ""
        } else {
            // Valid query - let the flows handle it
            _queryFlow.value = trimmed
            _suggestionsFlow.value = trimmed
        }
    }

    fun prepareSongForPlayback(song: Song) {
        viewModelScope.launch {
            logDebug("prepareSongForPlayback() videoId=${song.videoId}")
            _uiState.update { it.copy(isPlaybackPreparing = true) }

            val currentState = _uiState.value
            val searchState = currentState.searchState
            
            if (searchState !is SearchState.Success) {
                _uiState.update { it.copy(isPlaybackPreparing = false) }
                return@launch
            }
            
            val songs = searchState.results.songs
            val index = songs.indexOfFirst { it.videoId == song.videoId }
            
            if (index < 0) {
                _uiState.update { it.copy(isPlaybackPreparing = false) }
                return@launch
            }

            _uiState.update {
                it.copy(
                    isPlaybackPreparing = false,
                    lastPlayedSongId = song.videoId
                )
            }
            _events.emit(SearchEvent.PlayQueue(songs, index))
        }
    }

    fun clearSearch() {
        logDebug("clearSearch() called")
        _queryFlow.value = ""
        _suggestionsFlow.value = ""
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
        val baseUrl = BuildConfig.BASE_URL
        Log.e(TAG, "========================================")
        Log.e(TAG, "NETWORK ERROR DETAILS")
        Log.e(TAG, "Exception type: ${throwable.javaClass.simpleName}")
        Log.e(TAG, "Exception message: ${throwable.message}")
        Log.e(TAG, "Base URL: $baseUrl")
        Log.e(TAG, "Full stack trace:", throwable)
        Log.e(TAG, "========================================")
        return when (throwable) {
            is UnknownHostException -> {
                "Cannot resolve host. Check:\n• Backend server is running and reachable\n• Correct backend URL is set in the app\n• Device has internet access"
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

