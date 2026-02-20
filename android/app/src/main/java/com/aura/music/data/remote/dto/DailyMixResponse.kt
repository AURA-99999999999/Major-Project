package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response object for daily mixes endpoint
 * Returns 4 personalized mixes: Favorites, Similar Artists, Discover, Mood
 */
data class DailyMixResponse(
    @SerializedName("userId")
    val userId: String,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("cached")
    val cached: Boolean,
    
    @SerializedName("mixes")
    val mixes: MixesContainer?
)

/**
 * Container for all 4 mixes
 */
data class MixesContainer(
    @SerializedName("dailyMix1")
    val dailyMix1: MixContainer?,
    
    @SerializedName("dailyMix2")
    val dailyMix2: MixContainer?,
    
    @SerializedName("discoverMix")
    val discoverMix: MixContainer?,
    
    @SerializedName("moodMix")
    val moodMix: MixContainer?
)

/**
 * Individual mix metadata and songs
 */
data class MixContainer(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("songs")
    val songs: List<SongDto>?
)
