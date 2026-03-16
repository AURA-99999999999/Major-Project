package com.aura.music.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aura.music.data.repository.DownloadsRepository
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import com.aura.music.data.repository.RecentlyPlayedRepository
import com.aura.music.data.repository.LanguagePreferencesRepository
import com.aura.music.di.ServiceLocator
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.screens.language.LanguageSelectionViewModel
import com.aura.music.ui.viewmodel.LanguagePreferencesViewModel

/**
 * Factory for creating ViewModels with manual dependency injection
 */
class ViewModelFactory(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    private val firestoreRepository: FirestoreRepository,
    private val recentlyPlayedRepository: RecentlyPlayedRepository,
    private val languagePreferencesRepository: LanguagePreferencesRepository,
    private val downloadsRepository: DownloadsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    repository = musicRepository,
                    recentlyPlayedRepository = recentlyPlayedRepository,
                    playlistRepository = playlistRepository
                ) as T
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
            modelClass.isAssignableFrom(LikedSongsViewModel::class.java) -> {
                LikedSongsViewModel(firestoreRepository, musicRepository) as T
            }
            modelClass.isAssignableFrom(PlayerViewModel::class.java) -> {
                PlayerViewModel(firestoreRepository) as T
            }
            modelClass.isAssignableFrom(AlbumDetailViewModel::class.java) -> {
                AlbumDetailViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(ArtistDetailViewModel::class.java) -> {
                ArtistDetailViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(YTPlaylistDetailViewModel::class.java) -> {
                YTPlaylistDetailViewModel(musicRepository) as T
            }
            modelClass.isAssignableFrom(ThemeManager::class.java) -> {
                ThemeManager(application) as T
            }
            modelClass.isAssignableFrom(ListeningInsightsViewModel::class.java) -> {
                ListeningInsightsViewModel(application) as T
            }
            modelClass.isAssignableFrom(LanguageSelectionViewModel::class.java) -> {
                LanguageSelectionViewModel(languagePreferencesRepository) as T
            }
            modelClass.isAssignableFrom(LanguagePreferencesViewModel::class.java) -> {
                LanguagePreferencesViewModel(languagePreferencesRepository) as T
            }
            modelClass.isAssignableFrom(DownloadsViewModel::class.java) -> {
                DownloadsViewModel(downloadsRepository, application) as T
            }
            modelClass.isAssignableFrom(QueueViewModel::class.java) -> {
                QueueViewModel() as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        fun create(application: Application): ViewModelFactory {
            return ViewModelFactory(
                application,
                ServiceLocator.getMusicRepository(),
                ServiceLocator.getPlaylistRepository(),
                ServiceLocator.getFirestoreRepository(),
                ServiceLocator.getRecentlyPlayedRepository(),
                ServiceLocator.getLanguagePreferencesRepository(),
                ServiceLocator.getDownloadsRepository()
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

