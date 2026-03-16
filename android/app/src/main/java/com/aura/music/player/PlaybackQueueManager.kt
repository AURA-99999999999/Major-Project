package com.aura.music.player

import com.aura.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode {
    NONE,
    REPEAT_ALL,
    REPEAT_ONE
}

data class QueueState(
    val currentTrack: Song? = null,
    val queueList: List<Song> = emptyList(),
    val historyStack: List<Song> = emptyList()
)

object PlaybackQueueManager {
    private val lock = Any()

    private var queue: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var repeatMode: RepeatMode = RepeatMode.NONE
    private var shuffleEnabled: Boolean = false
    private var shuffledIndices: MutableList<Int> = mutableListOf()
    private val historyIndices: ArrayDeque<Int> = ArrayDeque()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    fun setQueue(songs: List<Song>, startIndex: Int) {
        synchronized(lock) {
            queue = songs
            if (songs.isEmpty()) {
                currentIndex = -1
                shuffledIndices.clear()
                historyIndices.clear()
                publishStateLocked()
                return
            }

            currentIndex = startIndex.coerceIn(0, songs.lastIndex)
            historyIndices.clear()
            rebuildShuffleOrder(currentIndex)
            publishStateLocked()
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
        if (currentIndex in queue.indices) {
            historyIndices.addLast(currentIndex)
        }
        currentIndex = nextIndex
        publishStateLocked()
        queue.getOrNull(currentIndex)
    }

    fun moveToPrevious(): Song? = synchronized(lock) {
        val prevIndex = if (historyIndices.isNotEmpty()) {
            historyIndices.removeLast()
        } else {
            getPreviousIndexLocked() ?: return null
        }
        currentIndex = prevIndex
        publishStateLocked()
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
    
    fun getQueue(): List<Song> = synchronized(lock) { queue }
    
    fun getCurrentIndex(): Int = synchronized(lock) { currentIndex }

    fun getRemainingCountAfterCurrent(): Int = synchronized(lock) {
        if (queue.isEmpty() || currentIndex !in queue.indices) return@synchronized 0

        if (shuffleEnabled) {
            val currentPos = shuffledIndices.indexOf(currentIndex).takeIf { it >= 0 } ?: 0
            (shuffledIndices.size - currentPos - 1).coerceAtLeast(0)
        } else {
            (queue.lastIndex - currentIndex).coerceAtLeast(0)
        }
    }

    fun clearQueue() {
        synchronized(lock) {
            queue = emptyList()
            currentIndex = -1
            shuffledIndices.clear()
            historyIndices.clear()
            publishStateLocked()
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

            publishStateLocked()
        }
    }

    fun appendToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return

        synchronized(lock) {
            if (queue.isEmpty()) {
                setQueue(songs, 0)
                return
            }

            val mutableQueue = queue.toMutableList()
            mutableQueue.addAll(songs)
            queue = mutableQueue

            if (shuffleEnabled) {
                val baseOrder = shuffledIndices.toMutableList()
                val currentSize = mutableQueue.size
                val appendedRange = (currentSize - songs.size until currentSize).toMutableList()
                appendedRange.shuffle()
                baseOrder.addAll(appendedRange)
                shuffledIndices = baseOrder
            }

            publishStateLocked()
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int): Boolean {
        synchronized(lock) {
            if (fromIndex !in queue.indices || toIndex !in queue.indices) return false
            if (fromIndex == toIndex) return true

            val mutableQueue = queue.toMutableList()
            val moved = mutableQueue.removeAt(fromIndex)
            mutableQueue.add(toIndex, moved)
            queue = mutableQueue

            currentIndex = when {
                currentIndex == fromIndex -> toIndex
                fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
                fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
                else -> currentIndex
            }

            if (shuffleEnabled) {
                rebuildShuffleOrder(currentIndex)
            }

            publishStateLocked()
            return true
        }
    }

    private fun publishStateLocked() {
        _queueState.value = QueueState(
            currentTrack = queue.getOrNull(currentIndex),
            queueList = queue,
            historyStack = historyIndices.mapNotNull { index -> queue.getOrNull(index) }
        )
    }

    private fun rebuildShuffleOrder(startIndex: Int) {
        if (!shuffleEnabled || queue.isEmpty()) return

        val indices = queue.indices.toMutableList()
        if (startIndex in indices) {
            indices.remove(startIndex)
            val ordered = buildDiverseShuffle(indices, queue[startIndex])
            shuffledIndices = mutableListOf(startIndex).apply { addAll(ordered) }
        } else {
            shuffledIndices = buildDiverseShuffle(indices, null)
        }
    }

    private fun buildDiverseShuffle(indices: MutableList<Int>, initialSong: Song?): MutableList<Int> {
        val pool = indices.toMutableList()
        pool.shuffle()

        val ordered = mutableListOf<Int>()
        var lastSong = initialSong

        while (pool.isNotEmpty()) {
            val diverseIndex = pool.indexOfFirst { candidateIndex ->
                val candidate = queue[candidateIndex]
                !isSameArtistOrAlbum(lastSong, candidate)
            }

            val pickAt = if (diverseIndex >= 0) diverseIndex else 0
            val chosenIndex = pool.removeAt(pickAt)
            ordered.add(chosenIndex)
            lastSong = queue[chosenIndex]
        }

        return ordered.toMutableList()
    }

    private fun isSameArtistOrAlbum(left: Song?, right: Song): Boolean {
        if (left == null) return false

        val leftArtist = left.getArtistString().trim().lowercase()
        val rightArtist = right.getArtistString().trim().lowercase()
        val leftAlbum = (left.album ?: "").trim().lowercase()
        val rightAlbum = (right.album ?: "").trim().lowercase()

        val sameArtist = leftArtist.isNotBlank() && leftArtist == rightArtist
        val sameAlbum = leftAlbum.isNotBlank() && leftAlbum == rightAlbum
        return sameArtist || sameAlbum
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
