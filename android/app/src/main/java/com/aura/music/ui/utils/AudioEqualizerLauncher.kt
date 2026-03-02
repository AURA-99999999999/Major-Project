package com.aura.music.ui.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Utility for launching the system audio equalizer interface.
 * Handles device compatibility, error cases, and graceful fallbacks.
 * 
 * Features:
 * - Connects to active audio session
 * - Detects equalizer availability before launch
 * - Graceful error handling
 * - Production-ready implementation
 */
object AudioEqualizerLauncher {
    
    private const val TAG = "AudioEqualizerLauncher"
    
    /**
     * Result sealed class for equalizer launch operations.
     */
    sealed class EqualizerResult {
        object Success : EqualizerResult()
        object NotAvailable : EqualizerResult()
        object InvalidSession : EqualizerResult()
        data class Error(val message: String) : EqualizerResult()
    }
    
    /**
     * Checks if the device supports system equalizer.
     * 
     * @param context Android context
     * @return true if equalizer is available, false otherwise
     */
    fun isEqualizerAvailable(context: Context): Boolean {
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
            intent.resolveActivity(context.packageManager) != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking equalizer availability", e)
            false
        }
    }
    
    /**
     * Launches the system equalizer connected to the active audio session.
     * 
     * @param activity Activity context required for launching intent
     * @param audioSessionId Audio session ID from media player
     * @return EqualizerResult indicating success or failure reason
     */
    fun launchEqualizer(
        activity: Activity,
        audioSessionId: Int?
    ): EqualizerResult {
        // Validate audio session ID
        if (audioSessionId == null || audioSessionId == 0) {
            Log.w(TAG, "Invalid audio session ID: $audioSessionId")
            return EqualizerResult.InvalidSession
        }
        
        // Check equalizer availability
        if (!isEqualizerAvailable(activity)) {
            Log.w(TAG, "Equalizer not available on this device")
            return EqualizerResult.NotAvailable
        }
        
        return try {
            val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                // Attach audio session
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                
                // Package identification
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, activity.packageName)
                
                // Content type for music
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                
                // Add flags for better compatibility
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            activity.startActivity(intent)
            
            Log.d(TAG, "Equalizer launched successfully with session ID: $audioSessionId")
            EqualizerResult.Success
            
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Equalizer activity not found", e)
            EqualizerResult.NotAvailable
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception launching equalizer", e)
            EqualizerResult.Error("Permission denied")
            
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error launching equalizer", e)
            EqualizerResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * Gets a user-friendly error message for equalizer result.
     * 
     * @param result The EqualizerResult to convert
     * @return Human-readable error message, or null if success
     */
    fun getResultMessage(result: EqualizerResult): String? {
        return when (result) {
            is EqualizerResult.Success -> null
            is EqualizerResult.NotAvailable -> "Equalizer not available on this device"
            is EqualizerResult.InvalidSession -> "No active playback session"
            is EqualizerResult.Error -> "Failed to open equalizer: ${result.message}"
        }
    }
}

/**
 * Composable function that provides an equalizer launcher callback.
 * Handles activity context extraction and result processing.
 * 
 * @param audioSessionId Current audio session ID from MusicService
 * @param onResult Callback invoked with launch result
 * @return Lambda that launches the equalizer when called
 */
@Composable
fun rememberEqualizerLauncher(
    audioSessionId: Int?,
    onResult: (AudioEqualizerLauncher.EqualizerResult) -> Unit = {}
): () -> Unit {
    val context = LocalContext.current
    
    return remember(audioSessionId) {
        {
            val activity = context as? Activity
            if (activity == null) {
                Log.e("EqualizerLauncher", "Context is not an Activity")
                onResult(AudioEqualizerLauncher.EqualizerResult.Error("Invalid context"))
            } else {
                val result = AudioEqualizerLauncher.launchEqualizer(activity, audioSessionId)
                onResult(result)
            }
        }
    }
}
