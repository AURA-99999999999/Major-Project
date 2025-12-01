# Quick Start Guide - Aura Android App

## ⚡ Fast Setup (5 minutes)

### 1. Prerequisites (one-time setup)
```bash
# Make sure you have:
- Android Studio installed
- Backend server running on localhost:5000
```

### 2. Open Project
1. Open Android Studio
2. **File → Open** → Select `Major-Project/android/` folder
3. Wait for Gradle sync to complete

### 3. Configure API URL (if using physical device)

**For Emulator** (default - already configured):
✅ No changes needed! `10.0.2.2:5000` = localhost

**For Physical Device:**
1. Find your computer's IP:
   - Windows: `ipconfig` → Look for IPv4 Address
   - Mac/Linux: `ifconfig` → Look for inet address

2. Update `app/build.gradle.kts` line ~25:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://YOUR_IP:5000/api\"")
   ```

3. Sync Gradle (🦣 button)

### 4. Start Backend Server
```bash
cd Major-Project
# Activate virtual environment
env\Scripts\activate  # Windows
source env/bin/activate  # Mac/Linux

# Start server
python app.py
```

### 5. Run App
1. Click **▶️ Play** button in Android Studio
2. Select device/emulator
3. Wait for app to install and launch

## 🎯 That's it!

The app should now be running. Try:
- Search for a song
- Play it
- Explore all features!

---

## 🔧 Quick Troubleshooting

**Build failed?**
→ **File → Invalidate Caches → Invalidate and Restart**

**Can't connect to backend?**
→ Check backend is running: `http://localhost:5000/api/health`
→ Verify API_BASE_URL in `app/build.gradle.kts`

**App crashes?**
→ Check **Logcat** tab for errors
→ Ensure backend server is running

---

For detailed setup, see `SETUP_GUIDE.md`

