package com.aura.music.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.tasks.await

/**
 * Repository for handling all Firebase Authentication operations
 *
 * Responsibilities:
 * - Email/Password authentication
 * - Google Sign-In
 * - Password reset email using Firebase default flow
 * - Error mapping to user-friendly messages
 *
 * Architecture:
 * - Separates Firebase logic from ViewModel
 * - Uses Result<T> for proper error handling
 * - All coroutine-safe operations
 * - Uses Firebase Auth's built-in user verification
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {

    /**
     * Send password reset email to user
     *
     * Uses Firebase Authentication's built-in password reset functionality.
     * Firebase Auth automatically:
     * - Validates email format
     * - Checks if user exists in Firebase Auth
     * - Sends password reset email with secure link
     * - Handles rate limiting
     *
     * Flow:
     * 1. Validate email is not blank
     * 2. Call Firebase Auth sendPasswordResetEmail()
     * 3. Firebase sends email if user exists (silently succeeds if user doesn't exist for security)
     * 4. Handle errors with user-friendly messages
     *
     * @param email User's registered email address
     * @return Result<Unit> - Success or mapped error message
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            if (email.isBlank()) {
                return Result.failure(IllegalArgumentException("Email cannot be empty"))
            }

            Log.d(TAG, "Sending password reset email to: $email")

            // Firebase Auth handles user verification internally
            // It will send email only if user exists in Firebase Auth
            firebaseAuth.sendPasswordResetEmail(email).await()

            Log.d(TAG, "Password reset email request processed successfully for: $email")
            Result.success(Unit)
        } catch (e: FirebaseAuthException) {
            Log.e(TAG, "Firebase Auth Error - Code: ${e.errorCode}", e)
            val userMessage = mapFirebaseError(e.errorCode)
            Result.failure(Exception(userMessage))
        } catch (e: Exception) {
            Log.e(TAG, "Password reset email error", e)
            Result.failure(Exception(e.message ?: "Failed to send password reset email"))
        }
    }

    /**
     * Map Firebase error codes to user-friendly messages
     *
     * @param errorCode Firebase error code
     * @return User-friendly error message
     */
    private fun mapFirebaseError(errorCode: String?): String {
        return when (errorCode) {
            "ERROR_USER_NOT_FOUND" -> "No account found with this email address"
            "ERROR_INVALID_EMAIL" -> "Invalid email address format"
            "ERROR_TOO_MANY_REQUESTS" -> "Too many password reset attempts. Please try again later"
            "ERROR_OPERATION_NOT_ALLOWED" -> "Password reset is currently unavailable. Please contact support"
            "ERROR_NETWORK_REQUEST_FAILED" -> "Network error. Please check your internet connection"
            "ERROR_INVALID_API_KEY" -> "Configuration error. Please contact support"
            "ERROR_WEAK_PASSWORD" -> "Password is too weak. Use at least 6 characters"
            else -> "An error occurred. Please try again"
        }
    }

    companion object {
        private const val TAG = "AuthRepository"
    }
}
