package com.aura.music.ui.screens.mood

import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.data.model.Song
import com.aura.music.data.model.YTMusicPlaylist
import com.aura.music.di.ServiceLocator
import com.aura.music.player.MusicService
import com.aura.music.ui.utils.MoodGradientMapper
import com.aura.music.ui.utils.MoodIconMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Immersive Mood Experience Screen
 * Displays mood-based playlists with premium UX
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(
    moodTitle: String,
    moodParams: String,
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    
    var isLoading by remember { mutableStateOf(true) }
    var playlists by remember { mutableStateOf<List<YTMusicPlaylist>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    // Mood-based colors and descriptions
    val (moodColor, moodDescription) = remember(moodTitle) {
        getMoodTheme(moodTitle)
    }
    
    // Mood icon from centralized mapper
    val moodIcon = remember(moodTitle) {
        MoodIconMapper.getIconForMood(moodTitle)
    }
    val moodIconDescription = remember(moodTitle) {
        MoodIconMapper.getIconContentDescription(moodTitle)
    }

    LaunchedEffect(moodParams) {
        isLoading = true
        try {
            val repository = ServiceLocator.getMusicRepository()
            val response = withContext(Dispatchers.IO) {
                repository.getMoodPlaylists(moodParams, limit = 20)
            }
            
            response.onSuccess { loadedPlaylists ->
                playlists = loadedPlaylists
                isLoading = false
                Log.i("MoodScreen", "Loaded ${playlists.size} playlists for mood: $moodTitle")
            }.onFailure { exception ->
                error = "Failed to load playlists: ${exception.message}"
                isLoading = false
                Log.e("MoodScreen", "Failed to load mood playlists", exception)
            }
        } catch (e: Exception) {
            error = "Error: ${e.message}"
            isLoading = false
            Log.e("MoodScreen", "Exception loading mood playlists", e)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = moodIcon,
                            contentDescription = moodIconDescription,
                            tint = moodColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = moodTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
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
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
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
                            moodColor.copy(alpha = 0.15f),
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 1000f
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
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "⚠️",
                                fontSize = 48.sp
                            )
                            Text(
                                text = error ?: "Unknown error",
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
                playlists.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "🎵",
                                fontSize = 48.sp
                            )
                            Text(
                                text = "No playlists found for this mood",
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Section
                        item {
                            // Get gradient colors for header card
                            val (gradientStart, gradientEnd) = MoodGradientMapper.getGradientForMood(moodTitle)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                gradientStart.copy(alpha = 0.9f),
                                                gradientEnd.copy(alpha = 0.9f)
                                            )
                                        )
                                    )
                                    .padding(vertical = 32.dp, horizontal = 24.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Mood Icon
                                    Icon(
                                        imageVector = moodIcon,
                                        contentDescription = moodIconDescription,
                                        tint = Color.White,
                                        modifier = Modifier.size(80.dp)
                                    )

                                    // Mood Title
                                    Text(
                                        text = moodTitle,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center
                                    )

                                    // Mood Description
                                    Text(
                                        text = moodDescription,
                                        fontSize = 16.sp,
                                        color = Color.White.copy(alpha = 0.9f),
                                        textAlign = TextAlign.Center
                                    )

                                    // Play Mood Mix Button
                                    Button(
                                        onClick = {
                                            // Shuffle and play all tracks from playlists
                                            // This would ideally aggregate tracks from top playlists
                                            // For now, just navigate to first playlist
                                            if (playlists.isNotEmpty()) {
                                                onNavigateToPlaylist(playlists.first().playlistId)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp)
                                            .padding(horizontal = 16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color.White,
                                            contentColor = gradientStart
                                        ),
                                        shape = RoundedCornerShape(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shuffle,
                                            contentDescription = "Shuffle",
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Shuffle & Play",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Playlist Count
                                    Text(
                                        text = "${playlists.size} playlists",
                                        fontSize = 14.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }

                        // Playlists Section Header
                        item {
                            Text(
                                text = "Playlists for ${moodTitle}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        // Playlists Grid
                        items(playlists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onNavigateToPlaylist(playlist.playlistId) }
                            )
                        }

                        // Bottom spacing for mini player
                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: YTMusicPlaylist,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist Artwork
            AsyncImage(
                model = playlist.thumbnail.takeIf { it.isNotEmpty() },
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )

            // Playlist Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = playlist.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (playlist.description.isNotEmpty()) {
                    Text(
                        text = playlist.description,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Play Icon
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * Returns mood theme (color, description) based on mood title.
 * Icons are provided by MoodIconMapper for consistency.
 */
private fun getMoodTheme(moodTitle: String): Pair<Color, String> {
    return when (moodTitle.lowercase()) {
        "happy", "joy", "joyful", "cheerful" -> Pair(
            Color(0xFFFFEB3B),
            "Feel-good vibes and upbeat tunes"
        )
        "energetic", "energy", "pump up" -> Pair(
            Color(0xFFFF5722),
            "High-energy tracks to power up your day"
        )
        "chill", "relax", "relaxed", "calm" -> Pair(
            Color(0xFF4FC3F7),
            "Unwind with mellow and soothing music"
        )
        "sad", "melancholy", "blue" -> Pair(
            Color(0xFF9575CD),
            "Music to match your reflective mood"
        )
        "romantic", "love" -> Pair(
            Color(0xFFE91E63),
            "Love songs and romantic melodies"
        )
        "focus", "concentration", "study" -> Pair(
            Color(0xFF26C6DA),
            "Stay focused with ambient and instrumental"
        )
        "workout", "gym", "fitness" -> Pair(
            Color(0xFFFF6F00),
            "Motivation and power for your workout"
        )
        "party", "celebration" -> Pair(
            Color(0xFFFF4081),
            "Get the party started with these hits"
        )
        "sleep", "sleepy", "bedtime" -> Pair(
            Color(0xFF5C6BC0),
            "Peaceful tunes for restful sleep"
        )
        "peaceful", "peace" -> Pair(
            Color(0xFF81C784),
            "Find your inner peace with calm sounds"
        )
        else -> Pair(
            Color(0xFF9B87F5),
            "Discover playlists perfect for this mood"
        )
    }
}
