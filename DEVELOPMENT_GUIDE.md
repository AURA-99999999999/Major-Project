# Aura Music Streaming App - Development Guide

## Project Overview
Full-stack music streaming application with Python Flask backend, React frontend, and Android Kotlin app.

## Quick Start

### Backend (Flask)
```bash
pip install -r requirements.txt
python app.py
```
Server runs on `http://localhost:5000`

### Frontend (React)
```bash
cd frontend
npm install
npm run dev
```

### Android App
```bash
cd android
./gradlew assembleDebug
```

## Architecture

### Backend Stack
- Flask (Python web framework)
- Firebase Authentication
- Music streaming service
- Playlist management
- User management

### Frontend Stack
- React + Vite
- Tailwind CSS
- Axios for API calls
- Firebase Auth integration

### Android Stack
- Kotlin + Jetpack Compose
- Material Design 3
- Firebase Authentication
- ExoPlayer for audio
- Retrofit for networking
- Navigation Component

## Key Features

1. **User Authentication**
   - Email/Password login
   - Google Sign-In
   - Firebase integration

2. **Music Streaming**
   - Audio playback with ExoPlayer
   - Background playback service
   - Notification controls
   - Queue management

3. **Navigation**
   - Single NavController architecture
   - Auth flow and Main flow separation
   - Clean backstack management

## Configuration

### Backend Config (config.py)
- API base URLs
- Firebase settings
- Environment variables

### Android Config
- `android/local.properties`: SDK path, API URLs
- `android/app/google-services.json`: Firebase config

## Common Issues & Fixes

### Build Issues
- Ensure JDK 17+ installed
- Sync Gradle files after changes
- Clean build: `./gradlew clean`

### Backend Connectivity
- Check API_BASE_URL in local.properties
- Use device IP for physical devices
- Use 10.0.2.2 for emulator

### Authentication
- Verify Firebase configuration
- Check google-services.json is present
- Ensure SHA-1 fingerprint registered in Firebase

## Development Workflow

1. Start backend server
2. Update API URL in Android config
3. Build and run Android app
4. Test features end-to-end

## Testing
- Backend: pytest
- Android: JUnit + Espresso
- Frontend: Jest + React Testing Library
