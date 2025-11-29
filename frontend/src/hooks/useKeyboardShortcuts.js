import { useEffect } from 'react'
import { usePlayer } from '../context/PlayerContext'

/**
 * Custom hook for keyboard shortcuts
 */
export const useKeyboardShortcuts = () => {
  const {
    isPlaying,
    togglePlayPause,
    playNext,
    playPrevious,
    volume,
    setVolume,
    currentTime,
    duration,
    seekTo,
  } = usePlayer()

  useEffect(() => {
    const handleKeyDown = (e) => {
      // Ignore if user is typing in an input
      if (
        e.target.tagName === 'INPUT' ||
        e.target.tagName === 'TEXTAREA' ||
        e.target.isContentEditable
      ) {
        return
      }

      switch (e.key) {
        case ' ': // Spacebar - Play/Pause
          e.preventDefault()
          togglePlayPause()
          break

        case 'ArrowLeft':
          e.preventDefault()
          if (e.shiftKey) {
            // Shift + Left = Previous track
            playPrevious()
          } else {
            // Left = Seek backward 10 seconds
            const newTime = Math.max(0, currentTime - 10)
            seekTo(newTime)
          }
          break

        case 'ArrowRight':
          e.preventDefault()
          if (e.shiftKey) {
            // Shift + Right = Next track
            playNext()
          } else {
            // Right = Seek forward 10 seconds
            const newTime = Math.min(duration, currentTime + 10)
            seekTo(newTime)
          }
          break

        case 'ArrowUp':
          e.preventDefault()
          // Volume up
          setVolume(Math.min(1, volume + 0.05))
          break

        case 'ArrowDown':
          e.preventDefault()
          // Volume down
          setVolume(Math.max(0, volume - 0.05))
          break

        case 'm':
        case 'M':
          e.preventDefault()
          // Mute toggle
          setVolume(volume > 0 ? 0 : 1)
          break

        default:
          break
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [
    isPlaying,
    togglePlayPause,
    playNext,
    playPrevious,
    volume,
    setVolume,
    currentTime,
    duration,
    seekTo,
  ])
}

export default useKeyboardShortcuts

