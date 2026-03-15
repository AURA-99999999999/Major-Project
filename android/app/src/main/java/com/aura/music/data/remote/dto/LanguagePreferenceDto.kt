package com.aura.music.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Response for getting user's language preferences
 */
data class LanguagePreferenceResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("languages")
    val languages: List<String>,
    @SerializedName("count")
    val count: Int
)

/**
 * Request for updating user's language preferences
 */
data class UpdateLanguagePreferenceRequest(
    @SerializedName("languages")
    val languages: List<String>
)

/**
 * Response for updating user's language preferences  
 */
data class UpdateLanguagePreferenceResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("languages")
    val languages: List<String>?,
    @SerializedName("error")
    val error: String?
)

/**
 * Response for getting supported languages
 */
data class SupportedLanguagesResponse(
    @SerializedName("success")
    val success: Boolean,
    @SerializedName("languages")
    val languages: List<String>,
    @SerializedName("count")
    val count: Int
)

/**
 * Language preference data class
 */
data class Language(
    val id: String,
    val displayName: String,
    val isSelected: Boolean = false
) {
    companion object {
        val SUPPORTED_LANGUAGES = listOf(
            Language("hindi", "Hindi"),
            Language("telugu", "Telugu"),
            Language("tamil", "Tamil"),
            Language("english", "English"),
            Language("malayalam", "Malayalam"),
            Language("kannada", "Kannada"),
            Language("punjabi", "Punjabi"),
            Language("bengali", "Bengali")
        )
    }
}
