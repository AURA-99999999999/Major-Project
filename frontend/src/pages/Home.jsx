import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { musicService } from '../services/musicService'
import { usePlayer } from '../context/PlayerContext'
import SongList from '../components/Music/SongList'
import PlaylistTile from '../components/Music/PlaylistTile'
import { playlistService } from '../services/playlistService'
import { useAuth } from '../context/AuthContext'
import Loading from '../components/UI/Loading'
import { FaPlay } from 'react-icons/fa'

const Home = () => {
  const [trending, setTrending] = useState([])
  const [playlists, setPlaylists] = useState([])
  const [loading, setLoading] = useState(true)
  const { playPlaylist } = usePlayer()
  const { user } = useAuth()
  const userId = user?.id || 'default'

  useEffect(() => {
    loadData()
  }, [])

  const loadData = async () => {
    try {
      setLoading(true)
      
      // Load trending songs
      const trendingData = await musicService.getTrending(20)
      if (trendingData.success) {
        setTrending(trendingData.results || [])
      }

      // Load playlists
      const playlistsData = await playlistService.getPlaylists(userId)
      if (playlistsData.success) {
        setPlaylists(playlistsData.playlists || [])
      }
    } catch (error) {
      console.error('Error loading home data:', error)
    } finally {
      setLoading(false)
    }
  }

  const handlePlayTrending = () => {
    if (trending.length > 0) {
      playPlaylist(trending)
    }
  }

  if (loading) {
    return <Loading fullScreen />
  }

  return (
    <div className="container mx-auto px-6 py-8">
      {/* Hero Section */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-12 relative overflow-hidden rounded-2xl glass p-12"
      >
        <div className="relative z-10">
          <h1 className="text-5xl font-bold mb-4 gradient-text">
            Welcome to Aura
          </h1>
          <p className="text-xl text-dark-400 mb-6">
            Discover your next favorite song from millions of tracks
          </p>
          {trending.length > 0 && (
            <motion.button
              whileHover={{ scale: 1.05 }}
              whileTap={{ scale: 0.95 }}
              onClick={handlePlayTrending}
              className="flex items-center gap-3 px-8 py-4 bg-primary-600 hover:bg-primary-700 rounded-full text-white font-semibold transition-colors"
            >
              <FaPlay className="text-lg" />
              Play Trending Now
            </motion.button>
          )}
        </div>
        <div className="absolute inset-0 bg-gradient-to-r from-primary-600/20 via-purple-600/20 to-pink-600/20 blur-3xl" />
      </motion.div>

      {/* Trending Songs */}
      {trending.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
          className="mb-12"
        >
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-3xl font-bold">Trending Now</h2>
          </div>
          <SongList songs={trending.slice(0, 10)} />
        </motion.div>
      )}

      {/* Your Playlists */}
      {playlists.length > 0 && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
          className="mb-12"
        >
          <h2 className="text-3xl font-bold mb-6">Your Playlists</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {playlists.slice(0, 6).map((playlist, index) => (
              <PlaylistTile key={playlist.id} playlist={playlist} index={index} />
            ))}
          </div>
        </motion.div>
      )}

      {/* Recently Played - if user is logged in */}
      {user && (
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4 }}
        >
          <h2 className="text-3xl font-bold mb-6">Recently Played</h2>
          <p className="text-dark-400">Check your profile to see recently played songs</p>
        </motion.div>
      )}
    </div>
  )
}

export default Home

