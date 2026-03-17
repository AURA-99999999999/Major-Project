package com.aura.music.data.remote

import com.aura.music.BuildConfig

enum class ApiEnvironment {
    EMULATOR,
    DEVICE,
    CUSTOM
}

object NetworkConfig {
    val activeBaseUrl: String = BuildConfig.BASE_URL
    fun description(): String = "baseUrl=$activeBaseUrl"
}

