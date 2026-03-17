package com.aura.music.di

import android.content.Context
import android.util.Log
import com.aura.music.BuildConfig
import com.aura.music.data.local.AppDatabase
import com.aura.music.data.local.LanguagePreferencesManager
import com.aura.music.data.remote.MusicApi
import com.aura.music.data.remote.NetworkConfig
import com.aura.music.data.repository.DownloadsRepository
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.data.repository.MusicRepository
import com.aura.music.data.repository.PlaylistRepository
import com.aura.music.data.repository.RecentlyPlayedRepository
import com.aura.music.data.repository.LanguagePreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.aura.music.AppInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service Locator for manual dependency injection
 * Replaces Hilt to avoid kapt compilation issues
 */
object ServiceLocator {
    private const val TAG = "ServiceLocator"
    
    private var okHttpClient: OkHttpClient? = null
    private var retrofit: Retrofit? = null
    private var musicApi: MusicApi? = null
    private var musicRepository: MusicRepository? = null
    private var firestoreRepository: FirestoreRepository? = null
    private var playlistRepository: PlaylistRepository? = null
    private var appDatabase: AppDatabase? = null
    private var recentlyPlayedRepository: RecentlyPlayedRepository? = null
    private var downloadsRepository: DownloadsRepository? = null
    private var languagePreferencesManager: LanguagePreferencesManager? = null
    private var languagePreferencesRepository: LanguagePreferencesRepository? = null
    private var appContext: Context? = null

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            // Initialize OkHttpClient with logging interceptor
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(AppInterceptor())
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // Initialize Retrofit with base URL from BuildConfig
            val baseUrl = NetworkConfig.activeBaseUrl
            safeLog {
                Log.i(TAG, "========================================")
                Log.i(TAG, "Initializing Network Client")
                Log.i(TAG, "Base URL: $baseUrl")
                Log.i(TAG, "========================================")
            }
            
            // Validate base URL format
            require(baseUrl.endsWith("/")) {
                "Base URL must end with '/': $baseUrl"
            }
            
            try {
                retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(okHttpClient!!)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
                safeLog {
                    Log.i(TAG, "Retrofit client initialized successfully")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Retrofit client", e)
                throw IllegalStateException("Failed to initialize Retrofit: ${e.message}", e)
            }

            // Initialize API
            musicApi = retrofit!!.create(MusicApi::class.java)

            // Perform health check to verify backend connectivity
            performHealthCheck()

            // Initialize Repository
            firestoreRepository = FirestoreRepository()
            playlistRepository = PlaylistRepository()
            musicRepository = MusicRepository(musicApi!!, firestoreRepository!!)
            appDatabase = AppDatabase.getInstance(context)
            recentlyPlayedRepository = RecentlyPlayedRepository(appDatabase!!.recentlyPlayedDao())
            downloadsRepository = DownloadsRepository(appDatabase!!.downloadedSongDao(), context)
            
            // Initialize Language Preferences
            languagePreferencesManager = LanguagePreferencesManager(context)
            languagePreferencesRepository = LanguagePreferencesRepository(
                languagePreferencesManager!!
            )

            initialized = true
        }
    }

    /**
     * Performs a health check request to verify backend connectivity
     * This confirms the Android app can reach the local backend server
     */
    private fun performHealthCheck() {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val response = musicApi!!.getHealth()
                Log.i(TAG, "========================================")
                Log.i(TAG, "Backend Health Check: SUCCESS")
                Log.i(TAG, "Status: ${response.status}")
                Log.i(TAG, "Service: ${response.service}")
                Log.i(TAG, "========================================")
            } catch (e: Exception) {
                Log.e(TAG, "========================================")
                Log.e(TAG, "Backend Health Check: FAILED")
                Log.e(TAG, "Error: ${e.message}")
                Log.e(TAG, "========================================", e)
            }
        }
    }

    fun getMusicRepository(): MusicRepository {
        checkInitialized()
        return musicRepository!!
    }

    fun getFirestoreRepository(): FirestoreRepository {
        checkInitialized()
        return firestoreRepository!!
    }

    fun getPlaylistRepository(): PlaylistRepository {
        checkInitialized()
        return playlistRepository!!
    }

    fun getMusicApi(): MusicApi {
        checkInitialized()
        return musicApi!!
    }

    fun getLanguagePreferencesRepository(): LanguagePreferencesRepository {
        checkInitialized()
        return languagePreferencesRepository!!
    }

    fun getRecentlyPlayedRepository(): RecentlyPlayedRepository {
        checkInitialized()
        return recentlyPlayedRepository!!
    }

    fun getDownloadsRepository(): DownloadsRepository {
        checkInitialized()
        return downloadsRepository!!
    }

    private fun checkInitialized() {
        if (!initialized) {
            appContext?.let { cachedContext ->
                initialize(cachedContext)
            }
        }

        if (!initialized) {
            throw IllegalStateException(
                "ServiceLocator not initialized. Call ServiceLocator.initialize(context) first."
            )
        }
    }

    // For testing - allows reset
    fun reset() {
        synchronized(this) {
            okHttpClient = null
            retrofit = null
            musicApi = null
            musicRepository = null
            firestoreRepository = null
            playlistRepository = null
            appDatabase = null
            recentlyPlayedRepository = null
            downloadsRepository = null
            languagePreferencesManager = null
            languagePreferencesRepository = null
            initialized = false
        }
    }

    private inline fun safeLog(block: () -> Unit) {
        if (!BuildConfig.DEBUG) return
        try {
            block()
        } catch (_: Throwable) {
            // Ignore logging issues (e.g., running on JVM tests)
        }
    }
}

