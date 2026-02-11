package com.aura.music.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.navigation.NavType
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.search.SearchScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistPreviewScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

/**
 * MainGraph - Navigation graph for the main music app
 * 
 * All routes are nested under "main" prefix:
 * - main/home (start destination)
 * - main/search
 * - main/player
 * - main/playlists
 * - main/playlist/{playlistId}
 * - main/profile
 * 
 * IMPORTANT:
 * - All navigation happens ONLY within this graph
 * - Bottom nav buttons navigate within this graph
 * - Search and Profile open without crashing
 * - When user logs out, entire MainGraph is cleared and user returns to auth
 * 
 * @param navController The main NavController
 * @param musicService The music playback service
 * @param authViewModel ViewModel that handles authentication
 * @param authState Current authentication state
 */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    musicService: MusicService?,
    authViewModel: AuthViewModel,
    authState: AuthState
) {
    navigation(
        startDestination = "main/home",
        route = "main"
    ) {
        // ==================== HOME SCREEN ====================
        composable("main/home") {
            HomeScreen(
                musicService = musicService,
                authState = authState,
                onNavigateToSearch = {
                    navController.navigate("main/search")
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                },
                onNavigateToPlaylists = {
                    navController.navigate("main/playlists")
                },
                onNavigateToProfile = {
                    navController.navigate("main/profile")
                },
                onNavigateToPlaylistPreview = { playlistId ->
                    navController.navigate("main/playlist-preview/$playlistId")
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== SEARCH SCREEN ====================
        composable("main/search") {
            SearchScreen(
                musicService = musicService,
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== PLAYER SCREEN ====================
        composable("main/player") {
            PlayerScreen(
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== PLAYLISTS SCREEN ====================
        composable("main/playlists") {
            PlaylistsScreen(
                musicService = musicService,
                onNavigateToPlaylistDetail = { playlistId ->
                    navController.navigate("main/playlist/$playlistId")
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== PLAYLIST DETAIL SCREEN ====================
        composable(
            "main/playlist/{playlistId}",
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaylistDetailScreen(
                playlistId = playlistId,
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== PLAYLIST PREVIEW SCREEN ====================
        composable(
            "main/playlist-preview/{playlistId}",
            arguments = listOf(
                navArgument("playlistId") {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            val context = LocalContext.current
            val repository = ViewModelFactory.getMusicRepository(context.applicationContext as android.app.Application)
            val coroutineScope = rememberCoroutineScope()
            
            PlaylistPreviewScreen(
                playlistId = playlistId,
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlaySong = { song ->
                    // Play the single song
                    musicService?.let { service ->
                        val state = service.playerState.value
                        if (state.currentSong?.videoId == song.videoId && state.isPlaying) return@let
                        
                        // Resolve song with streaming URL
                        coroutineScope.launch {
                            val resolvedSong = repository.getSong(song.videoId).getOrNull()
                            if (resolvedSong != null && !resolvedSong.url.isNullOrBlank()) {
                                service.playResolvedSong(
                                    resolvedSong.copy(
                                        title = song.title,
                                        artist = song.artist,
                                        artists = song.artists,
                                        thumbnail = song.thumbnail
                                    ),
                                    false,
                                    "ytmusic_playlist"
                                )
                                navController.navigate("main/player")
                            }
                        }
                    }
                },
                onPlayAll = { songs ->
                    // Play all songs from the playlist
                    if (songs.isEmpty()) return@PlaylistPreviewScreen
                    
                    musicService?.let { service ->
                        // Play first song and queue the rest
                        coroutineScope.launch {
                            val firstSong = songs.first()
                            val resolvedSong = repository.getSong(firstSong.videoId).getOrNull()
                            if (resolvedSong != null && !resolvedSong.url.isNullOrBlank()) {
                                service.playResolvedSong(
                                    resolvedSong.copy(
                                        title = firstSong.title,
                                        artist = firstSong.artist,
                                        artists = firstSong.artists,
                                        thumbnail = firstSong.thumbnail
                                    ),
                                    false,
                                    "ytmusic_playlist"
                                )
                                navController.navigate("main/player")
                            }
                        }
                    }
                }
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== PROFILE SCREEN ====================
        composable("main/profile") {
            ProfileScreen(
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlaylists = {
                    navController.navigate("main/playlists")
                },
                authViewModel = authViewModel
            )

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
