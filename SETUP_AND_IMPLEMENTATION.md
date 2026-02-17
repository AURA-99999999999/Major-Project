# Setup & Implementation Guide

## Quick Start

### Prerequisites
- Node.js 16+ (for frontend)
- Python 3.8+ (for backend)
- Android Studio 2021+ (for Android)
- Git

### Backend Setup

```bash
# Navigate to project root
cd Major-Project

# Install dependencies
pip install -r requirements.txt

# Configure Flask
export FLASK_ENV=development
export FLASK_APP=app.py

# Run backend
python app.py
```
Backend runs on: `http://localhost:5000/api/`

### Frontend Setup

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```
Frontend runs on: `http://localhost:3000`

### Android Setup

**Requirements**:
- JDK 11+
- Android SDK 34+
- Gradle 8.0+

```bash
cd android

# Configure local environment
echo "sdk.dir=$ANDROID_SDK_PATH" > local.properties

# Build debug APK
./gradlew assembleDebug

# Run on emulator/device
./gradlew installDebug
```

## Development Workflow

### Home Screen Development

**Key Files**:
- `android/app/src/main/java/com/aura/music/ui/screens/home/HomeScreen.kt`
- `android/app/src/main/java/com/aura/music/ui/viewmodel/HomeViewModel.kt`

**Features Implemented**:
1. Trending songs display with horizontal scrolling
2. Recommended for you section with personalized recommendations
3. Explore by Mood with category selection
4. Bottom navigation with Home, Search, Library, Profile
5. Pull-to-refresh functionality
6. Loading and error states

**To Add New Content Section**:
1. Add item to HomeUiState
2. Create composable function for section UI
3. Add LazyColumn item in HomeScreen
4. Fetch data through HomeViewModel

### Mini Player Implementation

**Files Modified**:
- `PlayerViewModel.kt` - Enhanced with StateFlows
- `MusicService.kt` - Added current song tracking
- `RootNavGraph.kt` - Mini player display logic
- `MiniPlayerBar.kt` - New UI component
- `FirestoreRepository.kt` - Persistence methods
- All screens - Added 120dp bottom padding

**Features**:
- Displays currently playing song above bottom nav
- Shows thumbnail, title, artist
- 3-button media controls (skip prev, play/pause, skip next)
- Hides on full player screen
- Persists last played song to Firestore
- Loads on app startup

**Testing Mini Player**:
1. Play a song from home screen
2. Mini player appears at bottom
3. Navigate to different screens - mini player stays visible
4. Click mini player -> navigates to full player
5. Press back -> returns to previous screen, mini player visible
6. Close app -> Mini player info persists
7. Reopen app -> Last played song loads
8. Use skip buttons -> Song changes, mini player updates

### Firestore Integration

**Database Rules** (`firestore.rules`):
```
Authenticated users can:
- Read/write their own user document
- Read/write their own playlists
- Read/write their own liked songs
- Read/write their own lastPlayed document
```

**Document Paths**:
- User profile: `users/{uid}/userProfile`
- Last played: `users/{uid}/lastPlayed/current`
- Playlists: `users/{uid}/playlists/{playlistId}`
- Liked songs: `users/{uid}/likedSongs/{videoId}`

**Methods** (`FirestoreRepository.kt`):
- `updateLastPlayedSong(song: Song)` - Saves/updates last played
- `getLastPlayedSong(): Song?` - Loads last played on startup
- `addPlaylist(playlist: Playlist)` - Creates new playlist
- `addToPlaylist(playlistId, song)` - Adds song to playlist
- `toggleLikeSong(song)` - Add/remove from liked songs

### Recommendations System

**Logic** (`services/recommendation_service.py`):
1. Fetch user's listening history
2. Get liked songs and their genres/artists
3. Fetch trending in matched genres/moods
4. Blend recent trends with personalized picks
5. Return top-N recommendations

**Integration Points**:
- HomeScreen loads recommendations on screen load
- Backend endpoint: `/api/recommendations?userId={uid}`
- Updates every app session

## Troubleshooting

### Backend Issues

**Port 5000 already in use**:
```bash
# Find process
lsof -i :5000
# Kill process
kill -9 <PID>
```

**Module import errors**:
```bash
# Reinstall requirements
pip install -r requirements.txt --force-reinstall
```

### Frontend Issues

**Node modules corruption**:
```bash
rm -rf node_modules package-lock.json
npm install
```

**Vite not starting**:
```bash
# Clear Vite cache
rm -rf frontend/.vite
npm run dev
```

### Android Issues

**Build failures**:
```bash
./gradlew clean
./gradlew assembleDebug --info
```

**Gradle daemon problems**:
```bash
./gradlew --stop
./gradlew assembleDebug
```

**Firebase connection**:
- Ensure `google-services.json` in `android/app/`
- Check Firebase project config
- Verify authentication enabled in Firebase console

**Mini Player not showing**:
- Check route detection logic in RootNavGraph
- Verify PlayerViewModel is injected correctly
- Check Firestore rules allow read/write access

## Environment Variables

### Backend
```
FLASK_ENV=development
FLASK_APP=app.py
```

### Frontend
```
VITE_API_URL=http://localhost:5000/api
```

### Android
```
# local.properties
sdk.dir=/path/to/android/sdk
```

## Deployment

### Backend (Flask)
```bash
# Production
export FLASK_ENV=production
gunicorn app:app --bind 0.0.0.0:5000
```

### Frontend (React)
```bash
npm run build
# Deploy dist/ folder to static host
```

### Android
```bash
# Generate signed APK
./gradlew bundleRelease
# Sign with release keystore
jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 \
  -keystore release.keystore app-release.aab alias_name
```

## Performance Tuning

### Backend
- Enable SQLite caching for song metadata
- Implement recommendation caching (TTL: 1 hour)
- Use pagination for large song lists

### Frontend
- Lazy load route components
- Image optimization with compression
- Implement virtual scrolling for long lists

### Android
- Use ProGuard/R8 for code shrinking in release builds
- Enable multidex for large app
- Optimize image sizes and caching
- Profile with Android Studio Profiler

## Testing Checklist

- [ ] Backend API endpoints return correct data
- [ ] Frontend loads and displays songs
- [ ] Android app builds successfully
- [ ] Firebase authentication works
- [ ] Mini player displays song info
- [ ] Media controls work (play, pause, skip)
- [ ] Scroll doesn't obscure content
- [ ] Last played persists across app restarts
- [ ] Recommendations load on home screen
- [ ] Navigation between screens works
- [ ] Error states display properly
- [ ] Loading indicators show during data fetch
