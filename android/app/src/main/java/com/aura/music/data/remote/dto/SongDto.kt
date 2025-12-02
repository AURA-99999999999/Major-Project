package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SongDto(
    @SerializedName("videoId")
    val videoId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String?,
    @SerializedName("artists")
    val artists: List<String>?,
    @SerializedName("thumbnail")
    val thumbnail: String?,
    @SerializedName("duration")
    val duration: String?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("album")
    val album: String?,
    @SerializedName("artistId")
    val artistId: String?
)

