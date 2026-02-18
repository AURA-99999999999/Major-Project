package com.aura.music.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.ui.platform.LocalContext
import com.aura.music.auth.screens.LoginScreen
import com.aura.music.auth.screens.SignupScreen
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.state.PasswordResetState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.components.MiniPlayerBar
import com.aura.music.ui.viewmodel.PlayerViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory

/**
 * RootNavGraph - Main navigation graph for the entire application
 * 
 * This is the single NavHost that manages navigation between:
 * 1. Auth flow (login/signup) - when user is unauthenticated
 * 2. Main app flow (home/search/profile/player) - when user is authenticated
 * 
 * Key principles:
 * - Single NavHost in MainActivity (no nesting)
 * - Single NavController (created in MainActivity)
 * - Automatic navigation based on AuthState
 * - Proper backstack management with popUpTo
 * - No navigation inside screens (all callbacks pass to NavController)
 * - No creating NavControllers inside composables
 * 
 * IMPORTANT FOR MINI PLAYER:
 * - When authenticated, wraps NavHost with Column layout
 * - MiniPlayerBar positioned above bottom navigation
 * - Uses shared PlayerViewModel for state
 * - MiniPlayerBar observes currentSong and isPlaying from PlayerViewModel
 * - Also listens to MusicService for real-time playback updates
 * 
 * @param navController The single NavController from MainActivity
 * @param authViewModel ViewModel that manages authentication state
 * @param musicService The music playback service
 * @param authState Current authentication state
 */
@Composable
fun RootNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    musicService: MusicService?,
    authState: AuthState
) {
    // Create shared PlayerViewModel for mini player state
    val playerViewModel: PlayerViewModel = viewModel(
        factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application)
    )

    // Determine the root of the navigation graph based on auth state
    val startDestination = when (authState) {
        is AuthState.Authenticated -> "main"
        else -> "auth"
    }

    // Observe password reset state
    val passwordResetState by authViewModel.passwordResetState.collectAsState()

    // Observe player state for mini player display
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val lastPlayedSong by playerViewModel.lastPlayedSong.collectAsState()
    
    // Observe playback progress for seekbar
    val currentPosition = musicService?.playerState?.collectAsState()?.value?.currentPosition ?: 0L
    val duration = musicService?.playerState?.collectAsState()?.value?.duration ?: 0L
    
    // Load lastPlayed song when user logs in
    LaunchedEffect(authState) {
        if (authState is AuthState.Authenticated) {
            playerViewModel.loadLastPlayedSong()
        } else if (authState is AuthState.Unauthenticated) {
            // Clear player state on logout
            playerViewModel.clearState()
        }
    }
    
    // Observe MusicService state and sync with PlayerViewModel
    if (musicService != null) {
        val serviceState by musicService.playerState.collectAsState()
        LaunchedEffect(serviceState.currentSong, serviceState.isPlaying) {
            if (serviceState.currentSong != null) {
                playerViewModel.updateCurrentSong(serviceState.currentSong)
                playerViewModel.updateIsPlaying(serviceState.isPlaying)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
        ) {
            // ==================== AUTH GRAPH ====================
            authGraph(
                navController = navController,
                authViewModel = authViewModel,
                authState = authState,
                passwordResetState = passwordResetState
            )

            // ==================== MAIN APP GRAPH ====================
            mainGraph(
                navController = navController,
                musicService = musicService,
                authViewModel = authViewModel,
                authState = authState,
                playerViewModel = playerViewModel
            )
        }

        // Mini player bar overlay - positioned above bottom nav
        // visible only during authenticated state when song exists and NOT on player screen
        if (authState is AuthState.Authenticated) {
            val displaySong = currentSong ?: lastPlayedSong
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            val isOnPlayerScreen = currentRoute == "main/player"
            
            if (!isOnPlayerScreen && displaySong != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 70.dp)  // Height of bottom nav (56dp) + extra space upward
                ) {
                    MiniPlayerBar(
                        song = displaySong,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        onClick = {
                            // Navigate to full player
                            if (currentSong != null) {
                                navController.navigate("main/player")
                            }
                        },
                        onPlayPause = {
                            // Toggle playback
                            musicService?.togglePlayPause()
                        },
                        onSkipPrevious = {
                            musicService?.playPrevious()
                        },
                        onSkipNext = {
                            musicService?.playNext()
                        },
                        onSeek = { position ->
                            musicService?.seekTo(position)
                        }
                    )
                }
            }
        }
    }
}
