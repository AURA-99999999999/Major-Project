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
import com.aura.music.MainActivity
import com.aura.music.data.model.Song
import com.aura.music.data.model.withFallbackMetadata
import com.aura.music.data.repository.FirestoreRepository
import com.aura.music.di.ServiceLocator
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    // Expose individual StateFlows for mini player and shared UI consumption
    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val repository by lazy { ServiceLocator.getMusicRepository() }
    private val firestoreRepository by lazy { FirestoreRepository() }

    private var lastLoggedPlayKey: String? = null
    private var lastLoggedAtMs: Long = 0L
    private var currentPlaybackSource: String = "unknown"
    private val playStartThresholdMs = 2_000L

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onCreate() {
        super.onCreate()
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
                                    source = currentPlaybackSource
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
                            }
                            Player.STATE_ENDED -> {
                                handleTrackEnd()
                            }
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

    fun playSong(song: Song, addToQueue: Boolean = false, source: String = "unknown") {
        serviceScope.launch {
            try {
                _playerState.update { it.copy(isLoading = true, error = null) }
                val resolvedSong = resolveSong(song)
                playResolvedSongInternal(resolvedSong, addToQueue, source)
            } catch (e: Exception) {
                Log.e(TAG, "playSong() failed", e)
                _playerState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to play song"
                    )
                }
            }
        }
    }

    fun playResolvedSong(song: Song, addToQueue: Boolean = false, source: String = "unknown") {
        serviceScope.launch {
            try {
                _playerState.update { it.copy(isLoading = true, error = null) }
                if (song.url.isNullOrBlank()) {
                    throw IllegalStateException("Stream URL missing for ${song.title}")
                }
                playResolvedSongInternal(song, addToQueue, source)
            } catch (e: Exception) {
                Log.e(TAG, "playResolvedSong() failed", e)
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
        _playerState.update { state ->
            state.copy(currentPosition = position.coerceAtLeast(0L))
        }
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

    fun setShuffleEnabled(enabled: Boolean) {
        _playerState.update { it.copy(shuffleEnabled = enabled) }
    }

    fun setVolume(volume: Float) {
        _playerState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
        exoPlayer?.volume = volume
    }

    fun addToQueue(song: Song) {
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

    fun playPlaylist(songs: List<Song>) {
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
        _isPlaying.value = false
    }



    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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

    private fun playResolvedSongInternal(resolvedSong: Song, addToQueue: Boolean, source: String) {
        currentPlaybackSource = source.ifBlank { "unknown" }
        val currentSong = _playerState.value.currentSong
        if (currentSong != null && currentSong.videoId != resolvedSong.videoId) {
            _playerState.update {
                it.copy(history = listOf(currentSong) + it.history.take(49))
            }
        }

        val mediaItem = MediaItem.fromUri(resolvedSong.url!!)
        exoPlayer?.let { player ->
            if (addToQueue && currentSong != null) {
                val queue = _playerState.value.queue
                if (!queue.any { it.videoId == resolvedSong.videoId }) {
                    _playerState.update { it.copy(queue = queue + resolvedSong) }
                }
                return
            }

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

    companion object {
        private const val TAG = "MusicService"
    }
}

