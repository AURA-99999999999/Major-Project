import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FaSearch, FaUser, FaSignOutAlt } from 'react-icons/fa'
import { useAuth } from '../../context/AuthContext'
import Button from '../UI/Button'

const Navbar = () => {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [searchQuery, setSearchQuery] = useState('')

  const handleSearch = (e) => {
    e.preventDefault()
    if (searchQuery.trim()) {
      navigate(`/search?q=${encodeURIComponent(searchQuery.trim())}`)
    }
  }

  return (
    <nav className="glass border-b border-white/10 px-6 py-4">
      <div className="flex items-center justify-between">
        {/* Search */}
        <form onSubmit={handleSearch} className="flex-1 max-w-2xl">
          <div className="relative">
            <FaSearch className="absolute left-4 top-1/2 -translate-y-1/2 text-dark-400" />
            <input
              type="text"
              placeholder="Search for songs, artists, albums..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-12 pr-4 py-2 bg-dark-200 rounded-full text-white placeholder-dark-400 focus:outline-none focus:ring-2 focus:ring-primary-500"
            />
          </div>
        </form>

        {/* User Menu */}
        <div className="flex items-center gap-4 ml-6">
          {user ? (
            <>
              <div className="flex items-center gap-3">
                <div className="text-right">
                  <p className="text-white font-medium text-sm">{user.username}</p>
                  <p className="text-dark-400 text-xs">{user.email}</p>
                </div>
                <button
                  onClick={() => navigate('/profile')}
                  className="w-10 h-10 rounded-full bg-primary-600 flex items-center justify-center hover:bg-primary-700 transition-colors"
                >
                  <FaUser className="text-white" />
                </button>
              </div>
              <button
                onClick={logout}
                className="p-2 hover:bg-dark-200 rounded-lg transition-colors"
                aria-label="Logout"
              >
                <FaSignOutAlt className="text-dark-400 hover:text-white" />
              </button>
            </>
          ) : (
            <>
              <Button
                variant="ghost"
                onClick={() => navigate('/login')}
              >
                Log In
              </Button>
              <Button
                variant="primary"
                onClick={() => navigate('/register')}
              >
                Sign Up
              </Button>
            </>
          )}
        </div>
      </div>
    </nav>
  )
}

export default Navbar

