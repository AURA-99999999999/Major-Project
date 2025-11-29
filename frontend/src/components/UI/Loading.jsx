import { motion } from 'framer-motion'

const Loading = ({ size = 'md', fullScreen = false }) => {
  const sizes = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  }
  
  const spinner = (
    <motion.div
      className={`${sizes[size]} border-4 border-dark-300 border-t-primary-600 rounded-full`}
      animate={{ rotate: 360 }}
      transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
    />
  )
  
  if (fullScreen) {
    return (
      <div className="fixed inset-0 flex items-center justify-center bg-dark-50/80 backdrop-blur-sm z-50">
        {spinner}
      </div>
    )
  }
  
  return <div className="flex items-center justify-center p-4">{spinner}</div>
}

export default Loading

