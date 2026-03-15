package com.aura.music.data.remote.dto

import com.aura.music.data.model.MoodCategory
import com.google.gson.annotations.SerializedName

data class MoodCategoryDto(
    @SerializedName("title") val title: String,
    @SerializedName("mood") val mood: String,
    @SerializedName("color") val color: String? = "#FF5722"
) {
    fun toMoodCategory() = MoodCategory(
        title = title,
        mood = mood,
        color = color ?: "#FF5722"
    )
}

data class MoodCategoriesResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("categories") val categories: List<MoodCategoryDto>,
    @SerializedName("count") val count: Int
)
