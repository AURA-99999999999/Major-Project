import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { motion } from 'framer-motion'
import { useAuth } from '../context/AuthContext'
import Button from '../components/UI/Button'
import Loading from '../components/UI/Loading'

const Login = () => {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    
    const result = await login(email, password)
    setLoading(false)
    
    if (result.success) {
      navigate('/')
    }
  }

  if (loading) {
    return <Loading fullScreen />
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gradient-to-br from-dark-50 via-dark-100 to-dark-200">
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="glass rounded-2xl p-8 max-w-md w-full shadow-2xl"
      >
        <div className="text-center mb-8">
          <h1 className="text-4xl font-bold mb-2 gradient-text">Aura</h1>
          <p className="text-dark-400">Sign in to your account</p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium mb-2">Email</label>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-3 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="you@example.com"
              required
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-2">Password</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 bg-dark-200 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-primary-500"
              placeholder="••••••••"
              required
            />
          </div>

          <Button type="submit" variant="primary" className="w-full" size="lg">
            Sign In
          </Button>
        </form>

        <p className="mt-6 text-center text-dark-400">
          Don't have an account?{' '}
          <Link to="/register" className="text-primary-400 hover:text-primary-300">
            Sign up
          </Link>
        </p>
      </motion.div>
    </div>
  )
}

export default Login

