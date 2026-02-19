package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

// Album DTO
data class AlbumDto(
    @SerializedName("browseId")
    val browseId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("artists")
    val artists: List<String>?,
    @SerializedName("thumbnail")
    val thumbnail: String?,
    @SerializedName("year")
    val year: String?,
    @SerializedName("type")
    val type: String?
)

// Artist DTO
data class ArtistDto(
    @SerializedName("browseId")
    val browseId: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("thumbnail")
    val thumbnail: String?,
    @SerializedName("subscribers")
    val subscribers: String?
)

// Playlist Search Result DTO
data class PlaylistSearchDto(
    @SerializedName("browseId")
    val browseId: String?,
    @SerializedName("playlistId")
    val playlistId: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("author")
    val author: String?,
    @SerializedName("thumbnail")
    val thumbnail: String?,
    @SerializedName("itemCount")
    val itemCount: String?
)

// Search Response with all categories
data class SearchResponseDto(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("songs")
    val songs: List<SongDto>?,
    @SerializedName("albums")
    val albums: List<AlbumDto>?,
    @SerializedName("artists")
    val artists: List<ArtistDto>?,
    @SerializedName("playlists")
    val playlists: List<PlaylistSearchDto>?,
    @SerializedName("count")
    val count: Int?,
    @SerializedName("query")
    val query: String?,
    @SerializedName("cached")
    val cached: Boolean?,
    @SerializedName("error")
    val error: String?
)

// Search Suggestions Response
data class SearchSuggestionsDto(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("suggestions")
    val suggestions: List<String>?,
    @SerializedName("error")
    val error: String?
)
