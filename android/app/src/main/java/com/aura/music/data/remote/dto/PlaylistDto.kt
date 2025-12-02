package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PlaylistDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String?,
    @SerializedName("userId")
    val userId: String,
    @SerializedName("songs")
    val songs: List<SongDto>?,
    @SerializedName("coverImage")
    val coverImage: String?,
    @SerializedName("createdAt")
    val createdAt: String?
)

