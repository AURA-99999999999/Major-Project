package com.aura.music.data.model

/**
 * Data models for Listening Insights analytics
 */

data class ListeningInsights(
    val artistDistribution: List<ArtistListeningData> = emptyList(),
    val weeklyActivity: List<DailyListeningData> = emptyList(),
    val timeOfDayPattern: Map<String, Int> = emptyMap(),
    val topTracks: List<TrackListeningData> = emptyList(),
    val totalPlays: Int = 0,
    val uniqueArtists: Int = 0,
    val lastUpdated: Long = 0L,
    val isEmpty: Boolean = true
)

data class ArtistListeningData(
    val artistName: String,
    val playCount: Int,
    val percentage: Float,
    val color: Long = 0xFF6200EE
) {
    companion object {
        private val colors = listOf(
            0xFFE53935,  // Vibrant Red
            0xFF1E88E5,  // Bright Blue
            0xFFFB8C00,  // Deep Orange
            0xFF43A047,  // Forest Green
            0xFF8E24AA,  // Deep Purple
            0xFF00ACC1,  // Cyan
            0xFFC0CA33   // Lime Green
        )
        
        fun getColorForIndex(index: Int): Long {
            return colors[index % colors.size]
        }
    }
}

data class DailyListeningData(
    val dayOfWeek: String,  // "Mon", "Tue", etc.
    val playCount: Int,
    val normalized: Float  // 0-1 for bar height
)

data class TrackListeningData(
    val videoId: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val normalized: Float  // 0-1 for bar width
)

data class TimeOfDayData(
    val period: String,
    val playCount: Int,
    val percentage: Float,
    val color: Long = 0xFF6200EE
) {
    companion object {
        val periods = listOf(
            "Morning" to 0xFFFB8C00,    // 6-12 Deep Orange
            "Afternoon" to 0xFF1E88E5,  // 12-18 Bright Blue
            "Evening" to 0xFF8E24AA,    // 18-24 Deep Purple
            "Night" to 0xFF37474F       // 0-6 Dark Blue Grey
        )
    }
}

/**
 * UI state for Listening Insights screen
 */
sealed class InsightsUiState {
    data object Loading : InsightsUiState()
    data class Success(val insights: ListeningInsights) : InsightsUiState()
    data class Error(val message: String) : InsightsUiState()
    data object Empty : InsightsUiState()
}
