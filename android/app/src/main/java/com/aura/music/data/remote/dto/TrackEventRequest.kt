package com.aura.music.data.remote.dto

/**
 * Concrete request body for tracking endpoints.
 * Using a data class avoids Retrofit wildcard generic issues with raw Map bodies.
 */
data class TrackEventRequest(
    val uid: String,
    val song: Map<String, Any>
)
