# Aura Music Streaming Platform

A modern, full-stack music streaming application with Flask backend, React frontend, and native Android app.

## Quick Links

- **Architecture & Components**: See [ARCHITECTURE.md](ARCHITECTURE.md)
- **Setup & Development Guide**: See [SETUP_AND_IMPLEMENTATION.md](SETUP_AND_IMPLEMENTATION.md)

## 🎵 Features

- **🤖 Advanced ML-Inspired Recommendation Engine**
  - Weighted signals (liked songs 2x, plays 1x)
  - Time-decay weighting for freshness
  - Diversity enforcement (max 2/artist, 3/album)
  - Cold-start fallback with trending songs
- Music search & streaming with real-time playback
- Persistent Mini Player (Now Playing Bar) across all screens
- Playlists management with liked songs
- Firebase authentication with Google Sign-In
- Cross-platform (Web + Android)
- Background audio playback with ExoPlayer
- Queue management and playback controls
- Firestore-backed persistence with timestamps

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

### 🤖 Advanced Recommendation System

**Production-grade hybrid ML recommender** with intelligent personalization:

#### Core Algorithm Features
- **Weighted Signal Processing**: 
  - Liked songs: 2x preference weight
  - Play history: 1x base weight with playCount multiplier
  - Combines both signals for comprehensive taste profile

- **Time-Decay Weighting** (Exponential Freshness):
  - Formula: `weight = base_weight × exp(-λ × days_since_play)`
  - Default λ = 0.15 (configurable via `RECOMMENDER_DECAY_LAMBDA`)
  - Recent plays (today): ~100% influence
  - Week-old plays: ~35% influence
  - Month-old plays: ~1% influence
  - Ensures recommendations stay fresh and relevant

- **Diversity Layer** (Anti-Dominance):
  - Maximum 2 songs per artist
  - Maximum 3 songs per album
  - Prevents artist/album saturation
  - Maintains ranking order without re-scoring

- **Cold-Start Handling**:
  - Trending songs fallback for new users
  - Works with zero history seamlessly
  - Graceful degradation

#### Smart Candidate Generation
- Top 5 weighted artists extraction
- Top 3 weighted albums clustering
- Multi-source search (YouTube Music API)
- Artist similarity and related songs
- Real-time query optimization

#### API Endpoint
```
GET /api/recommendations?uid={user_id}&limit=20
```

**Result**: Dynamic, diverse, and fresh recommendations that evolve with user taste over time.

---

### 🎵 Music Playback & UI

**Mini Player (Now Playing Bar)**
- Persistent across all screens (above bottom navigation)
- Media controls: previous, play/pause, next
- Firestore-backed session persistence

**Full Player Screen**
- Full-screen album art
- Complete playback controls & queue management
- Current timestamp display

**Search & Discovery**
- Real-time song search (YouTube Music)
- Play from search results
- Explore by Mood categories

**Playlists & Library**
- Create custom playlists
- Like/unlike songs
- View liked songs collection

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
**Current Features**: Production-grade recommendation engine with time-decay weighting, diversity layer, mini player, home screen, search, playlists, authentication, cross-platform persistence

Built with ❤️ using Flask, React, Kotlin, and Jetpack Compose
