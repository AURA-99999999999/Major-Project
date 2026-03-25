package com.aura.music.debug

import android.util.Log
import com.aura.music.BuildConfig
import com.aura.music.data.remote.NetworkConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.logging.HttpLoggingInterceptor

/**
 * LocalConnectionTest - Debug utility for testing connection to local backend
 * 
 * Usage:
 * ```kotlin
 * Log.d("DEBUG", LocalConnectionTest.testLocalBackendConnection())
 * ```
 */
object LocalConnectionTest {
    private const val TAG = "LocalConnectionTest"
    
    /**
     * Test connection to local backend and return detailed status
     */
    fun testLocalBackendConnection(): String {
        return buildString {
            appendLine("========================================")
            appendLine("LOCAL BACKEND CONNECTION TEST")
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
            val isHttp = NetworkConfig.activeBaseUrl.startsWith("http://")
            val hasTrailingSlash = NetworkConfig.activeBaseUrl.endsWith("/")
            
            appendLine("   ✓ Starts with http/https: ${isHttp || isHttps}")
            appendLine("   ✓ Has trailing slash: $hasTrailingSlash")
            appendLine("   ✓ Is HTTP (local): $isHttp")
            appendLine("   ✓ Is HTTPS (production): $isHttps")
            appendLine("")
            
            // 4. Connection Status
            appendLine("4. Connection Status:")
            appendLine("   Environment: ${if (NetworkConfig.isLocal()) "LOCAL DEBUG" else "PRODUCTION"}")
            appendLine("   Target URL: ${NetworkConfig.activeBaseUrl}api/health")
            appendLine("")
            
            // 5. Recommendations
            appendLine("5. Recommendations:")
            if (NetworkConfig.isLocal()) {
                appendLine("   ✓ DEBUG MODE - Connected to local backend")
                appendLine("   ✓ Ensure Flask is running: python app.py")
                appendLine("   ✓ For emulator: Flask should be on http://localhost:5000")
                appendLine("   ✓ For device: Flask should be on http://<local-ip>:5000")
            } else {
                appendLine("   ✓ PRODUCTION MODE - Connected to Render backend")
                appendLine("   ✓ Using: ${NetworkConfig.activeBaseUrl}")
            }
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
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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
        append("Env: ${if (NetworkConfig.isLocal()) "LOCAL" else "PROD"} | ")
        append("URL: ${NetworkConfig.activeBaseUrl}")
    }
}
