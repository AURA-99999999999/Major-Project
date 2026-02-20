# Aura Music Streaming Platform

A modern, full-stack music streaming application with Flask backend, React frontend, and native Android app featuring an advanced ML-inspired recommendation engine.

## 🎵 Key Features

### 🤖 Advanced Recommendation Engine

**Production-grade hybrid recommender system** with intelligent personalization:

#### Core Algorithm
- **Weighted Signal Processing**
  - Liked songs: 2x preference weight
  - Play history: 1x base weight with playCount multiplier
  - Combines both signals for comprehensive taste profile

- **Time-Decay Weighting** (Exponential Freshness)
  - Formula: `weight = base_weight × exp(-λ × days_since_play)`
  - Default λ = 0.15 (configurable via `RECOMMENDER_DECAY_LAMBDA`)
  - Recent plays (today): ~100% influence
  - Week-old plays: ~35% influence
  - Month-old plays: ~1% influence

- **Diversity Layer** (Anti-Dominance)
  - Maximum 2 songs per artist
  - Maximum 3 songs per album
  - Prevents artist/album saturation
  - Maintains ranking order without re-scoring

- **Cold-Start Handling**
  - Trending songs fallback for new users
  - Works seamlessly with zero history

#### Smart Candidate Generation
- Top 5 weighted artists extraction
- Top 3 weighted albums clustering
- Multi-source search (YouTube Music API)
- Artist similarity and related songs
- Real-time query optimization

#### Top Artists Feature
- Displays personalized top 5-10 artists on home screen
- Based on weighted listening history
- Circular artist thumbnails with subscriber count
- Click to view artist details, songs, and albums

### 🎵 Music Features
- **Search & Discovery**: Real-time song search with YouTube Music integration
- **Explore by Mood**: Browse curated playlists by mood/genre categories
- **Music Filtering**: Smart filtering excludes non-music content (interviews, podcasts, etc.)
- **Trending Songs**: Updated trending charts with proper validation
- **Playlists**: Create custom playlists and manage liked songs
- **Persistent Mini Player**: Now Playing Bar visible across all screens
- **Queue Management**: Full playback control with queue support

### 🔐 Authentication & Security
- Firebase Google Sign-In with OAuth
- Email verification for password reset
- Secure token-based sessions
- Auto-login on app restart
- Logout clears all user data

## 🏗️ Tech Stack

**Backend**: Flask (Python), YTMusic API  
**Frontend**: React, Vite, Tailwind CSS  
**Android**: Kotlin, Jetpack Compose, Material 3  
**Auth**: Firebase Authentication  
**Database**: Firestore  
**Audio**: ExoPlayer (Android), Web Audio API (Web)  
**State**: StateFlow, MVVM Pattern

## 🚀 Quick Start

### Backend
```bash
cd backend
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

## 📂 Project Structure

```
Major-Project/
├── README.md                    # Project documentation
├── backend/                     # Flask backend server
│   ├── app.py                   # Main Flask application
│   ├── requirements.txt         # Python dependencies
│   ├── config.py                # Configuration
│   ├── services/                # Backend business logic
│   │   ├── recommendation_service.py  # ML recommendation engine
│   │   ├── music_service.py           # Music search & trending
│   │   ├── music_filter.py            # Smart content filtering
│   │   ├── mood_service.py            # Mood-based discovery
│   │   ├── detail_service.py          # Artist/album details
│   │   ├── playlist_service.py        # Playlist management
│   │   └── user_service.py            # User data management
│   ├── data/                    # Local data storage
│   ├── templates/               # HTML templates
│   └── test_*.py                # Test files
├── frontend/                    # React web app
│   └── src/
│       ├── pages/               # Home, Search, Player, etc.
│       ├── components/          # Reusable UI components
│       └── services/            # API integration
└── android/                     # Kotlin Android app
    └── app/src/main/java/com/aura/music/
        ├── navigation/          # App navigation
        ├── ui/screens/          # Composable screens
        ├── ui/viewmodel/        # ViewModels (MVVM)
        ├── data/repository/     # Data layer
        └── player/              # ExoPlayer service
```

## 🎯 API Endpoints

### Recommendations
- `GET /api/recommendations?uid={uid}&limit=20` - Personalized song recommendations
- `GET /api/home/top-artists?uid={uid}&limit=10` - Top artists for user

### Music Discovery
- `GET /api/search?query={query}` - Search songs
- `GET /api/trending?limit=20` - Trending songs
- `GET /api/mood/categories` - Explore by mood categories
- `GET /api/mood/playlists?params={params}` - Mood playlists

### Song Details
- `GET /api/get-song/{videoId}` - Get streaming URL
- `GET /api/song/{videoId}` - Get song metadata
- `GET /api/artist/{browseId}` - Get artist details
- `GET /api/album/{browseId}` - Get album details

### User Management
- `POST /api/play` - Track song play
- `POST /api/like` - Like/unlike song
- `GET /api/user/{uid}/plays` - Get play history
- `GET /api/user/{uid}/liked` - Get liked songs

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

## 🔄 Recommendation Algorithm Details

### Weighted Signals
The recommender combines multiple signal sources:
```python
SIGNAL_WEIGHTS = {
    'liked_songs': 2.0,    # User explicitly liked
    'play_history': 1.0,   # User listened to
}
```

### Time-Decay Function
Recent activity is prioritized using exponential decay:
```python
def calculate_time_weight(days_since):
    return math.exp(-decay_lambda * days_since)

# Examples with λ=0.15:
# Today (0 days):    weight = 1.00 (100%)
# 1 week (7 days):   weight = 0.35 (35%)
# 1 month (30 days): weight = 0.01 (1%)
```

### Artist/Album Extraction
1. Fetch all user plays and liked songs
2. Apply time-decay weights to each signal
3. Aggregate by artist: `artist_score = Σ(signal_weight × time_weight)`
4. Extract top 5 artists by score
5. Extract top 3 albums by score

### Candidate Generation (Multi-Source)
- Search top 5 artists on YouTube Music
- Fetch related artists for each top artist
- Get artist radio/similar songs
- Fetch album tracks for top 3 albums
- Search by weighted genre/mood tags

### Diversity Enforcement
After scoring candidates:
1. Sort by recommendation score (descending)
2. Track count per artist and album
3. Keep song if: artist_count < 2 AND album_count < 3
4. Skip song otherwise
5. Return top N diverse recommendations

## 📊 Data Flow

```
User Action (play/like song)
  ↓
Update Firestore (plays/likedSongs with timestamp)
  ↓
User requests recommendations
  ↓
RecommendationService fetches user signals
  ↓
Calculate time-weighted artist/album scores
  ↓
Generate candidates from multiple sources
  ↓
Apply diversity layer
  ↓
Return personalized song list
  ↓
User plays song → cycle repeats
```

## 🎨 Android Architecture (MVVM)

```
┌─────────────────────────────────────────┐
│          MainActivity                    │
│    (RootNavGraph + MiniPlayerBar)       │
└─────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────┐
│      PlayerViewModel (SHARED)           │
│  - currentSong: StateFlow               │
│  - isPlaying: StateFlow                 │
│  - Synced with MusicService             │
└─────────────────────────────────────────┘
           ↓
┌─────────────────────────────────────────┐
│      MusicService (ExoPlayer)           │
│  - Background audio playback            │
│  - Queue management                     │
│  - Media session controls               │
└─────────────────────────────────────────┘
```

## 📈 Performance Optimizations

- Lazy-loaded list rendering (LazyColumn/LazyRow)
- Image caching with Coil
- Async Firestore updates (non-blocking)
- ExoPlayer for hardware-accelerated audio
- StateFlow for efficient recomposition
- LRU caching for API responses (30 min categories, 15 min playlists)
- Thread-safe concurrent API calls with ThreadPoolExecutor

## 🐛 Troubleshooting

### Backend Issues
- **Port 5000 in use**: Change port in `app.py` or kill conflicting process
- **YTMusic errors**: Check internet connection, YTMusic API may be rate-limiting
- **Import errors**: Run `pip install -r requirements.txt`

### Frontend Issues
- **npm install fails**: Try `npm install --legacy-peer-deps`
- **Vite port conflict**: Change port in `vite.config.js`
- **API connection**: Verify backend is running on correct host/port

### Android Issues
- **Build fails**: Run `./gradlew clean` then rebuild
- **Firebase errors**: Check `google-services.json` is present and valid
- **ExoPlayer crashes**: Verify network permissions in AndroidManifest.xml
- **Empty recommendations**: User needs play/like history, or check Firestore connection

## 📝 License

MIT License - Educational purposes

---

**Documentation Updated**: February 2026  
**Version**: 2.0  
**Features**: Advanced recommender with time-decay + diversity, top artists, mood exploration, smart filtering, mini player, cross-platform persistence

Built with ❤️ using Flask, React, Kotlin, and Jetpack Compose
