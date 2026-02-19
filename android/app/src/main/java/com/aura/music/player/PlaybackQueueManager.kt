package com.aura.music.player

import com.aura.music.data.model.Song

enum class RepeatMode {
    NONE,
    REPEAT_ALL,
    REPEAT_ONE
}

object PlaybackQueueManager {
    private val lock = Any()

    private var queue: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var repeatMode: RepeatMode = RepeatMode.NONE
    private var shuffleEnabled: Boolean = false
    private var shuffledIndices: MutableList<Int> = mutableListOf()

    fun setQueue(songs: List<Song>, startIndex: Int) {
        synchronized(lock) {
            queue = songs
            if (songs.isEmpty()) {
                currentIndex = -1
                shuffledIndices.clear()
                return
            }

            currentIndex = startIndex.coerceIn(0, songs.lastIndex)
            rebuildShuffleOrder(currentIndex)
        }
    }

    fun getCurrentSong(): Song? = synchronized(lock) {
        queue.getOrNull(currentIndex)
    }

    fun getNextSong(): Song? = synchronized(lock) {
        val nextIndex = getNextIndexLocked() ?: return null
        queue.getOrNull(nextIndex)
    }

    fun getPreviousSong(): Song? = synchronized(lock) {
        val prevIndex = getPreviousIndexLocked() ?: return null
        queue.getOrNull(prevIndex)
    }

    fun hasNext(): Boolean = synchronized(lock) {
        getNextIndexLocked() != null
    }

    fun hasPrevious(): Boolean = synchronized(lock) {
        getPreviousIndexLocked() != null
    }

    fun moveToNext(): Song? = synchronized(lock) {
        val nextIndex = getNextIndexLocked() ?: return null
        currentIndex = nextIndex
        queue.getOrNull(currentIndex)
    }

    fun moveToPrevious(): Song? = synchronized(lock) {
        val prevIndex = getPreviousIndexLocked() ?: return null
        currentIndex = prevIndex
        queue.getOrNull(currentIndex)
    }

    fun setRepeatMode(mode: RepeatMode) {
        synchronized(lock) {
            repeatMode = mode
        }
    }

    fun getRepeatMode(): RepeatMode = synchronized(lock) { repeatMode }

    fun setShuffle(enabled: Boolean) {
        synchronized(lock) {
            if (shuffleEnabled == enabled) return
            shuffleEnabled = enabled
            if (shuffleEnabled) {
                rebuildShuffleOrder(currentIndex)
            } else {
                shuffledIndices.clear()
            }
        }
    }

    fun isShuffleEnabled(): Boolean = synchronized(lock) { shuffleEnabled }

    fun clearQueue() {
        synchronized(lock) {
            queue = emptyList()
            currentIndex = -1
            shuffledIndices.clear()
        }
    }

    fun insertNext(song: Song) {
        synchronized(lock) {
            if (queue.isEmpty() || currentIndex !in queue.indices) {
                setQueue(listOf(song), 0)
                return
            }

            val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
            val nextSong = queue.getOrNull(insertIndex)
            if (nextSong?.videoId == song.videoId) return

            val mutableQueue = queue.toMutableList()
            mutableQueue.add(insertIndex, song)
            queue = mutableQueue

            if (shuffleEnabled) {
                if (shuffledIndices.isEmpty()) {
                    rebuildShuffleOrder(currentIndex)
                    return
                }

                val shifted = shuffledIndices.map { index ->
                    if (index >= insertIndex) index + 1 else index
                }.toMutableList()

                val currentPos = shifted.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
                val insertPos = (currentPos + 1).coerceAtMost(shifted.size)
                val alreadyNext = shifted.getOrNull(currentPos + 1) == insertIndex
                if (!alreadyNext) {
                    shifted.add(insertPos, insertIndex)
                }
                shuffledIndices = shifted
            }
        }
    }

    private fun rebuildShuffleOrder(startIndex: Int) {
        if (!shuffleEnabled || queue.isEmpty()) return

        val indices = queue.indices.toMutableList()
        if (startIndex in indices) {
            indices.remove(startIndex)
            indices.shuffle()
            shuffledIndices = mutableListOf(startIndex).apply { addAll(indices) }
        } else {
            indices.shuffle()
            shuffledIndices = indices
        }
    }

    private fun getNextIndexLocked(): Int? {
        if (queue.isEmpty()) return null

        return if (shuffleEnabled) {
            val currentPos = shuffledIndices.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
            val nextPos = currentPos + 1
            when {
                nextPos < shuffledIndices.size -> shuffledIndices[nextPos]
                repeatMode == RepeatMode.REPEAT_ALL -> shuffledIndices.firstOrNull()
                else -> null
            }
        } else {
            val nextIndex = currentIndex + 1
            when {
                nextIndex <= queue.lastIndex -> nextIndex
                repeatMode == RepeatMode.REPEAT_ALL -> 0
                else -> null
            }
        }
    }

    private fun getPreviousIndexLocked(): Int? {
        if (queue.isEmpty()) return null

        return if (shuffleEnabled) {
            val currentPos = shuffledIndices.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
            val prevPos = currentPos - 1
            when {
                prevPos >= 0 -> shuffledIndices[prevPos]
                repeatMode == RepeatMode.REPEAT_ALL -> shuffledIndices.lastOrNull()
                else -> null
            }
        } else {
            val prevIndex = currentIndex - 1
            when {
                prevIndex >= 0 -> prevIndex
                repeatMode == RepeatMode.REPEAT_ALL -> queue.lastIndex
                else -> null
            }
        }
    }
}
