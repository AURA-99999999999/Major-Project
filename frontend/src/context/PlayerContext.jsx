import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import { musicService } from '../services/musicService'
import { userService } from '../services/userService'
import toast from 'react-hot-toast'
import { validatePlayerState } from '../utils/playerValidation'

/**
 * @type {React.Context<import('../types/player').PlayerState | undefined>}
 */
const PlayerContext = createContext(undefined)

/**
 * Hook to access player context
 * @returns {import('../types/player').PlayerState}
 * @throws {Error} If used outside PlayerProvider
 */
export const usePlayer = () => {
  const context = useContext(PlayerContext)
  if (!context) {
    throw new Error('usePlayer must be used within PlayerProvider. Make sure PlayerProvider wraps your component tree.')
  }
  
  // In development, validate the state structure
  if (process.env.NODE_ENV === 'development') {
    try {
      validatePlayerState(context)
    } catch (error) {
      console.error('Player state validation failed:', error)
      // Don't throw in production, but log the error
      if (process.env.NODE_ENV === 'development') {
        throw error
      }
    }
  }
  
  return context
}

export const PlayerProvider = ({ children }) => {
  const [currentSong, setCurrentSong] = useState(null)
  const [queue, setQueue] = useState([])
  const [history, setHistory] = useState([])
  const [isPlaying, setIsPlaying] = useState(false)
  const [volume, setVolume] = useState(() => {
    const saved = localStorage.getItem('playerVolume')
    return saved ? parseFloat(saved) : 1
  })
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [buffered, setBuffered] = useState(0)
  const [repeatMode, setRepeatMode] = useState('off') // 'off', 'one', 'all'
  const [shuffle, setShuffle] = useState(false)
  const [loading, setLoading] = useState(false)
  const [isSeeking, setIsSeeking] = useState(false)

  const audioRef = useRef(null)
  const timeUpdateIntervalRef = useRef(null)
  const user = JSON.parse(localStorage.getItem('user') || 'null')
  
  // Refs for event handlers to access current state and functions
  const handlersRef = useRef({
    repeatMode,
    currentSong,
    queue,
    history,
    playSongInternal: null,
    playNext: null,
  })
  
  // Update refs when state/functions change
  useEffect(() => {
    handlersRef.current.repeatMode = repeatMode
    handlersRef.current.currentSong = currentSong
    handlersRef.current.queue = queue
    handlersRef.current.history = history
  }, [repeatMode, currentSong, queue, history])

  // Initialize audio element once
  useEffect(() => {
    if (!audioRef.current) {
      audioRef.current = new Audio()
      audioRef.current.preload = 'metadata'
      
      // Use stable event handlers
      const handleTimeUpdate = () => {
        if (audioRef.current && !isSeeking) {
          setCurrentTime(audioRef.current.currentTime)
          // Update buffered
          if (audioRef.current.buffered.length > 0) {
            const bufferedEnd = audioRef.current.buffered.end(audioRef.current.buffered.length - 1)
            setBuffered(bufferedEnd)
          }
        }
      }

      const handleLoadedMetadata = () => {
        if (audioRef.current) {
          const dur = audioRef.current.duration
          if (dur && isFinite(dur)) {
            setDuration(dur)
          }
        }
      }

      const handleLoadedData = () => {
        if (audioRef.current) {
          const dur = audioRef.current.duration
          if (dur && isFinite(dur)) {
            setDuration(dur)
          }
        }
      }

      const handleDurationChange = () => {
        if (audioRef.current) {
          const dur = audioRef.current.duration
          if (dur && isFinite(dur)) {
            setDuration(dur)
          }
        }
      }

      const handleCanPlay = () => {
        setLoading(false)
      }

      const handleWaiting = () => {
        setLoading(true)
      }

      const handlePlaying = () => {
        setLoading(false)
        setIsPlaying(true)
      }

      const handlePause = () => {
        setIsPlaying(false)
      }

      const handleEnded = () => {
        setIsPlaying(false)
        setCurrentTime(0)
        
        // Use refs to get current values
        const state = handlersRef.current
        if (!state.playSongInternal || !state.playNext) {
          // Functions not ready yet, will be set below
          return
        }
        
        if (state.repeatMode === 'one') {
          if (state.currentSong) {
            state.playSongInternal(state.currentSong.videoId)
          }
        } else if (state.queue.length > 0) {
          state.playNext()
        } else if (state.repeatMode === 'all' && state.history.length > 0) {
          if (state.history[0]) {
            state.playSongInternal(state.history[0].videoId)
          }
        }
      }

      const handleError = (e) => {
        console.error('Audio error:', e)
        toast.error('Failed to load audio. Please try another song.')
        setLoading(false)
        setIsPlaying(false)
      }

      // Add all event listeners
      audioRef.current.addEventListener('timeupdate', handleTimeUpdate)
      audioRef.current.addEventListener('loadedmetadata', handleLoadedMetadata)
      audioRef.current.addEventListener('loadeddata', handleLoadedData)
      audioRef.current.addEventListener('durationchange', handleDurationChange)
      audioRef.current.addEventListener('canplay', handleCanPlay)
      audioRef.current.addEventListener('waiting', handleWaiting)
      audioRef.current.addEventListener('playing', handlePlaying)
      audioRef.current.addEventListener('pause', handlePause)
      audioRef.current.addEventListener('ended', handleEnded)
      audioRef.current.addEventListener('error', handleError)

      // Store handlers for cleanup
      audioRef.current._handlers = {
        handleTimeUpdate,
        handleLoadedMetadata,
        handleLoadedData,
        handleDurationChange,
        handleCanPlay,
        handleWaiting,
        handlePlaying,
        handlePause,
        handleEnded,
        handleError,
      }

      // Set initial volume
      audioRef.current.volume = volume
    }

    return () => {
      // Cleanup will happen on unmount
      if (audioRef.current && audioRef.current._handlers) {
        const h = audioRef.current._handlers
        audioRef.current.removeEventListener('timeupdate', h.handleTimeUpdate)
        audioRef.current.removeEventListener('loadedmetadata', h.handleLoadedMetadata)
        audioRef.current.removeEventListener('loadeddata', h.handleLoadedData)
        audioRef.current.removeEventListener('durationchange', h.handleDurationChange)
        audioRef.current.removeEventListener('canplay', h.handleCanPlay)
        audioRef.current.removeEventListener('waiting', h.handleWaiting)
        audioRef.current.removeEventListener('playing', h.handlePlaying)
        audioRef.current.removeEventListener('pause', h.handlePause)
        audioRef.current.removeEventListener('ended', h.handleEnded)
        audioRef.current.removeEventListener('error', h.handleError)
      }
    }
  }, []) // Empty deps - only run once

  // Volume control
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume
      localStorage.setItem('playerVolume', volume.toString())
    }
  }, [volume])

  // Internal play song function
  const playSongInternal = useCallback(async (videoId) => {
    try {
      setLoading(true)
      const response = await musicService.getSong(videoId)
      
      if (!response.success) {
        throw new Error(response.error || 'Failed to load song')
      }

      const song = response.data

      // Add current song to history
      setCurrentSong((prevSong) => {
        if (prevSong && prevSong.videoId !== song.videoId) {
          setHistory((h) => [prevSong, ...h.slice(0, 49)])
        }
        return song
      })

      // Load new audio source
      if (audioRef.current) {
        // Reset states
        setCurrentTime(0)
        setDuration(0)
        setBuffered(0)
        
        // Set new source
        audioRef.current.src = song.url
        audioRef.current.load()
        
        // Wait for metadata to load before playing
        const playWhenReady = () => {
          if (audioRef.current) {
            audioRef.current.play()
              .then(() => {
                setIsPlaying(true)
                setLoading(false)
              })
              .catch((err) => {
                console.error('Play error:', err)
                toast.error('Failed to play audio. Please try again.')
                setIsPlaying(false)
                setLoading(false)
              })
            audioRef.current.removeEventListener('loadedmetadata', playWhenReady)
          }
        }
        
        audioRef.current.addEventListener('loadedmetadata', playWhenReady)
      }

      // Add to recently played if user is logged in
      if (user?.id) {
        userService.addRecentlyPlayed(user.id, song).catch(console.error)
      }
    } catch (error) {
      console.error('Play song error:', error)
      toast.error(error.message || 'Failed to play song')
      setLoading(false)
      setIsPlaying(false)
    }
  }, [user])

  // Play/Pause control
  const togglePlayPause = useCallback(() => {
    if (!currentSong || !audioRef.current) return
    
    if (isPlaying) {
      audioRef.current.pause()
    } else {
      audioRef.current.play()
        .then(() => setIsPlaying(true))
        .catch((err) => {
          console.error('Play error:', err)
          toast.error('Failed to play audio')
          setIsPlaying(false)
        })
    }
  }, [currentSong, isPlaying])

  const playSong = useCallback((videoId, addToQueue = false) => {
    if (addToQueue && currentSong) {
      // Find song in queue or add it
      const songToQueue = queue.find(s => s.videoId === videoId) || { videoId }
      if (!queue.find(s => s.videoId === videoId)) {
        setQueue((prev) => [...prev, songToQueue])
      }
      return
    }
    playSongInternal(videoId)
  }, [playSongInternal, currentSong, queue])

  const playNext = useCallback(() => {
    if (queue.length > 0) {
      const nextSong = shuffle
        ? queue[Math.floor(Math.random() * queue.length)]
        : queue[0]
      
      setQueue((prev) => prev.filter((s) => s.videoId !== nextSong.videoId))
      playSongInternal(nextSong.videoId)
    } else if (repeatMode === 'all' && history.length > 0 && history[0]) {
      playSongInternal(history[0].videoId)
    } else {
      setIsPlaying(false)
      setCurrentTime(0)
    }
  }, [queue, shuffle, repeatMode, history, playSongInternal])

  // Update function refs
  useEffect(() => {
    handlersRef.current.playSongInternal = playSongInternal
    handlersRef.current.playNext = playNext
  }, [playSongInternal, playNext])

  const playPrevious = useCallback(() => {
    if (history.length > 0 && history[0]) {
      const prevSong = history[0]
      setHistory((prev) => prev.slice(1))
      playSongInternal(prevSong.videoId)
    } else if (currentSong) {
      // Restart current song if no history
      if (audioRef.current) {
        audioRef.current.currentTime = 0
        audioRef.current.play().catch(console.error)
      }
    }
  }, [history, currentSong, playSongInternal])

  const addToQueue = useCallback((song) => {
    setQueue((prev) => {
      if (!prev.find(s => s.videoId === song.videoId)) {
        return [...prev, song]
      }
      return prev
    })
    toast.success('Added to queue')
  }, [])

  const removeFromQueue = useCallback((videoId) => {
    setQueue((prev) => prev.filter((s) => s.videoId !== videoId))
  }, [])

  const clearQueue = useCallback(() => {
    setQueue([])
  }, [])

  const seekTo = useCallback((time) => {
    if (audioRef.current && duration > 0) {
      const seekTime = Math.max(0, Math.min(time, duration))
      setIsSeeking(true)
      audioRef.current.currentTime = seekTime
      setCurrentTime(seekTime)
      
      // Reset seeking flag after a short delay
      setTimeout(() => setIsSeeking(false), 100)
    }
  }, [duration])

  const toggleRepeat = useCallback(() => {
    const modes = ['off', 'all', 'one']
    const currentIndex = modes.indexOf(repeatMode)
    const nextMode = modes[(currentIndex + 1) % modes.length]
    setRepeatMode(nextMode)
    
    const messages = {
      off: 'Repeat: Off',
      all: 'Repeat: All',
      one: 'Repeat: One'
    }
    toast.success(messages[nextMode])
  }, [repeatMode])

  const toggleShuffle = useCallback(() => {
    setShuffle((prev) => {
      toast.success(`Shuffle: ${!prev ? 'On' : 'Off'}`)
      return !prev
    })
  }, [])

  const playPlaylist = useCallback((songs) => {
    if (songs.length === 0) return
    const [first, ...rest] = songs
    setQueue(rest)
    playSongInternal(first.videoId)
  }, [playSongInternal])

  const value = {
    currentSong,
    queue,
    history,
    isPlaying,
    volume,
    setVolume,
    currentTime,
    duration,
    buffered,
    repeatMode,
    shuffle,
    loading,
    isSeeking,
    playSong,
    togglePlayPause,
    playNext,
    playPrevious,
    addToQueue,
    removeFromQueue,
    clearQueue,
    seekTo,
    toggleRepeat,
    toggleShuffle,
    playPlaylist,
  }

  return (
    <PlayerContext.Provider value={value}>
      {children}
    </PlayerContext.Provider>
  )
}
