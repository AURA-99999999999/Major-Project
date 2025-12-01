package com.aura.music.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.music.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicService : MediaSessionService() {
    private val binder = MusicBinder()
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var notificationManager: NotificationManager? = null

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        initializePlayer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()
            .also { player ->
                player.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playerState.update { it.copy(isPlaying = isPlaying, isLoading = false) }
                        updateNotification()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playerState.update { it.copy(isLoading = true) }
                            }
                            Player.STATE_READY -> {
                                _playerState.update { it.copy(isLoading = false) }
                            }
                            Player.STATE_ENDED -> {
                                handleTrackEnd()
                            }
                        }
                        updateNotification()
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateCurrentPosition()
                    }

                    override fun onPositionDiscontinuity(
                        oldPosition: Player.PositionInfo,
                        newPosition: Player.PositionInfo,
                        reason: Int
                    ) {
                        updateCurrentPosition()
                    }
                })

                serviceScope.launch {
                    while (player.isPlaying) {
                        _playerState.update {
                            it.copy(
                                currentPosition = player.currentPosition,
                                duration = player.duration,
                                bufferedPosition = player.bufferedPosition
                            )
                        }
                        kotlinx.coroutines.delay(500)
                    }
                }
            }

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setCallback(object : MediaSession.Callback {})
            .build()

        startForeground(NOTIFICATION_ID, createNotification())
    }

    private fun updateCurrentPosition() {
        exoPlayer?.let { player ->
            _playerState.update {
                it.copy(
                    currentPosition = player.currentPosition,
                    duration = player.duration,
                    bufferedPosition = player.bufferedPosition
                )
            }
        }
    }

    fun playSong(song: com.aura.music.data.model.Song, addToQueue: Boolean = false) {
        serviceScope.launch {
            try {
                _playerState.update { it.copy(isLoading = true, error = null) }

                val currentSong = _playerState.value.currentSong
                if (currentSong != null && currentSong.videoId != song.videoId) {
                    _playerState.update {
                        it.copy(history = listOf(currentSong) + it.history.take(49))
                    }
                }

                val mediaItem = MediaItem.fromUri(song.url ?: "")
                exoPlayer?.let { player ->
                    if (addToQueue && currentSong != null) {
                        val queue = _playerState.value.queue
                        if (!queue.any { it.videoId == song.videoId }) {
                            _playerState.update { it.copy(queue = queue + song) }
                        }
                        return@launch
                    }

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()

                    _playerState.update {
                        it.copy(
                            currentSong = song,
                            isPlaying = true,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _playerState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to play song"
                    )
                }
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun playNext() {
        val queue = _playerState.value.queue
        val shuffle = _playerState.value.shuffleEnabled
        val repeatMode = _playerState.value.repeatMode

        when {
            queue.isNotEmpty() -> {
                val nextSong = if (shuffle) {
                    queue.random()
                } else {
                    queue.first()
                }
                _playerState.update {
                    it.copy(queue = it.queue.filter { song -> song.videoId != nextSong.videoId })
                }
                playSong(nextSong)
            }
            repeatMode == RepeatMode.ALL && _playerState.value.history.isNotEmpty() -> {
                playSong(_playerState.value.history.first())
            }
            else -> {
                stopPlayback()
            }
        }
    }

    fun playPrevious() {
        val history = _playerState.value.history
        if (history.isNotEmpty()) {
            val prevSong = history.first()
            _playerState.update { it.copy(history = history.drop(1)) }
            playSong(prevSong)
        } else {
            exoPlayer?.seekTo(0)
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _playerState.update { it.copy(repeatMode = mode) }
        exoPlayer?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    fun toggleShuffle() {
        _playerState.update { it.copy(shuffleEnabled = !it.shuffleEnabled) }
    }

    fun setVolume(volume: Float) {
        _playerState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        exoPlayer?.volume = volume
    }

    fun addToQueue(song: com.aura.music.data.model.Song) {
        _playerState.update { state ->
            if (!state.queue.any { it.videoId == song.videoId }) {
                state.copy(queue = state.queue + song)
            } else {
                state
            }
        }
    }

    fun removeFromQueue(videoId: String) {
        _playerState.update { it.copy(queue = it.queue.filter { song -> song.videoId != videoId }) }
    }

    fun clearQueue() {
        _playerState.update { it.copy(queue = emptyList()) }
    }

    fun playPlaylist(songs: List<com.aura.music.data.model.Song>) {
        if (songs.isEmpty()) return
        val first = songs.first()
        val rest = songs.drop(1)
        _playerState.update { it.copy(queue = rest) }
        playSong(first)
    }

    private fun handleTrackEnd() {
        val repeatMode = _playerState.value.repeatMode
        when (repeatMode) {
            RepeatMode.ONE -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                playNext()
            }
        }
    }

    private fun stopPlayback() {
        exoPlayer?.stop()
        _playerState.update {
            it.copy(
                isPlaying = false,
                currentPosition = 0L
            )
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): android.app.Notification {
        val currentSong = _playerState.value.currentSong
        val isPlaying = _playerState.value.isPlaying

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            createPendingIntent(ACTION_TOGGLE_PLAY_PAUSE)
        )

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_rew,
            "Previous",
            createPendingIntent(ACTION_PREVIOUS)
        )

        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_ff,
            "Next",
            createPendingIntent(ACTION_NEXT)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentSong?.title ?: "Aura")
            .setContentText(currentSong?.getArtistString() ?: "No song playing")
            .setSmallIcon(android.R.drawable.ic_menu_slideshow)
            .setContentIntent(pendingIntent)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY_PAUSE -> togglePlayPause()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_NEXT -> playNext()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        mediaSession?.release()
        exoPlayer = null
        mediaSession = null
    }

    companion object {
        private const val CHANNEL_ID = "music_channel"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE_PLAY_PAUSE = "com.aura.music.TOGGLE_PLAY_PAUSE"
        private const val ACTION_PREVIOUS = "com.aura.music.PREVIOUS"
        private const val ACTION_NEXT = "com.aura.music.NEXT"
    }
}

