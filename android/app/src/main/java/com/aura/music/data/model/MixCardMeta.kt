package com.aura.music.data.model

import androidx.compose.ui.graphics.Color
import com.aura.music.data.model.Song

/**
 * UI model for a daily mix card (metadata only, no songs)
 */
import com.google.gson.annotations.SerializedName

data class MixCardMeta(
    @SerializedName("type")
    val key: String? = null,
    val name: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val color: Color? = null
)
