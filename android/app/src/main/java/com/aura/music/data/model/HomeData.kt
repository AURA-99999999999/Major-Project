package com.aura.music.data.model

data class HomeData(
    val trending: List<Song> = emptyList(),
    val recommendations: List<Song> = emptyList()
)
