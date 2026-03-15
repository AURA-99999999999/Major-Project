package com.aura.music.data.model

/**
 * Trending songs grouped by language (matches web app.js implementation)
 */
data class TrendingData(
    val hindiSongs: List<Song> = emptyList(),
    val teluguSongs: List<Song> = emptyList(),
    val englishSongs: List<Song> = emptyList()
) {
    /**
     * Returns all trending songs combined (for backward compatibility)
     */
    fun getAllSongs(): List<Song> {
        return hindiSongs + teluguSongs + englishSongs
    }
    
    /**
     * Returns total count of trending songs
     */
    fun getTotalCount(): Int {
        return hindiSongs.size + teluguSongs.size + englishSongs.size
    }
}
