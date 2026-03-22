package com.aura.music.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch


data class DailyMixUiState(
    val isLoading: Boolean = false,
    val mixName: String = "Daily Mix",
    val mixDescription: String = "",
    val mixColor: Color = Color(0xFF9B87F5),
    val mixIcon: String = "\uD83C\uDFB5",
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

class DailyMixViewModel(
    private val repository: MusicRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DailyMixUiState())
    val uiState: StateFlow<DailyMixUiState> = _uiState.asStateFlow()

    fun loadMix(type: String, displayNameFallback: String = "Daily Mix", refresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = repository.getDailyMixSongs(type = type, refresh = refresh)
            result
                .onSuccess { mix ->
                    _uiState.value = DailyMixUiState(
                        isLoading = false,
                        mixName = if (mix.name.isNotBlank()) mix.name else displayNameFallback,
                        mixDescription = mix.description,
                        mixColor = mix.color,
                        mixIcon = mix.icon,
                        songs = mix.songs,
                        error = null,
                    )
                }
                .onFailure { error ->
                    _uiState.value = DailyMixUiState(
                        isLoading = false,
                        mixName = displayNameFallback,
                        error = "Failed to load mix: ${error.message}",
                    )
                }
        }
    }
}
