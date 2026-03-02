package com.aura.music.player

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.music.data.model.Song
import com.aura.music.data.model.withFallbackMetadata
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.di.ServiceLocator
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicService : MediaSessionService() {
    private val binder = MusicBinder()
    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var notificationManager: MusicNotificationManager? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val queueManager = PlaybackQueueManager
    private val smartAutoplayManager by lazy { SmartAutoplayManager(repository) }
    
    // Audio effects manager for equalizer, bass boost, virtualizer
    private val audioEffectsManager = AudioEffectsManager()

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Expose individual StateFlows for mini player and shared UI consumption
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val repository by lazy { ServiceLocator.getMusicRepository() }
    private val recentlyPlayedRepository by lazy { ServiceLocator.getRecentlyPlayedRepository() }
    private val firestoreRepository by lazy { FirestoreRepository() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var lastLoggedPlayKey: String? = null
    private var lastLoggedAtMs: Long = 0L
    private var currentPlaybackSource: String = "unknown"
    private val playStartThresholdMs = 2_000L
    
    // Track liked songs for notification
    private val _likedSongs = MutableStateFlow<Set<String>>(emptySet())
    private var isForegroundService = false
    private var audioEffectsInitialized = false

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        observeLikedSongs()
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
                        _isPlaying.value = isPlaying

                        val debugSong = _playerState.value.currentSong
                        Log.d(
                            TAG,
                            "onIsPlayingChanged() isPlaying=$isPlaying " +
                                "song=${debugSong?.title ?: "null"} " +
                                "posMs=${player.currentPosition}"
                        )

                        if (isPlaying) {
                            val currentSong = _playerState.value.currentSong ?: return
                            val positionMs = player.currentPosition
                            if (positionMs > playStartThresholdMs) return

                            val artists = when {
                                !currentSong.artists.isNullOrEmpty() -> currentSong.artists
                                !currentSong.artist.isNullOrBlank() -> listOfNotNull(currentSong.artist)
                                else -> emptyList()
                            }

                            val key = buildString {
                                append(currentSong.videoId)
                                append('|')
                                append(currentSong.title.lowercase())
                                append('|')
                                append((currentSong.album ?: "").lowercase())
                                append('|')
                                append(artists.joinToString("|") { it.lowercase() })
                            }

                            val now = System.currentTimeMillis()
                            if (key == lastLoggedPlayKey && now - lastLoggedAtMs < 30_000L) return

                            lastLoggedPlayKey = key
                            lastLoggedAtMs = now

                            serviceScope.launch {
                                recentlyPlayedRepository.onTrackPlayed(currentSong)

                                Log.d(
                                    TAG,
                                    "Logging play: title='${currentSong.title}', " +
                                        "album='${currentSong.album ?: ""}', " +
                                        "artists=${artists.size}"
                                )
                                firestoreRepository.logSongPlay(
                                    videoId = currentSong.videoId,
                                    songName = currentSong.title,
                                    albumName = currentSong.album ?: "",
                                    artists = artists,
                                    source = currentPlaybackSource,
                                    thumbnail = currentSong.thumbnail
                                ).onFailure { error ->
                                    Log.e(TAG, "Failed to log song play to Firestore", error)
                                }
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playerState.update { it.copy(isLoading = true) }
                            }
                            Player.STATE_READY -> {
                                _playerState.update { it.copy(isLoading = false) }
                                initializeAudioEffects()
                            }
                            Player.STATE_ENDED -> handleSongCompletion()
                        }
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
                    while (isActive) {
                        if (player.playbackState != Player.STATE_IDLE) {
                            _playerState.update {
                                it.copy(
                                    currentPosition = player.currentPosition,
                                    duration = player.duration,
                                    bufferedPosition = player.bufferedPosition
                                )
                            }
                        }
                        delay(500)
                    }
                }
            }

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setCallback(object : MediaSession.Callback {})
            .build()
            
        // Initialize notification manager
        notificationManager = MusicNotificationManager(this, mediaSession!!)
        
        // Observe player state changes to update notification
        serviceScope.launch {
            _playerState.collect { state ->
                state.currentSong?.let { song ->
                    updateNotification(song, state.isPlaying)
                }
            }
        }
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

    fun setQueueAndPlay(songs: List<Song>, startIndex: Int, source: String = "unknown") {
        if (songs.isEmpty()) {
            stopPlaybackGracefully()
            return
        }

        smartAutoplayManager.resetForUserAction()
        queueManager.setQueue(songs, startIndex)
        Log.d(TAG, "Queue set size=${songs.size} startIndex=$startIndex source=$source")
        playCurrentFromQueue(source)
    }

    fun playCurrentFromQueue(source: String = "unknown") {
        serviceScope.launch {
            val current = queueManager.getCurrentSong()
            if (current == null) {
                stopPlaybackGracefully()
                return@launch
            }

            try {
                _playerState.update { it.copy(isLoading = true, error = null) }
                val resolvedSong = if (current.url.isNullOrBlank()) {
                    resolveSong(current)
                } else {
                    current
                }
                playResolvedSongInternal(resolvedSong, source)
            } catch (e: Exception) {
                Log.e(TAG, "playCurrentFromQueue() failed", e)
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
        Log.d(TAG, "Next triggered")
        val next = queueManager.moveToNext()
        if (next == null) {
            stopPlaybackGracefully()
            return
        }
        playCurrentFromQueue(currentPlaybackSource)
    }

    fun playPrevious() {
        val previous = queueManager.moveToPrevious()
        if (previous == null) {
            exoPlayer?.seekTo(0)
            return
        }
        playCurrentFromQueue(currentPlaybackSource)
    }

    fun insertNext(song: Song) {
        queueManager.insertNext(song)
        Log.d(TAG, "Play Next inserted videoId=${song.videoId}")
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playerState.update { state ->
            state.copy(currentPosition = position.coerceAtLeast(0L))
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        queueManager.setRepeatMode(mode)
        _playerState.update { it.copy(repeatMode = mode) }
        exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
    }

    fun toggleShuffle() {
        val enabled = !queueManager.isShuffleEnabled()
        setShuffleEnabled(enabled)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        queueManager.setShuffle(enabled)
        _playerState.update { it.copy(shuffleEnabled = enabled) }
        Log.d(TAG, "Shuffle enabled=$enabled")
    }

    fun setVolume(volume: Float) {
        _playerState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        exoPlayer?.volume = volume
    }

    fun clearQueue() {
        queueManager.clearQueue()
    }
    
    fun getQueue(): List<Song> {
        return queueManager.getQueue()
    }
    
    fun getCurrentQueueIndex(): Int {
        return queueManager.getCurrentIndex()
    }
    
    /**
     * Gets the audio session ID from the ExoPlayer for equalizer integration.
     * Returns null if player is not initialized.
     */
    fun getAudioSessionId(): Int? {
        return exoPlayer?.audioSessionId
    }

    /**
     * Expose audio effects manager so UI can access it.
     */
    fun getAudioEffectsManager(): AudioEffectsManager = audioEffectsManager

    /**
     * Initializes audio effects for the current audio session.
     * Call when player is ready and has a valid audio session.
     */
    private fun initializeAudioEffects() {
        if (audioEffectsInitialized) return

        exoPlayer?.audioSessionId?.let { sessionId ->
            if (sessionId > 0) {
                val success = audioEffectsManager.initialize(sessionId)
                if (success) {
                    audioEffectsInitialized = true
                    Log.d(TAG, "Audio effects initialized for session: $sessionId")
                } else {
                    Log.w(TAG, "Failed to initialize audio effects for session: $sessionId")
                }
            }
        }
    }

    private fun handleSongCompletion() {
        when (queueManager.getRepeatMode()) {
            RepeatMode.REPEAT_ONE -> {
                Log.d(TAG, "Repeat one triggered")
                playCurrentFromQueue(currentPlaybackSource)
            }
            RepeatMode.REPEAT_ALL -> {
                Log.d(TAG, "Repeat all triggered")
                val next = queueManager.moveToNext()
                if (next != null) {
                    playCurrentFromQueue(currentPlaybackSource)
                } else {
                    stopPlaybackGracefully()
                }
            }
            RepeatMode.NONE -> {
                val next = queueManager.moveToNext()
                if (next != null) {
                    playCurrentFromQueue(currentPlaybackSource)
                } else {
                    attemptSmartAutoplay()
                }
            }
        }
    }

    private fun attemptSmartAutoplay() {
        serviceScope.launch {
            val autoplayQueue = smartAutoplayManager.getAutoplayQueue()
            if (autoplayQueue.isNotEmpty()) {
                queueManager.setQueue(autoplayQueue, 0)
                Log.d(TAG, "Smart autoplay queue set size=${autoplayQueue.size}")
                playCurrentFromQueue("smart_autoplay")
            } else {
                stopPlaybackGracefully()
            }
        }
    }

    private fun stopPlaybackGracefully() {
        exoPlayer?.stop()
        _playerState.update {
            it.copy(
                isPlaying = false,
                isLoading = false,
                currentPosition = 0L
            )
        }
        _isPlaying.value = false
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle notification action intents
        when (intent?.action) {
            MusicNotificationManager.ACTION_PLAY_PAUSE -> togglePlayPause()
            MusicNotificationManager.ACTION_PREVIOUS -> playPrevious()
            MusicNotificationManager.ACTION_NEXT -> playNext()
            MusicNotificationManager.ACTION_LIKE -> toggleLike()
            MusicNotificationManager.ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
            // Release audio effects
            audioEffectsManager.release()
            audioEffectsInitialized = false
        if (isForegroundService) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundService = false
        }
        notificationManager?.cancelNotification()
        exoPlayer?.release()
        mediaSession?.release()
        exoPlayer = null
        mediaSession = null
        notificationManager = null
    }

    private suspend fun resolveSong(song: Song): Song {
        if (song.videoId.isBlank()) {
            throw IllegalStateException("Video ID missing for ${song.title}")
        }
        return withContext(Dispatchers.IO) {
            repository.getSong(song.videoId)
        }.onFailure { throwable ->
            throw IllegalStateException(throwable.message ?: "Unable to get stream URL", throwable)
        }.getOrThrow()
            .withFallbackMetadata(song)
            .also { resolved ->
                if (resolved.url.isNullOrBlank()) {
                    throw IllegalStateException("Stream URL missing for ${song.title}")
                }
            }
    }

    private fun playResolvedSongInternal(resolvedSong: Song, source: String) {
        currentPlaybackSource = source.ifBlank { "unknown" }

        val mediaItem = MediaItem.fromUri(resolvedSong.url!!)
        exoPlayer?.let { player ->
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            _playerState.update {
                it.copy(
                    currentSong = resolvedSong,
                    playbackSource = currentPlaybackSource,
                    isPlaying = true,
                    isLoading = false
                )
            }
            // Update individual StateFlows for mini player
            _currentSong.value = resolvedSong
            _isPlaying.value = true
        }
    }
    
    /**
     * Update notification with current song and state
     */
    private fun updateNotification(song: Song, isPlaying: Boolean) {
        val isLiked = _likedSongs.value.contains(song.videoId)
        notificationManager?.updateNotification(song, isPlaying, isLiked)
        
        // Start foreground service if playing
        if (isPlaying && !isForegroundService) {
            serviceScope.launch {
                try {
                    val notification = notificationManager?.buildNotification(song, isPlaying, isLiked)
                    notification?.let {
                        startForeground(MusicNotificationManager.NOTIFICATION_ID, it)
                        isForegroundService = true
                        Log.d(TAG, "Started foreground service")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start foreground service", e)
                }
            }
        }
    }
    
    /**
     * Observe liked songs from Firestore
     */
    private fun observeLikedSongs() {
        serviceScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId.isNullOrBlank()) {
                    Log.w(TAG, "No user ID available for observing liked songs")
                    return@launch
                }
                
                firestoreRepository.getLikedSongs(userId).collect { likedSongs ->
                    val likedIds = likedSongs.map { it.videoId }.toSet()
                    _likedSongs.value = likedIds
                    Log.d(TAG, "Liked songs updated: ${likedIds.size} songs")
                    
                    // Update notification if currently playing song's like status changed
                    _playerState.value.currentSong?.let { song ->
                        updateNotification(song, _playerState.value.isPlaying)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to observe liked songs", e)
            }
        }
    }
    
    /**
     * Toggle like status of current song
     */
    private fun toggleLike() {
        val currentSong = _playerState.value.currentSong ?: return
        val isLiked = _likedSongs.value.contains(currentSong.videoId)
        
        serviceScope.launch {
            try {
                val userId = auth.currentUser?.uid
                if (userId.isNullOrBlank()) {
                    Log.w(TAG, "No user ID available for toggling like")
                    return@launch
                }
                
                if (isLiked) {
                    firestoreRepository.removeFromLikedSongs(userId, currentSong.videoId)
                        .onSuccess {
                            Log.d(TAG, "Removed from liked songs: ${currentSong.title}")
                        }
                } else {
                    firestoreRepository.addToLikedSongs(userId, currentSong)
                        .onSuccess {
                            Log.d(TAG, "Added to liked songs: ${currentSong.title}")
                        }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle like", e)
            }
        }
    }

    companion object {
        private const val TAG = "MusicService"
    }
}

