package com.aura.music.data.model

/**
 * Artist model for search results
 */
data class Artist(
    val browseId: String,
    val name: String,
    val thumbnail: String?,
    val subscribers: String? // e.g., "10M subscribers"
)
