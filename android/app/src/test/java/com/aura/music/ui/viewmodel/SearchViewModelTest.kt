package com.aura.music.ui.viewmodel

import com.aura.music.data.model.Song
import com.aura.music.data.repository.MusicRepository
import com.aura.music.ui.viewmodel.SearchEvent.PlaySong
import com.aura.music.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository: MusicRepository = mockk(relaxed = true)

    private fun stubSong(
        videoId: String = "abc",
        title: String = "Monica",
        url: String? = "https://cdn.example.com/audio.mp3"
    ) = Song(
        videoId = videoId,
        title = title,
        artist = "Artist",
        artists = listOf("Artist"),
        thumbnail = null,
        duration = "03:30",
        url = url,
        album = "Album",
        artistId = "artist-1"
    )

    @Test
    fun `search updates results after debounce`() = runTest {
        val expectedSong = stubSong()
        coEvery { repository.searchSongs("Monica", any()) } returns Result.success(listOf(expectedSong))

        val viewModel = SearchViewModel(repository)

        viewModel.search("Monica")
        advanceTimeBy(400)
        advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(1, uiState.results.size)
        assertEquals("Monica", uiState.results.first().title)
        assertTrue(!uiState.isLoading)
    }

    @Test
    fun `prepareSongForPlayback emits event when stream resolved`() = runTest {
        val searchSong = stubSong(url = null)
        val resolvedSong = stubSong(url = "https://cdn.example.com/audio.mp3")

        coEvery { repository.getSong(searchSong.videoId) } returns Result.success(resolvedSong)

        val viewModel = SearchViewModel(repository)

        val eventDeferred = async { viewModel.events.first() }

        viewModel.prepareSongForPlayback(searchSong)
        advanceUntilIdle()

        val event = eventDeferred.await()
        assertTrue(event is PlaySong)
        if (event is PlaySong) {
            assertEquals("https://cdn.example.com/audio.mp3", event.song.url)
        }
    }
}

