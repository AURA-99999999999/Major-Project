package com.aura.music.data.remote

import com.aura.music.BuildConfig

enum class ApiEnvironment {
    EMULATOR,
    DEVICE,
    CUSTOM
}

object NetworkConfig {
    val environment: ApiEnvironment = runCatching {
        ApiEnvironment.valueOf(BuildConfig.API_ENV.uppercase())
    }.getOrDefault(ApiEnvironment.EMULATOR)

    val activeBaseUrl: String = BuildConfig.API_BASE_URL
    val emulatorBaseUrl: String = BuildConfig.API_BASE_URL_EMULATOR
    val deviceBaseUrl: String = BuildConfig.API_BASE_URL_DEVICE
    val customBaseUrl: String = BuildConfig.API_BASE_URL_CUSTOM

    fun description(): String = "env=${environment.name} -> $activeBaseUrl"
}

