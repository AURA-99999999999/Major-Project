import { NavLink } from 'react-router-dom'
import { motion } from 'framer-motion'
import {
  FaHome,
  FaSearch,
  FaMusic,
  FaHeart,
  FaPlus,
  FaList,
  FaMoon,
  FaSun,
} from 'react-icons/fa'
import { useTheme } from '../../context/ThemeContext'
import { useAuth } from '../../context/AuthContext'

const Sidebar = () => {
  const { theme, toggleTheme } = useTheme()
  const { user } = useAuth()

  const navItems = [
    { path: '/', icon: FaHome, label: 'Home' },
    { path: '/search', icon: FaSearch, label: 'Search' },
    { path: '/playlists', icon: FaList, label: 'Your Library' },
  ]

  const userNavItems = user
    ? [{ path: '/profile', icon: FaHeart, label: 'Liked Songs' }]
    : []

  return (
    <aside className="w-64 bg-dark-100 border-r border-white/10 flex flex-col">
      {/* Logo */}
      <div className="p-6 border-b border-white/10">
        <h1 className="text-2xl font-bold gradient-text">Aura</h1>
        <p className="text-dark-400 text-xs mt-1">Music Streaming</p>
      </div>

      {/* Navigation */}
      <nav className="flex-1 p-4 space-y-1">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                isActive
                  ? 'bg-primary-600/20 text-primary-400'
                  : 'text-dark-400 hover:bg-dark-200 hover:text-white'
              }`
            }
          >
            <item.icon className="text-lg" />
            <span className="font-medium">{item.label}</span>
          </NavLink>
        ))}

        {userNavItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                isActive
                  ? 'bg-primary-600/20 text-primary-400'
                  : 'text-dark-400 hover:bg-dark-200 hover:text-white'
              }`
            }
          >
            <item.icon className="text-lg" />
            <span className="font-medium">{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {/* Create Playlist */}
      <div className="p-4 border-t border-white/10">
        <motion.button
          whileHover={{ scale: 1.05 }}
          whileTap={{ scale: 0.95 }}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-lg bg-dark-200 hover:bg-dark-300 text-white transition-colors"
        >
          <FaPlus className="text-lg" />
          <span className="font-medium">Create Playlist</span>
        </motion.button>
      </div>

      {/* Theme Toggle */}
      <div className="p-4 border-t border-white/10">
        <button
          onClick={toggleTheme}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-dark-200 text-dark-400 hover:text-white transition-colors"
        >
          {theme === 'dark' ? (
            <>
              <FaSun className="text-lg" />
              <span className="font-medium">Light Mode</span>
            </>
          ) : (
            <>
              <FaMoon className="text-lg" />
              <span className="font-medium">Dark Mode</span>
            </>
          )}
        </button>
      </div>
    </aside>
  )
}

export default Sidebar

