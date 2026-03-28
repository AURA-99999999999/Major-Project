package com.aura.music.data.remote

enum class ApiEnvironment {
    PRODUCTION
}

object NetworkConfig {
    const val BASE_URL: String = "https://aura-b7vm.onrender.com/"
    const val activeBaseUrl: String = BASE_URL
    const val apiEnvironment: String = "PRODUCTION"

    init {
        require(activeBaseUrl.startsWith("https://")) {
            "BASE URL must start with https://"
        }
        require(activeBaseUrl.endsWith("/")) { "BASE URL must end with '/'" }
    }

    fun description(): String {
        return buildString {
            append("Environment: $apiEnvironment | ")
            append("Base URL: $activeBaseUrl")
        }
    }

    fun isLocal(): Boolean = false
    fun isProduction(): Boolean = apiEnvironment.contains("PRODUCTION", ignoreCase = true)
}

