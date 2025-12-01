package com.aura.music.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Login : Screen("login")
    object Register : Screen("register")
    object Home : Screen("home")
    object Search : Screen("search")
    object Player : Screen("player")
    object Playlists : Screen("playlists")
    object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: String) = "playlist_detail/$playlistId"
    }
    object Profile : Screen("profile")
}

