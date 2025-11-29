import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { playlistService } from '../services/playlistService'
import { useAuth } from '../context/AuthContext'
import { usePlayer } from '../context/PlayerContext'
import SongList from '../components/Music/SongList'
import Loading from '../components/UI/Loading'
import Button from '../components/UI/Button'
import { FaPlay, FaTrash, FaEdit } from 'react-icons/fa'
import toast from 'react-hot-toast'

const PlaylistDetail = () => {
  const { id } = useParams()
  const navigate = useNavigate()
  const [playlist, setPlaylist] = useState(null)
  const [loading, setLoading] = useState(true)
  const [showEditModal, setShowEditModal] = useState(false)
  const [editName, setEditName] = useState('')
  const [editDesc, setEditDesc] = useState('')
  const { user } = useAuth()
  const userId = user?.id || 'default'
  const { playPlaylist } = usePlayer()

  useEffect(() => {
    loadPlaylist()
  }, [id])

  const loadPlaylist = async () => {
    try {
      setLoading(true)
      const response = await playlistService.getPlaylist(id, userId)
      if (response.success) {
        setPlaylist(response.playlist)
        setEditName(response.playlist.name)
        setEditDesc(response.playlist.description || '')
      } else {
        toast.error('Playlist not found')
        navigate('/playlists')
      }
    } catch (error) {
      console.error('Error loading playlist:', error)
      toast.error('Failed to load playlist')
      navigate('/playlists')
    } finally {
      setLoading(false)
    }
  }

  const handlePlay = () => {
    if (playlist.songs.length > 0) {
      playPlaylist(playlist.songs)
    }
  }

  const handleDeletePlaylist = async () => {
    if (!confirm('Are you sure you want to delete this playlist?')) return

    try {
      const response = await playlistService.deletePlaylist(id, userId)
      if (response.success) {
        toast.success('Playlist deleted')
        navigate('/playlists')
      }
    } catch (error) {
      toast.error('Failed to delete playlist')
    }
  }

  const handleUpdatePlaylist = async (e) => {
    e.preventDefault()
    try {
      const response = await playlistService.updatePlaylist(
        id,
        { name: editName, description: editDesc },
        userId
      )
      if (response.success) {
        setPlaylist(response.playlist)
        setShowEditModal(false)
        toast.success('Playlist updated')
      }
    } catch (error) {
      toast.error('Failed to update playlist')
    }
  }

  const handleRemoveSong = async (videoId) => {
    try {
      const response = await playlistService.removeSong(id, videoId, userId)
      if (response.success) {
        setPlaylist(response.playlist)
        toast.success('Song removed')
      }
    } catch (error) {
      toast.error('Failed to remove song')
    }
  }

  if (loading) {
    return <Loading fullScreen />
  }

  if (!playlist) {
    return null
  }

  const songs = playlist.songs || []

  return (
    <div className="container mx-auto px-6 py-8">
      {/* Playlist Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-8"
      >
        <div className="flex items-end gap-6 mb-6">
          <div className="w-48 h-48 rounded-2xl bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center flex-shrink-0">
            <span className="text-6xl">🎵</span>
          </div>
          <div className="flex-1">
            <p className="text-sm text-dark-400 mb-2">PLAYLIST</p>
            <h1 className="text-5xl font-bold mb-4">{playlist.name}</h1>
            {playlist.description && (
              <p className="text-dark-400 mb-4">{playlist.description}</p>
            )}
            <div className="flex items-center gap-4">
              <Button onClick={handlePlay} icon={FaPlay} variant="primary" size="lg">
                Play
              </Button>
              <Button
                onClick={() => setShowEditModal(true)}
                icon={FaEdit}
                variant="secondary"
              >
                Edit
              </Button>
              <Button
                onClick={handleDeletePlaylist}
                icon={FaTrash}
                variant="danger"
              >
                Delete
              </Button>
            </div>
          </div>
        </div>
      </motion.div>

      {/* Songs */}
      <SongList
        songs={songs}
        title={`${songs.length} ${songs.length === 1 ? 'Song' : 'Songs'}`}
      />

      {/* Edit Modal */}
      {showEditModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="glass rounded-2xl p-8 max-w-md w-full"
          >
            <h2 className="text-2xl font-bold mb-6">Edit Playlist</h2>
            <form onSubmit={handleUpdatePlaylist}>
              <div className="mb-4">
                <label className="block text-sm font-medium mb-2">Name</label>
                <input
                  type="text"
                  value={editName}
                  onChange={(e) => setEditName(e.target.value)}
                  className="w-full px-4 py-2 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                  required
                />
              </div>
              <div className="mb-6">
                <label className="block text-sm font-medium mb-2">
                  Description
                </label>
                <textarea
                  value={editDesc}
                  onChange={(e) => setEditDesc(e.target.value)}
                  className="w-full px-4 py-2 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                  rows="3"
                />
              </div>
              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setShowEditModal(false)}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" className="flex-1">
                  Save
                </Button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </div>
  )
}

export default PlaylistDetail

