package com.aura.music.data.remote.dto

import com.aura.music.data.model.JioSaavnPlaylist
import com.aura.music.data.model.Song
import com.google.gson.annotations.SerializedName

data class YTMusicPlaylistDto(
    @SerializedName("playlistId") val playlistId: String,
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = "",
    @SerializedName("thumbnail") val thumbnail: String? = "",
    @SerializedName("author") val author: String? = "YouTube Music",
    @SerializedName("songCount") val songCount: Int? = 0
) {
    fun toJioSaavnPlaylist() = JioSaavnPlaylist(
        id = playlistId,
        name = title,
        image = thumbnail ?: "",
        url = playlistId,
        song_count = songCount ?: 0
    )
}

data class TrendingPlaylistsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("playlists") val playlists: List<YTMusicPlaylistDto> = emptyList(),
    @SerializedName("count") val count: Int
)

data class MoodPlaylistsResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("playlists") val playlists: List<JioSaavnPlaylistDto> = emptyList(),
    @SerializedName("count") val count: Int = 0
)

/**
 * JioSaavn playlist DTO from mood playlist search
 * Response format from backend /api/home/mood-playlists endpoint
 */
data class JioSaavnPlaylistDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("song_count") val songsCount: Int? = null
) {
    fun toJioSaavnPlaylist() = JioSaavnPlaylist(
        id = id.orEmpty(),
        name = name.orEmpty(),
        image = image.orEmpty(),
        url = url.orEmpty(),
        song_count = songsCount ?: 0
    )
}

data class PlaylistSongsResponseDto(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("data") val data: List<PlaylistSongDto>? = null,
    @SerializedName("error") val error: String? = null
)

data class PlaylistSongDto(
    @SerializedName("title") val title: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("singers") val singers: String? = null,
    @SerializedName("starring") val starring: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("year") val year: String? = null
) {
    fun toSong(): Song = Song(
        videoId = url.orEmpty().ifBlank { title.orEmpty() },
        title = title.orEmpty().ifBlank { "Unknown Title" },
        artist = singers.orEmpty().ifBlank { null },
        thumbnail = imageUrl.orEmpty().ifBlank { null },
        duration = duration.orEmpty().ifBlank { null },
        url = url.orEmpty().ifBlank { null },
        album = album.orEmpty().ifBlank { null },
        language = language.orEmpty().ifBlank { "unknown" },
        year = year.orEmpty().ifBlank { "unknown" }
    )
}

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

data class JioSaavnPlaylistDetailResponse(
    @SerializedName("listid") val listId: String? = null,
    @SerializedName("listname") val listName: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("songs") val songs: List<JioSaavnSongDto>? = null
)

data class JioSaavnSongDto(
    @SerializedName("id") val id: String,
    @SerializedName("song") val song: String,
    @SerializedName("primary_artists") val primaryArtists: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("duration") val duration: String? = null,
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("perma_url") val permaUrl: String? = null,
    @SerializedName("album") val album: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("year") val year: String? = null
) {
    fun toSong(): Song = Song(
        videoId = id,
        title = song,
        artist = primaryArtists,
        thumbnail = image,
        duration = duration,
        url = mediaUrl ?: permaUrl,
        album = album,
        language = language ?: "unknown",
        year = year ?: "unknown"
    )
}
