import { motion } from 'framer-motion'
import { FaPlay, FaMusic } from 'react-icons/fa'
import { useNavigate } from 'react-router-dom'

const PlaylistTile = ({ playlist, index = 0 }) => {
  const navigate = useNavigate()
  const songs = playlist.songs || []
  
  const handleClick = () => {
    navigate(`/playlist/${playlist.id}`)
  }
  
  const handlePlay = (e) => {
    e.stopPropagation()
    // Will be handled by playlist detail page
    navigate(`/playlist/${playlist.id}`)
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
      <div className="relative aspect-square rounded-lg overflow-hidden mb-4 bg-gradient-to-br from-primary-600 to-purple-600">
        {playlist.coverImage ? (
          <img
            src={playlist.coverImage}
            alt={playlist.name}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <FaMusic className="text-6xl text-white/50" />
          </div>
        )}
        
        <div className="absolute inset-0 bg-black/50 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
          <motion.button
            whileHover={{ scale: 1.1 }}
            whileTap={{ scale: 0.9 }}
            onClick={handlePlay}
            className="bg-primary-600 rounded-full p-4 hover:bg-primary-700 transition-colors"
          >
            <FaPlay className="text-white text-xl ml-1" />
          </motion.button>
        </div>
      </div>
      
      <h3 className="font-semibold text-white truncate mb-1">
        {playlist.name}
      </h3>
      <p className="text-dark-400 text-sm">
        {songs.length} {songs.length === 1 ? 'song' : 'songs'}
      </p>
      {playlist.description && (
        <p className="text-dark-400 text-xs mt-2 line-clamp-2">
          {playlist.description}
        </p>
      )}
    </motion.div>
  )
}

export default PlaylistTile

