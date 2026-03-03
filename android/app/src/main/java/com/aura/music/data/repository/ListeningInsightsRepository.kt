package com.aura.music.data.repository

import android.util.Log
import com.aura.music.data.model.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Repository for computing and retrieving user listening insights from Firestore.
 * 
 * Responsibilities:
 * - Fetch user's play history from Firestore
 * - Compute artist distribution, weekly activity, time-of-day patterns
 * - Calculate listening metrics and statistics
 * - Handle empty states gracefully
 * 
 * All data is user-specific and derived from users/{userId}/plays collection.
 */
class ListeningInsightsRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    companion object {
        private const val TAG = "ListeningInsightsRepository"
        private const val MIN_PLAYS_FOR_INSIGHTS = 5
        private const val ROLLING_DAYS = 7
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }

    private var cachedWeeklyActivity: List<DailyListeningData>? = null
    private var cachedWeeklyActivityDayStartMs: Long = -1L

    /**
     * Fetch and compute all listening insights for current user.
     * 
     * Flow:
     * 1. Validate user is authenticated
     * 2. Fetch all plays from Firestore
     * 3. Compute multiple analytics metrics
     * 4. Return aggregated insights or empty state
     * 
     * @return ListeningInsights with computed metrics or empty state
     */
    suspend fun getListeningInsights(): Result<ListeningInsights> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = auth.currentUser
                if (currentUser == null) {
                    Log.w(TAG, "getListeningInsights() - No authenticated user")
                    return@withContext Result.success(ListeningInsights(isEmpty = true))
                }

                val userId = currentUser.uid
                Log.d(TAG, "getListeningInsights() userId=$userId")

                val playsSnapshot = firestore.collection("users")
                    .document(userId)
                    .collection("plays")
                    .orderBy("lastPlayedAt", Query.Direction.DESCENDING)
                    .get()
                    .await()

                if (playsSnapshot.isEmpty) {
                    Log.d(TAG, "No plays found for user")
                    return@withContext Result.success(ListeningInsights(isEmpty = true))
                }

                val playsData = playsSnapshot.documents.mapNotNull { doc ->
                    try {
                        PlayData(
                            videoId = doc.getString("videoId") ?: return@mapNotNull null,
                            title = doc.getString("title") ?: "",
                            artists = (doc.get("artists") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            playCount = (doc.getLong("playCount") ?: 1L).toInt().coerceAtLeast(0),
                            lastPlayedAt = (doc.getTimestamp("lastPlayedAt")?.toDate()?.time) ?: System.currentTimeMillis()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing play data", e)
                        null
                    }
                }

                if (playsData.size < MIN_PLAYS_FOR_INSIGHTS) {
                    Log.d(TAG, "Insufficient plays (${playsData.size}) for insights")
                    return@withContext Result.success(ListeningInsights(isEmpty = true))
                }

                val artistDistribution = computeArtistDistribution(playsData)
                val weeklyActivity = computeRollingWeeklyActivity(userId)
                val timeOfDay = computeTimeOfDayPattern(playsData)
                val topTracks = computeTopTracks(playsData)
                val totalPlays = playsData.sumOf { it.playCount }
                val uniqueArtists = playsData.flatMap { it.artists }.distinct().size

                val insights = ListeningInsights(
                    artistDistribution = artistDistribution,
                    weeklyActivity = weeklyActivity,
                    timeOfDayPattern = timeOfDay,
                    topTracks = topTracks,
                    totalPlays = totalPlays,
                    uniqueArtists = uniqueArtists,
                    lastUpdated = System.currentTimeMillis(),
                    isEmpty = false
                )

                Log.d(TAG, "✓ Computed insights: ${artistDistribution.size} artists, ${weeklyActivity.size} days, ${topTracks.size} tracks")
                Result.success(insights)
            } catch (e: Exception) {
                Log.e(TAG, "✗ Error fetching listening insights", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Compute artist listening distribution.
     * 
     * Algorithm:
     * 1. Sum playCount per artist across all plays
     * 2. Sort by play count descending
     * 3. Select top 4-5 artists
     * 4. Group remaining as "Others"
     * 5. Calculate percentage for each
     * 
     * @param playsData All plays from Firestore
     * @return List of ArtistListeningData sorted by play count
     */
    private fun computeArtistDistribution(playsData: List<PlayData>): List<ArtistListeningData> {
        val artistPlayCounts = mutableMapOf<String, Int>()
        
        playsData.forEach { play ->
            play.artists.forEach { artist ->
                artistPlayCounts[artist] = (artistPlayCounts[artist] ?: 0) + play.playCount
            }
        }

        val totalPlays = artistPlayCounts.values.sum()
        if (totalPlays == 0) return emptyList()

        val sortedArtists = artistPlayCounts
            .toList()
            .sortedByDescending { it.second }
            .take(4)  // Top 4 artists

        val result = sortedArtists.mapIndexed { index, (artistName, playCount) ->
            ArtistListeningData(
                artistName = artistName,
                playCount = playCount,
                percentage = (playCount * 100f) / totalPlays,
                color = ArtistListeningData.getColorForIndex(index)
            )
        }

        // Add "Others" if there are more artists
        if (artistPlayCounts.size > 4) {
            val othersPlayCount = artistPlayCounts.values.sum() - result.sumOf { it.playCount }
            if (othersPlayCount > 0) {
                val othersData = ArtistListeningData(
                    artistName = "Others",
                    playCount = othersPlayCount,
                    percentage = (othersPlayCount * 100f) / totalPlays,
                    color = ArtistListeningData.getColorForIndex(4)
                )
                return result + othersData
            }
        }

        return result
    }

    private suspend fun computeRollingWeeklyActivity(userId: String): List<DailyListeningData> {
        val now = System.currentTimeMillis()
        val todayStart = getStartOfDayMillis(now)
        if (cachedWeeklyActivity != null && cachedWeeklyActivityDayStartMs == todayStart) {
            return cachedWeeklyActivity!!
        }

        val cutoffStart = todayStart - (ROLLING_DAYS - 1) * MILLIS_PER_DAY
        val cutoffDate = Date(cutoffStart)

        val snapshot = firestore.collection("users")
            .document(userId)
            .collection("plays")
            .whereGreaterThanOrEqualTo("lastPlayedAt", cutoffDate)
            .orderBy("lastPlayedAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val countsByDayStart = mutableMapOf<Long, Int>()
        snapshot.documents.forEach { doc ->
            val playedAt = doc.getTimestamp("lastPlayedAt")?.toDate()?.time ?: return@forEach
            val dayStart = getStartOfDayMillis(playedAt)
            if (dayStart in cutoffStart..todayStart) {
                countsByDayStart[dayStart] = (countsByDayStart[dayStart] ?: 0) + 1
            }
        }

        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        val rollingSeries = (0 until ROLLING_DAYS).map { index ->
            val dayStart = cutoffStart + index * MILLIS_PER_DAY
            val count = countsByDayStart[dayStart] ?: 0
            DailyListeningData(
                dayOfWeek = formatter.format(Date(dayStart)),
                playCount = count,
                normalized = 0f
            )
        }

        val maxCount = rollingSeries.maxOfOrNull { it.playCount } ?: 0
        val normalizedSeries = rollingSeries.map { day ->
            day.copy(normalized = if (maxCount > 0) day.playCount.toFloat() / maxCount else 0f)
        }

        cachedWeeklyActivity = normalizedSeries
        cachedWeeklyActivityDayStartMs = todayStart
        return normalizedSeries
    }

    private fun getStartOfDayMillis(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * Compute time-of-day listening pattern.
     * 
     * Groups plays into time periods:
     * - Morning: 6-12
     * - Afternoon: 12-18
     * - Evening: 18-24
     * - Night: 0-6
     * 
     * @param playsData All plays from Firestore
     * @return Map of time period name to play count
     */
    private fun computeTimeOfDayPattern(playsData: List<PlayData>): Map<String, Int> {
        val timeOfDayPattern = mutableMapOf(
            "Morning" to 0,
            "Afternoon" to 0,
            "Evening" to 0,
            "Night" to 0
        )

        playsData.forEach { play ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = play.lastPlayedAt
            }
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            val period = when (hour) {
                in 6..11 -> "Morning"
                in 12..17 -> "Afternoon"
                in 18..23 -> "Evening"
                else -> "Night"  // 0-5
            }
            
            timeOfDayPattern[period] = (timeOfDayPattern[period] ?: 0) + 1
        }

        return timeOfDayPattern
    }

    /**
     * Compute top played tracks.
     * 
     * Selects top 5 most played songs and normalizes play counts
     * for visual representation.
     * 
     * @param playsData All plays from Firestore
     * @return List of top 5 tracks with normalized play counts
     */
    private fun computeTopTracks(playsData: List<PlayData>): List<TrackListeningData> {
        val topTracks = playsData
            .sortedByDescending { it.playCount }
            .take(5)

        val maxPlays = topTracks.maxOfOrNull { it.playCount } ?: 1
        
        return topTracks.map { play ->
            TrackListeningData(
                videoId = play.videoId,
                title = play.title,
                artist = play.artists.firstOrNull() ?: "Unknown",
                playCount = play.playCount,
                normalized = play.playCount.toFloat() / max(1, maxPlays)
            )
        }
    }

    /**
     * Internal data class for parsing Firestore plays
     */
    private data class PlayData(
        val videoId: String,
        val title: String,
        val artists: List<String>,
        val playCount: Int,
        val lastPlayedAt: Long
    )
}
