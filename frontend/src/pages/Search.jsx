import { useState, useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import { motion } from 'framer-motion'
import { musicService } from '../services/musicService'
import SongList from '../components/Music/SongList'
import Loading from '../components/UI/Loading'
import { FaSearch } from 'react-icons/fa'

const Search = () => {
  const [searchParams, setSearchParams] = useSearchParams()
  const [query, setQuery] = useState(searchParams.get('q') || '')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    const searchQuery = searchParams.get('q')
    if (searchQuery) {
      setQuery(searchQuery)
      performSearch(searchQuery)
    }
  }, [searchParams])

  const performSearch = async (searchQuery) => {
    if (!searchQuery.trim()) {
      setResults([])
      return
    }

    try {
      setLoading(true)
      const response = await musicService.search(searchQuery, 50)
      if (response.success) {
        setResults(response.results || [])
      }
    } catch (error) {
      console.error('Search error:', error)
    } finally {
      setLoading(false)
    }
  }

  const handleSubmit = (e) => {
    e.preventDefault()
    if (query.trim()) {
      setSearchParams({ q: query.trim() })
      performSearch(query.trim())
    }
  }

  return (
    <div className="container mx-auto px-6 py-8">
      {/* Search Header */}
      <motion.div
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
        className="mb-8"
      >
        <h1 className="text-4xl font-bold mb-6">Search</h1>
        <form onSubmit={handleSubmit} className="max-w-2xl">
          <div className="relative">
            <FaSearch className="absolute left-6 top-1/2 -translate-y-1/2 text-dark-400 text-xl" />
            <input
              type="text"
              placeholder="Search for songs, artists, albums..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              className="w-full pl-16 pr-6 py-4 bg-dark-100 rounded-full text-white placeholder-dark-400 focus:outline-none focus:ring-2 focus:ring-primary-500 text-lg"
            />
          </div>
        </form>
      </motion.div>

      {/* Results */}
      {loading ? (
        <Loading fullScreen />
      ) : query ? (
        <SongList
          songs={results}
          title={results.length > 0 ? `Search results for "${query}"` : ''}
        />
      ) : (
        <div className="text-center py-16">
          <FaSearch className="text-6xl text-dark-400 mx-auto mb-4" />
          <p className="text-dark-400 text-xl">
            Search for your favorite songs, artists, or albums
          </p>
        </div>
      )}
    </div>
  )
}

export default Search

