package com.aura.music.auth.state

/**
 * Sealed class representing different authentication states
 */
sealed class AuthState {
    /**
     * Initial loading state while checking authentication
     */
    object Loading : AuthState()

    /**
     * User is authenticated
     * @param userId The unique identifier of the authenticated user
     * @param email The email of the authenticated user
     */
    data class Authenticated(val userId: String, val email: String) : AuthState()

    /**
     * User is not authenticated
     */
    object Unauthenticated : AuthState()

    /**
     * An error occurred during authentication
     * @param message Error message to display to user
     */
    data class Error(val message: String) : AuthState()
}
