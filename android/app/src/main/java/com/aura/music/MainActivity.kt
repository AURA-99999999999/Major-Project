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

    /**
     * MainActivity - Application entry point
     * 
     * Hosts the single NavController and RootNavGraph that manages:
     * 1. Authentication flow (login/signup)
     * 2. Main app flow (home, search, profile, player)
     * 
     * Navigation is state-driven:
     * - AuthState changes trigger navigation automatically
     * - All navigation logic is in RootNavGraph, AuthGraph, MainGraph
     * - No navigation inside screens (clean separation of concerns)
     * 
     * Key features:
     * - Single NavController (no nesting)
     * - Single NavHost (no nested NavHosts)
     * - State-driven navigation with LaunchedEffect
     * - Proper backstack management with popUpTo
     * - MusicService binding for audio playback
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
        try {
            installSplashScreen()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to install splash screen", e)
        }
        super.onCreate(savedInstanceState)

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
                AuraTheme {
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
                            authState = authState
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
