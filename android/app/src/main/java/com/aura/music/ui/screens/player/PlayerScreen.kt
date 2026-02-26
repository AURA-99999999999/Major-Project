package com.aura.music.ui.screens.player

import android.util.Log
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.music.player.MusicService
import com.aura.music.player.PlayerState
import com.aura.music.player.RepeatMode as PlayerRepeatMode
import com.aura.music.ui.theme.ColorBlendingUtils
import com.aura.music.ui.theme.DynamicGradientBuilder
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.GradientProvider
import com.aura.music.ui.theme.GradientTheme
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.PrimaryVariant
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.theme.ThemeColorEffect
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.floor

@Composable
fun PlayerScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    themeManager: ThemeManager? = null
) {
    val context = LocalContext.current
    val actualThemeManager: ThemeManager = themeManager ?: viewModel(
        factory = ViewModelFactory.create(context.applicationContext as android.app.Application)
    )
    val themeState by actualThemeManager.themeState.collectAsState()
    
    val playerState = musicService?.playerState?.collectAsState()?.value ?: PlayerState()
    val likedSongsViewModel: LikedSongsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val likedSongsState by likedSongsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        likedSongsViewModel.observeLikedSongs()
    }

    // Trigger dynamic theme color extraction when artwork loads
    ThemeColorEffect(
        song = playerState.currentSong,
        themeManager = actualThemeManager
    )

    if (playerState.currentSong == null || playerState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (playerState.isLoading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Loading audio...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp
                    )
                }
            } else {
                Text(
                    text = "No song playing",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp
                )
            }
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

    // Dynamic background colors from album artwork or theme
    val backgroundColors = if (themeState.dynamicAlbumColors && themeState.currentDynamicColors.dominant != null) {
        val dynamic = themeState.currentDynamicColors
        val isDark = themeState.themeMode != com.aura.music.ui.theme.ThemeMode.LIGHT
        
        // Get primary color with fallback
        val primaryColor = dynamic.getPrimaryColor()
            ?: MaterialTheme.colorScheme.background
        
        if (isDark) {
            // Build dynamic gradient from album colors
            listOf(
                ColorBlendingUtils.lighten(primaryColor, 0.15f),
                primaryColor,
                ColorBlendingUtils.darken(primaryColor, 0.3f),
                Color(0xFF0A0A0A)
            )
        } else {
            // Light theme: Use carefully blended lighter colors
            listOf(
                ColorBlendingUtils.lighten(primaryColor, 0.35f),
                ColorBlendingUtils.lighten(primaryColor, 0.15f),
                primaryColor,
                ColorBlendingUtils.desaturate(primaryColor, 0.2f)
            )
        }
    } else if (themeState.gradientTheme != GradientTheme.NONE) {
        // Fallback to theme gradient
        GradientProvider.getGradientColors(
            themeState.gradientTheme,
            themeState.themeMode == com.aura.music.ui.theme.ThemeMode.DARK
        ) ?: listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface
        )
    } else {
        // Final fallback to theme palette
        listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surface
        )
    }
    
    // Calculate readability overlay opacity based on brightness
    val overlayAlpha = if (backgroundColors.isNotEmpty()) {
        val brightness = ColorBlendingUtils.getPerceivedBrightness(backgroundColors[0])
        when {
            brightness > 0.7f -> 0.5f   // Light: strong overlay
            brightness > 0.5f -> 0.35f  // Medium: moderate overlay
            else -> 0.15f               // Dark: subtle overlay
        }
    } else {
        0.2f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = backgroundColors)
            )
    ) {
        // Readability overlay - ensures text remains visible
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = overlayAlpha))
        )
        
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
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = { /* Menu */ }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_menu_more),
                        contentDescription = "Menu",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (playerState.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            playerState.error?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }

            // Album Art - Large, rotating when playing
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
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
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = song.getArtistString(),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Progress Bar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = if (duration > 0) {
                            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { progress ->
                            musicService?.seekTo((progress * duration).toLong())
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                        Text(
                            text = formatTime(duration),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
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
                    // Repeat / Repeat One / Shuffle button (left)
                    IconButton(
                        onClick = {
                            val isShuffle = playerState.shuffleEnabled
                            val repeatMode = playerState.repeatMode

                            when {
                                isShuffle -> {
                                    musicService?.setShuffleEnabled(false)
                                    musicService?.setRepeatMode(PlayerRepeatMode.REPEAT_ALL)
                                }
                                repeatMode == PlayerRepeatMode.REPEAT_ALL -> {
                                    musicService?.setRepeatMode(PlayerRepeatMode.REPEAT_ONE)
                                    musicService?.setShuffleEnabled(false)
                                }
                                repeatMode == PlayerRepeatMode.REPEAT_ONE -> {
                                    musicService?.setRepeatMode(PlayerRepeatMode.NONE)
                                    musicService?.setShuffleEnabled(true)
                                }
                                else -> {
                                    musicService?.setShuffleEnabled(false)
                                    musicService?.setRepeatMode(PlayerRepeatMode.REPEAT_ALL)
                                }
                            }
                        }
                    ) {
                        val modeIcon = when {
                            playerState.shuffleEnabled -> Icons.Filled.Shuffle
                            playerState.repeatMode == PlayerRepeatMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        }
                        val isActive = playerState.shuffleEnabled ||
                            playerState.repeatMode != PlayerRepeatMode.NONE

                        Icon(
                            imageVector = modeIcon,
                            contentDescription = "Repeat mode",
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }

                    // Previous
                    IconButton(
                        onClick = { musicService?.playPrevious() }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause - Large button with glow
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable { musicService?.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onBackground,
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
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Like/Favorite button (right)
                    IconButton(
                        onClick = {
                            likedSongsViewModel.toggleLike(song)
                        }
                    ) {
                        val isLiked = likedSongsState.likedSongIds.contains(song.videoId)
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Liked" else "Not liked",
                            tint = if (isLiked) Color.Red.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
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
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.size(24.dp)
                    )

                    Slider(
                        value = volume,
                        onValueChange = { musicService?.setVolume(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                        )
                    )

                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
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

