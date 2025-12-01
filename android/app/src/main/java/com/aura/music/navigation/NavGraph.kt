package com.aura.music.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aura.music.player.MusicService
import com.aura.music.ui.screens.auth.LoginScreen
import com.aura.music.ui.screens.auth.RegisterScreen
import com.aura.music.ui.screens.home.HomeScreen
import com.aura.music.ui.screens.player.PlayerScreen
import com.aura.music.ui.screens.playlist.PlaylistDetailScreen
import com.aura.music.ui.screens.playlist.PlaylistsScreen
import com.aura.music.ui.screens.profile.ProfileScreen
import com.aura.music.ui.screens.search.SearchScreen
import com.aura.music.ui.screens.splash.SplashScreen

@Composable
fun NavGraph(
    musicService: MusicService?,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Splash.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToHome = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }},
                onNavigateToLogin = { navController.navigate(Screen.Login.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }}
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = { navController.navigate(Screen.Register.route) },
                onLoginSuccess = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Login.route) { inclusive = true }
                }}
            )
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = { navController.popBackStack() },
                onRegisterSuccess = { navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Register.route) { inclusive = true }
                }}
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

