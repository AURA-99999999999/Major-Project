import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { motion } from 'framer-motion'
import { playlistService } from '../services/playlistService'
import { useAuth } from '../context/AuthContext'
import PlaylistTile from '../components/Music/PlaylistTile'
import Loading from '../components/UI/Loading'
import Button from '../components/UI/Button'
import { FaPlus } from 'react-icons/fa'
import toast from 'react-hot-toast'

const Playlists = () => {
  const [playlists, setPlaylists] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreateModal, setShowCreateModal] = useState(false)
  const [newPlaylistName, setNewPlaylistName] = useState('')
  const [newPlaylistDesc, setNewPlaylistDesc] = useState('')
  const { user } = useAuth()
  const userId = user?.id || 'default'
  const navigate = useNavigate()

  useEffect(() => {
    loadPlaylists()
  }, [])

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

  const handleCreatePlaylist = async (e) => {
    e.preventDefault()
    if (!newPlaylistName.trim()) {
      toast.error('Playlist name is required')
      return
    }

    try {
      const response = await playlistService.createPlaylist(
        newPlaylistName,
        newPlaylistDesc,
        userId
      )
      if (response.success) {
        toast.success('Playlist created!')
        setShowCreateModal(false)
        setNewPlaylistName('')
        setNewPlaylistDesc('')
        loadPlaylists()
        navigate(`/playlist/${response.playlist.id}`)
      }
    } catch (error) {
      toast.error('Failed to create playlist')
    }
  }

  if (loading) {
    return <Loading fullScreen />
  }

  return (
    <div className="container mx-auto px-6 py-8">
      <div className="flex items-center justify-between mb-8">
        <h1 className="text-4xl font-bold">Your Library</h1>
        <Button
          onClick={() => setShowCreateModal(true)}
          icon={FaPlus}
          variant="primary"
        >
          Create Playlist
        </Button>
      </div>

      {playlists.length === 0 ? (
        <div className="text-center py-16">
          <p className="text-dark-400 text-xl mb-6">No playlists yet</p>
          <Button
            onClick={() => setShowCreateModal(true)}
            icon={FaPlus}
            variant="primary"
          >
            Create Your First Playlist
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {playlists.map((playlist, index) => (
            <PlaylistTile key={playlist.id} playlist={playlist} index={index} />
          ))}
        </div>
      )}

      {/* Create Playlist Modal */}
      {showCreateModal && (
        <div className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <motion.div
            initial={{ opacity: 0, scale: 0.9 }}
            animate={{ opacity: 1, scale: 1 }}
            className="glass rounded-2xl p-8 max-w-md w-full"
          >
            <h2 className="text-2xl font-bold mb-6">Create Playlist</h2>
            <form onSubmit={handleCreatePlaylist}>
              <div className="mb-4">
                <label className="block text-sm font-medium mb-2">
                  Playlist Name *
                </label>
                <input
                  type="text"
                  value={newPlaylistName}
                  onChange={(e) => setNewPlaylistName(e.target.value)}
                  className="w-full px-4 py-2 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
                  placeholder="My Awesome Playlist"
                  required
                />
              </div>
              <div className="mb-6">
                <label className="block text-sm font-medium mb-2">
                  Description
                </label>
                <textarea
                  value={newPlaylistDesc}
                  onChange={(e) => setNewPlaylistDesc(e.target.value)}
                  className="w-full px-4 py-2 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
                  rows="3"
                  placeholder="Add a description..."
                />
              </div>
              <div className="flex gap-3">
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => setShowCreateModal(false)}
                  className="flex-1"
                >
                  Cancel
                </Button>
                <Button type="submit" variant="primary" className="flex-1">
                  Create
                </Button>
              </div>
            </form>
          </motion.div>
        </div>
      )}
    </div>
  )
}

export default Playlists

