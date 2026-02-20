package com.aura.music

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.aura.music.auth.state.AuthState
import com.aura.music.auth.viewmodel.AuthViewModel
import com.aura.music.navigation.RootNavGraph
import com.aura.music.player.MusicService
import com.aura.music.ui.theme.AuraTheme
import com.aura.music.ui.theme.ThemeManager
import com.aura.music.ui.viewmodel.ViewModelFactory

    /**
     * MainActivity - Application entry point
     * 
     * Hosts the single NavController and RootNavGraph that manages:
     * 1. Authentication flow (login/signup)
     * 2. Main app flow (home, search, profile, player)
     * 3. Firestore integration (handled in ViewModels)
     * 
     * Navigation is state-driven:
     * - AuthState changes trigger navigation automatically
     * - All navigation logic is in RootNavGraph, AuthGraph, MainGraph
     * - No navigation inside screens (clean separation of concerns)
     * 
     * Theme Management:
     * - Centralized via ThemeManager
     * - Real-time theme updates via StateFlow
     * - No activity restart required for theme changes
     * - Material 3 integration with dynamic accent colors
     * 
     * Firestore Architecture:
     * - User creation/update: Handled in AuthViewModel on login/signup
     * - Search logging: Handled in SearchViewModel after successful search
     * - Play logging: Handled in MusicService when song plays
     * - NO Firestore logic in MainActivity (follows MVVM architecture)
     * 
     * Key features:
     * - Single NavController (no nesting)
     * - Single NavHost (no nested NavHosts)
     * - State-driven navigation
     * - Proper backstack management with popUpTo
     * - MusicService binding for audio playback
     * - Dynamic theme system
     */
class MainActivity : ComponentActivity() {
    
    private var musicService by mutableStateOf<MusicService?>(null)
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
            Log.d(TAG, "MusicService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
            Log.d(TAG, "MusicService disconnected")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "MainActivity onCreate started")
        try {
            installSplashScreen()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install splash screen", e)
        }
        super.onCreate(savedInstanceState)
        Log.d(TAG, "super.onCreate() completed")

        // Bind to music service
        try {
            Intent(this, MusicService::class.java).also { intent ->
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind music service", e)
        }

        try {
            setContent {
                // Create ThemeManager for theme state management with proper factory
                val themeManager: ThemeManager = viewModel(
                    factory = ViewModelFactory.create(applicationContext as android.app.Application)
                )
                val themeState by themeManager.themeState.collectAsState()

                // Apply theme with dynamic color support
                AuraTheme(themeState = themeState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Create single NavController for entire app
                        val navController = rememberNavController()

                        // Create ViewModel that manages auth state
                        val authViewModel: AuthViewModel = viewModel()
                        val authState by authViewModel.authState.collectAsState()

                        // Single RootNavGraph that manages all navigation
                        RootNavGraph(
                            navController = navController,
                            authViewModel = authViewModel,
                            musicService = musicService,
                            authState = authState,
                            themeManager = themeManager
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Compose content", e)
            throw e
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
            musicService = null
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        var musicServiceInstance: MusicService? = null
            private set
    }
}
