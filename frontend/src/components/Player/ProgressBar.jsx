import { useState, useRef, useEffect } from 'react'
import { motion } from 'framer-motion'

/**
 * Reusable progress bar component with seeking functionality
 */
const ProgressBar = ({ 
  currentTime, 
  duration, 
  buffered = 0, 
  onSeek, 
  className = '',
  showTime = true,
  height = 'h-1',
  disabled = false 
}) => {
  const [isDragging, setIsDragging] = useState(false)
  const [dragValue, setDragValue] = useState(0)
  const barRef = useRef(null)

  const progress = duration > 0 ? (currentTime / duration) * 100 : 0
  const bufferedPercent = duration > 0 ? (buffered / duration) * 100 : 0
  const displayProgress = isDragging ? dragValue : progress

  const formatTime = (seconds) => {
    if (!seconds || isNaN(seconds) || !isFinite(seconds)) return '0:00'
    const mins = Math.floor(seconds / 60)
    const secs = Math.floor(seconds % 60)
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }

  const handleMouseDown = (e) => {
    if (disabled || !duration) return
    setIsDragging(true)
    handleMouseMove(e)
  }

  const handleMouseMove = (e) => {
    if (!barRef.current || !duration) return
    
    const rect = barRef.current.getBoundingClientRect()
    const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    const seekTime = percent * duration
    
    setDragValue(percent * 100)
    
    if (isDragging && onSeek) {
      onSeek(seekTime)
    }
  }

  const handleMouseUp = (e) => {
    if (!isDragging || !duration || !barRef.current) return
    
    const rect = barRef.current.getBoundingClientRect()
    const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
    const seekTime = percent * duration
    
    if (onSeek) {
      onSeek(seekTime)
    }
    
    setIsDragging(false)
    setDragValue(0)
  }

  useEffect(() => {
    if (isDragging) {
      const handleGlobalMouseMove = (e) => handleMouseMove(e)
      const handleGlobalMouseUp = (e) => handleMouseUp(e)
      
      window.addEventListener('mousemove', handleGlobalMouseMove)
      window.addEventListener('mouseup', handleGlobalMouseUp)
      
      return () => {
        window.removeEventListener('mousemove', handleGlobalMouseMove)
        window.removeEventListener('mouseup', handleGlobalMouseUp)
      }
    }
  }, [isDragging, duration])

  return (
    <div className={`w-full ${className}`}>
      <div
        ref={barRef}
        className={`${height} bg-dark-200 rounded-full cursor-pointer relative group ${disabled ? 'cursor-not-allowed opacity-50' : ''}`}
        onMouseDown={handleMouseDown}
        role="slider"
        aria-label="Progress"
        aria-valuemin={0}
        aria-valuemax={duration || 0}
        aria-valuenow={currentTime}
        tabIndex={disabled ? -1 : 0}
      >
        {/* Buffered progress */}
        {bufferedPercent > 0 && (
          <div
            className="absolute top-0 left-0 h-full bg-dark-300 rounded-full transition-all"
            style={{ width: `${bufferedPercent}%` }}
          />
        )}
        
        {/* Current progress */}
        <motion.div
          className="absolute top-0 left-0 h-full bg-gradient-to-r from-primary-500 to-purple-500 rounded-full"
          style={{ width: `${displayProgress}%` }}
          transition={{ duration: isDragging ? 0 : 0.1 }}
        />
        
        {/* Handle */}
        <motion.div
          className="absolute top-1/2 -translate-y-1/2 w-4 h-4 bg-white rounded-full shadow-lg opacity-0 group-hover:opacity-100 transition-opacity"
          style={{ 
            left: `${displayProgress}%`, 
            marginLeft: '-8px',
            ...(isDragging && { opacity: 1 })
          }}
        />
      </div>
      
      {showTime && (
        <div className="flex justify-between text-sm text-dark-400 mt-2">
          <span>{formatTime(currentTime)}</span>
          <span>{formatTime(duration)}</span>
        </div>
      )}
    </div>
  )
}

export default ProgressBar

