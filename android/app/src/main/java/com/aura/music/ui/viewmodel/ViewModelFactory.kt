package com.aura.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aura.music.data.repository.MusicRepository
import com.aura.music.di.ServiceLocator

/**
 * Factory for creating ViewModels with manual dependency injection
 */
class ViewModelFactory(
    private val musicRepository: MusicRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(AuthViewModel::class.java) -> {
                AuthViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(SearchViewModel::class.java) -> {
                SearchViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(PlaylistViewModel::class.java) -> {
                PlaylistViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(musicRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        fun create(): ViewModelFactory {
            return ViewModelFactory(ServiceLocator.getMusicRepository())
        }
    }
}

