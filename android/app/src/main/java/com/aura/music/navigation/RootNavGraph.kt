package com.aura.music.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.platform.LocalContext
import com.aura.music.auth.screens.LoginScreen
import com.aura.music.auth.screens.SignupScreen
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.state.PasswordResetState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.components.MiniPlayerBar
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.ThemeManager
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
 * - Theme management via centralized ThemeManager
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
 * @param themeManager ThemeManager for theme customization
 */
@Composable
fun RootNavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    musicService: MusicService?,
    authState: AuthState,
    themeManager: ThemeManager
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
    val isBuffering by playerViewModel.isBuffering.collectAsState()
    val isPreparing by playerViewModel.isPreparing.collectAsState()
    
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
        LaunchedEffect(serviceState.currentSong, serviceState.isPlaying, serviceState.isLoading) {
            if (serviceState.currentSong != null) {
                playerViewModel.updateCurrentSong(serviceState.currentSong)
                playerViewModel.updateIsPlaying(serviceState.isPlaying)
                playerViewModel.updateIsPreparing(serviceState.isLoading)
                playerViewModel.updateIsBuffering(serviceState.isLoading)
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isOnPlayerScreen = currentRoute == "main/player"
    val showBottomBar = authState is AuthState.Authenticated &&
        currentRoute?.startsWith("main/") == true &&
        !isOnPlayerScreen

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = DarkSurface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    fun navigateTo(route: String) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    val isHomeSelected = currentRoute == "main/home" ||
                        currentRoute?.startsWith("main/playlist-preview") == true ||
                        currentRoute?.startsWith(Screen.DailyMixDetail.route.substringBefore("/{")) == true
                    val isSearchSelected = currentRoute == "main/search"
                    val isLibrarySelected = currentRoute == "main/playlists" ||
                        currentRoute?.startsWith("main/playlist/") == true ||
                        currentRoute == "main/liked-songs"
                    val isProfileSelected = currentRoute == "main/profile" ||
                        currentRoute == "main/theme-settings"

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isHomeSelected) Icons.Filled.Home else Icons.Outlined.Home,
                                contentDescription = "Home",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                        selected = isHomeSelected,
                        onClick = { navigateTo("main/home") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isSearchSelected) Icons.Filled.Search else Icons.Outlined.Search,
                                contentDescription = "Search",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Search", style = MaterialTheme.typography.labelSmall) },
                        selected = isSearchSelected,
                        onClick = { navigateTo("main/search") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isLibrarySelected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote,
                                contentDescription = "Library",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Library", style = MaterialTheme.typography.labelSmall) },
                        selected = isLibrarySelected,
                        onClick = { navigateTo("main/playlists") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (isProfileSelected) Icons.Filled.Person else Icons.Outlined.Person,
                                contentDescription = "Profile",
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text("Profile", style = MaterialTheme.typography.labelSmall) },
                        selected = isProfileSelected,
                        onClick = { navigateTo("main/profile") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    playerViewModel = playerViewModel,
                    themeManager = themeManager
                )
            }

            // Mini player bar overlay - positioned above bottom nav
            // visible only during authenticated state when song exists and NOT on player screen
            // IMPORTANT: Mini player stays visible even when paused - only hide when song is cleared
            if (authState is AuthState.Authenticated) {
                val displaySong = currentSong ?: lastPlayedSong

                if (!isOnPlayerScreen && displaySong != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    ) {
                        MiniPlayerBar(
                            song = displaySong,
                            isPlaying = isPlaying,
                            isBuffering = isBuffering,
                            isPreparing = isPreparing,
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
}
