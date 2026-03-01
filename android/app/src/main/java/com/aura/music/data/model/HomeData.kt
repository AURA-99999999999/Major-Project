package com.aura.music.data.model

data class HomeData(
    val trending: List<Song> = emptyList(),
    val trendingPlaylists: List<YTMusicPlaylist> = emptyList(),
    val moodCategories: List<MoodCategory> = emptyList(),
    val moodPlaylists: List<YTMusicPlaylist> = emptyList(),
    val selectedMoodTitle: String = "",
    val recommendations: List<Song> = emptyList(),
    val collaborativeRecommendations: CollaborativeSection? = null,
    val collaborative: List<Song> = emptyList()
)

data class CollaborativeSection(
    val title: String = "From listeners like you",
    val tracks: List<Song> = emptyList(),
    val count: Int = 0
)

