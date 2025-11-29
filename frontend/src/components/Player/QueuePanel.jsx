import { motion, AnimatePresence } from 'framer-motion'
import { FaTimes, FaPlay, FaTrash, FaMusic } from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import SongCard from '../Music/SongCard'

/**
 * Queue panel showing up next and history
 */
const QueuePanel = ({ isOpen, onClose }) => {
  const { queue, history, removeFromQueue, playSong, currentSong } = usePlayer()

  if (!isOpen) return null

  return (
    <AnimatePresence>
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex justify-end"
        onClick={onClose}
      >
        <motion.div
          initial={{ x: '100%' }}
          animate={{ x: 0 }}
          exit={{ x: '100%' }}
          transition={{ type: 'spring', damping: 25, stiffness: 200 }}
          onClick={(e) => e.stopPropagation()}
          className="w-full max-w-md bg-dark-100 border-l border-white/10 h-full overflow-y-auto scrollbar-thin"
        >
          <div className="sticky top-0 bg-dark-100 border-b border-white/10 p-4 flex items-center justify-between">
            <h2 className="text-xl font-bold">Queue</h2>
            <button
              onClick={onClose}
              className="p-2 hover:bg-dark-200 rounded-full transition-colors"
              aria-label="Close"
            >
              <FaTimes className="text-dark-400" />
            </button>
          </div>

          <div className="p-4 space-y-6">
            {/* Currently Playing */}
            {currentSong && (
              <div>
                <h3 className="text-sm font-semibold text-dark-400 mb-3 uppercase">
                  Now Playing
                </h3>
                <div className="glass rounded-xl p-4">
                  <div className="flex items-center gap-4">
                    <div className="w-16 h-16 rounded-lg bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center flex-shrink-0">
                      <FaMusic className="text-white text-xl" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-semibold text-white truncate">
                        {currentSong.title}
                      </p>
                      <p className="text-sm text-dark-400 truncate">
                        {currentSong.artists?.join(', ') || currentSong.artist}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {/* Queue (Up Next) */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-dark-400 uppercase">
                  Up Next ({queue.length})
                </h3>
                {queue.length > 0 && (
                  <button
                    onClick={() => {
                      queue.forEach((song) => removeFromQueue(song.videoId))
                    }}
                    className="text-xs text-dark-400 hover:text-white transition-colors"
                  >
                    Clear all
                  </button>
                )}
              </div>
              {queue.length === 0 ? (
                <p className="text-dark-400 text-sm py-4 text-center">
                  No songs in queue
                </p>
              ) : (
                <div className="space-y-2">
                  {queue.map((song, index) => (
                    <motion.div
                      key={song.videoId || index}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.05 }}
                      className="flex items-center gap-2 group"
                    >
                      <span className="text-xs text-dark-400 w-6">
                        {index + 1}
                      </span>
                      <div className="flex-1">
                        <SongCard song={song} showPlayButton={false} showActions={false} />
                      </div>
                      <button
                        onClick={() => removeFromQueue(song.videoId)}
                        className="p-2 hover:bg-dark-200 rounded transition-colors opacity-0 group-hover:opacity-100"
                        aria-label="Remove from queue"
                      >
                        <FaTrash className="text-dark-400 hover:text-red-400 text-sm" />
                      </button>
                    </motion.div>
                  ))}
                </div>
              )}
            </div>

            {/* History */}
            {history.length > 0 && (
              <div>
                <h3 className="text-sm font-semibold text-dark-400 mb-3 uppercase">
                  Recently Played
                </h3>
                <div className="space-y-2">
                  {history.slice(0, 10).map((song, index) => (
                    <motion.div
                      key={song.videoId || index}
                      initial={{ opacity: 0, x: -20 }}
                      animate={{ opacity: 1, x: 0 }}
                      transition={{ delay: index * 0.05 }}
                    >
                      <div
                        onClick={() => playSong(song.videoId)}
                        className="flex items-center gap-3 p-2 hover:bg-dark-200 rounded-lg cursor-pointer transition-colors"
                      >
                        <div className="w-10 h-10 rounded bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center flex-shrink-0">
                          <FaMusic className="text-white text-xs" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <p className="font-medium text-white text-sm truncate">
                            {song.title}
                          </p>
                          <p className="text-xs text-dark-400 truncate">
                            {song.artists?.join(', ') || song.artist}
                          </p>
                        </div>
                        <FaPlay className="text-dark-400 text-xs" />
                      </div>
                    </motion.div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}

export default QueuePanel

