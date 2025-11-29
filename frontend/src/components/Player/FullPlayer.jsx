import { motion } from 'framer-motion'
import { FaVolumeUp, FaVolumeMute, FaEllipsisV } from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import { useEffect, useState } from 'react'
import ProgressBar from './ProgressBar'
import PlayerControls from './PlayerControls'
import LikeButton from '../Music/LikeButton'
import AddToPlaylistMenu from '../Music/AddToPlaylistMenu'

/**
 * Full-screen player component
 * Displays the full immersive player view with album art, controls, and queue
 * 
 * IMPORTANT: All values used in this component MUST be destructured from usePlayer().
 * Missing destructuring will cause "is not defined" errors.
 * 
 * @returns {JSX.Element}
 */
const FullPlayer = () => {
  // Destructure ALL required values from player context
  // If you use a value (like isPlaying), it MUST be in this list
  const {
    currentSong,
    isPlaying, // Required for album art rotation animation
    volume,
    setVolume,
    currentTime,
    duration,
    buffered,
    seekTo,
  } = usePlayer()

  const [isMuted, setIsMuted] = useState(false)
  const [previousVolume, setPreviousVolume] = useState(1)
  const [showPlaylistMenu, setShowPlaylistMenu] = useState(false)

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
          className="text-dark-400 text-lg mb-4"
        >
          {currentSong.artists?.join(', ') || currentSong.artist}
        </motion.p>
        
        {/* Actions */}
        <div className="flex items-center justify-center gap-4">
          <LikeButton song={currentSong} size="text-2xl" />
          <button
            onClick={() => setShowPlaylistMenu(true)}
            className="p-3 hover:bg-dark-200 rounded-full transition-colors"
            aria-label="Add to playlist"
          >
            <FaEllipsisV className="text-dark-400 hover:text-white text-xl" />
          </button>
        </div>
      </div>

      {/* Progress Bar */}
      <div className="w-full max-w-2xl mb-6">
        <ProgressBar
          currentTime={currentTime}
          duration={duration}
          buffered={buffered}
          onSeek={seekTo}
          height="h-2"
          showTime={true}
        />
      </div>

      {/* Controls */}
      <div className="flex items-center justify-center mb-8">
        <PlayerControls variant="full" />
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
      
      {/* Add to Playlist Menu */}
      <AddToPlaylistMenu
        song={currentSong}
        isOpen={showPlaylistMenu}
        onClose={() => setShowPlaylistMenu(false)}
      />
    </div>
  )
}

export default FullPlayer

