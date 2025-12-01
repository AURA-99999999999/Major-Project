# Aura - Android Music Streaming App

A feature-parity Android implementation of the React web music streaming app "Aura" (formerly "DeciBel").

## 📱 Features

### Core Features
- ✅ Music search and discovery
- ✅ Audio streaming with ExoPlayer
- ✅ Playlist management (create, edit, delete)
- ✅ User authentication
- ✅ User profiles with liked songs and recently played
- ✅ Queue system with shuffle and repeat modes
- ✅ Background playback with media notification
- ✅ Modern Material 3 UI

### Player Features (Matching Reference Design)
- Large rotating album art
- Full playback controls (play/pause, next, previous)
- Progress bar with seek functionality
- Repeat modes (Off, All, One)
- Shuffle mode
- Like/Favorite button
- Volume control
- Beautiful gradient backgrounds

## 🏗️ Architecture

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Coil
- **Music Player**: Media3 ExoPlayer
- **Navigation**: Compose Navigation
- **State Management**: Kotlin Coroutines Flow

## 📂 Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/aura/music/
│   │   │   ├── data/
│   │   │   │   ├── model/          # Domain models
│   │   │   │   ├── remote/         # API DTOs and Retrofit service
│   │   │   │   ├── repository/     # Repository implementations
│   │   │   │   └── mapper/         # DTO to model mappers
│   │   │   ├── di/                 # Hilt dependency injection modules
│   │   │   ├── navigation/         # Navigation setup and screens
│   │   │   ├── player/             # MusicService and player state
│   │   │   └── ui/
│   │   │       ├── components/     # Reusable Compose components
│   │   │       ├── screens/        # All app screens
│   │   │       ├── theme/          # Material 3 theme
│   │   │       └── viewmodel/      # ViewModels for each screen
│   │   └── res/                    # Resources (strings, themes, etc.)
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

## 🚀 Setup

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK 34
- Kotlin 1.9.20+

### Configuration

1. **Update API Base URL**
   
   The app is configured to use `http://10.0.2.2:5000/api` by default (Android Emulator localhost).
   
   For a physical device, update in `app/build.gradle.kts`:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://YOUR_COMPUTER_IP:5000/api\"")
   ```

2. **Build and Run**
   
   Open the project in Android Studio and run the app.

## 🔌 API Integration

The app uses the same backend API as the React web app:

- `GET /api/search` - Search songs
- `GET /api/song/{videoId}` - Get song details
- `GET /api/trending` - Get trending songs
- `GET /api/playlists` - Get user playlists
- `POST /api/playlists` - Create playlist
- `GET /api/playlists/{id}` - Get playlist details
- `PUT /api/playlists/{id}` - Update playlist
- `DELETE /api/playlists/{id}` - Delete playlist
- `POST /api/auth/login` - Login
- `POST /api/auth/register` - Register
- And more...

See the React app's `app.py` for complete API documentation.

## 📱 Screens

### Implemented Screens

1. **Splash Screen** - App initialization and branding
2. **Login/Register** - User authentication
3. **Home** - Trending songs and quick access
4. **Search** - Search for songs with debounced input
5. **Player** - Full-screen player matching reference design
6. **Playlists** - List and manage playlists
7. **Playlist Detail** - View and edit playlist
8. **Profile** - User profile with liked songs and history

## 🎨 UI/UX

- **Dark Theme**: Modern dark theme optimized for music streaming
- **Material 3**: Uses latest Material Design 3 components
- **Gradients**: Beautiful gradient backgrounds throughout
- **Animations**: Smooth transitions and animations
- **Responsive**: Works on phones and tablets

## 🎵 Player Features

The player screen matches the reference image with:

- **Album Art**: Large circular album art that rotates when playing
- **Progress Bar**: Interactive seekbar with time display
- **Controls**: Full playback control buttons
- **Repeat/Shuffle**: Toggle repeat modes and shuffle
- **Like Button**: Heart icon for favorites
- **Volume**: Volume slider at bottom
- **Background**: Gradient overlay matching album art colors

## 🔧 Known Issues / TODO

Some screens may need additional implementation:

- Playlists screen - Full playlist grid/list view
- Playlist Detail - Complete CRUD operations UI
- Profile screen - Enhanced user profile UI
- Like button functionality - Integration with backend
- Queue panel - Visual queue management UI
- Mini player - Sticky bottom player bar

## 📝 Notes

- The app uses `http://10.0.2.2:5000/api` by default for the Android emulator
- For physical devices, use your computer's local IP address
- Ensure the Flask backend is running before testing
- Some icons use system resources - you may want to add custom icons

## 🤝 Contributing

This is a feature-parity implementation. Any features in the React web app should be matched in this Android app.

## 📄 License

Same license as the main project.

