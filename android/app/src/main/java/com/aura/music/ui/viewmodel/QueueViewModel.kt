package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.aura.music.player.PlaybackQueueManager
import com.aura.music.player.QueueState
import kotlinx.coroutines.flow.StateFlow

class QueueViewModel : ViewModel() {
    val queueState: StateFlow<QueueState> = PlaybackQueueManager.queueState

    fun reorderQueue(fromIndex: Int, toIndex: Int): Boolean {
        return PlaybackQueueManager.reorderQueue(fromIndex, toIndex)
    }
}
