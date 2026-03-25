package com.aura.music.data.remote

import com.aura.music.BuildConfig

enum class ApiEnvironment {
    EMULATOR,
    DEVICE,
    CUSTOM,
    LOCAL_EMULATOR,
    PRODUCTION
}

object NetworkConfig {
    // Dynamically load from BuildConfig to support both local and production
    const val activeBaseUrl: String = BuildConfig.BASE_URL
    const val apiEnvironment: String = BuildConfig.API_ENV

    init {
        // Allow both HTTP (for local development) and HTTPS (for production)
        require(activeBaseUrl.startsWith("http://") || activeBaseUrl.startsWith("https://")) {
            "BASE URL must start with http:// or https://"
        }
        require(activeBaseUrl.endsWith("/")) { "BASE URL must end with '/'" }
    }

    fun description(): String {
        return buildString {
            append("Environment: $apiEnvironment | ")
            append("Base URL: $activeBaseUrl")
        }
    }

    fun isLocal(): Boolean = apiEnvironment.contains("LOCAL", ignoreCase = true)
    fun isProduction(): Boolean = apiEnvironment.contains("PRODUCTION", ignoreCase = true)
}

