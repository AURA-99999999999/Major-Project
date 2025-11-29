import { motion } from 'framer-motion'
import {
  FaPlay,
  FaPause,
  FaStepForward,
  FaStepBackward,
  FaRandom,
  FaRedo,
  FaVolumeUp,
  FaVolumeMute,
} from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import { useEffect, useState } from 'react'

const FullPlayer = () => {
  const {
    currentSong,
    isPlaying,
    togglePlayPause,
    playNext,
    playPrevious,
    volume,
    setVolume,
    currentTime,
    duration,
    seekTo,
    repeatMode,
    shuffle,
    toggleRepeat,
    toggleShuffle,
    queue,
  } = usePlayer()

  const [isMuted, setIsMuted] = useState(false)
  const [previousVolume, setPreviousVolume] = useState(1)

  useEffect(() => {
    if (isMuted) {
      setPreviousVolume(volume)
      setVolume(0)
    } else {
      setVolume(previousVolume)
    }
  }, [isMuted])

  if (!currentSong) {
    return (
      <div className="flex items-center justify-center h-full">
        <p className="text-dark-400">No song playing</p>
      </div>
    )
  }

  const formatTime = (seconds) => {
    if (!seconds || isNaN(seconds)) return '0:00'
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0

  const handleProgressClick = (e) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const percent = (e.clientX - rect.left) / rect.width
    seekTo(percent * duration)
  }

  return (
    <div className="h-full flex flex-col items-center justify-center p-8">
      {/* Album Art */}
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        className="relative mb-8 w-80 h-80 rounded-2xl shadow-2xl overflow-hidden bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center"
      >
        {currentSong.thumbnail ? (
          <motion.img
            src={currentSong.thumbnail}
            alt={currentSong.title}
            className="w-full h-full object-cover"
            animate={isPlaying ? { rotate: [0, 360] } : {}}
            transition={{
              rotate: {
                duration: 20,
                repeat: Infinity,
                ease: 'linear',
              },
            }}
            onError={(e) => {
              e.target.style.display = 'none'
              e.target.nextSibling.style.display = 'flex'
            }}
          />
        ) : null}
        <motion.div
          className="absolute inset-0 flex items-center justify-center text-6xl"
          style={{ display: currentSong.thumbnail ? 'none' : 'flex' }}
          animate={isPlaying ? { rotate: [0, 360] } : {}}
          transition={{
            rotate: {
              duration: 20,
              repeat: Infinity,
              ease: 'linear',
            },
          }}
        >
          🎵
        </motion.div>
        <div className="absolute inset-0 rounded-2xl bg-gradient-to-t from-black/50 to-transparent" />
      </motion.div>

      {/* Song Info */}
      <div className="text-center mb-8 max-w-md">
        <motion.h2
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          className="text-3xl font-bold mb-2 text-white"
        >
          {currentSong.title}
        </motion.h2>
        <motion.p
          initial={{ y: 20, opacity: 0 }}
          animate={{ y: 0, opacity: 1 }}
          transition={{ delay: 0.1 }}
          className="text-dark-400 text-lg"
        >
          {currentSong.artists?.join(', ') || currentSong.artist}
        </motion.p>
      </div>

      {/* Progress Bar */}
      <div className="w-full max-w-2xl mb-6">
        <div
          className="h-1 bg-dark-200 rounded-full cursor-pointer relative"
          onClick={handleProgressClick}
        >
          <motion.div
            className="h-full bg-gradient-to-r from-primary-500 to-purple-500 rounded-full"
            style={{ width: `${progress}%` }}
          />
          <motion.div
            className="absolute top-1/2 -translate-y-1/2 w-4 h-4 bg-white rounded-full shadow-lg"
            style={{ left: `${progress}%`, marginLeft: '-8px' }}
          />
        </div>
        <div className="flex justify-between text-sm text-dark-400 mt-2">
          <span>{formatTime(currentTime)}</span>
          <span>{formatTime(duration)}</span>
        </div>
      </div>

      {/* Controls */}
      <div className="flex items-center gap-6 mb-8">
        <button
          onClick={toggleShuffle}
          className={`p-3 rounded-full transition-colors ${
            shuffle ? 'text-primary-400 bg-primary-400/20' : 'text-dark-400 hover:text-white'
          }`}
          aria-label="Shuffle"
        >
          <FaRandom className="text-xl" />
        </button>

        <button
          onClick={playPrevious}
          className="p-3 text-white hover:bg-dark-200 rounded-full transition-colors"
          aria-label="Previous"
        >
          <FaStepBackward className="text-2xl" />
        </button>

        <motion.button
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.9 }}
          onClick={togglePlayPause}
          className="p-6 bg-primary-600 hover:bg-primary-700 rounded-full transition-colors shadow-lg neon-glow"
          aria-label={isPlaying ? 'Pause' : 'Play'}
        >
          {isPlaying ? (
            <FaPause className="text-white text-3xl" />
          ) : (
            <FaPlay className="text-white text-3xl ml-1" />
          )}
        </motion.button>

        <button
          onClick={playNext}
          className="p-3 text-white hover:bg-dark-200 rounded-full transition-colors"
          aria-label="Next"
        >
          <FaStepForward className="text-2xl" />
        </button>

        <button
          onClick={toggleRepeat}
          className={`p-3 rounded-full transition-colors ${
            repeatMode !== 'off' ? 'text-primary-400 bg-primary-400/20' : 'text-dark-400 hover:text-white'
          }`}
          aria-label="Repeat"
        >
          <FaRedo className={`text-xl ${repeatMode === 'one' ? 'text-primary-400' : ''}`} />
        </button>
      </div>

      {/* Volume Control */}
      <div className="flex items-center gap-3 w-full max-w-md">
        <button
          onClick={() => setIsMuted(!isMuted)}
          className="text-dark-400 hover:text-white transition-colors"
          aria-label={isMuted ? 'Unmute' : 'Mute'}
        >
          {isMuted || volume === 0 ? (
            <FaVolumeMute className="text-xl" />
          ) : (
            <FaVolumeUp className="text-xl" />
          )}
        </button>
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          value={isMuted ? 0 : volume}
          onChange={(e) => {
            setVolume(parseFloat(e.target.value))
            setIsMuted(false)
          }}
          className="flex-1 h-1 bg-dark-200 rounded-lg appearance-none cursor-pointer accent-primary-600"
        />
        <span className="text-dark-400 text-sm w-10">
          {Math.round((isMuted ? 0 : volume) * 100)}%
        </span>
      </div>

      {/* Queue info */}
      {queue.length > 0 && (
        <div className="mt-6 text-dark-400 text-sm">
          {queue.length} {queue.length === 1 ? 'song' : 'songs'} in queue
        </div>
      )}
    </div>
  )
}

export default FullPlayer

