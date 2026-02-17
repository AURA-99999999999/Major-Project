# Aura Music - System Architecture

## Overview

Multi-layered architecture combining Flask backend, React frontend, and native Android app with real-time music streaming and cross-platform state synchronization.

## Backend Architecture

### Flask Server
- **Port**: 5000 (http://192.168.1.5:5000/api/)
- **Services**: Music fetching, recommendations, user management
- **Database**: Firestore (Firebase)

### Key Endpoints
- `/api/search` - Search songs by query
- `/api/get-song/{videoId}` - Get song streaming URL
- `/api/trending` - Get trending songs
- `/api/recommendations` - Get personalized recommendations

## Frontend (Web) Architecture

**Stack**: React + Vite + Tailwind CSS

### Key Screens
- **Home**: Trending songs, playlists, recommendations
- **Search**: Search and play songs
- **Library**: User playlists, liked songs
- **Player**: Full-screen player with controls

### State Management
- Context API (AuthContext, PlayerContext, ThemeContext)
- Real-time audio playback via Web Audio API

## Android Architecture

**Stack**: Kotlin + Jetpack Compose + Material 3

### MVVM Pattern with Single Source of Truth

```
┌─────────────────────────────────────────┐
│          RootNavGraph                   │
│    (Top-level navigation & mini player) │
└─────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────┐
│      PlayerViewModel (SHARED)           │
│  - currentSong                          │
│  - isPlaying                            │
│  - lastPlayedSong                       │
└─────────────────────────────────────────┘
    ↓              ↓              ↓
MusicService   Screens      MiniPlayerBar
   (ExoPlayer)  (Home,    (UI Component)
               Search, etc)
```

### Key Components

#### 1. PlayerViewModel (`viewmodel/PlayerViewModel.kt`)
Shared state holder across all screens:
- Holds `currentSong`, `isPlaying`, `lastPlayedSong` as StateFlows
- Auto-persists to Firestore via `persistLastPlayedSong()`
- Loaded from Firestore on login via `loadLastPlayedSong()`
- Cleared on logout via `clearState()`

#### 2. MusicService (`player/MusicService.kt`)
Manages ExoPlayer playback:
- Exposes `_currentSong` and `_isPlaying` StateFlows
- Syncs with PlayerViewModel for UI updates
- Handles playback queue and controls
- Integration with Firestore for play logging

#### 3. MiniPlayerBar (`ui/components/MiniPlayerBar.kt`)
Now Playing Bar component:
- Displays song thumbnail, title, artist
- Shows 3 media controls:
  - ⏮ Skip Previous
  - ⏯ Play/Pause (toggles based on state)
  - ⏭ Skip Next
- Tappable to navigate to full player screen
- Hides on player screen (route detection)

#### 4. RootNavGraph (`navigation/RootNavGraph.kt`)
Top-level navigation:
- Creates and manages PlayerViewModel
- Syncs MusicService ↔ PlayerViewModel bidirectionally
- Conditionally displays MiniPlayerBar with route detection
- Overlaid positioning (56dp + 14dp from bottom nav)
- Lifecycle hooks for login/logout

#### 5. FirestoreRepository (`repository/FirestoreRepository.kt`)
Firebase integration:
- `updateLastPlayedSong(song)` - Persists to Firestore
- `getLastPlayedSong()` - Loads from Firestore
- Document path: `users/{uid}/lastPlayed/current`
- Real-time sync for cross-device continuity

### Navigation Structure

```
RootNavGraph (Single NavHost)
├── Auth Graph (unauthenticated)
│   ├── Login
│   └── Signup
└── Main Graph (authenticated)
    ├── Home (start)
    ├── Search
    ├── Player
    ├── Playlists
    ├── Playlist Detail
    ├── Liked Songs
    └── Profile
```

### Data Flow: Song Playback

```
User clicks song on HomeScreen
  ↓
homeViewModel.playSongByVideoId()
  ↓
musicRepository.getSong() resolves streaming URL
  ↓
musicService.playResolvedSong()
  ↓
MusicService updates _currentSong, _isPlaying StateFlows
  ↓
RootNavGraph observes changes
  ↓
playerViewModel.updateCurrentSong() & updateIsPlaying()
  ↓
playerViewModel.persistLastPlayedSong() (async)
  ↓
MiniPlayerBar updates visually
  ↓
Firestore document at users/{uid}/lastPlayed/current created/updated
```

### Authentication Flow

**Login**:
1. User signs in via Google OAuth
2. AuthViewModel stores auth state
3. RootNavGraph detects AuthState.Authenticated
4. LaunchedEffect calls `playerViewModel.loadLastPlayedSong()`
5. Last played song displays in mini player if available
6. MainGraph renders

**Logout**:
1. User clicks logout in ProfileScreen
2. AuthViewModel updates to AuthState.Unauthenticated
3. RootNavGraph detects change
4. LaunchedEffect calls `playerViewModel.clearState()`
5. MiniPlayerBar disappears
6. Navigation clears back stack and returns to Auth screen

### Firestore Structure

```
users/
  {uid}/
    userProfile/
      email: string
      displayName: string
      createdAt: timestamp
    lastPlayed/
      current/
        videoId: string
        title: string
        artists: list<string>
        album: string
        thumbnail: string
        duration: number
        lastPlayedAt: server_timestamp
    playlists/
      {playlistId}: {...}
    likedSongs/
      {videoId}: {...}
```

### Recommendations System

Real-time personalized recommendations based on:
- Listening history
- Liked songs
- Search patterns
- Trending songs

Integration: `services/recommendation_service.py` + HomeScreen UI

### Mini Player Features

**Position**: Above bottom navigation (56dp + 14dp extra spacing from bottom)
**Visibility**: All screens except full player screen
**Controls**: Skip previous, play/pause, skip next
**Persistence**: Last played song loaded on app restart
**Scroll Handling**: 120dp bottom padding on all screens prevents content overlap

## State Management Pattern

### Single Source of Truth
- PlayerViewModel is canonical source for current playback state
- MusicService updates PlayerViewModel through defined methods
- UI observes PlayerViewModel via collectAsState()
- No duplicate state in separate places

### StateFlow Pattern
```kotlin
private val _currentSong = MutableStateFlow<Song?>(null)
val currentSong = _currentSong.asStateFlow()

fun updateCurrentSong(song: Song?) {
    _currentSong.value = song
}
```

## Dependency Injection

ViewModelFactory pattern with application context:
```kotlin
viewModel(factory = ViewModelFactory.create(context))
```

Handles:
- PlayerViewModel creation
- FirestoreRepository injection
- MusicRepository injection

## Performance Considerations

1. **Image Loading**: Coil with caching for thumbnails
2. **List Rendering**: LazyColumn/LazyRow for large datasets
3. **State Updates**: StateFlow for efficient recomposition
4. **Persistence**: Async Firestore updates don't block UI
5. **Media**: ExoPlayer for optimized audio playback

## File Organization

```
android/app/src/main/java/com/aura/music/
├── MainActivity.kt
├── auth/
│   ├── screens/
│   ├── state/
│   └── viewmodel/
├── player/
│   └── MusicService.kt
├── ui/
│   ├── components/
│   │   ├── MiniPlayerBar.kt
│   │   └── [other components]
│   ├── screens/
│   │   ├── home/
│   │   ├── search/
│   │   ├── player/
│   │   └── [other screens]
│   ├── theme/
│   └── viewmodel/
│       ├── PlayerViewModel.kt
│       └── [other viewmodels]
├── navigation/
│   ├── RootNavGraph.kt
│   ├── AuthGraph.kt
│   ├── MainGraph.kt
│   └── ViewModelFactory.kt
├── repository/
│   ├── FirestoreRepository.kt
│   └── MusicRepository.kt
└── data/
    │── model/
    └── remote/
```
