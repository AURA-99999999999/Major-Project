package com.aura.music.auth.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.auth.state.AuthState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for handling all authentication logic
 *
 * Responsibilities:
 * - Manage Firebase Authentication (Email/Password and Google Sign-In)
 * - Emit auth state changes through StateFlow
 * - Handle login, signup, logout operations
 * - Check initial auth status on app launch
 */
class AuthViewModel(private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()) :
    ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkAuthStatus()
    }

    /**
     * Check if user is already authenticated
     * Called on app launch to determine initial navigation
     */
    fun checkAuthStatus() {
        viewModelScope.launch {
            val currentUser = firebaseAuth.currentUser
            _authState.value = if (currentUser != null) {
                AuthState.Authenticated(
                    userId = currentUser.uid,
                    email = currentUser.email ?: ""
                )
            } else {
                AuthState.Unauthenticated
            }
        }
    }

    /**
     * Login user with email and password
     *
     * @param email User email address
     * @param password User password
     */
    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                if (email.isBlank() || password.isBlank()) {
                    _authState.value = AuthState.Error("Email and password cannot be empty")
                    return@launch
                }

                firebaseAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user != null) {
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        } else {
                            _authState.value = AuthState.Error("Login failed: User is null")
                        }
                    }
                    .addOnFailureListener { exception ->
                        _authState.value = AuthState.Error(
                            exception.message ?: "Unknown error occurred during login"
                        )
                    }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Create a new user account with email and password
     *
     * @param email User email address
     * @param password User password
     */
    fun signup(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                if (email.isBlank() || password.isBlank()) {
                    _authState.value = AuthState.Error("Email and password cannot be empty")
                    return@launch
                }

                if (password.length < 6) {
                    _authState.value = AuthState.Error("Password must be at least 6 characters")
                    return@launch
                }

                firebaseAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user != null) {
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        } else {
                            _authState.value = AuthState.Error("Signup failed: User is null")
                        }
                    }
                    .addOnFailureListener { exception ->
                        _authState.value = AuthState.Error(
                            exception.message ?: "Unknown error occurred during signup"
                        )
                    }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    /**
     * Sign in with Google using ID token from Google Sign-In
     *
     * @param idToken ID token from GoogleSignInAccount
     */
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                firebaseAuth.signInWithCredential(credential)
                    .addOnSuccessListener { result ->
                        val user = result.user
                        if (user != null) {
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        } else {
                            _authState.value = AuthState.Error("Google Sign-In failed: User is null")
                        }
                    }
                    .addOnFailureListener { exception ->
                        _authState.value = AuthState.Error(
                            exception.message ?: "Google Sign-In failed"
                        )
                    }
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Google Sign-In error")
            }
        }
    }

    /**
     * Sign out the current user
     */
    fun signout() {
        viewModelScope.launch {
            try {
                firebaseAuth.signOut()
                _authState.value = AuthState.Unauthenticated
            } catch (e: Exception) {
                _authState.value = AuthState.Error(e.message ?: "Error during signout")
            }
        }
    }
}

