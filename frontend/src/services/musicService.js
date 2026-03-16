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
  
  getRecommendations: async (userId = 'default', limit = 20) => {
    // If the backend expects userId as a param, add it; otherwise, just call the endpoint
    const response = await api.get('/recommendations', {
      params: { userId, limit },
    })
    return response.data
  },
}

