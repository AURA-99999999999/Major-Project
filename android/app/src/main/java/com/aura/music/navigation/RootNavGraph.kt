package com.aura.music.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aura.music.auth.screens.LoginScreen
import com.aura.music.auth.screens.SignupScreen
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.player.MusicService

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
    // Determine the root of the navigation graph based on auth state
    val startDestination = when (authState) {
        is AuthState.Authenticated -> "main"
        else -> "auth"
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ==================== AUTH GRAPH ====================
        authGraph(
            navController = navController,
            authViewModel = authViewModel,
            authState = authState
        )

        // ==================== MAIN APP GRAPH ====================
        mainGraph(
            navController = navController,
            musicService = musicService,
            authViewModel = authViewModel,
            authState = authState
        )
    }
}
