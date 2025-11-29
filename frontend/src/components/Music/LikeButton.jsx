import { useState, useEffect } from 'react'
import { FaHeart, FaRegHeart } from 'react-icons/fa'
import { useAuth } from '../../context/AuthContext'
import { userService } from '../../services/userService'
import toast from 'react-hot-toast'
import { motion } from 'framer-motion'

/**
 * Like/Favorite button component for songs
 */
const LikeButton = ({ song, size = 'text-xl' }) => {
  const { user } = useAuth()
  const [isLiked, setIsLiked] = useState(false)
  const [loading, setLoading] = useState(false)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    if (user && song?.videoId) {
      checkLikedStatus()
    } else {
      setChecking(false)
    }
  }, [user, song?.videoId])

  const checkLikedStatus = async () => {
    if (!user?.id || !song?.videoId) {
      setChecking(false)
      return
    }

    try {
      const response = await userService.getLikedSongs(user.id)
      if (response.success) {
        const liked = response.songs?.some((s) => s.videoId === song.videoId) || false
        setIsLiked(liked)
      }
    } catch (error) {
      console.error('Error checking liked status:', error)
    } finally {
      setChecking(false)
    }
  }

  const handleToggleLike = async (e) => {
    e.stopPropagation()

    if (!user) {
      toast.error('Please login to like songs')
      return
    }

    if (!song?.videoId) {
      toast.error('Invalid song')
      return
    }

    setLoading(true)
    try {
      if (isLiked) {
        const response = await userService.removeLikedSong(user.id, song.videoId)
        if (response.success) {
          setIsLiked(false)
          toast.success('Removed from favorites')
        }
      } else {
        const response = await userService.addLikedSong(user.id, song)
        if (response.success) {
          setIsLiked(true)
          toast.success('Added to favorites')
        }
      }
    } catch (error) {
      console.error('Error toggling like:', error)
      toast.error('Failed to update favorites')
    } finally {
      setLoading(false)
    }
  }

  if (!user) {
    return null
  }

  return (
    <motion.button
      whileHover={{ scale: 1.1 }}
      whileTap={{ scale: 0.9 }}
      onClick={handleToggleLike}
      disabled={loading || checking}
      className={`${size} transition-colors ${
        isLiked
          ? 'text-red-500 hover:text-red-400'
          : 'text-dark-400 hover:text-red-500'
      } ${loading || checking ? 'opacity-50 cursor-not-allowed' : ''}`}
      aria-label={isLiked ? 'Remove from favorites' : 'Add to favorites'}
    >
      {isLiked ? <FaHeart /> : <FaRegHeart />}
    </motion.button>
  )
}

export default LikeButton

