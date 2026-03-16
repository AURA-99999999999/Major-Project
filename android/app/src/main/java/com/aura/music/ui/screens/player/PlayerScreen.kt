package com.aura.music.ui.screens.player

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import coil.compose.AsyncImage
import com.aura.music.player.MusicService
import com.aura.music.player.PlaybackUiState
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
import com.aura.music.ui.utils.AudioEqualizerLauncher
import com.aura.music.ui.utils.rememberEqualizerLauncher
import com.aura.music.ui.viewmodel.DownloadsEvent
import com.aura.music.ui.viewmodel.DownloadsViewModel
import com.aura.music.ui.viewmodel.LikedSongsViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit,
    themeManager: ThemeManager? = null,
    onNavigateToEqualizer: () -> Unit = {}
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
    val downloadsViewModel: DownloadsViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )
    val downloads by downloadsViewModel.downloads.collectAsState()
    
    // Queue bottom sheet state
    var showQueueBottomSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        likedSongsViewModel.observeLikedSongs()
    }

    LaunchedEffect(downloadsViewModel) {
        downloadsViewModel.events.collectLatest { event ->
            when (event) {
                is DownloadsEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // Trigger dynamic theme color extraction when artwork loads
    ThemeColorEffect(
        song = playerState.currentSong,
        themeManager = actualThemeManager
    )

    if (playerState.currentSong == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (playerState.isLoading || playerState.uiState == PlaybackUiState.LOADING) {
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
    val isLoading = playerState.uiState == PlaybackUiState.LOADING || playerState.isLoading
    val isCurrentSongDownloaded = remember(song?.videoId, downloads) {
        val currentVideoId = song?.videoId ?: return@remember false
        downloads.any { it.videoId == currentVideoId }
    }

    val controlsOffsetPx = with(LocalDensity.current) { 12.dp.toPx() }
    val backgroundAlpha = remember { Animatable(0f) }
    val controlsAlpha = remember { Animatable(0f) }
    val controlsTranslateY = remember { Animatable(controlsOffsetPx) }
    val albumArtScale = remember { Animatable(0.94f) }
    val playButtonScale = remember { Animatable(0.95f) }

    LaunchedEffect(song?.videoId) {
        backgroundAlpha.snapTo(0f)
        controlsAlpha.snapTo(0f)
        controlsTranslateY.snapTo(controlsOffsetPx)
        albumArtScale.snapTo(0.94f)
        playButtonScale.snapTo(0.95f)

        launch {
            backgroundAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }

        delay(100)

        launch {
            controlsAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
        launch {
            controlsTranslateY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
            )
        }
        launch {
            albumArtScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
        }
        launch {
            playButtonScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
        }
    }

    // Keep cover rotating while actively playing.
    val infiniteTransition = rememberInfiniteTransition(label = "album_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22000, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart
        ),
        label = "album_rotation_angle"
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
            .graphicsLayer(alpha = backgroundAlpha.value)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Top section - Back button and action icons
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Queue / Up Next button
                    IconButton(
                        onClick = { showQueueBottomSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Equalizer / Audio Settings button
                    IconButton(
                        onClick = onNavigateToEqualizer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            if (isLoading) {
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

            // Album Art with elevated rounded style and song-change scale animation
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .widthIn(max = 360.dp)
                    .aspectRatio(1f)
                    .shadow(22.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                } else {
                    AsyncImage(
                        model = song.thumbnail ?: "",
                        contentDescription = song.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(if (isPlaying) rotationAngle else 0f)
                            .scale(albumArtScale.value)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Song Info
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer(
                        alpha = controlsAlpha.value,
                        translationY = controlsTranslateY.value
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = song.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Slider(
                        value = if (duration > 0) {
                            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { progress ->
                            if (!isLoading) {
                                musicService?.seekTo((progress * duration).toLong())
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
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

                // ConstraintLayout chain for perfectly centered and balanced controls.
                ConstraintLayout(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(72.dp)
                ) {
                    val (modeRef, previousRef, playRef, nextRef, likeRef) = createRefs()

                    TransportIconButton(
                        onClick = {
                            when {
                                !playerState.shuffleEnabled && playerState.repeatMode == PlayerRepeatMode.NONE -> {
                                    musicService?.setShuffleEnabled(true)
                                    musicService?.setRepeatMode(PlayerRepeatMode.NONE)
                                }
                                playerState.shuffleEnabled -> {
                                    musicService?.setShuffleEnabled(false)
                                    musicService?.setRepeatMode(PlayerRepeatMode.REPEAT_ALL)
                                }
                                playerState.repeatMode == PlayerRepeatMode.REPEAT_ALL -> {
                                    musicService?.setShuffleEnabled(false)
                                    musicService?.setRepeatMode(PlayerRepeatMode.REPEAT_ONE)
                                }
                                else -> {
                                    musicService?.setShuffleEnabled(false)
                                    musicService?.setRepeatMode(PlayerRepeatMode.NONE)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .constrainAs(modeRef) {
                                start.linkTo(parent.start)
                                end.linkTo(previousRef.start, margin = 24.dp)
                                top.linkTo(playRef.top)
                                bottom.linkTo(playRef.bottom)
                            }
                    ) {
                        val modeIcon = when {
                            playerState.shuffleEnabled -> Icons.Filled.Shuffle
                            playerState.repeatMode == PlayerRepeatMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        }
                        val modeActive = playerState.shuffleEnabled ||
                            playerState.repeatMode != PlayerRepeatMode.NONE

                        Icon(
                            imageVector = modeIcon,
                            contentDescription = "Playback mode",
                            tint = if (modeActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    TransportIconButton(
                        onClick = { if (!isLoading) musicService?.playPrevious() },
                        modifier = Modifier
                            .size(52.dp)
                            .constrainAs(previousRef) {
                                end.linkTo(playRef.start, margin = 28.dp)
                                top.linkTo(playRef.top)
                                bottom.linkTo(playRef.bottom)
                            }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_rew),
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(playButtonScale.value)
                            .shadow(16.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                if (isLoading) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.primary
                            )
                            .clickable(enabled = !isLoading) { musicService?.togglePlayPause() }
                            .constrainAs(playRef) {
                                start.linkTo(parent.start)
                                end.linkTo(parent.end)
                                top.linkTo(parent.top)
                                bottom.linkTo(parent.bottom)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                            ),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    TransportIconButton(
                        onClick = { if (!isLoading) musicService?.playNext() },
                        modifier = Modifier
                            .size(52.dp)
                            .constrainAs(nextRef) {
                                start.linkTo(playRef.end, margin = 28.dp)
                                top.linkTo(playRef.top)
                                bottom.linkTo(playRef.bottom)
                            }
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_ff),
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    TransportIconButton(
                        onClick = { likedSongsViewModel.toggleLike(song) },
                        modifier = Modifier
                            .size(44.dp)
                            .constrainAs(likeRef) {
                                start.linkTo(nextRef.end, margin = 24.dp)
                                end.linkTo(parent.end)
                                top.linkTo(playRef.top)
                                bottom.linkTo(playRef.bottom)
                            }
                    ) {
                        val isLiked = likedSongsState.likedSongIds.contains(song.videoId)
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (isLiked) "Liked" else "Not liked",
                            tint = if (isLiked) {
                                Color.Red.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
                            },
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Spacer(modifier = Modifier.height(8.dp))

                // Volume Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
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
                        onValueChange = { if (!isLoading) musicService?.setVolume(it) },
                        modifier = Modifier
                            .weight(0.55f)
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.22f)
                        )
                    )

                    Text(
                        text = "${(volume * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.width(40.dp)
                    )

                    IconButton(
                        onClick = {
                            if (isLoading) return@IconButton
                            playerState.currentSong?.let {
                                if (isCurrentSongDownloaded) {
                                    downloadsViewModel.deleteSong(it.videoId)
                                } else {
                                    downloadsViewModel.downloadSong(it)
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isCurrentSongDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                            contentDescription = if (isCurrentSongDownloaded) "Remove from downloads" else "Download",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

        }
        
        // Snackbar for error messages (bottom of screen)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
        
        // Queue Bottom Sheet
        if (showQueueBottomSheet) {
            QueueBottomSheet(
                musicService = musicService,
                onDismiss = { showQueueBottomSheet = false }
            )
        }
    }
}

@Composable
private fun TransportIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (isPressed) 0.92f else 1f)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Bottom sheet displaying the current playback queue.
 * Shows all songs in the queue with the currently playing song highlighted.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun QueueBottomSheet(
    musicService: MusicService?,
    onDismiss: () -> Unit
) {
    val queue = musicService?.getQueue() ?: emptyList()
    val currentIndex = musicService?.getCurrentQueueIndex() ?: -1
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Up Next",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "${queue.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (queue.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs in queue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Queue list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    queue.forEachIndexed { index, song ->
                        val isCurrentSong = index == currentIndex
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isCurrentSong) 
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    else 
                                        Color.Transparent
                                )
                                .clickable {
                                    // TODO: Allow jumping to specific song in queue
                                }
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            AsyncImage(
                                model = song.thumbnail ?: "",
                                contentDescription = song.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            
                            // Song info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrentSong) 
                                        MaterialTheme.colorScheme.primary 
                                    else 
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                                
                                Text(
                                    text = song.getArtistString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            
                            // Current indicator
                            if (isCurrentSong) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(android.R.drawable.ic_media_play),
                                    contentDescription = "Now playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

