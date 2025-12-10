package com.aura.music.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.AuraApplication
import com.aura.music.data.repository.MusicRepository
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null
)

private const val TAG = "AuthViewModel"

class AuthViewModel(
    application: Application,
    private val repository: MusicRepository
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()
    
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    
    init {
        // Check if user is already signed in on initialization
        checkAuthState()
    }

    fun login(email: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.login(email, password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                    setLoggedIn(true)
                    onSuccess(user.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Login failed"
                    )
                }
        }
    }

    fun register(username: String, email: String, password: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.register(username, email, password)
                .onSuccess { user ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true
                    )
                    setLoggedIn(true)
                    onSuccess(user.id)
                }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Registration failed"
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun setLoggedIn(isLoggedIn: Boolean) {
        val authPrefs = (getApplication<AuraApplication>()).authPreferences
        viewModelScope.launch {
            authPrefs.setLoggedIn(isLoggedIn)
        }
    }
    
    /**
     * Check if user is currently signed in with Firebase Auth
     */
    fun checkAuthState() {
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            _uiState.value = _uiState.value.copy(isLoggedIn = true)
            setLoggedIn(true)
            Log.d(TAG, "User already signed in: ${currentUser.email}")
        } else {
            _uiState.value = _uiState.value.copy(isLoggedIn = false)
            setLoggedIn(false)
        }
    }
    
    /**
     * Sign in with Google using the ID token from GoogleSignInAccount
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                val idToken = account.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val result = firebaseAuth.signInWithCredential(credential).await()
                    
                    if (result.user != null) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true
                        )
                        setLoggedIn(true)
                        Log.d(TAG, "Google sign-in successful: ${result.user?.email}")
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Sign-in failed: No user returned"
                        )
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sign-in failed: No ID token received"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Google sign-in failed"
                )
                Log.e(TAG, "Google sign-in error", e)
            }
        }
    }
    
    /**
     * Handle Google Sign-In result from Activity Result
     */
    fun handleGoogleSignInResult(task: com.google.android.gms.tasks.Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                signInWithGoogle(account)
            }
        } catch (e: ApiException) {
            val errorMessage = when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> "Network error. Please check your connection."
                com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> "Sign-in was cancelled."
                else -> "Google sign-in failed. Please try again."
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = errorMessage
            )
            Log.e(TAG, "Google sign-in failed: ${e.statusCode}", e)
        }
    }
    
    /**
     * Get current Firebase user
     */
    fun getCurrentUser(): FirebaseUser? = firebaseAuth.currentUser
    
    /**
     * Sign out from Firebase
     */
    fun signOut() {
        firebaseAuth.signOut()
        _uiState.value = _uiState.value.copy(isLoggedIn = false)
        setLoggedIn(false)
    }
    
    /**
     * Login or Register with email and password using Firebase Auth.
     * 
     * This method attempts to sign in first. If the user doesn't exist (ERROR_USER_NOT_FOUND),
     * it automatically creates a new account. This provides a seamless login/registration experience.
     * 
     * @param email User's email address
     * @param password User's password
     */
    fun loginOrRegister(email: String, password: String) {
        viewModelScope.launch {
            val trimmedEmail = email.trim()
            val trimmedPassword = password.trim()
            
            // Validate inputs
            if (trimmedEmail.isEmpty() || trimmedPassword.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    error = "Email and password cannot be empty"
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Step 1: Try to sign in with existing credentials
                val signInResult = firebaseAuth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword).await()
                
                // Sign in successful
                val user = signInResult.user
                if (user != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        error = null
                    )
                    setLoggedIn(true)
                    Log.d(TAG, "Sign in successful: ${user.email}")
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Sign in failed: No user returned"
                    )
                }
            } catch (e: FirebaseAuthException) {
                // Handle Firebase Auth specific errors
                when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> {
                        // User doesn't exist, create new account
                        Log.d(TAG, "User not found, creating new account...")
                        registerUserWithFirebase(trimmedEmail, trimmedPassword)
                    }
                    "ERROR_WRONG_PASSWORD" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Incorrect password. Please try again."
                        )
                        Log.e(TAG, "Wrong password", e)
                    }
                    "ERROR_INVALID_EMAIL" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Invalid email address. Please check and try again."
                        )
                        Log.e(TAG, "Invalid email", e)
                    }
                    "ERROR_USER_DISABLED" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "This account has been disabled. Please contact support."
                        )
                        Log.e(TAG, "User disabled", e)
                    }
                    "ERROR_TOO_MANY_REQUESTS" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Too many failed attempts. Please try again later."
                        )
                        Log.e(TAG, "Too many requests", e)
                    }
                    "ERROR_NETWORK_REQUEST_FAILED" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Network error. Please check your connection and try again."
                        )
                        Log.e(TAG, "Network error", e)
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Authentication failed. Please try again."
                        )
                        Log.e(TAG, "Sign in error: ${e.errorCode}", e)
                    }
                }
            } catch (e: Exception) {
                // Handle other exceptions
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred. Please try again."
                )
                Log.e(TAG, "Unexpected error during sign in", e)
            }
        }
    }
    
    /**
     * Register a new user with Firebase Auth (called automatically by loginOrRegister when user doesn't exist)
     */
    private fun registerUserWithFirebase(email: String, password: String) {
        viewModelScope.launch {
            try {
                val createResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                
                val user = createResult.user
                if (user != null) {
                    // Account created successfully - user is automatically signed in
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        error = null
                    )
                    setLoggedIn(true)
                    Log.d(TAG, "Account created and signed in: ${user.email}")
                    
                    // TODO: If Firestore is configured, create user document here
                    // Example:
                    // val userDoc = mapOf(
                    //     "uid" to user.uid,
                    //     "email" to user.email,
                    //     "createdAt" to FieldValue.serverTimestamp()
                    // )
                    // firestore.collection("users").document(user.uid).set(userDoc).await()
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Account creation failed: No user returned"
                    )
                }
            } catch (e: FirebaseAuthWeakPasswordException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Password is too weak. Please use at least 6 characters."
                )
                Log.e(TAG, "Weak password", e)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Invalid email address. Please check and try again."
                )
                Log.e(TAG, "Invalid credentials", e)
            } catch (e: FirebaseAuthUserCollisionException) {
                // This shouldn't happen since we only create if user doesn't exist, but handle it anyway
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "An account with this email already exists. Please sign in instead."
                )
                Log.e(TAG, "User collision", e)
            } catch (e: FirebaseAuthException) {
                when (e.errorCode) {
                    "ERROR_EMAIL_ALREADY_IN_USE" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "An account with this email already exists. Please sign in instead."
                        )
                    }
                    "ERROR_INVALID_EMAIL" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Invalid email address. Please check and try again."
                        )
                    }
                    "ERROR_OPERATION_NOT_ALLOWED" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Email/password accounts are not enabled. Please contact support."
                        )
                    }
                    "ERROR_NETWORK_REQUEST_FAILED" -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = "Network error. Please check your connection and try again."
                        )
                    }
                    else -> {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = e.message ?: "Account creation failed. Please try again."
                        )
                    }
                }
                Log.e(TAG, "Registration error: ${e.errorCode}", e)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "An unexpected error occurred. Please try again."
                )
                Log.e(TAG, "Unexpected error during registration", e)
            }
        }
    }
}

