import { motion } from 'framer-motion'
import { FaPlay, FaPause, FaMusic, FaEllipsisV } from 'react-icons/fa'
import { usePlayer } from '../../context/PlayerContext'
import { useState } from 'react'
import LikeButton from './LikeButton'
import AddToPlaylistMenu from './AddToPlaylistMenu'

const PLACEHOLDER_IMAGE = 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTYwIiBoZWlnaHQ9IjE2MCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMTAwJSIgaGVpZ2h0PSIxMDAlIiBmaWxsPSIjMTgxODFiIi8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIzMiIgZmlsbD0iIzcxNzE3YSIgdGV4dC1hbmNob3I9Im1pZGRsZSIgZG9taW5hbnQtYmFzZWxpbmU9Im1pZGRsZSI+8J+OrTwvdGV4dD48L3N2Zz4='

const SongCard = ({ song, index = 0, showPlayButton = true, showActions = true }) => {
  const { currentSong, isPlaying, playSong, togglePlayPause } = usePlayer()
  const [imageError, setImageError] = useState(false)
  const [showMenu, setShowMenu] = useState(false)
  
  const isCurrentSong = currentSong?.videoId === song.videoId
  
  const handleClick = () => {
    if (isCurrentSong) {
      togglePlayPause()
    } else {
      playSong(song.videoId)
    }
  }
  
  const handleMenuClick = (e) => {
    e.stopPropagation()
    setShowMenu(true)
  }
  
  const formatDuration = (seconds) => {
    if (!seconds) return '0:00'
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }
  
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.05 }}
      whileHover={{ scale: 1.02, y: -4 }}
      onClick={handleClick}
      className="glass rounded-xl p-4 cursor-pointer group hover:bg-dark-200/80 transition-all duration-300"
    >
      <div className="flex items-center gap-4">
        <div className="relative flex-shrink-0 w-16 h-16 rounded-lg bg-dark-200 flex items-center justify-center overflow-hidden">
          {imageError || !song.thumbnail ? (
            <FaMusic className="text-dark-400 text-2xl" />
          ) : (
            <img
              src={song.thumbnail}
              alt={song.title}
              className="w-full h-full object-cover"
              onError={() => setImageError(true)}
            />
          )}
          {showPlayButton && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/50 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity">
              {isCurrentSong && isPlaying ? (
                <FaPause className="text-white text-xl" />
              ) : (
                <FaPlay className="text-white text-xl ml-1" />
              )}
            </div>
          )}
        </div>
        
        <div className="flex-1 min-w-0">
          <h3 className={`font-semibold truncate ${isCurrentSong ? 'text-primary-400' : 'text-white'}`}>
            {song.title || 'Unknown Title'}
          </h3>
          <p className="text-dark-400 text-sm truncate">
            {song.artists?.join(', ') || song.artist || 'Unknown Artist'}
          </p>
        </div>
        
        <div className="flex items-center gap-2 flex-shrink-0">
          {song.duration && (
            <span className="text-dark-400 text-sm">
              {typeof song.duration === 'string' ? song.duration : formatDuration(song.duration)}
            </span>
          )}
          
          {showActions && (
            <>
              <div onClick={(e) => e.stopPropagation()}>
                <LikeButton song={song} size="text-lg" />
              </div>
              <button
                onClick={handleMenuClick}
                className="p-1 hover:bg-dark-200 rounded transition-colors opacity-0 group-hover:opacity-100"
                aria-label="More options"
              >
                <FaEllipsisV className="text-dark-400 hover:text-white text-sm" />
              </button>
            </>
          )}
        </div>
      </div>
      
      {showActions && (
        <AddToPlaylistMenu
          song={song}
          isOpen={showMenu}
          onClose={() => setShowMenu(false)}
        />
      )}
    </motion.div>
  )
}

export default SongCard

