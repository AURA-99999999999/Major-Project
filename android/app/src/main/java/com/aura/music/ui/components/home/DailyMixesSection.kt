
package com.aura.music.ui.components.home

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.aura.music.data.model.Song
import com.aura.music.di.ServiceLocator
import com.aura.music.ui.theme.ColorBlendingUtils
import com.aura.music.data.model.MixCardMeta
import com.aura.music.data.model.MixCardData
import androidx.compose.ui.graphics.Brush
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun normalizeMixName(mixKey: String, rawName: String?): String {
    val trimmed = rawName?.trim().orEmpty()
    return when (mixKey) {
        "favorites" -> if (trimmed.isBlank()) "Your Favorites" else trimmed
        "similar" -> if (trimmed.isBlank()) "Similar Artists" else trimmed
        "discover" -> if (trimmed.isBlank()) "Discover Mix" else trimmed
        "mood" -> if (trimmed.isBlank()) "Mood Mix" else trimmed
        else -> trimmed
    }
}

private fun moodMixDescriptionByTime(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Soft sunrise sounds."
        in 12..16 -> "Midday glow, steady flow."
        in 17..20 -> "Sunset vibes, easy mind."
        else -> "Late-night lights, mellow nights."
    }
}

private fun mixDescriptionFor(mixKey: String, rawSubtitle: String?): String {
    val subtitle = rawSubtitle?.trim().orEmpty()
    val normalizedKey = mixKey.trim().lowercase()

    // Always keep Mood Mix contextual and time-aware.
    if (normalizedKey == "mood") return moodMixDescriptionByTime()

    if (subtitle.isNotBlank()) return subtitle

    return when (normalizedKey) {
        "favorites" -> "Only your forever tracks."
        "discover" -> "New gems, zero skips."
        "similar" -> "Artists you will instantly vibe with."
        else -> "Fresh sound for right now."
    }
}

private fun mixEmojiFor(mixKey: String, backendEmoji: String?): String {
    return when (mixKey.trim().lowercase()) {
        "mood" -> "\uD83C\uDF19"
        else -> backendEmoji ?: "\uD83C\uDFB5"
    }
}

private fun mixColorFor(mixKey: String, backendColorHex: String?): String? {
    return when (mixKey.trim().lowercase()) {
        "discover" -> "#3A86FF"
        else -> backendColorHex
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
    val repository = remember { ServiceLocator.getMusicRepository() }
    val scope = rememberCoroutineScope()
    var dailyMixes by remember { mutableStateOf<List<MixCardMeta>>(emptyList()) }
    var mixSongsCache by remember { mutableStateOf<Map<String, List<Song>>>(emptyMap()) }

    LaunchedEffect(userId) {
        val result = withContext(Dispatchers.IO) { repository.getDailyMixesMeta() }
        dailyMixes = result.getOrElse {
            listOf(
                MixCardMeta(key = "favorites", title = "Your Favorites", subtitle = "", emoji = "\u2764\uFE0F", colorHex = "#FF6B6B"),
                MixCardMeta(key = "mood", title = "Mood Mix", subtitle = "", emoji = "\uD83C\uDF19", colorHex = "#6C5CE7"),
                MixCardMeta(key = "discover", title = "Discover Mix", subtitle = "", emoji = "\u2728", colorHex = "#3A86FF"),
                MixCardMeta(key = "similar", title = "Similar Artists", subtitle = "", emoji = "\uD83C\uDFA4", colorHex = "#4ECDC4")
            )
        }
    }

    fun getCachedSongs(mixKey: String): List<Song>? = mixSongsCache[mixKey]

    suspend fun loadSongsForMix(mixKey: String): List<Song> {
        getCachedSongs(mixKey)?.let { return it }

        val result = withContext(Dispatchers.IO) { repository.getDailyMixSongs(mixKey) }
        val songs = result.getOrNull()?.songs.orEmpty()
        if (songs.isNotEmpty()) {
            mixSongsCache = mixSongsCache + (mixKey to songs)
        }
        return songs
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(animationSpec = tween(300))
    ) {
        Text(
            text = "Daily Mixes",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(dailyMixes) { mix ->
                DailyMixCard(
                    mixKey = mix.key,
                    mixData = MixCardData(
                        key = mix.key,
                        name = mix.title,
                        description = mixDescriptionFor(mix.key, mix.subtitle),
                        icon = mixEmojiFor(mix.key, mix.emoji),
                        color = parseHexColor(mixColorFor(mix.key, mix.colorHex), fallback = Color(0xFF9B87F5)),
                        songs = emptyList()
                    ),
                    onPlayMix = {
                        scope.launch {
                            val songs = loadSongsForMix(mix.key)
                            onPlayMix(mix.key, songs)
                        }
                    },
                    onNavigateToMix = { onNavigateToMix(mix.key, mix.title, emptyList()) },
                    onShufflePlayMix = {
                        scope.launch {
                            val songs = loadSongsForMix(mix.key)
                            onShufflePlayMix(mix.key, songs)
                        }
                    },
                    onSaveMix = {
                        scope.launch {
                            val songs = loadSongsForMix(mix.key)
                            onSaveMix(mix.key, mix.title, songs)
                        }
                    }
                )
            }
        }
    }
}

private fun parseHexColor(hex: String?, fallback: Color): Color {
    return try {
        if (hex.isNullOrBlank()) return fallback
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Individual mix card component
 * - 160x200dp card with gradient background
 * - Shows mix name and description
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
            // Header: Icon, name, description
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

                Text(
                    text = mixData.description,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
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
