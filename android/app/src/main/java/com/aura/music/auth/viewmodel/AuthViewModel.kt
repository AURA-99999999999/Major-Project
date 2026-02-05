package com.aura.music.auth.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.auth.state.AuthState
import com.aura.music.data.repository.FirestoreRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * ViewModel for handling all authentication logic
 *
 * Responsibilities:
 * - Manage Firebase Authentication (Email/Password and Google Sign-In)
 * - Emit auth state changes through StateFlow
 * - Handle login, signup, logout operations
 * - Check initial auth status on app launch
 * - Create/update Firestore user document on successful authentication
 */
class AuthViewModel(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestoreRepository: FirestoreRepository = FirestoreRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        checkAuthStatus()
    }

    /**
     * Check if user is already authenticated
     * Called on app launch to determine initial navigation
     * 
     * Note: Does NOT create Firestore document here - only on explicit login
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
     * On successful login:
     * 1. Authenticate with Firebase Auth
     * 2. Create/update Firestore user document
     * 3. Update auth state to Authenticated
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

                Log.d(TAG, "Attempting login for: $email")
                
                // Use await() instead of callbacks for proper coroutine handling
                val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val user = result.user
                
                if (user != null) {
                    Log.d(TAG, "Firebase Auth successful for userId: ${user.uid}")
                    
                    // Now create/update Firestore user document
                    firestoreRepository.createOrUpdateUser()
                        .onSuccess {
                            Log.d(TAG, "Firestore user document created/updated successfully")
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                        .onFailure { firestoreError ->
                            // Firestore failed but auth succeeded - still allow login
                            Log.e(TAG, "Firestore update failed on login", firestoreError)
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                } else {
                    Log.e(TAG, "Login failed: User is null")
                    _authState.value = AuthState.Error("Login failed: User is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login exception", e)
                _authState.value = AuthState.Error(e.message ?: "Unknown error occurred during login")
            }
        }
    }

    /**
     * Create a new user account with email and password
     * 
     * On successful signup:
     * 1. Create Firebase Auth account
     * 2. Create Firestore user document
     * 3. Update auth state to Authenticated
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

                Log.d(TAG, "Attempting signup for: $email")
                
                // Use await() instead of callbacks for proper coroutine handling
                val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                val user = result.user
                
                if (user != null) {
                    Log.d(TAG, "Firebase Auth signup successful for userId: ${user.uid}")
                    
                    // Now create Firestore user document
                    firestoreRepository.createOrUpdateUser()
                        .onSuccess {
                            Log.d(TAG, "Firestore user document created successfully")
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                        .onFailure { firestoreError ->
                            // Firestore failed but auth succeeded - still allow signup
                            Log.e(TAG, "Firestore creation failed on signup", firestoreError)
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                } else {
                    Log.e(TAG, "Signup failed: User is null")
                    _authState.value = AuthState.Error("Signup failed: User is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signup exception", e)
                _authState.value = AuthState.Error(e.message ?: "Unknown error occurred during signup")
            }
        }
    }

    /**
     * Sign in with Google using ID token from Google Sign-In
     * 
     * On successful Google Sign-In:
     * 1. Authenticate with Firebase using Google credential
     * 2. Create/update Firestore user document
     * 3. Update auth state to Authenticated
     *
     * @param idToken ID token from GoogleSignInAccount
     */
    fun signInWithGoogle(idToken: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                Log.d(TAG, "Attempting Google Sign-In")
                
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                
                // Use await() instead of callbacks for proper coroutine handling
                val result = firebaseAuth.signInWithCredential(credential).await()
                val user = result.user
                
                if (user != null) {
                    Log.d(TAG, "Google Sign-In successful for userId: ${user.uid}")
                    
                    // Now create/update Firestore user document
                    firestoreRepository.createOrUpdateUser()
                        .onSuccess {
                            Log.d(TAG, "Firestore user document created/updated successfully")
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                        .onFailure { firestoreError ->
                            // Firestore failed but auth succeeded - still allow login
                            Log.e(TAG, "Firestore update failed on Google Sign-In", firestoreError)
                            _authState.value = AuthState.Authenticated(
                                userId = user.uid,
                                email = user.email ?: ""
                            )
                        }
                } else {
                    Log.e(TAG, "Google Sign-In failed: User is null")
                    _authState.value = AuthState.Error("Google Sign-In failed: User is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In exception", e)
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
                Log.d(TAG, "User signing out")
                firebaseAuth.signOut()
                _authState.value = AuthState.Unauthenticated
            } catch (e: Exception) {
                Log.e(TAG, "Signout error", e)
                _authState.value = AuthState.Error(e.message ?: "Error during signout")
            }
        }
    }
    
    companion object {
        private const val TAG = "AuthViewModel"
    }
}

