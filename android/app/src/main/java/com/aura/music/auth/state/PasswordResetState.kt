package com.aura.music.auth.state

/**
 * Sealed class representing the state of password reset operations
 *
 * States:
 * - Idle: Initial state, not performing any operation
 * - Loading: Request is in progress
 * - Success: Password reset email was sent successfully
 * - Error: An error occurred during the operation
 */
sealed class PasswordResetState {
    /**
     * Initial/idle state - no operation in progress
     */
    object Idle : PasswordResetState()

    /**
     * Loading state - request is in progress
     */
    object Loading : PasswordResetState()

    /**
     * Success state - password reset email sent
     * @param message Success message to display to user
     */
    data class Success(val message: String = "Password reset link sent to your email. Please check your inbox.") : PasswordResetState()

    /**
     * Error state - operation failed
     * @param message User-friendly error message
     */
    data class Error(val message: String) : PasswordResetState()
}
