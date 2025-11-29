import { motion, AnimatePresence } from 'framer-motion'
import { FaVolumeUp } from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import { useNavigate } from 'react-router-dom'
import ProgressBar from './ProgressBar'
import PlayerControls from './PlayerControls'

/**
 * Mini player component - Sticky bottom player bar
 * Shows current song, controls, and progress bar
 * @returns {JSX.Element | null}
 */
const MiniPlayer = () => {
  const {
    currentSong,
    volume,
    setVolume,
    currentTime,
    duration,
    buffered,
    seekTo,
  } = usePlayer()
  const navigate = useNavigate()

  if (!currentSong) return null

  return (
    <AnimatePresence>
      <motion.div
        initial={{ y: 100 }}
        animate={{ y: 0 }}
        exit={{ y: 100 }}
        className="fixed bottom-0 left-0 right-0 z-50 glass border-t border-white/10"
      >
        <div className="container mx-auto px-4 py-3">
          {/* Progress bar */}
          <div className="absolute top-0 left-0 right-0">
            <ProgressBar
              currentTime={currentTime}
              duration={duration}
              buffered={buffered}
              onSeek={seekTo}
              height="h-1"
              showTime={false}
            />
          </div>

          <div className="flex items-center gap-4">
            {/* Song info */}
            <div
              onClick={() => navigate('/player')}
              className="flex items-center gap-3 flex-1 min-w-0 cursor-pointer"
            >
              <div className="w-14 h-14 rounded-lg bg-dark-200 flex items-center justify-center overflow-hidden flex-shrink-0">
                {currentSong.thumbnail ? (
                  <img
                    src={currentSong.thumbnail}
                    alt={currentSong.title}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      e.target.style.display = 'none'
                      e.target.nextSibling.style.display = 'flex'
                    }}
                  />
                ) : null}
                <div className="w-full h-full bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center" style={{ display: currentSong.thumbnail ? 'none' : 'flex' }}>
                  <span className="text-xl">🎵</span>
                </div>
              </div>
              <div className="min-w-0 flex-1">
                <p className="font-medium text-white truncate">
                  {currentSong.title}
                </p>
                <p className="text-dark-400 text-sm truncate">
                  {currentSong.artists?.join(', ') || currentSong.artist}
                </p>
              </div>
            </div>

            {/* Controls */}
            <PlayerControls variant="mini" />

            {/* Volume */}
            <div className="hidden md:flex items-center gap-2 w-32">
              <FaVolumeUp className="text-dark-400" />
              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                value={volume}
                onChange={(e) => setVolume(parseFloat(e.target.value))}
                className="flex-1 h-1 bg-dark-200 rounded-lg appearance-none cursor-pointer accent-primary-600"
              />
            </div>
          </div>
        </div>
      </motion.div>
    </AnimatePresence>
  )
}

export default MiniPlayer

