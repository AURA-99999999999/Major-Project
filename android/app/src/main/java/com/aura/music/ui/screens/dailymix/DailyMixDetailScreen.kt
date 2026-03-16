package com.aura.music.ui.screens.dailymix

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.aura.music.data.mapper.toSongs
import com.aura.music.data.model.Song
import com.aura.music.di.ServiceLocator
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.playlist.PlaylistPickerBottomSheet
import com.aura.music.ui.theme.ColorBlendingUtils
import com.aura.music.ui.components.SongOptionsMenuButton
import com.aura.music.ui.viewmodel.DownloadsViewModel
import com.aura.music.ui.viewmodel.HomeViewModel
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.PlaylistViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun normalizeMixName(mixKey: String, rawName: String?): String {
    val trimmed = rawName?.trim().orEmpty()
    return when (mixKey) {
        "dailyMix1" -> if (trimmed.equals("Daily Mix 1", ignoreCase = true) || trimmed.isBlank()) "Favorites Mix" else trimmed
        "dailyMix2" -> if (trimmed.equals("Daily Mix 2", ignoreCase = true) || trimmed.isBlank()) "Similar Artists Mix" else trimmed
        "discoverMix" -> if (trimmed.equals("Daily Mix 3", ignoreCase = true) || trimmed.isBlank()) "Discover Mix" else trimmed
        "moodMix" -> if (trimmed.isBlank()) "Mood Mix" else trimmed
        else -> if (trimmed.isBlank()) "Daily Mix" else trimmed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMixDetailScreen(
    mixKey: String,
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onShufflePlayMix: (List<Song>) -> Unit = {},
    onSaveMix: (String, List<Song>) -> Unit = { _, _ -> },
    likedSongsViewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    ),
    homeViewModel: HomeViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val likedSongsState by likedSongsViewModel.uiState.collectAsState()
    val downloadsViewModel: DownloadsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    
    var isLoading by remember { mutableStateOf(true) }
    var mixName by remember { mutableStateOf("Daily Mix") }
    var mixDescription by remember { mutableStateOf("") }
    var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var mixColor by remember { mutableStateOf(Color(0xFF9B87F5)) }
    var mixIcon by remember { mutableStateOf("🎧") }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        likedSongsViewModel.observeLikedSongs()
        playlistViewModel.observePlaylists()
    }

    LaunchedEffect(mixKey) {
        isLoading = true
        try {
            val repository = ServiceLocator.getMusicRepository()
            val response = withContext(Dispatchers.IO) {
                repository.getDailyMixes(refresh = false)
            }
            
            response.onSuccess { dailyMixResponse ->
                val mixData = when (mixKey) {
                    "dailyMix1" -> {
                        mixName = normalizeMixName("dailyMix1", dailyMixResponse.mixes?.dailyMix1?.name)
                        mixDescription = dailyMixResponse.mixes?.dailyMix1?.description ?: ""
                        mixColor = Color(0xFF9B87F5)
                        mixIcon = "🎧"
                        dailyMixResponse.mixes?.dailyMix1?.songs?.toSongs()
                    }
                    "dailyMix2" -> {
                        mixName = normalizeMixName("dailyMix2", dailyMixResponse.mixes?.dailyMix2?.name)
                        mixDescription = dailyMixResponse.mixes?.dailyMix2?.description ?: ""
                        mixColor = Color(0xFF87F5E0)
                        mixIcon = "🎶"
                        dailyMixResponse.mixes?.dailyMix2?.songs?.toSongs()
                    }
                    "discoverMix" -> {
                        mixName = normalizeMixName("discoverMix", dailyMixResponse.mixes?.discoverMix?.name)
                        mixDescription = dailyMixResponse.mixes?.discoverMix?.description ?: ""
                        mixColor = Color(0xFFF5B787)
                        mixIcon = "✨"
                        dailyMixResponse.mixes?.discoverMix?.songs?.toSongs()
                    }
                    "moodMix" -> {
                        mixName = normalizeMixName("moodMix", dailyMixResponse.mixes?.moodMix?.name)
                        mixDescription = dailyMixResponse.mixes?.moodMix?.description ?: ""
                        mixColor = Color(0xFFF587B2)
                        mixIcon = "🌙"
                        dailyMixResponse.mixes?.moodMix?.songs?.toSongs()
                    }
                    else -> emptyList()
                }
                
                songs = mixData ?: emptyList()
                isLoading = false
                Log.i("DailyMixDetail", "Loaded mix $mixKey with ${songs.size} songs")
            }.onFailure { exception ->
                error = "Failed to load mix: ${exception.message}"
                isLoading = false
                Log.e("DailyMixDetail", "Failed to load mix $mixKey", exception)
            }
        } catch (e: Exception) {
            error = "Error: ${e.message}"
            isLoading = false
            Log.e("DailyMixDetail", "Exception loading mix $mixKey", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = mixName, color = MaterialTheme.colorScheme.onBackground) },
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
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header with mix info
                        item {
                            MixHeader(
                                mixIcon = mixIcon,
                                mixName = mixName,
                                mixDescription = mixDescription,
                                songCount = songs.size,
                                mixColor = mixColor,
                                onPlayAll = {
                                    if (songs.isNotEmpty()) {
                                        musicService?.setQueueAndPlay(songs, 0, "daily_mix_$mixKey")
                                        onNavigateToPlayer()
                                    }
                                },
                                onShufflePlay = {
                                    homeViewModel.shufflePlayMix(mixKey, songs)
                                    onNavigateToPlayer()
                                },
                                onSaveMix = {
                                    homeViewModel.saveMixToLibrary(mixKey, mixName, songs)
                                }
                            )
                        }

                        // Songs list
                        itemsIndexed(songs) { index, song ->
                            DailyMixSongItem(
                                song = song,
                                isLiked = likedSongsState.likedSongIds.contains(song.videoId),
                                onSongClick = {
                                    musicService?.setQueueAndPlay(songs, index, "daily_mix_$mixKey")
                                    onNavigateToPlayer()
                                },
                                onToggleLike = {
                                    if (likedSongsState.likedSongIds.contains(song.videoId)) {
                                        likedSongsViewModel.removeFromLikedSongs(song.videoId)
                                    } else {
                                        likedSongsViewModel.addToLikedSongs(song)
                                    }
                                },
                                onPlayNext = {
                                    musicService?.insertNext(song)
                                },
                                onAddToPlaylist = {
                                    pendingSongForPlaylist = song
                                },
                                onDownload = {
                                    downloadsViewModel.downloadSong(song)
                                }
                            )
                        }
                        
                        item {
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                }
            }
        }
    }

    pendingSongForPlaylist?.let { selectedSong ->
        PlaylistPickerBottomSheet(
            playlists = playlistState.playlists,
            onDismiss = { pendingSongForPlaylist = null },
            onPlaylistSelected = { playlist ->
                playlistViewModel.addSongToPlaylist(playlist.id, selectedSong)
                pendingSongForPlaylist = null
            }
        )
    }
}

@Composable
fun MixHeader(
    mixIcon: String,
    mixName: String,
    mixDescription: String,
    songCount: Int,
    mixColor: Color,
    onPlayAll: () -> Unit,
    onShufflePlay: () -> Unit = {},
    onSaveMix: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        // Background gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            mixColor,
                            ColorBlendingUtils.darken(mixColor, 0.4f)
                        )
                    )
                )
        ) {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon - Reduced size for better spacing
                Text(
                    text = mixIcon,
                    fontSize = 48.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                // Mix name
                Text(
                    text = mixName,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                
                // Description
                if (mixDescription.isNotBlank()) {
                    Text(
                        text = mixDescription,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                
                // Song count
                Text(
                    text = "$songCount songs",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                
                // Action buttons row - Similar to home screen cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play all button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onPlayAll),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play all",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Play",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Shuffle button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onShufflePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shuffle,
                                contentDescription = "Shuffle play",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Shuffle",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }

                    // Save button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(onClick = onSaveMix),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BookmarkAdd,
                                contentDescription = "Save mix",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(
                                text = "Save",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyMixSongItem(
    song: Song,
    isLiked: Boolean,
    onSongClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = song.thumbnail,
            contentDescription = song.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        
        // Song info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.getArtistString(),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Like button
        IconButton(onClick = onToggleLike) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = if (isLiked) "Unlike" else "Like",
                tint = if (isLiked) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        
        SongOptionsMenuButton(
            isLiked = isLiked,
            onPlayNext = onPlayNext,
            onToggleLike = onToggleLike,
            onAddToPlaylist = onAddToPlaylist,
            onDownload = onDownload,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
