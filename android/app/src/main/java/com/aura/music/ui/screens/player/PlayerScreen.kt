package com.aura.music.ui.screens.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.player.MusicService
import com.aura.music.player.PlayerState
import com.aura.music.player.RepeatMode as PlayerRepeatMode
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.PrimaryVariant
import com.aura.music.ui.theme.Secondary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import kotlin.math.floor

@Composable
fun PlayerScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit
) {
    val playerState = musicService?.playerState?.collectAsState()?.value ?: PlayerState()

    if (playerState.currentSong == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No song playing",
                color = TextSecondary,
                fontSize = 16.sp
            )
        }
        return
    }

    val song = playerState.currentSong
    val isPlaying = playerState.isPlaying
    val currentPosition = playerState.currentPosition
    val duration = playerState.duration
    val volume = playerState.volume

    // Rotation animation for album art
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        DarkSurface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Back button and menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_revert),
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                IconButton(onClick = { /* Menu */ }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_more),
                        contentDescription = "Menu",
                        tint = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Album Art - Large, rotating when playing
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Primary, PrimaryVariant)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = song.thumbnail ?: "",
                    contentDescription = song.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(if (isPlaying) rotationAngle else 0f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Song Info
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = song.getArtistString(),
                    fontSize = 16.sp,
                    color = TextSecondary,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) (currentPosition / duration).toFloat() else 0f,
                        onValueChange = { progress ->
                            musicService?.seekTo((progress * duration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary,
                            inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Text(
                            text = formatTime(duration),
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Repeat/Shuffle button (left)
                    IconButton(
                        onClick = {
                            val nextMode = when (playerState.repeatMode) {
                                PlayerRepeatMode.OFF -> PlayerRepeatMode.ALL
                                PlayerRepeatMode.ALL -> PlayerRepeatMode.ONE
                                PlayerRepeatMode.ONE -> PlayerRepeatMode.OFF
                            }
                            musicService?.setRepeatMode(nextMode)
                        }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                android.R.drawable.ic_menu_revert
                            ),
                            contentDescription = "Repeat",
                            tint = if (playerState.repeatMode != PlayerRepeatMode.OFF) Primary else TextSecondary
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { musicService?.playPrevious() }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause - Large button with glow
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(Primary, PrimaryVariant)
                                )
                            )
                            .clickable { musicService?.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = TextPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Next
                    IconButton(
                        onClick = { musicService?.playNext() }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Like/Favorite button (right)
                    IconButton(
                        onClick = { /* Toggle like */ }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.star_big_on),
                            contentDescription = "Like",
                            tint = Color.Red.copy(alpha = 0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Volume Control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_lock_silent_mode),
                        contentDescription = "Volume",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )

                    Slider(
                        value = volume,
                        onValueChange = { musicService?.setVolume(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Primary,
                            activeTrackColor = Primary,
                            inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

