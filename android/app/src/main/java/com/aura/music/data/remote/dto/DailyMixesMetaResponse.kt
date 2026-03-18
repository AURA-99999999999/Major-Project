package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Metadata for a single daily mix (no songs)
 */
data class DailyMixMetaDto(
    @SerializedName("key")
    val key: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("icon")
    val icon: String?,
    @SerializedName("color")
    val color: String? // Hex color string, e.g. #9B87F5
)

/**
 * Response for /api/daily-mixes/meta
 */
data class DailyMixesMetaResponse(
    @SerializedName("mixes")
    val mixes: List<DailyMixMetaDto>
)
