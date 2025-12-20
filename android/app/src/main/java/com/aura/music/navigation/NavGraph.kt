package com.aura.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.login.LoginScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.screens.search.SearchScreen
import com.aura.music.ui.viewmodel.AuthViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory

/**
 * Navigation graph for the app.
 * 
 * Handles:
 * - Authentication flow (Auth screen)
 * - Main app navigation (Home, Search, Player, etc.)
 * - Back stack management (clears auth screen after successful login)
 * 
 * @param musicService The music service instance for playback
 * @param navController Optional NavController (creates one if not provided)
 * @param startDestination Optional start destination (defaults based on auth state)
 */
@Composable
fun NavGraph(
    musicService: MusicService?,
    navController: NavHostController = rememberNavController(),
    startDestination: String? = null
) {
    val context = LocalContext.current
    val authViewModel: AuthViewModel = viewModel(
        factory = ViewModelFactory.create(context.applicationContext as android.app.Application)
    )
    val authState by authViewModel.uiState.collectAsState()
    
    // Determine start destination based on auth state
    val initialDestination = startDestination ?: if (authState.isLoggedIn) {
        Screen.Home.route
    } else {
        Screen.Auth.route
    }
    
    // Navigate to Home when user successfully signs in
    LaunchedEffect(authState.isLoggedIn) {
        if (authState.isLoggedIn) {
            // Only navigate if we're not already on the Home screen
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != Screen.Home.route) {
                // Clear back stack and navigate to Home
                navController.navigate(Screen.Home.route) {
                    // Pop all destinations from back stack
                    popUpTo(Screen.Auth.route) {
                        inclusive = true
                    }
                    // Prevent multiple instances of Home
                    launchSingleTop = true
                    // Clear entire back stack
                    restoreState = false
                }
            }
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = initialDestination
    ) {
        composable(Screen.Auth.route) {
            // Pass the same ViewModel instance to LoginScreen
            // This ensures state changes are observed by both NavGraph and LoginScreen
            LoginScreen(
                onSignInSuccess = {
                    // Navigation is handled by LaunchedEffect above
                    // This callback is kept for potential future use
                },
                viewModel = authViewModel
            )
        }
        
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
