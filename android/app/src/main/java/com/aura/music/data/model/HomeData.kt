package com.aura.music.data.model

data class HomeData(
    val trendingData: TrendingData = TrendingData(),
    val trending: List<Song> = emptyList(), // Deprecated - kept for backward compatibility
    val trendingPlaylists: List<JioSaavnPlaylist> = emptyList(),
    val moodCategories: List<MoodCategory> = emptyList(),
    val moodPlaylists: List<JioSaavnPlaylist> = emptyList(),
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

