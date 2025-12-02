package com.aura.music.ui.screens.splash

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aura.music.AuraApplication
import com.aura.music.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var startAnimation by remember { mutableStateOf(false) }
    val alphaAnim = animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(
            durationMillis = 1000
        ), label = ""
    )

    val context = LocalContext.current.applicationContext as AuraApplication

    LaunchedEffect(key1 = true) {
        startAnimation = true
        delay(2000)
        val isLoggedIn = context.authPreferences.isLoggedIn.first()
        if (isLoggedIn) {
            onNavigateToHome()
        } else {
            onNavigateToLogin()
        }
    }

    Splash(alpha = alphaAnim.value)
}

@Composable
fun Splash(alpha: Float) {
    Box(
        modifier = Modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        com.aura.music.ui.theme.DarkBackground,
                        com.aura.music.ui.theme.DarkSurface
                    )
                )
            )
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Aura",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary.copy(alpha = alpha)
        )
    }
}

