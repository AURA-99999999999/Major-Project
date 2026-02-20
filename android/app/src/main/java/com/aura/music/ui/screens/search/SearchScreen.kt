package com.aura.music.ui.screens.search

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.aura.music.player.MusicService
import com.aura.music.ui.components.SongItem
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.LikedSongsEvent
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.PlaylistEvent
import com.aura.music.ui.viewmodel.PlaylistViewModel
import com.aura.music.ui.viewmodel.SearchEvent
import com.aura.music.ui.viewmodel.SearchState
import com.aura.music.ui.viewmodel.SearchViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import com.aura.music.data.model.Album
import com.aura.music.data.model.Artist
import com.aura.music.data.model.PlaylistSearchResult
import com.aura.music.data.model.Song
import com.aura.music.ui.screens.playlist.PlaylistPickerBottomSheet
import androidx.compose.runtime.setValue
import coil.compose.AsyncImage

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchScreen(
    musicService: MusicService?,
    onNavigateToPlayer: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application))
) {
    val uiState by viewModel.uiState.collectAsState()
    val playlistViewModel: PlaylistViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val playlistState by playlistViewModel.uiState.collectAsState()
    val likedSongsViewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val likedSongsState by likedSongsViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var pendingSongForPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(viewModel, musicService) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SearchEvent.PlayQueue -> {
                    musicService?.setQueueAndPlay(event.songs, event.startIndex, "search")
                    onNavigateToPlayer()
                }
                is SearchEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.verifyBackendConnection()
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
                title = {
                    Text(
                        text = "Search",
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = { viewModel.search(it) },
                    placeholder = { Text("Search for songs...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    )
                )

                if (uiState.isPlaybackPreparing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // State-based UI rendering (no flickering)
                when (val state = uiState.searchState) {
                    SearchState.Idle -> {
                        // Show search prompt when idle
                        if (uiState.query.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "🔍",
                                        fontSize = 48.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Search for your favorite songs",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                    
                    SearchState.Loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    
                    is SearchState.Success -> {
                        val results = state.results
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search Suggestions
                            if (uiState.suggestions.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = "Suggestions",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        uiState.suggestions.take(5).forEach { suggestion ->
                                            Text(
                                                text = suggestion,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.search(suggestion)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }
                            }
                            
                            // Songs Section
                            if (results.hasAnySongs()) {
                                item {
                                    Text(
                                        text = "Songs",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(
                                    items = results.songs,
                                    key = { it.videoId }
                                ) { song ->
                                    SongItem(
                                        song = song,
                                        isPlaying = musicService?.playerState?.value?.currentSong?.videoId == song.videoId &&
                                                musicService?.playerState?.value?.isPlaying == true,
                                        isLiked = likedSongsState.likedSongIds.contains(song.videoId),
                                        onClick = { viewModel.prepareSongForPlayback(song) },
                                        onToggleLike = { likedSongsViewModel.toggleLike(song) },
                                        onAddToPlaylist = { pendingSongForPlaylist = song },
                                        onPlayNext = { musicService?.insertNext(song) }
                                    )
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            
                            // Albums Section
                            if (results.hasAnyAlbums()) {
                                item {
                                    Text(
                                        text = "Albums",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(
                                    items = results.albums,
                                    key = { it.browseId }
                                ) { album ->
                                    AlbumItem(
                                        album = album,
                                        onClick = { onNavigateToAlbum(album.browseId) }
                                    )
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            
                            // Artists Section
                            if (results.hasAnyArtists()) {
                                item {
                                    Text(
                                        text = "Artists",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(
                                    items = results.artists,
                                    key = { it.browseId }
                                ) { artist ->
                                    ArtistItem(
                                        artist = artist,
                                        onClick = { onNavigateToArtist(artist.browseId) }
                                    )
                                }
                                
                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                            
                            // Playlists Section
                            if (results.hasAnyPlaylists()) {
                                item {
                                    Text(
                                        text = "Playlists",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                
                                items(
                                    items = results.playlists,
                                    key = { it.playlistId }
                                ) { playlist ->
                                    PlaylistSearchItem(
                                        playlist = playlist,
                                        onClick = { onNavigateToPlaylist(playlist.browseId) }
                                    )
                                }
                            }
                        }
                    }
                    
                    SearchState.Empty -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "😕",
                                    fontSize = 48.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "No results for \"${uiState.query}\"",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Try a different search term",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    
                    is SearchState.Error -> {
                        // Only show errors on final request, not during typing
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = "⚠️",
                                    fontSize = 48.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
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

@Composable
fun AlbumItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = album.thumbnail,
                contentDescription = "Album cover",
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = album.artists.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    album.year?.let { year ->
                        Text(
                            text = " • $year",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                album.type?.let { type ->
                    Text(
                        text = type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = artist.thumbnail,
                contentDescription = "Artist image",
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                artist.subscribers?.let { subscribers ->
                    Text(
                        text = subscribers,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistSearchItem(
    playlist: PlaylistSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.thumbnail,
                contentDescription = "Playlist cover",
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Playlist • ${playlist.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                playlist.itemCount?.let { count ->
                    Text(
                        text = count,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

