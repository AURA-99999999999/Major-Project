package com.aura.music.data.model

/**
 * Album model for search results
 */
data class Album(
    val browseId: String,
    val title: String,
    val artists: List<String>,
    val thumbnail: String?,
    val year: String?,
    val type: String? // "Album", "EP", "Single"
)
