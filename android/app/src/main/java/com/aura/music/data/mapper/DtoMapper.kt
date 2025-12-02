package com.aura.music.data.mapper

import com.aura.music.data.model.Playlist
import com.aura.music.data.model.Song
import com.aura.music.data.model.User
import com.aura.music.data.remote.dto.PlaylistDto
import com.aura.music.data.remote.dto.SongDto
import com.aura.music.data.remote.dto.UserDto

fun SongDto.toSong(): Song {
    return Song(
        videoId = videoId,
        title = title,
        artist = artist,
        artists = artists,
        thumbnail = thumbnail,
        duration = duration,
        url = url,
        album = album,
        artistId = artistId
    )
}

fun List<SongDto>.toSongs(): List<Song> {
    return map { it.toSong() }
}

fun PlaylistDto.toPlaylist(): Playlist {
    return Playlist(
        id = id,
        name = name,
        description = description,
        userId = userId,
        songs = songs?.toSongs() ?: emptyList(),
        coverImage = coverImage,
        createdAt = createdAt
    )
}

fun List<PlaylistDto>.toPlaylists(): List<Playlist> {
    return map { it.toPlaylist() }
}

fun UserDto.toUser(): User {
    return User(
        id = id,
        username = username,
        email = email,
        likedSongs = likedSongs?.toSongs() ?: emptyList(),
        recentlyPlayed = recentlyPlayed?.toSongs() ?: emptyList()
    )
}

fun Song.toSongDtoMap(): Map<String, Any> {
    return mapOf(
        "videoId" to videoId,
        "title" to title,
        "artist" to (artist ?: ""),
        "artists" to (artists ?: emptyList<String>()),
        "thumbnail" to (thumbnail ?: ""),
        "duration" to (duration ?: ""),
        "url" to (url ?: ""),
        "album" to (album ?: ""),
        "artistId" to (artistId ?: "")
    )
}

