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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.aura.music.ui.theme.GradientBackground
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.viewmodel.HomeUiState
import com.aura.music.ui.viewmodel.HomeViewModel
import com.aura.music.ui.viewmodel.LikedSongsEvent
import com.aura.music.ui.viewmodel.LikedSongsViewModel
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
    onNavigateToPlaylistPreview: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application))
) {
    val context = LocalContext.current
    val themeManager: ThemeManager = viewModel(factory = ViewModelFactory.create(context.applicationContext as android.app.Application))
    val themeState by themeManager.themeState.collectAsState()
    
    val uiState by viewModel.uiState.collectAsState()
    val recommendedSongs by viewModel.recommendedSongs.collectAsState()
    val topArtists by viewModel.topArtists.collectAsState()
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()
    val likedSongsViewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val likedSongsState by likedSongsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(musicService) {
        viewModel.attachMusicService(musicService)
    }
    
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            viewModel.loadHomeData()
            viewModel.loadRecommendationsIfNeeded()
        } else {
            viewModel.clearRecommendations()
        }
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
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = MaterialTheme.colorScheme.primary
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
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
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    ) { paddingValues ->
        GradientBackground(
            gradientTheme = themeState.gradientTheme,
            isDark = themeState.themeMode != com.aura.music.ui.theme.ThemeMode.LIGHT,
            hasDynamicColors = themeState.currentDynamicColors.dominant != null,
            dynamicColorsEnabled = themeState.dynamicAlbumColors,
            modifier = Modifier.padding(paddingValues)
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
                            text = "Your Music",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Your AURA",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = 120.dp  // Extra space for mini player
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Trending Songs Section
                            item { SectionHeader(title = "Trending Now") }

                            item {
                                TrendingRow(
                                    songs = state.trending,
                                    likedSongIds = likedSongsState.likedSongIds,
                                    onSongClick = { song, index ->
                                        viewModel.playSongFromList(state.trending, index, "trending")
                                        onNavigateToPlayer()
                                    },
                                    onToggleLike = { song ->
                                        likedSongsViewModel.toggleLike(song)
                                    },
                                    onAddToPlaylist = { song ->
                                        pendingSongForPlaylist = song
                                    },
                                    onPlayNext = { song ->
                                        musicService?.insertNext(song)
                                    }
                                )
                            }

                            // Trending Playlists Section
                            if (state.trendingPlaylists.isNotEmpty()) {
                                item { 
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionHeader(title = "Trending Playlists") 
                                }

                                item {
                                    TrendingPlaylistsRow(
                                        playlists = state.trendingPlaylists,
                                        onPlaylistClick = { playlist ->
                                            onNavigateToPlaylistPreview(playlist.playlistId)
                                        }
                                    )
                                }
                            }

                            if (recommendedSongs.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionHeader(title = "Recommended For You")
                                }

                                item {
                                    TrendingRow(
                                        songs = recommendedSongs,
                                        likedSongIds = likedSongsState.likedSongIds,
                                        onSongClick = { song, index ->
                                            viewModel.playSongFromList(recommendedSongs, index, "recommendations")
                                            onNavigateToPlayer()
                                        },
                                        onToggleLike = { song ->
                                            likedSongsViewModel.toggleLike(song)
                                        },
                                        onAddToPlaylist = { song ->
                                            pendingSongForPlaylist = song
                                        },
                                        onPlayNext = { song ->
                                            musicService?.insertNext(song)
                                        }
                                    )
                                }
                            }

                            // Top Artists Section (after Recommended For You)
                            if (topArtists.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionHeader(title = "Your Top Artists")
                                }

                                item {
                                    TopArtistsRow(
                                        artists = topArtists,
                                        onArtistClick = { artist ->
                                            onNavigateToArtist(artist.browseId)
                                        }
                                    )
                                }
                            }

                            // Mood Categories Section
                            if (state.moodCategories.isNotEmpty()) {
                                item { 
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionHeader(title = "Explore by Mood") 
                                }

                                item {
                                    MoodCategoriesRow(
                                        categories = state.moodCategories,
                                        selectedMoodTitle = state.selectedMoodTitle,
                                        onCategoryClick = { category ->
                                            if (state.selectedMoodTitle == category.title) {
                                                viewModel.clearMoodSelection()
                                            } else {
                                                viewModel.selectMood(category)
                                            }
                                        }
                                    )
                                }
                            }

                            // Mood Playlists Section (shown when mood is selected)
                            if (state.moodPlaylists.isNotEmpty() && state.selectedMoodTitle.isNotEmpty()) {
                                item { 
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SectionHeader(
                                        title = state.selectedMoodTitle,
                                        actionText = "Clear",
                                        onActionClick = { viewModel.clearMoodSelection() }
                                    )
                                }

                                item {
                                    MoodPlaylistsRow(
                                        playlists = state.moodPlaylists,
                                        onPlaylistClick = { playlist ->
                                            onNavigateToPlaylistPreview(playlist.playlistId)
                                        }
                                    )
                                }
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
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onActionClick) {
                Text(text = actionText, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun TrendingRow(
    songs: List<Song>,
    likedSongIds: Set<String>,
    onSongClick: (Song, Int) -> Unit,
    onToggleLike: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onPlayNext: (Song) -> Unit
) {
    if (songs.isEmpty()) {
        EmptyStateCard(message = "No trending songs right now. Pull to refresh or try again later.")
        return
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(songs) { index, song ->
            TrendingSongCard(
                song = song,
                isLiked = likedSongIds.contains(song.videoId),
                onSongClick = { onSongClick(song, index) },
                onToggleLike = { onToggleLike(song) },
                onAddToPlaylist = { onAddToPlaylist(song) },
                onPlayNext = { onPlayNext(song) }
            )
        }
    }
}

@Composable
private fun TrendingSongCard(
    song: Song,
    isLiked: Boolean,
    onSongClick: () -> Unit,
    onToggleLike: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

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
            color = MaterialTheme.colorScheme.surfaceVariant
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
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSongClick)
            )
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs"
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleLike()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to playlist") },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
            }
        }
        Text(
            text = song.getArtistString(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = playlist.description ?: "${playlist.songs.size} songs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = "▶",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun EmptyStateCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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

@Composable
private fun TrendingPlaylistsRow(
    playlists: List<com.aura.music.data.model.YTMusicPlaylist>,
    onPlaylistClick: (com.aura.music.data.model.YTMusicPlaylist) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            YTMusicPlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
private fun YTMusicPlaylistCard(
    playlist: com.aura.music.data.model.YTMusicPlaylist,
    onClick: () -> Unit
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
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            AsyncImage(
                model = playlist.thumbnail,
                contentDescription = playlist.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable(onClick = onClick)
        )
        
        Text(
            text = playlist.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MoodCategoriesRow(
    categories: List<com.aura.music.data.model.MoodCategory>,
    selectedMoodTitle: String,
    onCategoryClick: (com.aura.music.data.model.MoodCategory) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            MoodChip(
                category = category,
                isSelected = selectedMoodTitle == category.title,
                onClick = { onCategoryClick(category) }
            )
        }
    }
}

@Composable
private fun MoodChip(
    category: com.aura.music.data.model.MoodCategory,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MoodPlaylistsRow(
    playlists: List<com.aura.music.data.model.YTMusicPlaylist>,
    onPlaylistClick: (com.aura.music.data.model.YTMusicPlaylist) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(playlists) { playlist ->
            YTMusicPlaylistCard(
                playlist = playlist,
                onClick = { onPlaylistClick(playlist) }
            )
        }
    }
}

@Composable
private fun TopArtistsRow(
    artists: List<com.aura.music.data.remote.dto.TopArtistDto>,
    onArtistClick: (com.aura.music.data.remote.dto.TopArtistDto) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(artists) { artist ->
            TopArtistCard(
                artist = artist,
                onClick = { onArtistClick(artist) }
            )
        }
    }
}

@Composable
private fun TopArtistCard(
    artist: com.aura.music.data.remote.dto.TopArtistDto,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Circular artist image
        AsyncImage(
            model = artist.thumbnail,
            contentDescription = artist.name,
            modifier = Modifier
                .size(100.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )

        // Artist name
        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Subscribers (optional)
        if (!artist.subscribers.isNullOrEmpty()) {
            Text(
                text = artist.subscribers,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
