package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

data class HealthDto(
    @SerializedName("status") val status: String? = null,
    @SerializedName("service") val service: String? = null
)

