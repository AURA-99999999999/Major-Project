package com.aura.music.auth.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.music.auth.state.AuthState

/**
 * Home Screen UI Component
 *
 * Stateless composable that displays authenticated user's home
 * @param authState Current authentication state from ViewModel
 * @param onSignout Callback when user clicks logout
 */
@Composable
fun HomeScreen(
    authState: AuthState,
    onSignout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Welcome Message
            Text(
                text = "Welcome!",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // User Email
            if (authState is AuthState.Authenticated) {
                Text(
                    text = "Logged in as: ${authState.email}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 48.dp)
                )

                Text(
                    text = "User ID: ${authState.userId}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Signout Button
            Button(
                onClick = onSignout,
                modifier = Modifier.padding(top = 32.dp)
            ) {
                Text("Sign Out")
            }
        }
    }
}
