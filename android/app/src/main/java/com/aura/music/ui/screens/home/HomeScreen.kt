package com.aura.music.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.components.SongItem
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.viewmodel.HomeViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory

@Composable
fun HomeScreen(
    musicService: MusicService?,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToPlaylistDetail: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactory.create())
) {
    val uiState = viewModel.uiState.collectAsState().value

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aura",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row {
                    IconButton(onClick = onNavigateToSearch) {
                        Text("🔍", fontSize = 24.sp)
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Text("👤", fontSize = 24.sp)
                    }
                }
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "Trending Now",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    items(uiState.trendingSongs) { song ->
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

