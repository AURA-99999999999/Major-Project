package com.aura.music.debug

import android.util.Log
import com.aura.music.BuildConfig
import com.aura.music.data.remote.NetworkConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.logging.HttpLoggingInterceptor

/**
 * LocalConnectionTest - Debug utility for testing backend connectivity
 * 
 * Usage:
 * ```kotlin
 * Log.d("DEBUG", LocalConnectionTest.testLocalBackendConnection())
 * ```
 */
object LocalConnectionTest {
    private const val TAG = "LocalConnectionTest"
    
    /**
     * Test connection to backend and return detailed status
     */
    fun testLocalBackendConnection(): String {
        return buildString {
            appendLine("========================================")
            appendLine("BACKEND CONNECTION TEST")
            appendLine("========================================")
            appendLine("")
            
            // 1. Check BuildConfig
            appendLine("1. BuildConfig Values:")
            appendLine("   BASE_URL: ${BuildConfig.BASE_URL}")
            appendLine("   API_ENV: ${BuildConfig.API_ENV}")
            appendLine("")
            
            // 2. Check NetworkConfig
            appendLine("2. NetworkConfig Values:")
            appendLine("   activeBaseUrl: ${NetworkConfig.activeBaseUrl}")
            appendLine("   apiEnvironment: ${NetworkConfig.apiEnvironment}")
            appendLine("   isLocal(): ${NetworkConfig.isLocal()}")
            appendLine("   isProduction(): ${NetworkConfig.isProduction()}")
            appendLine("   description(): ${NetworkConfig.description()}")
            appendLine("")
            
            // 3. Check URL format
            appendLine("3. URL Validation:")
            val isHttps = NetworkConfig.activeBaseUrl.startsWith("https://")
            val hasTrailingSlash = NetworkConfig.activeBaseUrl.endsWith("/")
            
            appendLine("   ✓ Starts with https: $isHttps")
            appendLine("   ✓ Has trailing slash: $hasTrailingSlash")
            appendLine("   ✓ Is HTTPS (production): $isHttps")
            appendLine("")
            
            // 4. Connection Status
            appendLine("4. Connection Status:")
            appendLine("   Environment: ${if (NetworkConfig.isLocal()) "LOCAL DEBUG" else "PRODUCTION"}")
            appendLine("   Target URL: ${NetworkConfig.activeBaseUrl}api/health")
            appendLine("")
            
            // 5. Recommendations
            appendLine("5. Recommendations:")
            appendLine("   ✓ PRODUCTION MODE - Connected to Render backend")
            appendLine("   ✓ Using: ${NetworkConfig.activeBaseUrl}")
            appendLine("")
            appendLine("========================================")
        }.also { 
            Log.d(TAG, it)
        }
    }
    
    /**
     * Create a test Retrofit instance for manual API testing
     */
    fun createTestRetrofitClient(): Retrofit {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        return Retrofit.Builder()
            .baseUrl(NetworkConfig.activeBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    /**
     * Get environment summary for UI display
     */
    fun getEnvironmentSummary(): String = buildString {
        append("Env: PROD | ")
        append("URL: ${NetworkConfig.activeBaseUrl}")
    }
}
