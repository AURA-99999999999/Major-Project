package com.aura.music.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavGraphBuilder
import com.aura.music.auth.state.AuthState
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.liked.LikedSongsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.screens.search.SearchScreen

/**
 * Navigation graph for the music app.
 * 
 * This function defines all the music app routes (home, search, player, etc)
 * to be added to the parent NavHost in MainActivity.
 * 
 * Routes handled:
 * - Home screen (trending music, library)
 * - Search screen
 * - Player screen
 * - Playlists screens
 * - Profile screen
 * 
 * @param musicService The music service instance for playback
 * @param navController The parent NavController from MainActivity
 */
@Composable
fun NavGraph(
    musicService: MusicService?,
    navController: NavHostController,
    authState: AuthState
) {
    // Render music app screens using provided navigation structure
    // The NavHost is already created in MainActivity
    // Here we just render the Home screen which has all the navigation
    
    HomeScreen(
        musicService = musicService,
        authState = authState,
        onNavigateToSearch = { navController.navigate(Screen.Search.route) },
        onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
        onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
        onNavigateToPlaylistPreview = { playlistId ->
            navController.navigate("main/playlist-preview/$playlistId")
        }
    )
}

/**
 * Extension function to register all music app routes to a NavGraphBuilder
 * Call this in the parent NavHost if you need nested navigation graphs
 */
fun NavGraphBuilder.musicAppGraph(
    musicService: MusicService?,
    navController: NavHostController,
    authState: AuthState
) {
    composable(Screen.Home.route) {
        HomeScreen(
            musicService = musicService,
            authState = authState,
            onNavigateToSearch = { navController.navigate(Screen.Search.route) },
            onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
            onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
            onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
            onNavigateToPlaylistPreview = { playlistId ->
                navController.navigate("main/playlist-preview/$playlistId")
            }
        )
    }

    composable(Screen.Search.route) {
        SearchScreen(
            musicService = musicService,
            onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(Screen.LikedSongs.route) {
        LikedSongsScreen(
            musicService = musicService,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(Screen.Player.route) }
        )
    }
    
    composable(Screen.Player.route) {
        PlayerScreen(
            musicService = musicService,
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable(Screen.Playlists.route) {
        PlaylistsScreen(
            musicService = musicService,
            onNavigateToPlaylistDetail = { playlistId ->
                navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
            },
            onNavigateToLikedSongs = { navController.navigate(Screen.LikedSongs.route) },
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    composable(
        Screen.PlaylistDetail.route,
        arguments = listOf(
            navArgument("playlistId") {
                type = androidx.navigation.NavType.StringType
            }
        )
    ) { backStackEntry ->
        val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
        PlaylistDetailScreen(
            playlistId = playlistId,
            musicService = musicService,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPlayer = { navController.navigate(Screen.Player.route) }
        )
    }
    
    composable(Screen.Profile.route) {
        ProfileScreen(
            musicService = musicService,
            onNavigateBack = { navController.popBackStack() },
            onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
            onNavigateToLikedSongs = { navController.navigate(Screen.LikedSongs.route) }
        )
    }
}
