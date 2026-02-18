package com.aura.music.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.aura.music.auth.screens.HomeScreen
import com.aura.music.auth.screens.LoginScreen
import com.aura.music.auth.screens.SignupScreen
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.state.PasswordResetState
import com.aura.music.auth.viewmodel.AuthViewModel

/**
 * Authentication Navigation Graph
 *
 * Handles navigation between:
 * - Login screen (unauthenticated state)
 * - Signup screen (account creation)
 * - Home screen (authenticated state)
 *
 * Automatically navigates based on authentication state
 *
 * @param navController Navigation controller for auth flows
 * @param onAuthSuccess Callback when user is authenticated
 * @param onAuthFailure Callback when user logs out
 */
@Composable
fun AuthNavigation(
    navController: NavHostController,
    onAuthSuccess: () -> Unit = {},
    onAuthFailure: () -> Unit = {}
) {
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    val passwordResetState by authViewModel.passwordResetState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = when (authState) {
            is AuthState.Authenticated -> "home"
            else -> "login"
        }
    ) {
        composable("login") {
            LoginScreen(
                authState = authState,
                passwordResetState = passwordResetState,
                onLogin = { email, password ->
                    authViewModel.login(email, password)
                },
                onNavigateToSignup = {
                    navController.navigate("signup") {
                        popUpTo("login") { saveState = true }
                        launchSingleTop = true
                    }
                },
                onGoogleSignIn = { idToken ->
                    authViewModel.signInWithGoogle(idToken)
                },
                onSendPasswordResetEmail = { email ->
                    authViewModel.sendPasswordResetEmail(email)
                },
                onResetPasswordResetState = {
                    authViewModel.resetPasswordResetState()
                }
            )

            // Navigate to home when authenticated
            if (authState is AuthState.Authenticated) {
                onAuthSuccess()
            }
        }

        composable("signup") {
            SignupScreen(
                authState = authState,
                onSignup = { email, password ->
                    authViewModel.signup(email, password)
                },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("signup") { saveState = true }
                        launchSingleTop = true
                    }
                }
            )

            // Navigate to home when authenticated
            if (authState is AuthState.Authenticated) {
                onAuthSuccess()
            }
        }

        composable("home") {
            HomeScreen(
                authState = authState,
                onSignout = {
                    authViewModel.signout()
                    onAuthFailure()
                }
            )

            // Navigate to login when unauthenticated
            if (authState is AuthState.Unauthenticated) {
                navController.navigate("login") {
                    popUpTo("home") { inclusive = true }
                }
            }
        }
    }
}
