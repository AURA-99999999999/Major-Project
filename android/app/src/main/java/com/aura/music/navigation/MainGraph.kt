package com.aura.music.navigation

import androidx.compose.runtime.LaunchedEffect
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
import com.aura.music.ui.screens.liked.LikedSongsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
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
 * @param playerViewModel Shared player state holder for mini player
 */
fun NavGraphBuilder.mainGraph(
    navController: NavHostController,
    musicService: MusicService?,
    authViewModel: AuthViewModel,
    authState: AuthState,
    playerViewModel: com.aura.music.ui.viewmodel.PlayerViewModel? = null
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
