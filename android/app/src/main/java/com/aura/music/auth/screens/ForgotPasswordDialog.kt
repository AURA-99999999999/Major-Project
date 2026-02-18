package com.aura.music.auth.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.aura.music.auth.state.PasswordResetState

/**
 * Forgot Password Dialog
 *
 * Responsibility:
 * - Collect email from user
 * - Validate email format
 * - Display loading/error/success states
 * - Trigger password reset via callback
 *
 * Usage:
 * Call this composable when user clicks "Forgot Password?" button
 * Pass the onSendResetEmail callback to handle the actual API call
 *
 * @param isShowing Whether the dialog should be visible
 * @param passwordResetState Current state of password reset operation
 * @param onSendResetEmail Callback to send reset email (receives email as parameter)
 * @param onDismiss Callback to handle dialog dismissal
 */
@Composable
fun ForgotPasswordDialog(
    isShowing: Boolean,
    passwordResetState: PasswordResetState,
    onSendResetEmail: (email: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var email by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf("") }
    val context = LocalContext.current

    if (!isShowing) {
        return
    }

    // Auto-hide on success and show toast
    if (passwordResetState is PasswordResetState.Success) {
        Toast.makeText(context, passwordResetState.message, Toast.LENGTH_LONG).show()
        email = ""
        emailError = ""
        onDismiss()
        return
    }

    AlertDialog(
        onDismissRequest = {
            if (passwordResetState !is PasswordResetState.Loading) {
                email = ""
                emailError = ""
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Reset Password",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = "Enter your registered email address to receive a password reset link.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Email input field
                OutlinedTextField(
                    value = email,
                    onValueChange = { newValue ->
                        email = newValue
                        // Clear error when user starts typing
                        if (emailError.isNotEmpty()) {
                            emailError = ""
                        }
                    },
                    label = { Text("Email Address") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    enabled = passwordResetState !is PasswordResetState.Loading,
                    isError = emailError.isNotEmpty(),
                    supportingText = {
                        if (emailError.isNotEmpty()) {
                            Text(
                                text = emailError,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )

                // Error message from password reset state
                if (passwordResetState is PasswordResetState.Error) {
                    Text(
                        text = passwordResetState.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                // Loading indicator
                if (passwordResetState is PasswordResetState.Loading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Validate email before sending
                    if (email.isBlank()) {
                        emailError = "Email cannot be empty"
                        return@Button
                    }

                    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                        emailError = "Invalid email address format"
                        return@Button
                    }

                    // Send reset email
                    onSendResetEmail(email)
                },
                enabled = passwordResetState !is PasswordResetState.Loading && email.isNotBlank(),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                if (passwordResetState is PasswordResetState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Send Reset Link")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (passwordResetState !is PasswordResetState.Loading) {
                        email = ""
                        emailError = ""
                        onDismiss()
                    }
                },
                enabled = passwordResetState !is PasswordResetState.Loading
            ) {
                Text("Cancel")
            }
        },
        modifier = modifier
    )
}
