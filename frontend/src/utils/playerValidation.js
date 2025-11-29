/**
 * @fileoverview Validation utilities for player context
 * Helps catch missing or undefined values early
 */

/**
 * Validates that all required player context values are present
 * @param {import('../types/player').PlayerState} playerState
 * @throws {Error} If required values are missing
 */
export function validatePlayerState(playerState) {
  if (!playerState) {
    throw new Error('Player state is undefined. Make sure you are inside PlayerProvider.')
  }

  const requiredKeys = [
    'currentSong',
    'queue',
    'history',
    'isPlaying',
    'volume',
    'setVolume',
    'currentTime',
    'duration',
    'buffered',
    'repeatMode',
    'shuffle',
    'loading',
    'isSeeking',
    'playSong',
    'togglePlayPause',
    'playNext',
    'playPrevious',
    'addToQueue',
    'removeFromQueue',
    'clearQueue',
    'seekTo',
    'toggleRepeat',
    'toggleShuffle',
    'playPlaylist',
  ]

  const missingKeys = requiredKeys.filter(key => !(key in playerState))
  
  if (missingKeys.length > 0) {
    console.error('Missing player state keys:', missingKeys)
    console.error('Available keys:', Object.keys(playerState))
    throw new Error(`Player state is incomplete. Missing keys: ${missingKeys.join(', ')}`)
  }
}

/**
 * Validates that a song object has required properties
 * @param {import('../types/player').Song} song
 * @returns {boolean}
 */
export function isValidSong(song) {
  if (!song) return false
  return typeof song.videoId === 'string' && song.videoId.length > 0
}

