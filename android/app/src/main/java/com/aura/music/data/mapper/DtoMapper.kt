package com.aura.music.data.mapper

import com.aura.music.data.model.Album
import com.aura.music.data.model.Artist
import com.aura.music.data.model.Playlist
import com.aura.music.data.model.PlaylistSearchResult
import com.aura.music.data.model.SearchResults
import com.aura.music.data.model.Song
import com.aura.music.data.model.User
import com.aura.music.data.remote.dto.AlbumDto
import com.aura.music.data.remote.dto.ArtistDto
import com.aura.music.data.remote.dto.PlaylistDto
import com.aura.music.data.remote.dto.PlaylistSearchDto
import com.aura.music.data.remote.dto.SaavnArtistImage
import com.aura.music.data.remote.dto.SaavnArtistItem
import com.aura.music.data.remote.dto.SearchResponseDto
import com.aura.music.data.remote.dto.SongDto
import com.aura.music.data.remote.dto.UserDto
import com.google.gson.JsonElement

fun SongDto.toSong(): Song {
    return Song(
        videoId = videoId,
        title = title,
        artist = artist ?: singers,
        artists = artists,
        thumbnail = thumbnail,
        duration = duration,
        url = url,
        album = album.toAlbumName(),
        artistId = artistId,
        playCount = playCount ?: 0,
        language = language ?: "unknown",
        year = year ?: "unknown",
        starring = starring
    )
}

private fun JsonElement?.toAlbumName(): String? {
    if (this == null || isJsonNull) {
        return null
    }

    if (isJsonPrimitive) {
        return asString
    }

    if (isJsonObject) {
        val nameElement = asJsonObject.get("name")
        if (nameElement != null && nameElement.isJsonPrimitive) {
            return nameElement.asString
        }
    }

    return null
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
        "starring" to (starring ?: ""),
        "thumbnail" to (thumbnail ?: ""),
        "duration" to (duration ?: ""),
        "url" to (url ?: ""),
        "album" to (album ?: ""),
        "artistId" to (artistId ?: "")
    )
}

// Album mappers
fun AlbumDto.toAlbum(): Album {
    return Album(
        browseId = browseId,
        title = title,
        artists = artists ?: emptyList(),
        thumbnail = thumbnail,
        year = year,
        type = type
    )
}

fun List<AlbumDto>.toAlbums(): List<Album> {
    return map { it.toAlbum() }
}

// Artist mappers
fun ArtistDto.toArtist(): Artist {
    return Artist(
        browseId = browseId,
        name = name,
        thumbnail = thumbnail,
        subscribers = subscribers
    )
}

fun List<ArtistDto>.toArtists(): List<Artist> {
    return map { it.toArtist() }
}

// Playlist search result mappers
fun PlaylistSearchDto.toPlaylistSearchResult(): PlaylistSearchResult {
    return PlaylistSearchResult(
        browseId = browseId ?: playlistId,
        playlistId = playlistId,
        title = title,
        author = author.orEmpty(),
        thumbnail = thumbnail,
        itemCount = itemCount
    )
}

fun List<PlaylistSearchDto>.toPlaylistSearchResults(): List<PlaylistSearchResult> {
    return map { it.toPlaylistSearchResult() }
}

// Search results mapper
fun SearchResponseDto.toSearchResults(): SearchResults {
    return SearchResults(
        songs = songs?.toSongs() ?: emptyList(),
        albums = albums?.toAlbums() ?: emptyList(),
        artists = artists?.toArtists() ?: emptyList(),
        playlists = playlists?.toPlaylistSearchResults() ?: emptyList(),
        count = count ?: 0,
        query = query ?: ""
    )
}

// JioSaavn Artist Mappers

/**
 * Get the best quality image from JioSaavn artist images
 * Priority: 500x500 > 150x150 > 50x50
 */
fun getBestArtistImage(images: List<SaavnArtistImage>): String {
    return images
        .sortedByDescending { 
            it.quality.substringBefore("x").toIntOrNull() ?: 0 
        }
        .firstOrNull()?.url ?: ""
}

/**
 * Map JioSaavn artist item to app Artist model
 */
fun SaavnArtistItem.toArtist(): Artist {
    return Artist(
        browseId = id,
        name = name,
        thumbnail = getBestArtistImage(image),
        subscribers = role // Using role as subscriber info for JioSaavn artists
    )
}

/**
 * Map list of JioSaavn artist items to app Artist models
 */
fun List<SaavnArtistItem>.toSaavnArtists(): List<Artist> {
    return map { it.toArtist() }
}

