package com.aura.music.data.remote.dto

import com.aura.music.data.model.YTMusicPlaylist
import com.google.gson.annotations.SerializedName

data class YTMusicPlaylistDto(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = "",
    @SerializedName("thumbnail") val thumbnail: String? = "",
    @SerializedName("author") val author: String? = "YouTube Music",
    @SerializedName("songCount") val songCount: Int? = 0
) {
    fun toYTMusicPlaylist() = YTMusicPlaylist(
        playlistId = playlistId,
        title = title,
        description = description ?: "",
        thumbnail = thumbnail ?: "",
        author = author ?: "YouTube Music",
        songCount = songCount ?: 0
    )
}

data class TrendingPlaylistsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("playlists") val playlists: List<YTMusicPlaylistDto>,
    @SerializedName("count") val count: Int
)

data class MoodPlaylistsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("playlists") val playlists: List<YTMusicPlaylistDto>,
    @SerializedName("count") val count: Int
)

data class YTMusicPlaylistDetailResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("playlist") val playlist: PlaylistInfoDto,
    @SerializedName("songs") val songs: List<SongDto>,
    @SerializedName("count") val count: Int
)

data class PlaylistInfoDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = "",
    @SerializedName("thumbnail") val thumbnail: String? = "",
    @SerializedName("author") val author: String? = "YouTube Music",
    @SerializedName("songCount") val songCount: Int
)
