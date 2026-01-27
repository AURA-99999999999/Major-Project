package com.aura.music.navigation

sealed class Screen(val route: String) {
    // Auth Screens
    object Login : Screen("login")
    object Signup : Screen("signup")
    object AuthHome : Screen("auth_home")
    
    // Music App Screens
    object Home : Screen("home")
    object Search : Screen("search")
    object Player : Screen("player")
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    object Profile : Screen("profile")
}

