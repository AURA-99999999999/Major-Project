import api from './api'

export const userService = {
  register: async (username, email, password) => {
    const response = await api.post('/auth/register', {
      username,
      email,
      password,
    })
    return response.data
  },

  login: async (email, password) => {
    const response = await api.post('/auth/login', {
      email,
      password,
    })
    return response.data
  },

  getUser: async (userId) => {
    const response = await api.get(`/users/${userId}`)
    return response.data
  },

  getLikedSongs: async (userId) => {
    const response = await api.get(`/users/${userId}/liked`)
    return response.data
  },

  addLikedSong: async (userId, song) => {
    const response = await api.post(`/users/${userId}/liked`, { song })
    return response.data
  },

  removeLikedSong: async (userId, videoId) => {
    const response = await api.delete(`/users/${userId}/liked/${videoId}`)
    return response.data
  },

  getRecentlyPlayed: async (userId) => {
    const response = await api.get(`/users/${userId}/recent`)
    return response.data
  },

  addRecentlyPlayed: async (userId, song) => {
    const response = await api.post(`/users/${userId}/recent`, { song })
    return response.data
  },
}

