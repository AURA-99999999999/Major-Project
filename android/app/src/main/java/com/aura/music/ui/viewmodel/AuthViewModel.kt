package com.aura.music.ui.viewmodel

import android.app.Application
import android.util.Log
import android.util.Patterns
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
     * Sign in with Google using the ID token from GoogleSignInAccount.
     * 
     * This method:
     * 1. Extracts the ID token from the GoogleSignInAccount
     * 2. Creates a Firebase credential using GoogleAuthProvider.getCredential()
     * 3. Signs in to Firebase with the credential
     * 4. Persists the auth state on success
     */
    fun signInWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Extract ID token from Google account
                val idToken = account.idToken
                
                if (idToken != null) {
                    Log.d(TAG, "ID token received, length: ${idToken.length}")
                    Log.d(TAG, "User email: ${account.email}")
                    Log.d(TAG, "User display name: ${account.displayName}")
                    
                    // Create Firebase credential from Google ID token
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    Log.d(TAG, "Firebase credential created, signing in...")
                    
                    // Sign in to Firebase with the credential
                    val result = firebaseAuth.signInWithCredential(credential).await()
                    
                    // Check if sign-in was successful
                    val firebaseUser = result.user
                    if (firebaseUser != null) {
                        // Success - update UI state and persist auth state
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            error = null
                        )
                        setLoggedIn(true)
                        
                        Log.d(TAG, "Google sign-in to Firebase successful!")
                        Log.d(TAG, "Firebase user UID: ${firebaseUser.uid}")
                        Log.d(TAG, "Firebase user email: ${firebaseUser.email}")
                        Log.d(TAG, "Firebase user display name: ${firebaseUser.displayName}")
                    } else {
                        // No user returned - this is unexpected
                        val errorMsg = "Sign-in failed: No user returned from Firebase"
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = errorMsg
                        )
                        Log.e(TAG, errorMsg)
                    }
                } else {
                    // No ID token - this usually means wrong OAuth client ID or configuration issue
                    val errorMsg = "Sign-in failed: No ID token received from Google. Check OAuth client ID configuration."
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = errorMsg
                    )
                    Log.e(TAG, errorMsg)
                    Log.e(TAG, "Account email: ${account.email}")
                    Log.e(TAG, "Account ID: ${account.id}")
                    Log.e(TAG, "This usually means the Web client ID in strings.xml doesn't match Firebase configuration.")
                }
            } catch (e: FirebaseAuthException) {
                // Handle Firebase-specific errors
                val errorMsg = when (e.errorCode) {
                    "ERROR_INVALID_CREDENTIAL" -> {
                        Log.e(TAG, "Firebase Auth failed: Invalid credential", e)
                        "Invalid Google account credential. Please try again."
                    }
                    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> {
                        Log.e(TAG, "Firebase Auth failed: Account exists with different credential", e)
                        "An account already exists with this email using a different sign-in method."
                    }
                    "ERROR_NETWORK_REQUEST_FAILED" -> {
                        Log.e(TAG, "Firebase Auth failed: Network error", e)
                        "Network error. Please check your connection and try again."
                    }
                    "ERROR_TOO_MANY_REQUESTS" -> {
                        Log.e(TAG, "Firebase Auth failed: Too many requests", e)
                        "Too many sign-in attempts. Please try again later."
                    }
                    else -> {
                        Log.e(TAG, "Firebase Auth failed with error code: ${e.errorCode}", e)
                        Log.e(TAG, "Error message: ${e.message}")
                        "Authentication failed. Please try again."
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errorMsg
                )
            } catch (e: Exception) {
                // Handle any other unexpected errors
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "An unexpected error occurred during sign-in. Please try again."
                )
                Log.e(TAG, "Unexpected error during Google Sign-In to Firebase", e)
                Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
                Log.e(TAG, "Exception message: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Handle Google Sign-In result from Activity Result.
     * This method processes the GoogleSignInAccount and exchanges it for Firebase credentials.
     */
    fun handleGoogleSignInResult(task: com.google.android.gms.tasks.Task<GoogleSignInAccount>) {
        try {
            // Get the GoogleSignInAccount from the task
            val account = task.getResult(ApiException::class.java)
            
            if (account != null) {
                // Log successful account retrieval (without sensitive data)
                Log.d(TAG, "Google Sign-In account retrieved: ${account.email}")
                Log.d(TAG, "ID Token present: ${account.idToken != null}")
                
                // Proceed with Firebase authentication
                signInWithGoogle(account)
            } else {
                // Account is null - this shouldn't happen but handle it
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Google sign-in failed: No account returned"
                )
                Log.e(TAG, "Google Sign-In returned null account")
            }
        } catch (e: ApiException) {
            // Handle specific error codes with detailed logging
            val errorMessage = when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> {
                    Log.e(TAG, "Google Sign-In failed: Network error", e)
                    "Network error. Please check your connection and try again."
                }
                com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "Google Sign-In cancelled by user")
                    "Sign-in was cancelled."
                }
                com.google.android.gms.common.api.CommonStatusCodes.INTERNAL_ERROR -> {
                    Log.e(TAG, "Google Sign-In failed: Internal error", e)
                    "An internal error occurred. Please try again."
                }
                com.google.android.gms.common.api.CommonStatusCodes.INVALID_ACCOUNT -> {
                    Log.e(TAG, "Google Sign-In failed: Invalid account", e)
                    "Invalid Google account. Please try a different account."
                }
                com.google.android.gms.common.api.CommonStatusCodes.SIGN_IN_REQUIRED -> {
                    Log.e(TAG, "Google Sign-In failed: Sign-in required", e)
                    "Please sign in to your Google account first."
                }
                12500 -> { // SIGN_IN_FAILED
                    Log.e(TAG, "Google Sign-In failed: Sign-in failed (Status code 12500)", e)
                    "Google Sign-In failed. Please check your Google account settings."
                }
                12501 -> { // SIGN_IN_CANCELLED
                    Log.d(TAG, "Google Sign-In cancelled (Status code 12501)")
                    "Sign-in was cancelled."
                }
                12502 -> { // SIGN_IN_CURRENTLY_IN_PROGRESS
                    Log.w(TAG, "Google Sign-In already in progress (Status code 12502)")
                    "Sign-in is already in progress. Please wait."
                }
                12503 -> { // SIGN_IN_FAILED_DURING_SIGN_IN
                    Log.e(TAG, "Google Sign-In failed during sign-in (Status code 12503)", e)
                    "Sign-in failed. Please try again."
                }
                10 -> { // DEVELOPER_ERROR
                    Log.e(TAG, "Google Sign-In failed: Developer error (Status code 10). Check OAuth client ID configuration.", e)
                    "Sign-in configuration error. Please contact support."
                }
                else -> {
                    // Log the unknown error code for debugging
                    Log.e(TAG, "Google Sign-In failed with unknown error code: ${e.statusCode}", e)
                    Log.e(TAG, "Error message: ${e.message}")
                    Log.e(TAG, "Error cause: ${e.cause?.message}")
                    "Google sign-in failed. Please try again."
                }
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = errorMessage
            )
        } catch (e: Exception) {
            // Handle any other unexpected exceptions
            Log.e(TAG, "Unexpected error during Google Sign-In", e)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "An unexpected error occurred. Please try again."
            )
        }
    }
    
    /**
     * Handle Google Sign-In cancellation (user cancelled the sign-in flow)
     */
    fun handleGoogleSignInCancellation() {
        Log.d(TAG, "Google Sign-In was cancelled by user")
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            error = null // Don't show error for cancellation
        )
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
            
            // Basic email validation
            if (!Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                _uiState.value = _uiState.value.copy(
                    error = "Please enter a valid email address"
                )
                return@launch
            }
            
            // Password length validation (Firebase requires at least 6 characters)
            if (trimmedPassword.length < 6) {
                _uiState.value = _uiState.value.copy(
                    error = "Password must be at least 6 characters"
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

