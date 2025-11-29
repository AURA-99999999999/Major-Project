# Player Architecture Documentation

## Overview

The DeciBel music player uses a centralized state management pattern with React Context API. All player state lives in a single `PlayerContext`, ensuring that all views (mini player, full player, queue, etc.) stay perfectly in sync.

## Core Architecture

### Single Source of Truth: `PlayerContext`

**Location**: `src/context/PlayerContext.jsx`

The `PlayerContext` is the single source of truth for all player state. It manages:
- Currently playing song
- Playback state (playing, paused, loading)
- Time tracking (current time, duration, buffered)
- Queue and history
- Player settings (volume, repeat, shuffle)

**Key Principle**: There is ONLY ONE audio element instance, managed at the context level. All views simply control this single instance.

### Audio Element Management

The audio element (`<audio>`) is created once in `PlayerProvider` and stored in a ref:

```javascript
const audioRef = useRef(null)

// Created once on mount
useEffect(() => {
  if (!audioRef.current) {
    audioRef.current = new Audio()
    // Event listeners attached here
  }
}, [])
```

**Important**: The audio element never gets recreated or removed. It lives for the entire app lifecycle.

## How Components Access Player State

### Using the `usePlayer()` Hook

All components that need player state should use the `usePlayer()` hook:

```javascript
import { usePlayer } from '../../context/PlayerContext'

const MyComponent = () => {
  const {
    currentSong,
    isPlaying,
    togglePlayPause,
    // ... other values
  } = usePlayer()
  
  // Use the values...
}
```

### Destructuring Rules

**ALWAYS** destructure only what you need:
```javascript
// ✅ Good - Only destructure what you use
const { currentSong, isPlaying } = usePlayer()

// ❌ Bad - Destructuring unused values
const { currentSong, isPlaying, queue, history, volume, /* 20 more... */ } = usePlayer()
```

**IMPORTANT**: If a component uses a value from the context, it MUST be in the destructuring list. Missing values will cause `undefined` errors.

## Component Hierarchy

```
App
└── PlayerProvider (Context Provider)
    ├── Layout
    │   ├── MiniPlayer (uses usePlayer)
    │   └── Pages (Routes)
    │       └── PlayerPage
    │           └── FullPlayer (uses usePlayer)
    └── Other Components
        ├── SongCard (uses usePlayer)
        └── PlayerControls (uses usePlayer)
```

## Adding New Views/Features

### Step-by-Step Guide

#### 1. Create Your Component

```javascript
import { usePlayer } from '../../context/PlayerContext'

const MyNewView = () => {
  // Step 2: Destructure what you need
  const { currentSong, isPlaying, togglePlayPause } = usePlayer()
  
  // Step 3: Use the values
  return (
    <div>
      {currentSong && <h1>{currentSong.title}</h1>}
      <button onClick={togglePlayPause}>
        {isPlaying ? 'Pause' : 'Play'}
      </button>
    </div>
  )
}
```

#### 2. Ensure All Values Are Destructured

**Checklist**:
- [ ] Every variable used in the component is destructured from `usePlayer()`
- [ ] No direct access to `context.something` without destructuring
- [ ] All destructured values are actually used

#### 3. Add JSDoc Types (Optional but Recommended)

```javascript
/**
 * My new player view component
 * @returns {JSX.Element}
 */
const MyNewView = () => {
  // ...
}
```

#### 4. Handle Edge Cases

Always handle the case where values might be null/undefined:

```javascript
const { currentSong } = usePlayer()

// ✅ Good - Check before using
if (!currentSong) {
  return <div>No song playing</div>
}

// ❌ Bad - Accessing properties on null
return <div>{currentSong.title}</div>
```

## Common Patterns

### Checking if Song is Playing

```javascript
const { currentSong, isPlaying } = usePlayer()

const isCurrentSong = currentSong?.videoId === someSong.videoId
const isCurrentlyPlaying = isCurrentSong && isPlaying
```

### Playing a Song

```javascript
const { playSong } = usePlayer()

// Play immediately
playSong(videoId)

// Add to queue instead
playSong(videoId, true) // Second param adds to queue
```

### Accessing Queue

```javascript
const { queue, addToQueue, removeFromQueue } = usePlayer()

// Add song to queue
addToQueue(songObject)

// Remove from queue
removeFromQueue(videoId)
```

## Available Player State

All values available from `usePlayer()`:

### State Values
- `currentSong: Song | null` - Currently playing song
- `queue: Song[]` - Upcoming songs
- `history: Song[]` - Recently played songs
- `isPlaying: boolean` - Whether audio is playing
- `volume: number` - Volume level (0-1)
- `currentTime: number` - Current playback time (seconds)
- `duration: number` - Total duration (seconds)
- `buffered: number` - Buffered time (seconds)
- `repeatMode: 'off' | 'all' | 'one'` - Repeat mode
- `shuffle: boolean` - Shuffle enabled
- `loading: boolean` - Song is loading
- `isSeeking: boolean` - User is seeking

### Functions
- `setVolume(volume: number)` - Update volume
- `playSong(videoId: string, addToQueue?: boolean)` - Play a song
- `togglePlayPause()` - Toggle play/pause
- `playNext()` - Play next song
- `playPrevious()` - Play previous song
- `addToQueue(song: Song)` - Add song to queue
- `removeFromQueue(videoId: string)` - Remove from queue
- `clearQueue()` - Clear entire queue
- `seekTo(time: number)` - Seek to time
- `toggleRepeat()` - Cycle repeat mode
- `toggleShuffle()` - Toggle shuffle
- `playPlaylist(songs: Song[])` - Play a playlist

## Error Prevention

### Validation

The `usePlayer()` hook includes validation in development mode:

```javascript
// Automatically validates state structure in development
const playerState = usePlayer() // Will throw if state is incomplete
```

### Type Checking

Use JSDoc types for better IDE support:

```javascript
/**
 * @type {import('../types/player').PlayerState}
 */
const playerState = usePlayer()
```

### ESLint Rules

The project includes ESLint rules to catch undefined variables:

- `no-undef`: Error on undefined variables
- `react-hooks/rules-of-hooks`: Error on incorrect hook usage
- `react-hooks/exhaustive-deps`: Warn on missing dependencies

## Debugging

### Check Context Value

If a value is undefined, check:

1. Is it destructured from `usePlayer()`?
   ```javascript
   // ✅ Correct
   const { isPlaying } = usePlayer()
   
   // ❌ Wrong - value is undefined
   // isPlaying is used but not destructured
   ```

2. Is the component inside `PlayerProvider`?
   ```javascript
   // Component must be inside Provider
   <PlayerProvider>
     <MyComponent /> {/* ✅ Can use usePlayer() */}
   </PlayerProvider>
   ```

3. Is the value exported from context?
   - Check `PlayerContext.jsx` value object
   - Ensure value is included in the provider value

### Console Logging

```javascript
const playerState = usePlayer()
console.log('Player state:', {
  hasCurrentSong: !!playerState.currentSong,
  isPlaying: playerState.isPlaying,
  availableKeys: Object.keys(playerState),
})
```

## Best Practices

1. **Always destructure what you use** - Missing destructuring = undefined error
2. **Check for null/undefined** - Handle empty states gracefully
3. **Use TypeScript/JSDoc** - Get IDE autocomplete and catch errors early
4. **Single audio element** - Never create multiple audio elements
5. **One source of truth** - Always use `usePlayer()`, never duplicate state
6. **Error boundaries** - Wrap components in error boundaries for graceful failures

## Troubleshooting

### "isPlaying is not defined"

**Cause**: Value used but not destructured from `usePlayer()`

**Fix**: Add to destructuring:
```javascript
const { isPlaying } = usePlayer() // Add this line
```

### "usePlayer must be used within PlayerProvider"

**Cause**: Component is outside the Provider

**Fix**: Wrap component tree with PlayerProvider:
```javascript
<PlayerProvider>
  <App />
</PlayerProvider>
```

### Audio not playing

**Cause**: Multiple audio elements or state not syncing

**Fix**: Ensure only one audio element exists (in PlayerContext) and all components use the same context

---

**Last Updated**: See git history for changes
**Maintainer**: See project README

