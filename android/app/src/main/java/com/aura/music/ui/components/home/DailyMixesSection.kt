package com.aura.music.ui.components.home

import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.data.mapper.toSongs
import com.aura.music.data.model.Song
import com.aura.music.di.ServiceLocator
import com.aura.music.ui.theme.ColorBlendingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.aura.music.data.model.MixCardMeta
import com.aura.music.data.model.MixCardData
import androidx.compose.ui.graphics.Brush

private fun normalizeMixName(mixKey: String, rawName: String?): String {
    val trimmed = rawName?.trim().orEmpty()
    return when (mixKey) {
        "dailyMix1" -> if (trimmed.equals("Daily Mix 1", ignoreCase = true) || trimmed.isBlank()) "Favorites Mix" else trimmed
        "dailyMix2" -> if (trimmed.equals("Daily Mix 2", ignoreCase = true) || trimmed.isBlank()) "Similar Artists Mix" else trimmed
        "discoverMix" -> if (trimmed.equals("Daily Mix 3", ignoreCase = true) || trimmed.isBlank()) "Discover Mix" else trimmed
        "moodMix" -> if (trimmed.isBlank()) "Mood Mix" else trimmed
        else -> trimmed
    }
}

/**
 * Daily Mixes component - Displays personalized playlists for "Made for You" section
 * 
 * Features:
 * - 4 mix types: Favorites, Similar Artists, Discover, Mood
 * - Lazy loading with cache
 * - Integration with player
 * - Smooth animations
 * - Uses MusicRepository to fetch from backend
 */
@Composable
fun DailyMixesSection(
    userId: String?,
    onPlayMix: (String, List<Song>) -> Unit,
    onNavigateToMix: (String, String, List<Song>) -> Unit,
    onShufflePlayMix: (String, List<Song>) -> Unit = { _, _ -> },
    onSaveMix: (String, String, List<Song>) -> Unit = { _, _, _ -> }
) {
    var metaList by remember { mutableStateOf<List<MixCardMeta>?>(null) }
    var metaError by remember { mutableStateOf<String?>(null) }
    var metaLoading by remember { mutableStateOf(false) }

    // Per-mix state: key -> (loading, error, data)
    val mixStates = remember { mutableStateMapOf<String, Triple<Boolean, String?, MixCardData?>>() }

    // Fetch metadata on userId change
    LaunchedEffect(userId) {
        if (userId.isNullOrEmpty()) {
            metaError = "User ID not available"
            Log.w("DailyMixes", "No user ID provided")
            return@LaunchedEffect
        }
        metaLoading = true
        metaError = null
        try {
            val repository = ServiceLocator.getMusicRepository()
            val response = withContext(Dispatchers.IO) {
                repository.getDailyMixesMeta()
            }
            response.onSuccess { list ->
                metaList = list
                // Reset per-mix state
                mixStates.clear()
                list.forEach { meta ->
                    mixStates[meta.key] = Triple(false, null, null)
                }
            }.onFailure { exception ->
                metaError = "Error loading mixes: ${exception.message}"
                metaList = null
            }
        } catch (e: Exception) {
            metaError = "Error loading mixes: ${e.message}"
            metaList = null
        } finally {
            metaLoading = false
        }
    }

    // Helper to trigger per-mix load
    fun loadMixSongs(mixKey: String) {
        if (mixStates[mixKey]?.first == true || mixStates[mixKey]?.third != null) return // already loading or loaded
        mixStates[mixKey] = Triple(true, null, null)
        val repository = ServiceLocator.getMusicRepository()
        // Launch coroutine for loading songs
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.getDailyMixSongs(mixKey)
                }
                result.onSuccess { data ->
                    mixStates[mixKey] = Triple(false, null, data)
                }.onFailure { e ->
                    mixStates[mixKey] = Triple(false, "Failed to load songs: ${e.message}", null)
                }
            } catch (e: Exception) {
                mixStates[mixKey] = Triple(false, "Failed to load songs: ${e.message}", null)
            }
        }
    }
    
    // Always show the section container
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        // Section Header
        Text(
            text = "Daily Mixes",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        when {
            metaLoading -> {
                // Show shimmer loading state
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(4) {
                        com.aura.music.ui.components.ShimmerDailyMixCard()
                    }
                }
            }
            metaError != null -> {
                // Show error message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = metaError ?: "Failed to load mixes",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }
            metaList != null -> {
                // Show mixes carousel (metadata only, lazy load songs)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(metaList!!) { meta ->
                        val mixState = mixStates[meta.key] ?: Triple(false, null, null)
                        val (loading, error, data) = mixState
                        DailyMixCard(
                            mixKey = meta.key,
                            mixData = data ?: MixCardData(
                                key = meta.key,
                                name = meta.name,
                                description = meta.description,
                                icon = meta.icon,
                                color = meta.color,
                                songs = emptyList()
                            ),
                            onPlayMix = {
                                if (data == null && !loading) loadMixSongs(meta.key)
                                data?.let { onPlayMix(meta.key, it.songs) }
                            },
                            onNavigateToMix = {
                                if (data == null && !loading) loadMixSongs(meta.key)
                                data?.let { onNavigateToMix(meta.key, it.name, it.songs) }
                            },
                            onShufflePlayMix = {
                                if (data == null && !loading) loadMixSongs(meta.key)
                                data?.let { onShufflePlayMix(meta.key, it.songs) }
                            },
                            onSaveMix = {
                                if (data == null && !loading) loadMixSongs(meta.key)
                                data?.let { onSaveMix(meta.key, it.name, it.songs) }
                            }
                        )
                        if (loading) {
                            // Overlay loading indicator
                            Box(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 200.dp)
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.material3.CircularProgressIndicator(color = Color.White)
                            }
                        } else if (error != null) {
                            // Overlay error message
                            Box(
                                modifier = Modifier
                                    .size(width = 160.dp, height = 200.dp)
                                    .background(Color.Black.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = error,
                                    color = Color.White,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual mix card component
 * - 160x200dp card with gradient background
 * - Shows mix name, description, song count
 * - Play button with semi-transparent overlay
 * - Shuffle play and save mix options
 */
@Composable
fun DailyMixCard(
    mixKey: String,
    mixData: MixCardData,
    onPlayMix: () -> Unit,
    onNavigateToMix: () -> Unit,
    onShufflePlayMix: () -> Unit = {},
    onSaveMix: () -> Unit = {}
) {
    var showOptions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        mixData.color,
                        ColorBlendingUtils.darken(mixData.color, 0.3f)
                    )
                )
            )
            .clickable(onClick = onNavigateToMix)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: Icon, name, song count
            Column {
                // Icon
                Text(
                    text = mixData.icon,
                    fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Mix name
                Text(
                    text = mixData.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Song count
                Text(
                    text = "${mixData.songs.size} songs",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Play button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onPlayMix,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play ${mixData.name}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Shuffle button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            onShufflePlayMix()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shuffle,
                            contentDescription = "Shuffle play ${mixData.name}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Save button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .size(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            onSaveMix()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BookmarkAdd,
                            contentDescription = "Save ${mixData.name}",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Full mix playlist screen shown when mix card is clicked
 */
@Composable
fun MixPlaylistScreen(
    mixName: String,
    mixDescription: String,
    songs: List<Song>,
    onPlaySong: (Song) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = mixName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Text(
            text = mixDescription,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Songs list
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(songs) { song ->
                MixSongItem(
                    song = song,
                    onPlaySong = { onPlaySong(song) }
                )
            }
        }
    }
}

/**
 * Individual song item in mix playlist
 */
@Composable
fun MixSongItem(
    song: Song,
    onPlaySong: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Song thumbnail
        AsyncImage(
            model = song.thumbnail ?: "",
            contentDescription = "Album art for ${song.title}",
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Text(
                text = song.getArtistString(),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Play button
        IconButton(onClick = onPlaySong) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play ${song.title}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Data class for mix card UI data
 */
data class MixCardData(
    val name: String,
    val description: String,
    val icon: String,
    val songs: List<Song>,
    val color: Color
)
