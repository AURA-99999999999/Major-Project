package com.aura.music.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.music.data.model.Song
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary

/**
 * MiniPlayerBar - Compact now-playing bar for use above bottom navigation
 *
 * Display:
 * - Thumbnail (left)
 * - Song title and artist (center)
 * - Skip previous, Play/Pause, Skip next buttons (right)
 *
 * Behavior:
 * - Visible only when song is playing or last played exists
 * - Click to open full player
 * - Media control buttons to control playback
 * - Smooth animations (fade)
 *
 * Technical:
 * - Uses Material3 Card with elevation
 * - Proper null safety checks
 * - No memory leaks (pure composable)
 * - Lifecycle-aware state collection
 *
 * @param song The currently playing or last played song
 * @param isPlaying Current playback state
 * @param onClick Callback when mini player is clicked (open full player)
 * @param onPlayPause Callback for play/pause button
 * @param onSkipPrevious Callback for skip previous button
 * @param onSkipNext Callback for skip next button
 * @param modifier Optional modifier for positioning
 */
@Composable
fun MiniPlayerBar(
    song: Song?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = song != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (song != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(enabled = true) { onClick() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant,
                    contentColor = TextPrimary
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(DarkSurface)
                    ) {
                        if (!song.thumbnail.isNullOrBlank()) {
                            AsyncImage(
                                model = song.thumbnail,
                                contentDescription = "Mini player thumbnail",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop,
                                onError = { /* Gracefully handle missing thumbnail */ }
                            )
                        } else {
                            // Placeholder when thumbnail missing
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(DarkSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "♪",
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Primary
                                )
                            }
                        }
                    }

                    // Song info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.getArtistString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Control buttons group
                    Row(
                        modifier = Modifier
                            .padding(end = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Skip previous button
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Skip Previous",
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Play/Pause button
                        IconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Skip next button
                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Skip Next",
                                tint = Primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
