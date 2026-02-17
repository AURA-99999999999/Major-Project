# Aura Music Streaming Platform

A modern, full-stack music streaming application with Flask backend, React frontend, and native Android app.

## Quick Links

- **Architecture & Components**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **Setup & Development Guide**: See [SETUP_AND_IMPLEMENTATION.md](SETUP_AND_IMPLEMENTATION.md)

## 🎵 Features

- Music search & streaming with real-time playback
- Persistent Mini Player (Now Playing Bar) across all screens
- Playlists management with liked songs
- Firebase authentication with Google Sign-In
- Cross-platform (Web + Android)
- Background audio playback with ExoPlayer
- Queue management and playback controls
- Personalized recommendations
- Firestore-backed persistence

## 🏗️ Tech Stack

- **Backend**: Flask (Python)
- **Frontend**: React + Vite + Tailwind CSS
- **Android**: Kotlin + Jetpack Compose + Material 3
- **Auth**: Firebase Authentication
- **Database**: Firestore
- **Audio**: ExoPlayer (Android), Web Audio API (Web)
- **State**: StateFlow + MVVM Pattern

## 🚀 Quick Start

### Backend
```bash
pip install -r requirements.txt
python app.py
```
Server: `http://localhost:5000/api/`

### Frontend
```bash
cd frontend
npm install
npm run dev
```
App: `http://localhost:3000`

### Android
```bash
cd android
./gradlew assembleDebug
./gradlew installDebug
```

**For detailed setup instructions**, see [SETUP_AND_IMPLEMENTATION.md](SETUP_AND_IMPLEMENTATION.md)

## 📂 Project Structure

```
Major-Project/
├── README.md                    # This file
├── ARCHITECTURE.md              # System design & components
├── SETUP_AND_IMPLEMENTATION.md  # Development guide
├── app.py                       # Flask backend
├── requirements.txt             
├── services/                    # Backend business logic
├── frontend/                    # React web app
├── android/                     # Kotlin Android app
│   └── app/src/main/java/com/aura/music/
│       ├── MainActivity.kt
│       ├── navigation/          # RootNavGraph, MainGraph
│       ├── ui/
│       │   ├── components/MiniPlayerBar.kt
│       │   ├── screens/        # Home, Search, Player, etc
│       │   └── viewmodel/      # PlayerViewModel
│       └── player/MusicService.kt
└── data/                        # JSON data storage
```

## 🎯 Key Features

### Mini Player (Now Playing Bar)
- Appears above bottom navigation on all screens (except full player)
- Shows song thumbnail, title, artist
- Media controls: skip previous, play/pause, skip next
- Persists last played song with Firestore
- Loads previous session's song on app start

### Home Screen
- Trending songs with horizontal scrolling
- **Personalized recommendations** with ML-inspired features:
  - Weighted signals: Liked songs (2x) + play history (1x)
  - Time-decay weighting: Recent plays prioritized
  - Diversity enforcement: Max 2 songs per artist, 3 per album
  - Cold-start fallback for new users
- Explore by Mood with category selection
- Pull-to-refresh for fresh data

### Search
- Real-time song search
- Multi-source search (TMDB, YouTube Music)
- Play from search results

### Playlists
- Create and manage custom playlists
- Like/unlike songs
- View liked songs collection

### Player
- Full-screen player with album art
- Complete playback controls
- Queue management
- Current timestamp display

## 🔐 Authentication

Firebase Google Sign-In:
- Authenticate via Google OAuth
- Secure token-based sessions
- Auto-login on app restart
- Logout clears all user data

## 💾 Data Persistence

**Firestore Structure**:
```
users/{uid}/
  ├── userProfile/          # User info
  ├── lastPlayed/current    # Mini player persistence
  ├── plays/                # Play history with timestamps (for time-decay recommendations)
  ├── playlists/            # User playlists
  └── likedSongs/           # Liked songs collection with timestamps
```

## 🔄 State Management

**Single Source of Truth**: PlayerViewModel holds shared playback state
- Synced with MusicService (ExoPlayer)
- Observed by MiniPlayerBar and player screens
- Auto-persisted to Firestore

## 📊 Data Flow

```
User clicks song
  ↓ Resolves streaming URL
  ↓ Plays via ExoPlayer
  ↓ Updates PlayerViewModel
  ↓ Mini player displays in real-time
  ↓ Last played saved to Firestore
```

## 🐛 Troubleshooting

See [SETUP_AND_IMPLEMENTATION.md](SETUP_AND_IMPLEMENTATION.md) for:
- Backend/frontend/Android build issues
- Firebase configuration problems
- Mini player display issues
- Firestore connectivity troubleshooting

## 📈 Performance

- Lazy-loaded list rendering (LazyColumn/LazyRow)
- Image caching with Coil
- Async Firestore updates (non-blocking)
- ExoPlayer for hardware-accelerated audio
- StateFlow for efficient recomposition

## 📝 License

MIT License - Educational purposes

---

**Documentation Updated**: February 2026  
**Current Features**: Fully functional mini player, home screen, search, playlists, authentication, cross-platform persistence

Built with ❤️ using Flask, React, Kotlin, and Jetpack Compose
