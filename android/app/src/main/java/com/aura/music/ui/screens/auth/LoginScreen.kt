package com.aura.music.ui.screens.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.aura.music.ui.theme.AuraTheme
import com.aura.music.R
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.DarkSurfaceVariant
import com.aura.music.ui.theme.Error
import com.aura.music.ui.theme.Primary
import com.aura.music.ui.theme.TextPrimary
import com.aura.music.ui.theme.TextSecondary
import com.aura.music.ui.viewmodel.AuthViewModel
import com.aura.music.ui.viewmodel.ViewModelFactory

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit,
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = viewModel(factory = ViewModelFactory.create(LocalContext.current.applicationContext as android.app.Application))
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    // Use collectAsState to reactively observe state changes
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Initialize Google Sign-In client with Web Client ID from Firebase
    // IMPORTANT: Must use Web Client ID (client_type 3), not Android Client ID
    val googleSignInClient = remember {
        val webClientId = context.getString(R.string.default_web_client_id)
        
        // Log the client ID being used (for debugging)
        android.util.Log.d("LoginScreen", "Initializing Google Sign-In with Web Client ID: $webClientId")
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId) // Web Client ID from Firebase (client_type 3)
            .requestEmail()
            .requestProfile() // Request profile information (optional but useful)
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Handle Google Sign-In result
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Check if result was successful and has data
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            viewModel.handleGoogleSignInResult(task)
        } else {
            // User cancelled or result failed
            android.util.Log.d("LoginScreen", "Google Sign-In cancelled or failed. Result code: ${result.resultCode}")
            viewModel.handleGoogleSignInCancellation()
        }
    }

    // Function to trigger Google Sign-In
    val signInWithGoogle = {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    // Check if user is already signed in when screen loads (auto-login)
    LaunchedEffect(Unit) {
        viewModel.checkAuthState()
    }

    // Navigate to home when login succeeds (using LaunchedEffect to prevent multiple navigations)
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            // Small delay to ensure UI state is settled before navigation
            kotlinx.coroutines.delay(100)
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top icon + welcome texts
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                        .padding(16.dp),
                    color = DarkSurfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = "App Logo",
                        tint = Primary,
                        modifier = Modifier
                            .height(40.dp)
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Welcome!!",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Sign in to continue",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }

            // Card with form
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "Email",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = { Text("Email") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Email"
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = Primary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Password",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Password") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Password"
                            )
                        },
                        trailingIcon = {
                            val icon =
                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility
                            Icon(
                                imageVector = icon,
                                contentDescription = "Toggle password visibility",
                                modifier = Modifier.clickable {
                                    isPasswordVisible = !isPasswordVisible
                                }
                            )
                        },
                        visualTransformation = if (isPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = TextSecondary,
                            cursorColor = Primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Forgot Password?",
                        color = Primary,
                        modifier = Modifier
                            .align(Alignment.End)
                            .clickable { /* TODO: forgot password flow */ },
                        fontSize = 12.sp
                    )

                    val errorMessage = uiState.error
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorMessage,
                            color = Error,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            // Use Firebase Auth loginOrRegister method
                            // This will attempt sign in first, and create account if user doesn't exist
                            viewModel.loginOrRegister(email, password)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Primary
                        ),
                        enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank()
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(color = TextPrimary)
                        } else {
                            Text(
                                text = "Continue",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Bottom section: sign up + google
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Don't have an account? ",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Sign Up",
                        color = Primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onNavigateToRegister)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Or continue with",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(
                            enabled = !uiState.isLoading,
                            onClick = signInWithGoogle
                        ),
                    color = androidx.compose.ui.graphics.Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google",
                            modifier = Modifier.height(20.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    AuraTheme {
        LoginScreen(
            onNavigateToRegister = {},
            onLoginSuccess = {}
        )
    }
}

