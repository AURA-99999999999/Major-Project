package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Generic API wrapper used by the Flask backend.
 *
 * Every endpoint returns a `success` flag plus one or more payload fields. This DTO keeps the
 * parsing resilient by exposing optional slots for list/data responses plus the most common
 * collections returned by the server (playlists, songs, user, etc).
 */
data class ApiResponse<T>(
    @SerializedName("success")
    val success: Boolean = false,
    @SerializedName("results")
    val results: List<T>? = null,
    @SerializedName("data")
    val data: T? = null,
    @SerializedName("playlist")
    val playlist: PlaylistDto? = null,
    @SerializedName("playlists")
    val playlists: List<PlaylistDto>? = null,
    @SerializedName("songs")
    val songs: List<SongDto>? = null,
    @SerializedName("user")
    val user: UserDto? = null,
    @SerializedName("error")
    val error: String? = null,
    @SerializedName("count")
    val count: Int? = null,
    @SerializedName("message")
    val message: String? = null
)

