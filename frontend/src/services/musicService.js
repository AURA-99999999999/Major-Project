import api from './api'

export const musicService = {
  search: async (query, limit = 20, filter = 'songs') => {
    const response = await api.get('/search', {
      params: { query, limit, filter },
    })
    return response.data
  },

  getSong: async (videoId) => {
    const response = await api.get(`/song/${videoId}`)
    return response.data
  },

  getTrending: async (limit = 20) => {
    const response = await api.get('/trending', {
      params: { limit },
    })
    return response.data
  },

  getArtist: async (artistId) => {
    const response = await api.get(`/artist/${artistId}`)
    return response.data
  },
}

