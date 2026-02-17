package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RecommendationResponse(
    @SerializedName("count")
    val count: Int,
    @SerializedName("results")
    val results: List<SongDto>,
    @SerializedName("source")
    val source: String
)
