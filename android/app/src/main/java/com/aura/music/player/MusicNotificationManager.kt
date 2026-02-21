package com.aura.music.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.session.MediaSession
import coil.imageLoader
import coil.request.ImageRequest
import com.aura.music.MainActivity
import com.aura.music.R
import com.aura.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium MediaStyle Notification Manager for AURA
 * 
 * Features:
 * - Elegant MediaStyle notification with large album artwork
 * - Lock screen controls with artwork display
 * - Playback controls (previous, play/pause, next, like)
 * - Efficient bitmap loading and caching
 * - Smart update logic to avoid unnecessary rebuilds
 */
class MusicNotificationManager(
    private val context: Context,
    private val mediaSession: MediaSession
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private var currentArtworkBitmap: Bitmap? = null
    private var lastArtworkUrl: String? = null
    private var lastNotificationState: NotificationState? = null
    
    companion object {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "aura_playback"
        private const val TAG = "MusicNotification"
        
        // Action constants
        const val ACTION_PLAY_PAUSE = "com.aura.music.PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.aura.music.PREVIOUS"
        const val ACTION_NEXT = "com.aura.music.NEXT"
        const val ACTION_LIKE = "com.aura.music.LIKE"
        const val ACTION_STOP = "com.aura.music.STOP"
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows currently playing music"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Build and return the notification
     * Smart update: Only rebuilds when state actually changes
     */
    suspend fun buildNotification(
        song: Song,
        isPlaying: Boolean,
        isLiked: Boolean
    ): Notification {
        val currentState = NotificationState(song.videoId, isPlaying, isLiked)
        
        // Load artwork efficiently (cached if URL hasn't changed)
        val artwork = loadArtwork(song.thumbnail)
        
        // Build the notification
        return createNotification(song, isPlaying, isLiked, artwork)
    }
    
    /**
     * Update notification if it needs updating
     */
    fun updateNotification(
        song: Song,
        isPlaying: Boolean,
        isLiked: Boolean
    ) {
        scope.launch {
            try {
                val notification = buildNotification(song, isPlaying, isLiked)
                notificationManager.notify(NOTIFICATION_ID, notification)
                Log.d(TAG, "Notification updated: ${song.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update notification", e)
            }
        }
    }
    
    /**
     * Load album artwork efficiently with caching
     */
    private suspend fun loadArtwork(url: String?): Bitmap? {
        if (url.isNullOrBlank()) {
            lastArtworkUrl = null
            currentArtworkBitmap = null
            return null
        }
        
        // Reuse cached bitmap if URL hasn't changed
        if (url == lastArtworkUrl && currentArtworkBitmap != null) {
            Log.d(TAG, "Reusing cached artwork")
            return currentArtworkBitmap
        }
        
        return try {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(512, 512)
                    .allowHardware(false) // Required for notification
                    .build()
                
                val result = context.imageLoader.execute(request)
                result.drawable?.toBitmap()?.also {
                    currentArtworkBitmap = it
                    lastArtworkUrl = url
                    Log.d(TAG, "Loaded new artwork")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load artwork", e)
            null
        }
    }
    
    /**
     * Create the actual notification with MediaStyle
     */
    private fun createNotification(
        song: Song,
        isPlaying: Boolean,
        isLiked: Boolean,
        artwork: Bitmap?
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(song.title)
            .setContentText(song.artist ?: song.artists?.joinToString(", ") ?: "Unknown Artist")
            .setSubText(song.album ?: "")
            .setLargeIcon(artwork)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(createContentIntent())
            .setDeleteIntent(createDeleteIntent())
            
        // Add playback controls
        builder.addAction(createPreviousAction())
        builder.addAction(createPlayPauseAction(isPlaying))
        builder.addAction(createNextAction())
        builder.addAction(createLikeAction(isLiked))
        
        // Apply MediaStyle
        val mediaStyle = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession)
        
        builder.setStyle(mediaStyle)
        
        return builder.build()
    }
    
    /**
     * Create pending intent to open the app
     */
    private fun createContentIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_player", true)
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Create delete intent (stop service)
     */
    private fun createDeleteIntent(): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_STOP
        }
        return PendingIntent.getService(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    /**
     * Create previous track action
     */
    private fun createPreviousAction(): NotificationCompat.Action {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val pendingIntent = PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_previous,
            "Previous",
            pendingIntent
        ).build()
    }
    
    /**
     * Create play/pause action
     */
    private fun createPlayPauseAction(isPlaying: Boolean): NotificationCompat.Action {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val pendingIntent = PendingIntent.getService(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_notification_pause else R.drawable.ic_notification_play,
            if (isPlaying) "Pause" else "Play",
            pendingIntent
        ).build()
    }
    
    /**
     * Create next track action
     */
    private fun createNextAction(): NotificationCompat.Action {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_NEXT
        }
        val pendingIntent = PendingIntent.getService(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            R.drawable.ic_notification_next,
            "Next",
            pendingIntent
        ).build()
    }
    
    /**
     * Create like/unlike action
     */
    private fun createLikeAction(isLiked: Boolean): NotificationCompat.Action {
        val intent = Intent(context, MusicService::class.java).apply {
            action = ACTION_LIKE
        }
        val pendingIntent = PendingIntent.getService(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            if (isLiked) R.drawable.ic_notification_liked else R.drawable.ic_notification_like,
            if (isLiked) "Unlike" else "Like",
            pendingIntent
        ).build()
    }
    
    /**
     * Cancel the notification
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
        currentArtworkBitmap = null
        lastArtworkUrl = null
        lastNotificationState = null
    }
    
    /**
     * Data class to track notification state for smart updates
     */
    private data class NotificationState(
        val songId: String,
        val isPlaying: Boolean,
        val isLiked: Boolean
    )
}
