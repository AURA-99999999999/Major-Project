# Android App Guide

## Setup

1. **Requirements**
   - Android Studio Arctic Fox or later
   - JDK 17+
   - Android SDK 34
   - Gradle 8.12

2. **Configuration**
   - Update `local.properties` with SDK path and API URL
   - Place `google-services.json` in `app/` folder
   - Sync Gradle

3. **Build**
   ```bash
   ./gradlew assembleDebug
   ```

## Architecture

### Tech Stack
- Kotlin 2.0.21
- Jetpack Compose
- Material Design 3
- Firebase Auth
- ExoPlayer
- Retrofit + OkHttp
- Coroutines + Flow

### Project Structure
```
app/src/main/java/com/aura/music/
├── auth/           # Authentication screens & logic
├── navigation/     # Navigation graphs
├── player/         # Music player service
├── ui/            # Main app screens
└── MainActivity   # Entry point
```

## Key Features

### Authentication
- Email/Password login with Firebase
- Google Sign-In integration
- Persistent login state
- Automatic navigation based on auth state

### Music Player
- Background playback service
- Notification with controls
- Queue management
- Seek/pause/play controls

### Navigation
- Single NavController pattern
- AuthGraph for login/signup
- MainGraph for app screens
- Clean separation of concerns

## Known Issues & Solutions

### Google Sign-In Warnings
The app uses deprecated Google Sign-In APIs. They still work but show compiler warnings. To update later, migrate to Credentials API.

### Logo Background
App logo background is now transparent. See `res/values/ic_launcher_background.xml`

### Build Errors
- **KAPT issues**: Project uses KSP instead of KAPT
- **Compose version conflicts**: Using BOM 2023.10.01
- **Missing dependencies**: Run `./gradlew --refresh-dependencies`

## Testing
```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Debugging
- Use Android Studio's Logcat
- Filter by tag: `Aura`, `AuthViewModel`, `MusicService`
- Check network calls in Network Inspector
