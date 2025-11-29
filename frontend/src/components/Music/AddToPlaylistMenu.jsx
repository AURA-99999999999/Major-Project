import { useState, useEffect } from 'react'
import { motion, AnimatePresence } from 'framer-motion'
import { FaPlus, FaTimes, FaMusic } from 'react-icons/fa'
import { playlistService } from '../../services/playlistService'
import { useAuth } from '../../context/AuthContext'
import { useNavigate } from 'react-router-dom'
import toast from 'react-hot-toast'
import Button from '../UI/Button'

/**
 * Menu component for adding songs to playlists
 */
const AddToPlaylistMenu = ({ song, isOpen, onClose }) => {
  const [playlists, setPlaylists] = useState([])
  const [loading, setLoading] = useState(false)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newPlaylistName, setNewPlaylistName] = useState('')
  const { user } = useAuth()
  const userId = user?.id || 'default'
  const navigate = useNavigate()

  useEffect(() => {
    if (isOpen) {
      loadPlaylists()
    }
  }, [isOpen])

  const loadPlaylists = async () => {
    try {
      setLoading(true)
      const response = await playlistService.getPlaylists(userId)
      if (response.success) {
        setPlaylists(response.playlists || [])
      }
    } catch (error) {
      console.error('Error loading playlists:', error)
      toast.error('Failed to load playlists')
    } finally {
      setLoading(false)
    }
  }

  const handleAddToPlaylist = async (playlistId) => {
    try {
      const response = await playlistService.addSong(playlistId, song, userId)
      if (response.success) {
        toast.success('Added to playlist!')
        onClose()
      }
    } catch (error) {
      console.error('Error adding song:', error)
      toast.error('Failed to add song to playlist')
    }
  }

  const handleCreateAndAdd = async (e) => {
    e.preventDefault()
    if (!newPlaylistName.trim()) {
      toast.error('Playlist name is required')
      return
    }

    try {
      const response = await playlistService.createPlaylist(
        newPlaylistName,
        '',
        userId
      )
      if (response.success) {
        await handleAddToPlaylist(response.playlist.id)
        setShowCreateModal(false)
        setNewPlaylistName('')
        navigate(`/playlist/${response.playlist.id}`)
      }
    } catch (error) {
      toast.error('Failed to create playlist')
    }
  }

  if (!isOpen) return null

  return (
    <>
      <div
        className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4"
        onClick={onClose}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          exit={{ opacity: 0, scale: 0.9 }}
          onClick={(e) => e.stopPropagation()}
          className="glass rounded-2xl p-6 max-w-md w-full max-h-[80vh] overflow-y-auto scrollbar-thin"
        >
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-2xl font-bold">Add to Playlist</h2>
            <button
              onClick={onClose}
              className="p-2 hover:bg-dark-200 rounded-full transition-colors"
              aria-label="Close"
            >
              <FaTimes className="text-dark-400" />
            </button>
          </div>

          <div className="mb-4 p-3 bg-dark-200 rounded-lg">
            <p className="font-medium text-white truncate">{song?.title}</p>
            <p className="text-sm text-dark-400 truncate">
              {song?.artists?.join(', ') || song?.artist}
            </p>
          </div>

          <Button
            onClick={() => setShowCreateModal(true)}
            icon={FaPlus}
            variant="primary"
            className="w-full mb-4"
          >
            Create New Playlist
          </Button>

          <div className="space-y-2">
            {loading ? (
              <p className="text-dark-400 text-center py-4">Loading playlists...</p>
            ) : playlists.length === 0 ? (
              <p className="text-dark-400 text-center py-4">
                No playlists yet. Create one to get started!
              </p>
            ) : (
              playlists.map((playlist) => (
                <button
                  key={playlist.id}
                  onClick={() => handleAddToPlaylist(playlist.id)}
                  className="w-full flex items-center gap-3 p-3 hover:bg-dark-200 rounded-lg transition-colors text-left"
                >
                  <div className="w-12 h-12 rounded-lg bg-gradient-to-br from-primary-600 to-purple-600 flex items-center justify-center flex-shrink-0">
                    <FaMusic className="text-white" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="font-medium text-white truncate">{playlist.name}</p>
                    <p className="text-sm text-dark-400">
                      {playlist.songs?.length || 0} songs
                    </p>
                  </div>
                </button>
              ))
            )}
          </div>
        </motion.div>
      </div>

      {/* Create Playlist Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-[60] flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            onClick={(e) => e.stopPropagation()}
            className="glass rounded-2xl p-6 max-w-md w-full"
          >
            <h3 className="text-xl font-bold mb-4">Create Playlist</h3>
            <form onSubmit={handleCreateAndAdd}>
              <input
                type="text"
                value={newPlaylistName}
                onChange={(e) => setNewPlaylistName(e.target.value)}
                className="w-full px-4 py-2 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500 mb-4"
                placeholder="Playlist name"
                autoFocus
              />
              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setShowCreateModal(false)
                    setNewPlaylistName('')
                  }}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" className="flex-1">
                  Create & Add
                </Button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </>
  )
}

export default AddToPlaylistMenu

