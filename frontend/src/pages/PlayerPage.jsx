import FullPlayer from '../components/Player/FullPlayer'
import { motion } from 'framer-motion'

const PlayerPage = () => {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="h-full"
    >
      <FullPlayer />
    </motion.div>
  )
}

export default PlayerPage

