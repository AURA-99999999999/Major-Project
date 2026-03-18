package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Metadata for a single daily mix (no songs)
 */
data class DailyMixMetaDto(
    @SerializedName("type")
    val key: String? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("description")
    val description: String? = null,
    @SerializedName("icon")
    val icon: String? = null,
    @SerializedName("color")
    val color: String? = null // Hex color string, e.g. #9B87F5
)
