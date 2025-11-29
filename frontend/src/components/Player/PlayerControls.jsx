import { useState } from 'react'
import { motion } from 'framer-motion'
import {
  FaPlay,
  FaPause,
  FaStepForward,
  FaStepBackward,
  FaRandom,
  FaRedo,
  FaList,
} from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import QueuePanel from './QueuePanel'

/**
 * Player controls component with queue toggle
 * @param {Object} props
 * @param {'mini'|'full'} props.variant - Control variant (mini or full player)
 * @returns {JSX.Element}
 */
const PlayerControls = ({ variant = 'mini' }) => {
  const {
    isPlaying,
    togglePlayPause,
    playNext,
    playPrevious,
    repeatMode,
    shuffle,
    toggleRepeat,
    toggleShuffle,
    queue,
  } = usePlayer()
  const [showQueue, setShowQueue] = useState(false)

  const isMini = variant === 'mini'

  return (
    <>
      <div className={`flex items-center ${isMini ? 'gap-2' : 'gap-6'}`}>
        {!isMini && (
          <button
            onClick={toggleShuffle}
            className={`p-3 rounded-full transition-colors ${
              shuffle
                ? 'text-primary-400 bg-primary-400/20'
                : 'text-dark-400 hover:text-white'
            }`}
            aria-label="Shuffle"
          >
            <FaRandom className="text-xl" />
          </button>
        )}

        <button
          onClick={playPrevious}
          className={`${isMini ? 'p-2' : 'p-3'} text-white hover:bg-dark-200 rounded-full transition-colors`}
          aria-label="Previous"
        >
          <FaStepBackward className={isMini ? 'text-base' : 'text-2xl'} />
        </button>

        <motion.button
          whileHover={{ scale: 1.1 }}
          whileTap={{ scale: 0.9 }}
          onClick={togglePlayPause}
          className={`${isMini ? 'p-3' : 'p-6'} bg-primary-600 hover:bg-primary-700 rounded-full transition-colors ${!isMini ? 'shadow-lg neon-glow' : ''}`}
          aria-label={isPlaying ? 'Pause' : 'Play'}
        >
          {isPlaying ? (
            <FaPause className={`text-white ${isMini ? 'text-base' : 'text-3xl'}`} />
          ) : (
            <FaPlay
              className={`text-white ${isMini ? 'text-base ml-0.5' : 'text-3xl ml-1'}`}
            />
          )}
        </motion.button>

        <button
          onClick={playNext}
          className={`${isMini ? 'p-2' : 'p-3'} text-white hover:bg-dark-200 rounded-full transition-colors`}
          aria-label="Next"
        >
          <FaStepForward className={isMini ? 'text-base' : 'text-2xl'} />
        </button>

        {!isMini && (
          <button
            onClick={toggleRepeat}
            className={`p-3 rounded-full transition-colors ${
              repeatMode !== 'off'
                ? 'text-primary-400 bg-primary-400/20'
                : 'text-dark-400 hover:text-white'
            }`}
            aria-label="Repeat"
          >
            <FaRedo className={`text-xl ${repeatMode === 'one' ? 'text-primary-400' : ''}`} />
          </button>
        )}

        {/* Queue Button */}
        <button
          onClick={() => setShowQueue(true)}
          className={`${isMini ? 'hidden md:block p-2' : 'p-3'} relative text-dark-400 hover:text-white rounded-full transition-colors`}
          aria-label="Show queue"
        >
          <FaList className={isMini ? 'text-base' : 'text-xl'} />
          {queue.length > 0 && (
            <span className="absolute -top-1 -right-1 w-5 h-5 bg-primary-600 text-white text-xs rounded-full flex items-center justify-center">
              {queue.length > 9 ? '9+' : queue.length}
            </span>
          )}
        </button>
      </div>

      <QueuePanel isOpen={showQueue} onClose={() => setShowQueue(false)} />
    </>
  )
}

export default PlayerControls

