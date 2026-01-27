# Aura Music Streaming Platform

A modern, full-stack music streaming application with Flask backend, React frontend, and native Android app.

> **📚 Full Documentation**: See [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)

## 🎵 Features

- Music search & streaming
- Playlists management
- Firebase authentication with Google Sign-In
- Cross-platform (Web + Android)
- Background audio playback
- Queue management

## 🏗️ Tech Stack

- **Backend**: Flask (Python)
- **Frontend**: React + Vite + Tailwind CSS  
- **Android**: Kotlin + Jetpack Compose + Material 3
- **Auth**: Firebase
- **Audio**: ExoPlayer (Android), Web Audio API (Web)

## 🚀 Quick Start

### Backend
```bash
pip install -r requirements.txt
python app.py
```
Server runs on `http://localhost:5000`

### Frontend
```bash
cd frontend
npm install
npm run dev
```
App runs on `http://localhost:3000`

### Android
```bash
cd android
./gradlew assembleDebug
```

See [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md) for detailed setup and [android/ANDROID_GUIDE.md](android/ANDROID_GUIDE.md) for Android-specific info.

## 📂 Project Structure

```
Major-Project/
├── app.py              # Flask backend server
├── services/           # Backend business logic
├── frontend/           # React web application
├── android/            # Android Kotlin app
└── data/               # JSON data storage
```

## 📝 License

MIT License - Educational purposes

---

Built with Flask, React, and Kotlin
