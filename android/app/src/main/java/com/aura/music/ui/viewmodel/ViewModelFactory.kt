package com.aura.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import com.aura.music.di.ServiceLocator

/**
 * Factory for creating ViewModels with manual dependency injection
 */
class ViewModelFactory(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(PlaylistViewModel::class.java) -> {
                PlaylistViewModel(playlistRepository, musicRepository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(musicRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        fun create(application: Application): ViewModelFactory {
            return ViewModelFactory(
                application,
                ServiceLocator.getMusicRepository(),
                ServiceLocator.getPlaylistRepository()
            )
        }

        /**
         * Get MusicRepository instance for direct use in Composables
         */
        fun getMusicRepository(@Suppress("UNUSED_PARAMETER") application: Application): MusicRepository {
            return ServiceLocator.getMusicRepository()
        }
    }
}

