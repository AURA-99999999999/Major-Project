import api from './api'

export const playlistService = {
  getPlaylists: async (userId = 'default') => {
    const response = await api.get('/playlists', {
      params: { userId },
    })
    return response.data
  },

  getPlaylist: async (playlistId, userId = 'default') => {
    const response = await api.get(`/playlists/${playlistId}`, {
      params: { userId },
    })
    return response.data
  },

  createPlaylist: async (name, description = '', userId = 'default') => {
    const response = await api.post('/playlists', {
      name,
      description,
      userId,
    })
    return response.data
  },

  updatePlaylist: async (playlistId, updates, userId = 'default') => {
    const response = await api.put(`/playlists/${playlistId}`, {
      ...updates,
      userId,
    })
    return response.data
  },

  deletePlaylist: async (playlistId, userId = 'default') => {
    const response = await api.delete(`/playlists/${playlistId}`, {
      params: { userId },
    })
    return response.data
  },

  addSong: async (playlistId, song, userId = 'default') => {
    const response = await api.post(`/playlists/${playlistId}/songs`, {
      song,
      userId,
    })
    return response.data
  },

  removeSong: async (playlistId, videoId, userId = 'default') => {
    const response = await api.delete(`/playlists/${playlistId}/songs/${videoId}`, {
      params: { userId },
    })
    return response.data
  },
}

