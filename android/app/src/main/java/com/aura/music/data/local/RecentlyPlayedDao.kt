package com.aura.music.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentlyPlayedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: RecentlyPlayedEntity)

    @Query("DELETE FROM recently_played WHERE userId = :userId AND id = :id")
    suspend fun deleteTrackById(userId: String, id: String)

    @Query("SELECT * FROM recently_played WHERE userId = :userId ORDER BY playedAt DESC LIMIT :limit")
    fun getRecentTracks(userId: String, limit: Int = 6): Flow<List<RecentlyPlayedEntity>>

    @Query("DELETE FROM recently_played WHERE userId = :userId AND id NOT IN (SELECT id FROM recently_played WHERE userId = :userId ORDER BY playedAt DESC LIMIT 6)")
    suspend fun clearOverflow(userId: String)

    @Query("DELETE FROM recently_played WHERE userId = :userId")
    suspend fun clearUserHistory(userId: String)
}
