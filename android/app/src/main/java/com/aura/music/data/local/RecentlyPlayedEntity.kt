package com.aura.music.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "recently_played",
    primaryKeys = ["userId", "id"]
)
data class RecentlyPlayedEntity(
    val userId: String,
    val id: String,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val audioUrl: String?,
    val playedAt: Long
)
