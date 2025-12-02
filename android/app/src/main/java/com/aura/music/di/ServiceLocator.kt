package com.aura.music.di

import android.content.Context
import android.util.Log
import com.aura.music.BuildConfig
import com.aura.music.data.remote.MusicApi
import com.aura.music.data.remote.NetworkConfig
import com.aura.music.data.repository.MusicRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Service Locator for manual dependency injection
 * Replaces Hilt to avoid kapt compilation issues
 */
object ServiceLocator {
    private var okHttpClient: OkHttpClient? = null
    private var retrofit: Retrofit? = null
    private var musicApi: MusicApi? = null
    private var musicRepository: MusicRepository? = null

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return

            // Initialize OkHttpClient
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            okHttpClient = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            // Initialize Retrofit
            safeLog {
                Log.i("ServiceLocator", "Retrofit baseUrl -> ${NetworkConfig.description()}")
            }
            retrofit = Retrofit.Builder()
                .baseUrl(NetworkConfig.activeBaseUrl)
                .client(okHttpClient!!)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            // Initialize API
            musicApi = retrofit!!.create(MusicApi::class.java)

            // Initialize Repository
            musicRepository = MusicRepository(musicApi!!)

            initialized = true
        }
    }

    fun getMusicRepository(): MusicRepository {
        checkInitialized()
        return musicRepository!!
    }

    fun getMusicApi(): MusicApi {
        checkInitialized()
        return musicApi!!
    }

    private fun checkInitialized() {
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

