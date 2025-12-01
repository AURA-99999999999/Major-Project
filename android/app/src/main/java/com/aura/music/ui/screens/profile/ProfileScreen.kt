package com.aura.music.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.aura.music.player.MusicService
import com.aura.music.ui.theme.DarkBackground
import com.aura.music.ui.theme.DarkSurface
import com.aura.music.ui.theme.TextPrimary

@Composable
fun ProfileScreen(
    musicService: MusicService?,
    onNavigateBack: () -> Unit
) {
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
                .padding(16.dp)
        ) {
            Text(
                text = "Profile",
                color = TextPrimary
            )
            // TODO: Implement profile view
        }
    }
}

