package com.aura.music.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.aura.music.data.model.Song
import com.aura.music.data.model.PlaylistDetail
import com.aura.music.data.repository.MusicRepository
import com.aura.music.ui.components.SongItem
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.LikedSongsEvent
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.PlaylistEvent
import com.aura.music.ui.viewmodel.PlaylistViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

sealed class PlaylistPreviewUiState {
    object Loading : PlaylistPreviewUiState()
    data class Success(val playlist: PlaylistDetail) : PlaylistPreviewUiState()
    data class Error(val message: String) : PlaylistPreviewUiState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPreviewScreen(
    playlistId: String,
    repository: MusicRepository,
    onNavigateBack: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onPlayNext: (Song) -> Unit
) {
    var uiState by remember { mutableStateOf<PlaylistPreviewUiState>(PlaylistPreviewUiState.Loading) }
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()
    val likedSongsViewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val likedSongsState by likedSongsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(playlistId) {
        uiState = PlaylistPreviewUiState.Loading
        val result = withContext(Dispatchers.IO) {
            repository.getYTMusicPlaylistSongs(playlistId)
        }
        uiState = result.fold(
            onSuccess = { PlaylistPreviewUiState.Success(it) },
            onFailure = { PlaylistPreviewUiState.Error(it.message ?: "Failed to load playlist") }
        )
    }

    LaunchedEffect(Unit) {
        playlistViewModel.observePlaylists()
    }

    LaunchedEffect(Unit) {
        likedSongsViewModel.observeLikedSongs()
    }

    LaunchedEffect(playlistViewModel) {
        playlistViewModel.events.collectLatest { event ->
            when (event) {
                is PlaylistEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is PlaylistEvent.PlayQueue -> Unit
            }
        }
    }

    LaunchedEffect(likedSongsViewModel) {
        likedSongsViewModel.events.collectLatest { event ->
            when (event) {
                is LikedSongsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is LikedSongsEvent.PlayQueue -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Playlist", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface
                )
            )
        },
        floatingActionButton = {
            if (uiState is PlaylistPreviewUiState.Success) {
                val songs = (uiState as PlaylistPreviewUiState.Success).playlist.songs
                if (songs.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { onPlayAll(songs) },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play All",
                            tint = Color.Black
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surface)
                    )
                )
        ) {
            when (val state = uiState) {
                is PlaylistPreviewUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is PlaylistPreviewUiState.Success -> {
                    PlaylistContent(
                        playlist = state.playlist,
                        likedSongIds = likedSongsState.likedSongIds,
                        onSongClick = onPlaySong,
                        onToggleLike = { song -> likedSongsViewModel.toggleLike(song) },
                        onSongOverflowClick = { song -> pendingSongForPlaylist = song },
                        onPlayNext = onPlayNext
                    )
                }
                is PlaylistPreviewUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        ErrorCard(message = state.message)
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

@Composable
private fun PlaylistContent(
    playlist: PlaylistDetail,
    likedSongIds: Set<String>,
    onSongClick: (List<Song>, Int) -> Unit,
    onToggleLike: (Song) -> Unit,
    onSongOverflowClick: (Song) -> Unit,
    onPlayNext: (Song) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Playlist Header
        item {
            PlaylistHeader(playlist = playlist)
        }

        // Songs
        itemsIndexed(playlist.songs) { index, song ->
            SongItem(
                song = song,
                isPlaying = false,
                onClick = { onSongClick(playlist.songs, index) },
                isLiked = likedSongIds.contains(song.videoId),
                onToggleLike = { onToggleLike(song) },
                onAddToPlaylist = { onSongOverflowClick(song) },
                onPlayNext = { onPlayNext(song) }
            )
        }
    }
}

@Composable
private fun PlaylistHeader(playlist: PlaylistDetail) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Playlist Thumbnail
        Surface(
            modifier = Modifier
                .size(220.dp),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceVariant
        ) {
            AsyncImage(
                model = playlist.thumbnail,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Playlist Title
        Text(
            text = playlist.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Song count (provider label intentionally hidden)
        Text(
            text = "${playlist.songs.size} songs",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Description
        if (playlist.description.isNotEmpty()) {
            Text(
                text = playlist.description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}
