package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class HomeResponseDto(
    @SerializedName("trending")
    val trending: List<SongDto>? = null,
    @SerializedName("recommendations")
    val recommendations: List<SongDto>? = null,
    @SerializedName("collaborative_recommendations")
        val collaborativeRecommendations: CollaborativeSectionDto? = null,
        @SerializedName("collaborative")
        val collaborative: List<SongDto>? = null
)

data class CollaborativeSectionDto(
    @SerializedName("title")
    val title: String? = "From listeners like you",
    @SerializedName("tracks")
    val tracks: List<SongDto>? = null,
    @SerializedName("count")
    val count: Int? = 0
)
