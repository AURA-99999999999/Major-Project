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
import java.io.File
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
    private val smartQueueManager by lazy { QueueManager(repository, recentlyPlayedRepository) }
    private var currentQueueContext: QueueContext = QueueContext.SINGLE_SONG
    
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
    private val downloadsRepository by lazy { ServiceLocator.getDownloadsRepository() }
    private val firestoreRepository by lazy { FirestoreRepository() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private var lastLoggedPlayKey: String? = null
    private var lastLoggedAtMs: Long = 0L
    private var currentPlaybackSource: String = "unknown"
    private val playStartThresholdMs = 2_000L
    private val minQueueBuffer = 5
    private var extensionInProgress = false
    
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
                        _playerState.update { state ->
                            val uiState = when {
                                state.error != null -> PlaybackUiState.ERROR
                                state.isLoading -> PlaybackUiState.LOADING
                                isPlaying -> PlaybackUiState.PLAYING
                                state.currentSong != null -> PlaybackUiState.PAUSED
                                else -> PlaybackUiState.IDLE
                            }
                            state.copy(isPlaying = isPlaying, isLoading = false, uiState = uiState)
                        }
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
                                repository.trackPlayEvent(currentSong)
                                    .onFailure { error ->
                                        Log.e(TAG, "Failed to track play via backend", error)
                                    }
                            }
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _playerState.update { it.copy(isLoading = true, uiState = PlaybackUiState.LOADING) }
                            }
                            Player.STATE_READY -> {
                                _playerState.update { state ->
                                    val uiState = when {
                                        state.error != null -> PlaybackUiState.ERROR
                                        state.isPlaying -> PlaybackUiState.PLAYING
                                        state.currentSong != null -> PlaybackUiState.PAUSED
                                        else -> PlaybackUiState.IDLE
                                    }
                                    state.copy(isLoading = false, uiState = uiState)
                                }
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

        val safeStartIndex = startIndex.coerceIn(0, songs.lastIndex)
        val selectedSong = songs[safeStartIndex]

        smartAutoplayManager.resetForUserAction()
        serviceScope.launch {
            currentQueueContext = smartQueueManager.resolveContext(source, songs)

            val generatedQueue = runCatching {
                smartQueueManager.generateQueue(
                    selectedSong = selectedSong,
                    context = currentQueueContext,
                    sourceList = songs,
                    startIndex = safeStartIndex,
                    desiredSize = QueueManager.DEFAULT_QUEUE_LENGTH
                )
            }.getOrElse {
                Log.w(TAG, "Smart queue generation failed. Falling back to legacy ordering.", it)
                songs.drop(safeStartIndex) + songs.take(safeStartIndex)
            }

            val playbackQueue = generatedQueue.ifEmpty {
                songs.drop(safeStartIndex) + songs.take(safeStartIndex)
            }

            val startAt = playbackQueue.indexOfFirst { it.videoId == selectedSong.videoId }
                .takeIf { it >= 0 }
                ?: 0

            queueManager.setQueue(playbackQueue, startAt)
            Log.d(
                TAG,
                "Queue set size=${playbackQueue.size} startIndex=$startAt source=$source context=$currentQueueContext"
            )
            playCurrentFromQueue(source)
            ensureQueueBuffer()
        }
    }

    fun startRadio(seedSong: Song) {
        setQueueAndPlay(
            songs = listOf(seedSong),
            startIndex = 0,
            source = "radio"
        )
    }

    fun playCurrentFromQueue(source: String = "unknown") {
        serviceScope.launch {
            val current = queueManager.getCurrentSong()
            if (current == null) {
                stopPlaybackGracefully()
                return@launch
            }

            try {
                _playerState.update {
                    it.copy(
                        currentSong = current,
                        playbackSource = source,
                        isLoading = true,
                        uiState = PlaybackUiState.LOADING,
                        error = null
                    )
                }
                _currentSong.value = current
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
                        uiState = PlaybackUiState.ERROR,
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
        ensureQueueBuffer()
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
        currentQueueContext = QueueContext.SINGLE_SONG
    }
    
    fun getQueue(): List<Song> {
        return queueManager.getQueue()
    }
    
    fun getCurrentQueueIndex(): Int {
        return queueManager.getCurrentIndex()
    }

    fun getQueueState(): QueueState {
        return queueManager.queueState.value
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int): Boolean {
        return queueManager.reorderQueue(fromIndex, toIndex)
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
                    ensureQueueBuffer()
                } else {
                    attemptContextAwareExtensionOrAutoplay()
                }
            }
        }
    }

    private fun ensureQueueBuffer() {
        serviceScope.launch {
            if (!shouldExtendCurrentContext()) return@launch

            val remaining = queueManager.getRemainingCountAfterCurrent()
            if (remaining >= minQueueBuffer) return@launch
            if (extensionInProgress) return@launch

            extensionInProgress = true
            try {
                val current = queueManager.getCurrentSong() ?: return@launch
                val extension = smartQueueManager.extendQueue(
                    currentQueue = queueManager.getQueue(),
                    currentSong = current,
                    context = currentQueueContext,
                    targetSize = minQueueBuffer * 2
                )
                if (extension.isNotEmpty()) {
                    queueManager.appendToQueue(extension)
                    Log.d(
                        TAG,
                        "Queue proactively extended by ${extension.size} songs; remaining=$remaining"
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to proactively extend queue", e)
            } finally {
                extensionInProgress = false
            }
        }
    }

    private fun shouldExtendCurrentContext(): Boolean {
        return when (currentQueueContext) {
            QueueContext.DAILY_MIX,
            QueueContext.MOOD_MIX,
            QueueContext.SEARCH,
            QueueContext.ARTIST,
            QueueContext.RADIO,
            QueueContext.RECENTLY_PLAYED,
            QueueContext.RECOMMENDATIONS,
            QueueContext.SINGLE_SONG -> true

            QueueContext.PLAYLIST,
            QueueContext.ALBUM,
            QueueContext.LIKED_SONGS -> false
        }
    }

    private fun attemptContextAwareExtensionOrAutoplay() {
        serviceScope.launch {
            val current = queueManager.getCurrentSong() ?: _playerState.value.currentSong
            if (current == null) {
                attemptSmartAutoplay()
                return@launch
            }

            if (!shouldExtendCurrentContext()) {
                attemptSmartAutoplay()
                return@launch
            }

            val extension = smartQueueManager.extendQueue(
                currentQueue = queueManager.getQueue(),
                currentSong = current,
                context = currentQueueContext,
                targetSize = 15
            )

            if (extension.isNotEmpty()) {
                queueManager.appendToQueue(extension)
                val next = queueManager.moveToNext()
                if (next != null) {
                    Log.d(TAG, "Queue auto-extended with ${extension.size} songs (context=$currentQueueContext)")
                    playCurrentFromQueue(currentPlaybackSource)
                    return@launch
                }
            }

            attemptSmartAutoplay()
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
                currentSong = null,
                playbackSource = null,
                isPlaying = false,
                isLoading = false,
                uiState = PlaybackUiState.IDLE,
                currentPosition = 0L,
                duration = 0L,
                bufferedPosition = 0L,
                error = null
            )
        }
        _currentSong.value = null
        _isPlaying.value = false
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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

    private suspend fun playResolvedSongInternal(resolvedSong: Song, source: String) {
        currentPlaybackSource = source.ifBlank { "unknown" }

        // Prefer local downloaded file if available and present on disk
        val localPath = downloadsRepository.getLocalFilePath(resolvedSong.videoId)
        val mediaUri = if (localPath != null && File(localPath).exists()) {
            android.net.Uri.fromFile(File(localPath))
        } else {
            android.net.Uri.parse(resolvedSong.url!!)
        }

        val mediaItem = MediaItem.fromUri(mediaUri)
        exoPlayer?.let { player ->
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()

            _playerState.update {
                it.copy(
                    currentSong = resolvedSong,
                    playbackSource = currentPlaybackSource,
                    isPlaying = true,
                    isLoading = false,
                    uiState = PlaybackUiState.PLAYING
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
                    repository.trackLikeEvent(currentSong)
                        .onSuccess {
                            Log.d(TAG, "Added to liked songs via backend: ${currentSong.title}")
                        }
                        .onFailure { error ->
                            Log.e(TAG, "Failed to add to liked songs via backend", error)
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

