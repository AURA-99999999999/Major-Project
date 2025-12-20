package com.aura.music

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.aura.music.ui.screens.login.LoginScreen
import com.aura.music.ui.theme.AuraTheme

/**
 * LoginActivity handles Google Sign-In authentication using Firebase Auth.
 * 
 * Flow:
 * 1. On start, checks if user is already authenticated - if yes, goes to MainActivity
 * 2. Shows LoginScreen with "Continue with Google" button
 * 3. On Google sign-in button click, launches Google Sign-In intent
 * 4. On successful Google sign-in, exchanges ID token for Firebase credential
 * 5. Signs in to Firebase and navigates to MainActivity
 */
class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    
    // UI state for login screen - using mutableStateOf for Compose reactivity
    private val isLoading = mutableStateOf(false)
    private val errorMessage = mutableStateOf<String?>(null)

    /**
     * Activity Result Launcher for Google Sign-In.
     * Handles the result from Google Sign-In picker.
     */
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleGoogleSignInResult(task)
            } else {
                // User cancelled or result failed
                isLoading.value = false
                errorMessage.value = null // Don't show error for cancellation
                Log.d(TAG, "Google Sign-In cancelled by user or result failed. Result code: ${result.resultCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In options with Web Client ID from Firebase
        val webClientId = getString(R.string.default_web_client_id)
        Log.d(TAG, "Initializing Google Sign-In with Web Client ID: $webClientId")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId) // Must use Web Client ID (client_type 3)
            .requestEmail()
            .requestProfile() // Request profile information
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Set Compose content with state observation
        setContent {
            AuraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Observe state changes for recomposition using 'by' delegate
                    // This ensures Compose recomposes when state changes
                    val isLoadingValue by isLoading
                    val errorMessageValue by errorMessage
                    
                    LoginScreen(
                        isLoading = isLoadingValue,
                        errorMessage = errorMessageValue,
                        onGoogleSignInClick = { 
                            Log.d(TAG, "=== Google Sign-In button clicked in UI ===")
                            signInWithGoogle() 
                        },
                        onDismissError = { 
                            Log.d(TAG, "Error dismissed by user")
                            errorMessage.value = null 
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        
        // Check if user is already authenticated
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: ${currentUser.email}, navigating to MainActivity")
            navigateToMain()
        }
    }

    /**
     * Initiates Google Sign-In flow by launching the Google Sign-In intent.
     * LOG: Button click handler - logs when called.
     */
    private fun signInWithGoogle() {
        Log.d(TAG, "=== signInWithGoogle() called ===")
        isLoading.value = true
        errorMessage.value = null
        Log.d(TAG, "Set isLoading to true, cleared error message")
        Log.d(TAG, "Getting Google Sign-In intent...")
        
        try {
            val signInIntent = googleSignInClient.signInIntent
            Log.d(TAG, "Google Sign-In intent obtained, launching ActivityResultLauncher")
            googleSignInLauncher.launch(signInIntent)
            Log.d(TAG, "ActivityResultLauncher.launch() called successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error launching Google Sign-In intent", e)
            isLoading.value = false
            errorMessage.value = "Failed to start Google Sign-In. Please try again."
        }
    }

    /**
     * Handles the result from Google Sign-In.
     * Extracts the GoogleSignInAccount and exchanges it for Firebase credentials.
     * LOG: ActivityResultLauncher callback - logs Google Sign-In result.
     */
    private fun handleGoogleSignInResult(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount>) {
        Log.d(TAG, "=== handleGoogleSignInResult() called ===")
        Log.d(TAG, "Task is complete: ${task.isComplete}, is successful: ${task.isSuccessful}")
        
        try {
            val account = task.getResult(ApiException::class.java)
            Log.d(TAG, "Account retrieved from task: ${account != null}")
            
            if (account != null) {
                Log.d(TAG, "Google Sign-In account retrieved successfully")
                Log.d(TAG, "Account email: ${account.email}")
                Log.d(TAG, "Account display name: ${account.displayName}")
                Log.d(TAG, "Account ID: ${account.id}")
                Log.d(TAG, "ID Token present: ${account.idToken != null}")
                Log.d(TAG, "ID Token length: ${account.idToken?.length ?: 0}")
                
                val idToken = account.idToken
                if (idToken != null) {
                    Log.d(TAG, "ID token retrieved, proceeding to Firebase authentication")
                    // Exchange Google ID token for Firebase credential
                    firebaseAuthWithGoogle(idToken)
                } else {
                    isLoading.value = false
                    errorMessage.value = "Sign-in failed: No ID token received. Check OAuth client ID configuration."
                    Log.e(TAG, "ERROR: No ID token received from Google Sign-In.")
                    Log.e(TAG, "This usually means wrong OAuth client ID or misconfiguration.")
                }
            } else {
                isLoading.value = false
                errorMessage.value = "Sign-in failed: No account returned"
                Log.e(TAG, "ERROR: Google Sign-In returned null account")
            }
        } catch (e: ApiException) {
            isLoading.value = false
            Log.e(TAG, "=== ApiException caught in handleGoogleSignInResult ===")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Status code: ${e.statusCode}")
            Log.e(TAG, "Exception message: ${e.message}")
            Log.e(TAG, "Exception status: ${e.status?.statusMessage}")
            
            // Handle specific error codes
            val errorMsg = when (e.statusCode) {
                com.google.android.gms.common.api.CommonStatusCodes.NETWORK_ERROR -> {
                    Log.e(TAG, "Google Sign-In failed: Network error", e)
                    "Network error. Please check your connection and try again."
                }
                com.google.android.gms.common.api.CommonStatusCodes.CANCELED -> {
                    Log.d(TAG, "Google Sign-In cancelled by user")
                    null // Don't show error for cancellation
                }
                10 -> { // DEVELOPER_ERROR - OAuth client ID misconfiguration
                    Log.e(TAG, "Google Sign-In failed: Developer error (Status code 10). Check OAuth client ID.", e)
                    "Sign-in configuration error. Please check OAuth client ID settings."
                }
                12500 -> { // SIGN_IN_FAILED
                    Log.e(TAG, "Google Sign-In failed: Sign-in failed (Status code 12500)", e)
                    "Google Sign-In failed. Please check your Google account settings."
                }
                else -> {
                    Log.e(TAG, "Google Sign-In failed with error code: ${e.statusCode}", e)
                    Log.e(TAG, "Error message: ${e.message}")
                    "Google sign-in failed. Please try again."
                }
            }
            
            errorMessage.value = errorMsg
        } catch (e: Exception) {
            isLoading.value = false
            errorMessage.value = "An unexpected error occurred. Please try again."
            Log.e(TAG, "=== Unexpected Exception caught in handleGoogleSignInResult ===")
            Log.e(TAG, "Exception type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Exception message: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Exchanges Google ID token for Firebase credential and signs in to Firebase.
     * LOG: Firebase authentication - logs credential exchange and sign-in result.
     * 
     * @param idToken The ID token from Google Sign-In
     */
    private fun firebaseAuthWithGoogle(idToken: String) {
        Log.d(TAG, "=== firebaseAuthWithGoogle() called ===")
        Log.d(TAG, "ID token received, length: ${idToken.length}")
        Log.d(TAG, "Creating Firebase credential from Google ID token...")
        
        try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            Log.d(TAG, "Firebase credential created successfully")
            Log.d(TAG, "Calling FirebaseAuth.signInWithCredential()...")
            
            auth.signInWithCredential(credential)
                .addOnCompleteListener(this) { task ->
                    Log.d(TAG, "=== Firebase signInWithCredential callback fired ===")
                    Log.d(TAG, "Task is successful: ${task.isSuccessful}")
                    
                    if (task.isSuccessful) {
                        // Sign in success
                        val user = auth.currentUser
                        Log.d(TAG, "✅ SUCCESS: Firebase sign-in successful!")
                        Log.d(TAG, "Firebase user UID: ${user?.uid}")
                        Log.d(TAG, "Firebase user email: ${user?.email}")
                        Log.d(TAG, "Firebase user display name: ${user?.displayName}")
                        Log.d(TAG, "Navigating to MainActivity...")
                        
                        navigateToMain()
                    } else {
                        // Sign in failed
                        isLoading.value = false
                        Log.e(TAG, "❌ FAILURE: Firebase sign-in failed")
                        
                        val exception = task.exception
                        Log.e(TAG, "Exception type: ${exception?.javaClass?.simpleName}")
                        Log.e(TAG, "Exception message: ${exception?.message}")
                        
                        val errorMsg = when (exception) {
                            is FirebaseAuthException -> {
                                Log.e(TAG, "FirebaseAuthException error code: ${exception.errorCode}")
                                Log.e(TAG, "FirebaseAuthException message: ${exception.message}")
                                when (exception.errorCode) {
                                    "ERROR_INVALID_CREDENTIAL" -> {
                                        Log.e(TAG, "ERROR_INVALID_CREDENTIAL detected")
                                        "Invalid Google account credential. Please try again."
                                    }
                                    "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" -> {
                                        Log.e(TAG, "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL detected")
                                        "An account already exists with this email using a different sign-in method."
                                    }
                                    "ERROR_NETWORK_REQUEST_FAILED" -> {
                                        Log.e(TAG, "ERROR_NETWORK_REQUEST_FAILED detected")
                                        "Network error. Please check your connection and try again."
                                    }
                                    else -> {
                                        Log.e(TAG, "Unknown FirebaseAuthException error code: ${exception.errorCode}")
                                        "Authentication failed. Please try again."
                                    }
                                }
                            }
                            else -> {
                                Log.e(TAG, "Non-FirebaseAuthException: ${exception?.javaClass?.simpleName}")
                                "Authentication failed. Please try again."
                            }
                        }
                        
                        errorMessage.value = errorMsg
                        Log.d(TAG, "Error message set in UI: $errorMsg")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ addOnFailureListener fired in firebaseAuthWithGoogle")
                    Log.e(TAG, "Failure exception: ${e.javaClass.simpleName}")
                    Log.e(TAG, "Failure message: ${e.message}")
                    e.printStackTrace()
                    isLoading.value = false
                    errorMessage.value = "Authentication failed: ${e.message ?: "Unknown error"}"
                }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception in firebaseAuthWithGoogle()", e)
            isLoading.value = false
            errorMessage.value = "An error occurred during authentication. Please try again."
            e.printStackTrace()
        }
    }

    /**
     * Navigates to MainActivity and finishes this activity.
     * Prevents user from going back to login screen with back button.
     * LOG: Navigation - logs when navigating to main screen.
     */
    private fun navigateToMain() {
        Log.d(TAG, "=== navigateToMain() called ===")
        try {
            isLoading.value = false // Reset loading state
            val intent = Intent(this, MainActivity::class.java)
            Log.d(TAG, "Intent created for MainActivity")
            startActivity(intent)
            Log.d(TAG, "MainActivity started")
            finish() // Remove LoginActivity from back stack
            Log.d(TAG, "LoginActivity finished (removed from back stack)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error navigating to MainActivity", e)
            isLoading.value = false
            errorMessage.value = "Navigation error. Please restart the app."
        }
    }

    companion object {
        private const val TAG = "LoginActivity"
    }
}

