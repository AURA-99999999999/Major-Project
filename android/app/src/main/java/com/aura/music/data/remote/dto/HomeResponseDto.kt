package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class HomeResponseDto(
    @SerializedName("trending")
    val trending: List<SongDto>? = null,
    @SerializedName("recommendations")
    val recommendations: List<SongDto>? = null
)
