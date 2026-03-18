package com.aura.music.data.model

import androidx.compose.ui.graphics.Color
import com.aura.music.data.model.Song

/**
 * UI model for a daily mix card (metadata + songs)
 */
data class MixCardData(
    val key: String,
    val name: String,
    val description: String,
    val icon: String,
    val color: Color,
    val songs: List<Song>
)
