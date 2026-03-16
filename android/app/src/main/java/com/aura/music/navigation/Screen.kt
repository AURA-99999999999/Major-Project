package com.aura.music.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    // Auth Screens
    object Login : Screen("login")
    object Signup : Screen("signup")
    object AuthHome : Screen("auth_home")
    
    // Music App Screens
    object Home : Screen("home")
    object Search : Screen("search")
    object Player : Screen("player")
        object Equalizer : Screen("equalizer")
    object Playlists : Screen("playlists")
    object LikedSongs : Screen("liked-songs")
    object Downloads : Screen("downloads")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    object AlbumDetail : Screen("album_detail/{browseId}") {
        fun createRoute(browseId: String) = "album_detail/$browseId"
    }
    object ArtistDetail : Screen("artist_detail/{browseId}") {
        fun createRoute(browseId: String) = "artist_detail/${Uri.encode(browseId)}"
    }
    object YTPlaylistDetail : Screen("yt_playlist_detail/{browseId}") {
        fun createRoute(browseId: String) = "yt_playlist_detail/$browseId"
    }
    object DailyMixDetail : Screen("daily_mix_detail/{mixKey}") {
        fun createRoute(mixKey: String) = "daily_mix_detail/$mixKey"
    }
    object MoodDetail : Screen("mood_detail/{moodTitle}/{mood}") {
        fun createRoute(moodTitle: String, mood: String) = "mood_detail/$moodTitle/$mood"
    }
    object Profile : Screen("profile")
    object LanguageSelection : Screen("language_selection")
    object EditProfile : Screen("edit_profile")
    object ThemeSettings : Screen("theme_settings")
    object ListeningInsights : Screen("listening_insights")
}

