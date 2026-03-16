package com.aura.music.player

import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.RecentlyPlayedRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

enum class QueueContext {
    PLAYLIST,
    ALBUM,
    ARTIST,
    RADIO,
    SEARCH,
    DAILY_MIX,
    MOOD_MIX,
    RECENTLY_PLAYED,
    LIKED_SONGS,
    RECOMMENDATIONS,
    SINGLE_SONG
}

class QueueManager(
    private val repository: MusicRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository
) {
    companion object {
        const val DEFAULT_QUEUE_LENGTH = 30
        private const val MAX_SONGS_PER_ARTIST = 2
        private const val MAX_SONGS_PER_ALBUM = 3
    }

    fun resolveContext(source: String, sourceList: List<Song>): QueueContext {
        val normalized = source.trim().lowercase()
        return when {
            normalized.startsWith("playlist") || normalized.contains("ytmusic_playlist") || normalized.contains("yt_playlist") -> QueueContext.PLAYLIST
            normalized.contains("album") -> QueueContext.ALBUM
            normalized.contains("artist") -> QueueContext.ARTIST
            normalized.contains("radio") -> QueueContext.RADIO
            normalized.startsWith("search") -> QueueContext.SEARCH
            normalized.startsWith("daily_mix") -> QueueContext.DAILY_MIX
            normalized.startsWith("mood") -> QueueContext.MOOD_MIX
            normalized.startsWith("recent") -> QueueContext.RECENTLY_PLAYED
            normalized.startsWith("liked") -> QueueContext.LIKED_SONGS
            normalized.startsWith("recommend") || normalized.contains("smart_autoplay") -> QueueContext.RECOMMENDATIONS
            sourceList.size <= 1 -> QueueContext.SINGLE_SONG
            else -> QueueContext.SINGLE_SONG
        }
    }

    suspend fun generateQueue(
        selectedSong: Song,
        context: QueueContext,
        sourceList: List<Song>,
        startIndex: Int,
        desiredSize: Int = DEFAULT_QUEUE_LENGTH
    ): List<Song> {
        val normalizedSource = sourceList.distinctBy { it.videoId }
        val safeIndex = if (normalizedSource.isEmpty()) 0 else startIndex.coerceIn(0, normalizedSource.lastIndex)
        val rotated = rotateFromIndex(normalizedSource, safeIndex)

        return when (context) {
            QueueContext.PLAYLIST,
            QueueContext.LIKED_SONGS -> buildLoopingQueue(rotated, desiredSize)

            QueueContext.ALBUM -> if (rotated.isNotEmpty()) rotated else listOf(selectedSong)

            QueueContext.DAILY_MIX,
            QueueContext.MOOD_MIX -> {
                val base = if (rotated.isNotEmpty()) rotated else listOf(selectedSong)
                if (base.size >= desiredSize) {
                    base.take(desiredSize)
                } else {
                    val smartTail = buildSmartQueue(
                        selectedSong = selectedSong,
                        context = context,
                        sourceList = base,
                        targetSize = desiredSize - base.size,
                        existing = base
                    )
                    (base + smartTail).distinctBy { it.videoId }.take(desiredSize)
                }
            }

            QueueContext.SEARCH -> {
                val smartTail = buildSmartQueue(
                    selectedSong = selectedSong,
                    context = context,
                    sourceList = listOf(selectedSong),
                    targetSize = desiredSize - 1,
                    existing = listOf(selectedSong)
                )
                (listOf(selectedSong) + smartTail).take(desiredSize)
            }

            QueueContext.ARTIST,
            QueueContext.RADIO,
            QueueContext.RECENTLY_PLAYED,
            QueueContext.RECOMMENDATIONS,
            QueueContext.SINGLE_SONG -> {
                val seed = if (rotated.isNotEmpty()) rotated else listOf(selectedSong)
                val smartTail = buildSmartQueue(
                    selectedSong = selectedSong,
                    context = context,
                    sourceList = seed,
                    targetSize = desiredSize - 1,
                    existing = listOf(selectedSong)
                )
                (listOf(selectedSong) + smartTail).take(desiredSize)
            }
        }
    }

    suspend fun extendQueue(
        currentQueue: List<Song>,
        currentSong: Song,
        context: QueueContext,
        targetSize: Int = 15
    ): List<Song> {
        if (targetSize <= 0) return emptyList()
        val generated = buildSmartQueue(
            selectedSong = currentSong,
            context = context,
            sourceList = currentQueue,
            targetSize = targetSize,
            existing = currentQueue
        )
        return generated.take(targetSize)
    }

    private suspend fun buildSmartQueue(
        selectedSong: Song,
        context: QueueContext,
        sourceList: List<Song>,
        targetSize: Int,
        existing: List<Song>
    ): List<Song> {
        if (targetSize <= 0) return emptyList()

        val pools = loadCandidatePools(sourceList)
        val selectedArtist = artistKey(selectedSong)
        val selectedAlbum = albumKey(selectedSong)
        val selectedLanguage = languageKey(selectedSong)

        val allCandidates = pools.songs
            .filter { it.videoId != selectedSong.videoId }
            .distinctBy { it.videoId }

        val sameArtist = allCandidates.filter { artistKey(it) == selectedArtist }
        val sameAlbum = allCandidates.filter { selectedAlbum.isNotBlank() && albumKey(it) == selectedAlbum }
        val sameLanguage = allCandidates.filter { selectedLanguage.isNotBlank() && languageKey(it) == selectedLanguage }
        val relatedArtists = allCandidates.filter {
            val artist = artistKey(it)
            artist.isNotBlank() && artist != selectedArtist && (artist in pools.favoriteArtists || artist in pools.recentArtists)
        }

        val recommendedTracks = allCandidates

        val ordered = mutableListOf<Song>()
        when (context) {
            QueueContext.SEARCH -> {
                ordered += sameArtist
                ordered += sameLanguage
                ordered += recommendedTracks
            }
            QueueContext.ARTIST -> {
                ordered += sameArtist
                ordered += relatedArtists
                ordered += recommendedTracks
            }
            QueueContext.RADIO -> {
                ordered += sameArtist
                ordered += relatedArtists
                ordered += sameLanguage
                ordered += recommendedTracks
            }
            QueueContext.RECENTLY_PLAYED -> {
                ordered += relatedArtists
                ordered += sameAlbum
                ordered += recommendedTracks
            }
            QueueContext.SINGLE_SONG -> {
                ordered += sameArtist
                ordered += sameLanguage
                ordered += recommendedTracks
            }
            QueueContext.RECOMMENDATIONS -> {
                ordered += recommendedTracks
                ordered += sameArtist
                ordered += sameLanguage
            }
            QueueContext.DAILY_MIX,
            QueueContext.MOOD_MIX -> {
                ordered += recommendedTracks
                ordered += sameArtist
            }
            QueueContext.PLAYLIST,
            QueueContext.ALBUM,
            QueueContext.LIKED_SONGS -> {
                ordered += recommendedTracks
            }
        }

        val uniqueOrdered = ordered.distinctBy { it.videoId }
        return enforceDiversity(
            candidates = uniqueOrdered,
            existing = existing,
            limit = targetSize
        )
    }

    private suspend fun loadCandidatePools(sourceList: List<Song>): CandidatePools = coroutineScope {
        val recDeferred = async { repository.fetchUserRecommendations() }
        val collabDeferred = async {
            repository.fetchCollaborativeRecommendations(limit = 20)
                .getOrNull()
                ?.tracks
                ?: emptyList()
        }
        val trendingDeferred = async { repository.getTrending(limit = 40).getOrDefault(emptyList()) }
        val recentDeferred = async { recentlyPlayedRepository.getRecentTracksSnapshot(limit = 20) }
        val topArtistsDeferred = async {
            repository.getTopArtists(limit = 10).getOrDefault(emptyList()).map { it.name }
        }

        val recommendations = recDeferred.await()
        val collaborative = collabDeferred.await()
        val trending = trendingDeferred.await()
        val recent = recentDeferred.await()
        val topArtists = topArtistsDeferred.await()

        val allSongs = (sourceList + recommendations + collaborative + trending + recent)
            .distinctBy { it.videoId }

        CandidatePools(
            songs = allSongs,
            favoriteArtists = topArtists.map { normalizeText(it) }.filter { it.isNotBlank() }.toSet(),
            recentArtists = recent.map { normalizeText(it.getArtistString()) }.filter { it.isNotBlank() }.toSet()
        )
    }

    private fun enforceDiversity(candidates: List<Song>, existing: List<Song>, limit: Int): List<Song> {
        val artistCounts = mutableMapOf<String, Int>()
        val albumCounts = mutableMapOf<String, Int>()
        val existingIds = existing.map { it.videoId }.toHashSet()

        existing.forEach { song ->
            val artist = artistKey(song)
            val album = albumKey(song)
            if (artist.isNotBlank()) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            }
            if (album.isNotBlank()) {
                albumCounts[album] = (albumCounts[album] ?: 0) + 1
            }
        }

        val picked = mutableListOf<Song>()
        for (candidate in candidates) {
            if (picked.size >= limit) break
            if (candidate.videoId in existingIds) continue

            val artist = artistKey(candidate)
            val album = albumKey(candidate)

            if (artist.isNotBlank() && (artistCounts[artist] ?: 0) >= MAX_SONGS_PER_ARTIST) continue
            if (album.isNotBlank() && (albumCounts[album] ?: 0) >= MAX_SONGS_PER_ALBUM) continue

            picked += candidate
            existingIds += candidate.videoId
            if (artist.isNotBlank()) {
                artistCounts[artist] = (artistCounts[artist] ?: 0) + 1
            }
            if (album.isNotBlank()) {
                albumCounts[album] = (albumCounts[album] ?: 0) + 1
            }
        }

        return picked
    }

    private fun buildLoopingQueue(songs: List<Song>, desiredSize: Int): List<Song> {
        if (songs.isEmpty()) return emptyList()
        if (desiredSize <= songs.size) return songs.take(desiredSize)

        val output = mutableListOf<Song>()
        while (output.size < desiredSize) {
            output += songs
        }
        return output.take(desiredSize)
    }

    private fun rotateFromIndex(songs: List<Song>, index: Int): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val safeIndex = index.coerceIn(0, songs.lastIndex)
        return songs.drop(safeIndex) + songs.take(safeIndex)
    }

    private fun artistKey(song: Song): String {
        return normalizeText(song.getArtistString())
    }

    private fun albumKey(song: Song): String {
        return normalizeText(song.album ?: "")
    }

    private fun languageKey(song: Song): String {
        return normalizeText(song.language)
    }

    private fun normalizeText(value: String): String {
        return value.trim().lowercase()
    }

    private data class CandidatePools(
        val songs: List<Song>,
        val favoriteArtists: Set<String>,
        val recentArtists: Set<String>
    )
}
