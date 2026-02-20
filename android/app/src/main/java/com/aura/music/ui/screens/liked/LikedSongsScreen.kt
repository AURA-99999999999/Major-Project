package com.aura.music.ui.screens.liked

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aura.music.data.model.Song
import com.aura.music.player.MusicService
import com.aura.music.ui.components.SongItem
import com.aura.music.ui.screens.playlist.PlaylistPickerBottomSheet
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.LikedSongsEvent
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.PlaylistEvent
import com.aura.music.ui.viewmodel.PlaylistViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    viewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(Unit) {
        viewModel.observeLikedSongs()
    }

    LaunchedEffect(Unit) {
        playlistViewModel.observePlaylists()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is LikedSongsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is LikedSongsEvent.PlayQueue -> {
                    musicService?.setQueueAndPlay(event.songs, event.startIndex, "liked_songs")
                    onNavigateToPlayer()
                }
            }
        }
    }

    LaunchedEffect(playlistViewModel) {
        playlistViewModel.events.collectLatest { event ->
            when (event) {
                is PlaylistEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is PlaylistEvent.PlayQueue -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Liked Songs", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)))
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isPlaybackPreparing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                when {
                    uiState.isLoading && uiState.songs.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.songs.isEmpty() -> {
                        Text(
                            text = "No liked songs yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp)
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(uiState.songs) { _, song ->
                                SongItem(
                                    song = song,
                                    isPlaying = musicService?.playerState?.value?.currentSong?.videoId == song.videoId &&
                                            musicService?.playerState?.value?.isPlaying == true,
                                    isLiked = true,
                                    onClick = { viewModel.prepareSongForPlayback(song) },
                                    onToggleLike = { viewModel.removeFromLikedSongs(song.videoId) },
                                    onAddToPlaylist = { pendingSongForPlaylist = song },
                                    onPlayNext = { musicService?.insertNext(song) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingSongForPlaylist?.let { song ->
        PlaylistPickerBottomSheet(
            playlists = playlistState.playlists,
            onDismiss = { pendingSongForPlaylist = null },
            onPlaylistSelected = { playlist ->
                playlistViewModel.addSongToPlaylist(playlist.id, song)
                pendingSongForPlaylist = null
            }
        )
    }
}
