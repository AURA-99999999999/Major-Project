package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * JioSaavn Artist Search API Response DTOs
 * API: https://saavn.sumit.co/api/search/artists
 */

// Top-level response
data class SaavnArtistResponse(
    @SerializedName("success")
    val success: Boolean = true,
    @SerializedName("data")
    val data: SaavnArtistData = SaavnArtistData()
)

// Data container
data class SaavnArtistData(
    @SerializedName("total")
    val total: Int = 0,
    @SerializedName("start")
    val start: Int = 0,
    @SerializedName("results")
    val results: List<SaavnArtistItem> = emptyList()
)

// Individual artist item
data class SaavnArtistItem(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("role")
    val role: String? = null,
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("image")
    val image: List<SaavnArtistImage> = emptyList(),
    @SerializedName("url")
    val url: String? = null
)

// Artist image
data class SaavnArtistImage(
    @SerializedName("quality")
    val quality: String,
    @SerializedName("url")
    val url: String
)
