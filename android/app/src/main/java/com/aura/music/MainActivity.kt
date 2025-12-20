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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import com.aura.music.navigation.NavGraph
import com.aura.music.player.MusicService
import com.aura.music.ui.theme.AuraTheme

/**
 * MainActivity hosts the main app content (NavGraph with Home, Search, etc.)
 * 
 * On start, checks if user is authenticated - if not, redirects to LoginActivity.
 * This acts as a safety check in case LoginActivity is bypassed somehow.
 */
class MainActivity : ComponentActivity() {
    
    private var musicService by mutableStateOf<MusicService?>(null)
    private var isBound by mutableStateOf(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            musicService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Check if user is authenticated - if not, redirect to LoginActivity
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        
        if (currentUser == null) {
            Log.d(TAG, "No authenticated user found, redirecting to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish() // Finish MainActivity so user can't go back
            return
        }

        Log.d(TAG, "User authenticated: ${currentUser.email}, proceeding to main content")

        // Bind to music service
        Intent(this, MusicService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            startService(intent)
        }

        setContent {
            AuraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(musicService = musicService)
                }
            }
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

