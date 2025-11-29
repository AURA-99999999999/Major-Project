/**
 * @fileoverview Type definitions for player-related components and context
 * These JSDoc types help catch errors during development
 */

/**
 * @typedef {Object} Song
 * @property {string} videoId - YouTube video ID
 * @property {string} url - Audio streaming URL
 * @property {string} title - Song title
 * @property {string|string[]} artists - Artist name(s)
 * @property {string} artist - Single artist name (fallback)
 * @property {string} thumbnail - Album art URL
 * @property {number} duration - Duration in seconds
 * @property {string} album - Album name
 * @property {string} year - Release year
 */

/**
 * @typedef {Object} PlayerState
 * @property {Song|null} currentSong - Currently playing song
 * @property {Song[]} queue - Queue of upcoming songs
 * @property {Song[]} history - Recently played songs
 * @property {boolean} isPlaying - Whether audio is currently playing
 * @property {number} volume - Volume level (0-1)
 * @property {Function} setVolume - Function to update volume
 * @property {number} currentTime - Current playback time in seconds
 * @property {number} duration - Total duration in seconds
 * @property {number} buffered - Buffered time in seconds
 * @property {'off'|'all'|'one'} repeatMode - Repeat mode
 * @property {boolean} shuffle - Whether shuffle is enabled
 * @property {boolean} loading - Whether a song is loading
 * @property {boolean} isSeeking - Whether user is currently seeking
 * @property {Function} playSong - Function to play a song by videoId
 * @property {Function} togglePlayPause - Function to toggle play/pause
 * @property {Function} playNext - Function to play next song
 * @property {Function} playPrevious - Function to play previous song
 * @property {Function} addToQueue - Function to add song to queue
 * @property {Function} removeFromQueue - Function to remove song from queue
 * @property {Function} clearQueue - Function to clear the queue
 * @property {Function} seekTo - Function to seek to a specific time
 * @property {Function} toggleRepeat - Function to toggle repeat mode
 * @property {Function} toggleShuffle - Function to toggle shuffle
 * @property {Function} playPlaylist - Function to play a playlist
 */

export {}

