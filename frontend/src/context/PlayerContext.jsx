import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react'
import { musicService } from '../services/musicService'
import { userService } from '../services/userService'
import toast from 'react-hot-toast'

const PlayerContext = createContext()

export const usePlayer = () => {
  const context = useContext(PlayerContext)
  if (!context) {
    throw new Error('usePlayer must be used within PlayerProvider')
  }
  return context
}

export const PlayerProvider = ({ children }) => {
  const [currentSong, setCurrentSong] = useState(null)
  const [queue, setQueue] = useState([])
  const [history, setHistory] = useState([])
  const [isPlaying, setIsPlaying] = useState(false)
  const [volume, setVolume] = useState(1)
  const [currentTime, setCurrentTime] = useState(0)
  const [duration, setDuration] = useState(0)
  const [repeatMode, setRepeatMode] = useState('off') // 'off', 'one', 'all'
  const [shuffle, setShuffle] = useState(false)
  const [loading, setLoading] = useState(false)

  const audioRef = useRef(null)
  const user = JSON.parse(localStorage.getItem('user') || 'null')

  // Load audio element
  useEffect(() => {
    if (!audioRef.current) {
      audioRef.current = new Audio()
      audioRef.current.addEventListener('timeupdate', handleTimeUpdate)
      audioRef.current.addEventListener('loadedmetadata', handleLoadedMetadata)
      audioRef.current.addEventListener('ended', handleEnded)
      audioRef.current.addEventListener('error', handleError)
    }
    return () => {
      if (audioRef.current) {
        audioRef.current.removeEventListener('timeupdate', handleTimeUpdate)
        audioRef.current.removeEventListener('loadedmetadata', handleLoadedMetadata)
        audioRef.current.removeEventListener('ended', handleEnded)
        audioRef.current.removeEventListener('error', handleError)
      }
    }
  }, [])

  const handleTimeUpdate = () => {
    if (audioRef.current) {
      setCurrentTime(audioRef.current.currentTime)
    }
  }

  const handleLoadedMetadata = () => {
    if (audioRef.current) {
      setDuration(audioRef.current.duration)
    }
  }

  const handleEnded = () => {
    if (repeatMode === 'one') {
      playSong(currentSong.videoId)
    } else if (queue.length > 0) {
      playNext()
    } else if (repeatMode === 'all' && history.length > 0) {
      playSong(history[0].videoId)
    } else {
      setIsPlaying(false)
    }
  }

  const handleError = () => {
    toast.error('Failed to load audio')
    setLoading(false)
    setIsPlaying(false)
  }

  // Volume control
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.volume = volume
    }
  }, [volume])

  // Play/Pause
  useEffect(() => {
    if (audioRef.current) {
      if (isPlaying) {
        audioRef.current.play().catch(() => {
          setIsPlaying(false)
        })
      } else {
        audioRef.current.pause()
      }
    }
  }, [isPlaying])

  const playSong = useCallback(async (videoId, addToQueue = false) => {
    try {
      setLoading(true)
      const response = await musicService.getSong(videoId)
      
      if (!response.success) {
        throw new Error(response.error || 'Failed to load song')
      }

      const song = response.data

      // Add to history
      if (currentSong) {
        setHistory((prev) => [currentSong, ...prev.slice(0, 49)])
      }

      // Set current song
      setCurrentSong(song)

      // Load audio
      if (audioRef.current) {
        audioRef.current.src = song.url
        audioRef.current.load()
        setIsPlaying(true)
      }

      // Add to recently played if user is logged in
      if (user?.id) {
        userService.addRecentlyPlayed(user.id, song).catch(console.error)
      }

      setLoading(false)
    } catch (error) {
      toast.error(error.message || 'Failed to play song')
      setLoading(false)
      setIsPlaying(false)
    }
  }, [currentSong, user])

  const togglePlayPause = () => {
    if (!currentSong) return
    setIsPlaying((prev) => !prev)
  }

  const playNext = () => {
    if (queue.length > 0) {
      const nextSong = shuffle
        ? queue[Math.floor(Math.random() * queue.length)]
        : queue[0]
      
      setQueue((prev) => prev.filter((s) => s.videoId !== nextSong.videoId))
      playSong(nextSong.videoId)
    } else if (repeatMode === 'all' && history.length > 0) {
      playSong(history[0].videoId)
    }
  }

  const playPrevious = () => {
    if (history.length > 0) {
      const prevSong = history[0]
      setHistory((prev) => prev.slice(1))
      playSong(prevSong.videoId)
    }
  }

  const addToQueue = (song) => {
    setQueue((prev) => [...prev, song])
    toast.success('Added to queue')
  }

  const removeFromQueue = (videoId) => {
    setQueue((prev) => prev.filter((s) => s.videoId !== videoId))
  }

  const clearQueue = () => {
    setQueue([])
  }

  const seekTo = (time) => {
    if (audioRef.current) {
      audioRef.current.currentTime = time
      setCurrentTime(time)
    }
  }

  const toggleRepeat = () => {
    const modes = ['off', 'all', 'one']
    const currentIndex = modes.indexOf(repeatMode)
    const nextMode = modes[(currentIndex + 1) % modes.length]
    setRepeatMode(nextMode)
    toast.success(`Repeat: ${nextMode}`)
  }

  const toggleShuffle = () => {
    setShuffle((prev) => !prev)
    toast.success(`Shuffle: ${!shuffle ? 'on' : 'off'}`)
  }

  const playPlaylist = (songs) => {
    if (songs.length === 0) return
    const [first, ...rest] = songs
    setQueue(rest)
    playSong(first.videoId)
  }

  const value = {
    currentSong,
    queue,
    history,
    isPlaying,
    volume,
    setVolume,
    currentTime,
    duration,
    repeatMode,
    shuffle,
    loading,
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

