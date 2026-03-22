package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class DailyMixSongDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String? = null,
    @SerializedName("image")
    val image: String? = null,
    @SerializedName("stream_url")
    val streamUrl: String? = null
)

data class MixContainer(
    @SerializedName("id")
    val id: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("emoji")
    val emoji: String? = null,
    @SerializedName("color")
    val color: String? = null,
    @SerializedName("songs")
    val songs: List<DailyMixSongDto> = emptyList(),
    @SerializedName("count")
    val count: Int = 0
)
