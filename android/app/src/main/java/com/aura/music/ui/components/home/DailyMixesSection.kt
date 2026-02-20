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
import androidx.compose.ui.graphics.Brush

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
    var mixData by remember { mutableStateOf<Map<String, MixCardData>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    // Fetch mixes when userId changes
    LaunchedEffect(userId) {
        if (userId.isNullOrEmpty()) {
            error = "User ID not available"
            Log.w("DailyMixes", "No user ID provided")
            return@LaunchedEffect
        }
        
        isLoading = true
        error = null
        
        try {
            val repository = ServiceLocator.getMusicRepository()
            val response = withContext(Dispatchers.IO) {
                repository.getDailyMixes(refresh = false)
            }
            
            response.onSuccess { dailyMixResponse ->
                Log.i("DailyMixes", "Successfully loaded mixes")
                Log.d("DailyMixes", "Response: cached=${dailyMixResponse.cached}, mixes=${dailyMixResponse.mixes}")
                
                // Parse response into UI components
                val parsedMixes = mutableMapOf<String, MixCardData>()
                
                // Mix 1: Favorites
                dailyMixResponse.mixes?.dailyMix1?.let { mix ->
                    Log.d("DailyMixes", "Mix 1: ${mix.name}, songs=${mix.songs?.size ?: 0}")
                    parsedMixes["dailyMix1"] = MixCardData(
                        name = mix.name,
                        description = mix.description,
                        icon = "🎧",
                        songs = mix.songs?.toSongs() ?: emptyList(),
                        color = Color(0xFF9B87F5)
                    )
                }
                
                // Mix 2: Similar Artists
                dailyMixResponse.mixes?.dailyMix2?.let { mix ->
                    Log.d("DailyMixes", "Mix 2: ${mix.name}, songs=${mix.songs?.size ?: 0}")
                    parsedMixes["dailyMix2"] = MixCardData(
                        name = mix.name,
                        description = mix.description,
                        icon = "🎶",
                        songs = mix.songs?.toSongs() ?: emptyList(),
                        color = Color(0xFF87F5E0)
                    )
                }
                
                // Mix 3: Discover
                dailyMixResponse.mixes?.discoverMix?.let { mix ->
                    Log.d("DailyMixes", "Mix 3: ${mix.name}, songs=${mix.songs?.size ?: 0}")
                    parsedMixes["discoverMix"] = MixCardData(
                        name = mix.name,
                        description = mix.description,
                        icon = "✨",
                        songs = mix.songs?.toSongs() ?: emptyList(),
                        color = Color(0xFFF5B787)
                    )
                }
                
                // Mix 4: Mood
                dailyMixResponse.mixes?.moodMix?.let { mix ->
                    Log.d("DailyMixes", "Mix 4: ${mix.name}, songs=${mix.songs?.size ?: 0}")
                    parsedMixes["moodMix"] = MixCardData(
                        name = mix.name,
                        description = mix.description,
                        icon = "🌙",
                        songs = mix.songs?.toSongs() ?: emptyList(),
                        color = Color(0xFFF587B2)
                    )
                }
                
                if (parsedMixes.isNotEmpty()) {
                    mixData = parsedMixes
                    Log.i("DailyMixes", "Loaded ${parsedMixes.size} mixes successfully")
                } else {
                    error = "No mixes available"
                    Log.w("DailyMixes", "No mixes found in response")
                }
            }.onFailure { exception ->
                error = "Error loading mixes: ${exception.message}"
                Log.e("DailyMixes", "Failed to load mixes", exception)
            }
        } catch (e: Exception) {
            error = "Error loading mixes: ${e.message}"
            Log.e("DailyMixes", "Exception loading mixes", e)
        } finally {
            isLoading = false
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
            text = "🎧 Made for You",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        when {
            isLoading -> {
                // Show loading state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }
            error != null -> {
                // Show error message
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = error ?: "Failed to load mixes",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 14.sp
                    )
                }
            }
            mixData != null -> {
                // Show mixes carousel
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(listOf("dailyMix1", "dailyMix2", "discoverMix", "moodMix")) { mixKey ->
                        mixData?.get(mixKey)?.let { cardData ->
                            DailyMixCard(
                                mixKey = mixKey,
                                mixData = cardData,
                                onPlayMix = { onPlayMix(mixKey, cardData.songs) },
                                onNavigateToMix = { onNavigateToMix(mixKey, cardData.name, cardData.songs) },
                                onShufflePlayMix = { onShufflePlayMix(mixKey, cardData.songs) },
                                onSaveMix = { onSaveMix(mixKey, cardData.name, cardData.songs) }
                            )
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
