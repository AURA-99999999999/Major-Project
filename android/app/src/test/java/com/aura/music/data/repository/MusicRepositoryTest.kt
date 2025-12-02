package com.aura.music.data.repository

import com.aura.music.data.remote.MusicApi
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MusicRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: MusicApi
    private lateinit var repository: MusicRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        api = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(MusicApi::class.java)

        repository = MusicRepository(api)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `searchSongs returns songs when api succeeds`() = runTest {
        val body = """
            {
              "success": true,
              "results": [
                {
                  "videoId": "abc",
                  "title": "Monica",
                  "artist": "The Friends",
                  "artists": ["The Friends"],
                  "thumbnail": "https://example.com/image.jpg",
                  "duration": "03:30",
                  "url": "https://cdn.example.com/audio.mp3",
                  "album": "Greatest Hits",
                  "artistId": "artist-1"
                }
              ],
              "count": 1
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(body))

        val result = repository.searchSongs("Monica", 1)

        assertTrue(result.isSuccess)
        val songs = result.getOrNull()
        assertEquals(1, songs?.size)
        assertEquals("Monica", songs?.first()?.title)
        assertEquals("https://cdn.example.com/audio.mp3", songs?.first()?.url)
    }

    @Test
    fun `searchSongs fails when server errors`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":"Server error"}""")
        )

        val result = repository.searchSongs("error", 1)

        assertTrue(result.isFailure)
    }
}

