package com.aura.music.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import androidx.navigation.NavType
import android.util.Log
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.search.SearchScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistPreviewScreen
import com.aura.music.ui.screens.liked.LikedSongsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.screens.theme.ThemeSettingsScreen
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.viewmodel.ViewModelFactory

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
 * - main/theme-settings
 * 
 * IMPORTANT:
 * - All navigation happens ONLY within this graph
 * - Bottom nav buttons navigate within this graph
 * - Search and Profile open without crashing
 * - When user logs out, entire MainGraph is cleared and user returns to auth
 * - Theme settings accessible from Profile screen
 * 
 * @param navController The main NavController
 * @param musicService The music playback service
 * @param authViewModel ViewModel that handles authentication
 * @param authState Current authentication state
 * @param playerViewModel Shared player state holder for mini player
 * @param themeManager ThemeManager for theme customization
 */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    musicService: MusicService?,
    authViewModel: AuthViewModel,
    authState: AuthState,
    playerViewModel: com.aura.music.ui.viewmodel.PlayerViewModel? = null,
    themeManager: ThemeManager? = null
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
                },
                onNavigateToArtist = { browseId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(browseId))
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
                },
                onNavigateToAlbum = { browseId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(browseId))
                },
                onNavigateToArtist = { browseId ->
                    navController.navigate(Screen.ArtistDetail.createRoute(browseId))
                },
                onNavigateToPlaylist = { browseId ->
                    navController.navigate(Screen.YTPlaylistDetail.createRoute(browseId))
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
                },
                themeManager = themeManager
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
                onNavigateToLikedSongs = {
                    navController.navigate("main/liked-songs")
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

        // ==================== LIKED SONGS SCREEN ====================
        composable("main/liked-songs") {
            LikedSongsScreen(
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                }
            )

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
            
            PlaylistPreviewScreen(
                playlistId = playlistId,
                repository = repository,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onPlaySong = { songs, index ->
                    if (songs.isEmpty() || index !in songs.indices) return@PlaylistPreviewScreen
                    musicService?.setQueueAndPlay(songs, index, "ytmusic_playlist")
                    navController.navigate("main/player")
                },
                onPlayAll = { songs ->
                    if (songs.isEmpty()) return@PlaylistPreviewScreen
                    musicService?.setQueueAndPlay(songs, 0, "ytmusic_playlist")
                    navController.navigate("main/player")
                },
                onPlayNext = { song ->
                    musicService?.insertNext(song)
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
                onNavigateToLikedSongs = {
                    navController.navigate("main/liked-songs")
                },
                onNavigateToThemeSettings = {
                    Log.d("MainGraph", "Navigating to theme-settings, themeManager=$themeManager")
                    navController.navigate("main/theme-settings")
                },
                authViewModel = authViewModel,
                themeManager = themeManager
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

        // ==================== THEME SETTINGS SCREEN ====================
        composable("main/theme-settings") {
            Log.d("MainGraph", "Theme settings composable entered, themeManager=$themeManager")
            if (themeManager != null) {
                Log.d("MainGraph", "Rendering ThemeSettingsScreen")
                ThemeSettingsScreen(
                    themeManager = themeManager,
                    onBackPress = {
                        navController.popBackStack()
                    }
                )
            } else {
                // Show error UI if themeManager is null (shouldn't happen but defensive)
                Log.e("MainGraph", "ThemeManager is NULL in theme-settings route!")
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "⚠️ Theme Manager Not Available",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Please restart the app",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = { navController.popBackStack() }
                        ) {
                            Text("Go Back")
                        }
                    }
                }
            }

            // If user logs out, return to auth
            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true }
                }
            }
        }

        // ==================== ALBUM DETAIL SCREEN ====================
        composable(
            route = Screen.AlbumDetail.route,
            arguments = listOf(navArgument("browseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
            com.aura.music.ui.screens.detail.AlbumDetailScreen(
                browseId = browseId,
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                }
            )

            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== ARTIST DETAIL SCREEN ====================
        composable(
            route = Screen.ArtistDetail.route,
            arguments = listOf(navArgument("browseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
            com.aura.music.ui.screens.detail.ArtistDetailScreen(
                browseId = browseId,
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                },
                onNavigateToAlbum = { albumBrowseId ->
                    navController.navigate(Screen.AlbumDetail.createRoute(albumBrowseId))
                }
            )

            LaunchedEffect(authState) {
                if (authState is AuthState.Unauthenticated) {
                    navController.navigate("auth") {
                        popUpTo("main") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== YOUTUBE PLAYLIST DETAIL SCREEN ====================
        composable(
            route = Screen.YTPlaylistDetail.route,
            arguments = listOf(navArgument("browseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
            com.aura.music.ui.screens.detail.YTPlaylistDetailScreen(
                browseId = browseId,
                musicService = musicService,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToPlayer = {
                    navController.navigate("main/player")
                }
            )

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
