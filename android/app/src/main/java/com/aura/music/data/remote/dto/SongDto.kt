package com.aura.music.data.remote.dto

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class SongDto(
    @SerializedName("videoId")
    val videoId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artist")
    val artist: String?,
    @SerializedName("singers")
    val singers: String?,
    @SerializedName("artists")
    val artists: List<String>?,
    @SerializedName("thumbnail")
    val thumbnail: String?,
    @SerializedName("duration")
    val duration: String?,
    @SerializedName("url")
    val url: String?,
    @SerializedName("album")
    val album: JsonElement?,
    @SerializedName("artistId")
    val artistId: String?,
    @SerializedName("play_count")
    val playCount: Int?,
    @SerializedName("language")
    val language: String?,
    @SerializedName("year")
    val year: String?,
    @SerializedName("starring")
    val starring: String?
)

