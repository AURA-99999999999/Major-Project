package com.aura.music.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.screens.search.SearchScreen

/**
 * Navigation graph for the app.
 * 
 * Handles main app navigation between:
 * - Home screen (trending music, library)
 * - Search screen
 * - Player screen
 * - Playlists screens
 * - Profile screen
 * 
 * @param musicService The music service instance for playback
 * @param navController Optional NavController (creates one if not provided)
 * @param startDestination Optional start destination (defaults to Home)
 */
@Composable
fun NavGraph(
    musicService: MusicService?,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                musicService = musicService,
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToPlayer = { navController.navigate(Screen.Player.route) },
                onNavigateToPlaylists = { navController.navigate(Screen.Playlists.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToPlaylistDetail = { playlistId ->
                    navController.navigate(Screen.PlaylistDetail.createRoute(playlistId))
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
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.PlaylistDetail.route) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaylistDetailScreen(
                playlistId = playlistId,
                musicService = musicService,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                musicService = musicService,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
