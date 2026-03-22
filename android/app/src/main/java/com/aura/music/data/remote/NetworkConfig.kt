package com.aura.music.data.remote

enum class ApiEnvironment {
    EMULATOR,
    DEVICE,
    CUSTOM
}

object NetworkConfig {
    const val activeBaseUrl: String = "https://aura-b7vm.onrender.com/"

    init {
        require(activeBaseUrl.startsWith("https://")) { "BASE URL must use HTTPS" }
        require(activeBaseUrl.endsWith("/")) { "BASE URL must end with '/'" }
    }

    fun description(): String = "baseUrl=$activeBaseUrl"
}

