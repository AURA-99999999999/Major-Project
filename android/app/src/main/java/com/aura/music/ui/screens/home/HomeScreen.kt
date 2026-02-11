package com.aura.music.ui.screens.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.aura.music.player.MusicService
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.Song
import com.aura.music.auth.state.AuthState
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.HomeUiState
import com.aura.music.ui.viewmodel.HomeViewModel
import com.aura.music.ui.viewmodel.PlaylistEvent
import com.aura.music.ui.viewmodel.PlaylistViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import com.aura.music.ui.screens.playlist.PlaylistPickerBottomSheet

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeScreen(
    musicService: MusicService?,
    authState: AuthState,
    onNavigateToSearch: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(musicService) {
        viewModel.attachMusicService(musicService)
    }
    
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            viewModel.loadHomeData()
        }
    }

    LaunchedEffect(Unit) {
        playlistViewModel.observePlaylists()
    }

    LaunchedEffect(playlistViewModel) {
        playlistViewModel.events.collectLatest { event ->
            when (event) {
                is PlaylistEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
                is PlaylistEvent.PlaySong -> Unit
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = Primary
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Home else Icons.Outlined.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Search", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onNavigateToSearch()
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                            contentDescription = "Library",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Library", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 2,
                    onClick = {
                        selectedTab = 2
                        onNavigateToPlaylists()
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Person else Icons.Outlined.Person,
                            contentDescription = "Profile",
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
                    selected = selectedTab == 3,
                    onClick = {
                        selectedTab = 3
                        onNavigateToProfile()
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Primary,
                        selectedTextColor = Primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(DarkBackground, DarkSurface)
                    )
                )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top navigation with user info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Listen The",
                            fontSize = 16.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = "Latest Musics",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Row {
                        IconButton(onClick = onNavigateToSearch) {
                            Text("🔍", fontSize = 24.sp)
                        }
                        IconButton(onClick = onNavigateToProfile) {
                            Text("👤", fontSize = 24.sp)
                        }
                    }
                }

                when (val state = uiState) {
                    is HomeUiState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    is HomeUiState.Success -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            item { SectionHeader(title = "Trending Now") }

                            item {
                                TrendingRow(
                                    songs = state.trending,
                                    onSongClick = { song ->
                                        viewModel.playSongByVideoId(song.videoId)
                                        onNavigateToPlayer()
                                    },
                                    onAddToPlaylist = { song ->
                                        pendingSongForPlaylist = song
                                    }
                                )
                            }
                        }
                    }
                    is HomeUiState.Error -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            ErrorStateCard(message = state.message)
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

@Composable
private fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onActionClick) {
                Text(text = actionText, color = Primary)
            }
        }
    }
}

@Composable
private fun TrendingRow(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyStateCard(message = "No trending songs right now. Pull to refresh or try again later.")
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(songs) { song ->
            TrendingSongCard(
                song = song,
                onSongClick = { onSongClick(song) },
                onOverflowClick = { onAddToPlaylist(song) }
            )
        }
    }
}

@Composable
private fun TrendingSongCard(
    song: Song,
    onSongClick: () -> Unit,
    onOverflowClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(160.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .height(160.dp)
                .fillMaxWidth()
                .clickable(onClick = onSongClick),
            shape = RoundedCornerShape(16.dp),
            color = DarkSurfaceVariant
        ) {
            AsyncImage(
                model = song.thumbnail,
                contentDescription = song.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSongClick)
            )
            IconButton(onClick = onOverflowClick) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = TextSecondary
                )
            }
        }
        Text(
            text = song.getArtistString(),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = playlist.coverImage ?: playlist.songs.firstOrNull()?.thumbnail,
                contentDescription = playlist.name,
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.description ?: "${playlist.songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "▶",
            color = Primary,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceVariant
    ) {
        Text(
            text = message,
            color = TextSecondary,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun ErrorStateCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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

