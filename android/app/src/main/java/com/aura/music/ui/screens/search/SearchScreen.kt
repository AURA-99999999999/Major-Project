package com.aura.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.components.SongItem
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.SearchViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory

@Composable
fun SearchScreen(
    musicService: MusicService?,
    onNavigateToPlayer: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: SearchViewModel = viewModel(factory = ViewModelFactory.create())
) {
    val uiState = viewModel.uiState.collectAsState().value

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = { viewModel.search(it) },
                placeholder = { Text("Search for songs...", color = TextSecondary) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = TextSecondary
                )
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.results.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
                ) {
                    items(uiState.results) { song ->
                        SongItem(
                            song = song,
                            isPlaying = musicService?.playerState?.value?.currentSong?.videoId == song.videoId &&
                                    musicService?.playerState?.value?.isPlaying == true,
                            onClick = {
                                musicService?.playSong(song, false)
                                onNavigateToPlayer()
                            }
                        )
                    }
                }
            }
        }
    }
}

