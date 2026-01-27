package com.aura.music.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.aura.music.auth.screens.LoginScreen
import com.aura.music.auth.screens.SignupScreen
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.viewmodel.AuthViewModel

/**
 * AuthGraph - Navigation graph for authentication screens
 * 
 * Routes:
 * - auth/login (start destination)
 * - auth/signup
 * 
 * After successful authentication:
 * - User is automatically navigated to "main" graph
 * - All auth screens are cleared from backstack
 * 
 * @param navController The main NavController
 * @param authViewModel ViewModel that handles authentication
 * @param authState Current authentication state
 */
fun NavGraphBuilder.authGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    authState: AuthState
) {
    navigation(
        startDestination = "auth/login",
        route = "auth"
    ) {
        // ==================== LOGIN SCREEN ====================
        composable("auth/login") {
            LoginScreen(
                authState = authState,
                onLogin = { email, password ->
                    authViewModel.login(email, password)
                },
                onNavigateToSignup = {
                    navController.navigate("auth/signup") {
                        popUpTo("auth/login") { saveState = true }
                        launchSingleTop = true
                    }
                },
                onGoogleSignIn = { idToken ->
                    authViewModel.signInWithGoogle(idToken)
                }
            )

            // AUTO-NAVIGATE to MainGraph when authenticated
            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    navController.navigate("main") {
                        // Remove entire auth graph from backstack
                        popUpTo("auth") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // ==================== SIGNUP SCREEN ====================
        composable("auth/signup") {
            SignupScreen(
                authState = authState,
                onSignup = { email, password ->
                    authViewModel.signup(email, password)
                },
                onNavigateToLogin = {
                    navController.navigate("auth/login") {
                        popUpTo("auth/signup") { saveState = true }
                        launchSingleTop = true
                    }
                }
            )

            // AUTO-NAVIGATE to MainGraph when authenticated
            LaunchedEffect(authState) {
                if (authState is AuthState.Authenticated) {
                    navController.navigate("main") {
                        // Remove entire auth graph from backstack
                        popUpTo("auth") { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }
}
