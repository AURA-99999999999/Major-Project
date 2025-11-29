import { useEffect, useState } from 'react'
import { motion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import { userService } from '../services/userService'
import SongList from '../components/Music/SongList'
import Loading from '../components/UI/Loading'
import { FaHeart, FaClock } from 'react-icons/fa'

const Profile = () => {
  const { user } = useAuth()
  const [likedSongs, setLikedSongs] = useState([])
  const [recentSongs, setRecentSongs] = useState([])
  const [loading, setLoading] = useState(true)
  const [activeTab, setActiveTab] = useState('liked')

  useEffect(() => {
    if (user) {
      loadUserData()
    }
  }, [user])

  const loadUserData = async () => {
    try {
      setLoading(true)
      
      // Load liked songs
      const likedResponse = await userService.getLikedSongs(user.id)
      if (likedResponse.success) {
        setLikedSongs(likedResponse.songs || [])
      }

      // Load recently played
      const recentResponse = await userService.getRecentlyPlayed(user.id)
      if (recentResponse.success) {
        setRecentSongs(recentResponse.songs || [])
      }
    } catch (error) {
      console.error('Error loading user data:', error)
    } finally {
      setLoading(false)
    }
  }

  if (loading) {
    return <Loading fullScreen />
  }

  return (
    <div className="container mx-auto px-6 py-8">
      {/* Profile Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-8"
      >
        <div className="flex items-center gap-6 mb-6">
          <div className="w-32 h-32 rounded-full bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center text-4xl font-bold">
            {user?.username?.[0]?.toUpperCase() || 'U'}
          </div>
          <div>
            <h1 className="text-4xl font-bold mb-2">{user?.username}</h1>
            <p className="text-dark-400">{user?.email}</p>
          </div>
        </div>

        {/* Tabs */}
        <div className="flex gap-4 border-b border-dark-200">
          <button
            onClick={() => setActiveTab('liked')}
            className={`px-6 py-3 font-medium transition-colors ${
              activeTab === 'liked'
                ? 'text-primary-400 border-b-2 border-primary-400'
                : 'text-dark-400 hover:text-white'
            }`}
          >
            <FaHeart className="inline mr-2" />
            Liked Songs ({likedSongs.length})
          </button>
          <button
            onClick={() => setActiveTab('recent')}
            className={`px-6 py-3 font-medium transition-colors ${
              activeTab === 'recent'
                ? 'text-primary-400 border-b-2 border-primary-400'
                : 'text-dark-400 hover:text-white'
            }`}
          >
            <FaClock className="inline mr-2" />
            Recently Played ({recentSongs.length})
          </button>
        </div>
      </motion.div>

      {/* Content */}
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 0.2 }}
      >
        {activeTab === 'liked' ? (
          <SongList
            songs={likedSongs}
            title={likedSongs.length > 0 ? 'Your Liked Songs' : ''}
          />
        ) : (
          <SongList
            songs={recentSongs}
            title={recentSongs.length > 0 ? 'Recently Played' : ''}
          />
        )}
      </motion.div>
    </div>
  )
}

export default Profile

