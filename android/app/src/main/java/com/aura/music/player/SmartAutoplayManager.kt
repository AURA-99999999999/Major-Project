package com.aura.music.player

import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmartAutoplayManager(
    private val repository: MusicRepository
) {
    private val lock = Any()
    private var hasExtendedThisSession: Boolean = false

    fun resetForUserAction() {
        synchronized(lock) {
            hasExtendedThisSession = false
        }
    }

    suspend fun getAutoplayQueue(): List<Song> {
        synchronized(lock) {
            if (hasExtendedThisSession) return emptyList()
        }

        val recommendations = withContext(Dispatchers.IO) {
            repository.fetchUserRecommendations()
        }

        val autoplayQueue = if (recommendations.isNotEmpty()) {
            recommendations
        } else {
            withContext(Dispatchers.IO) {
                repository.getTrending(20).getOrElse { emptyList() }
            }
        }

        if (autoplayQueue.isNotEmpty()) {
            synchronized(lock) {
                hasExtendedThisSession = true
            }
        }

        return autoplayQueue
    }
}
