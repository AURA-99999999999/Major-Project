package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Metadata for a single daily mix (no songs)
 */
data class DailyMixMetaDto(
    @SerializedName("id")
    val id: String? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("emoji")
    val emoji: String? = null,
    @SerializedName("color")
    val color: String? = null // Hex color string, e.g. #9B87F5
)
