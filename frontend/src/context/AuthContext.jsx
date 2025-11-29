import { createContext, useContext, useState, useEffect } from 'react'
import { userService } from '../services/userService'
import toast from 'react-hot-toast'

const AuthContext = createContext()

export const useAuth = () => {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const savedUser = localStorage.getItem('user')
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser))
      } catch (e) {
        localStorage.removeItem('user')
      }
    }
    setLoading(false)
  }, [])

  const login = async (email, password) => {
    try {
      const response = await userService.login(email, password)
      if (response.success) {
        setUser(response.user)
        localStorage.setItem('user', JSON.stringify(response.user))
        toast.success('Welcome back!')
        return { success: true }
      }
      return { success: false, error: 'Login failed' }
    } catch (error) {
      const message = error.response?.data?.error || 'Login failed'
      toast.error(message)
      return { success: false, error: message }
    }
  }

  const register = async (username, email, password) => {
    try {
      const response = await userService.register(username, email, password)
      if (response.success) {
        setUser(response.user)
        localStorage.setItem('user', JSON.stringify(response.user))
        toast.success('Account created successfully!')
        return { success: true }
      }
      return { success: false, error: 'Registration failed' }
    } catch (error) {
      const message = error.response?.data?.error || 'Registration failed'
      toast.error(message)
      return { success: false, error: message }
    }
  }

  const logout = () => {
    setUser(null)
    localStorage.removeItem('user')
    localStorage.removeItem('authToken')
    toast.success('Logged out successfully')
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

