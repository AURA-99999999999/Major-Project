# 📱 Step-by-Step: Open & Run Aura Android App

## 🎯 Quick Visual Guide

```
┌─────────────────────────────────────────────────────────┐
│  STEP 1: Install Prerequisites                          │
│  ✅ Android Studio (latest version)                     │
│  ✅ JDK 17 (usually bundled)                            │
│  ✅ Android SDK API 34                                   │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 2: Start Backend Server                           │
│  Terminal: cd Major-Project                             │
│  Terminal: python app.py                                │
│  ✅ Server running on http://localhost:5000             │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 3: Open Project in Android Studio                 │
│  1. Launch Android Studio                               │
│  2. Click "Open"                                        │
│  3. Navigate to: Major-Project/android/                 │
│  4. Select the "android" folder                         │
│  5. Click "OK"                                          │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 4: Wait for Gradle Sync                           │
│  ⏳ Indexing files...                                   │
│  ⏳ Downloading dependencies...                         │
│  ✅ Sync completed (check bottom status bar)            │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 5: Configure API URL (if needed)                  │
│  📝 For Emulator: Already configured ✅                 │
│  📝 For Phone: Update app/build.gradle.kts              │
│     Change 10.0.2.2 to your computer's IP               │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 6: Create Virtual Device (Emulator)               │
│  1. Click Device Manager icon 📱                        │
│  2. Click "Create Device"                               │
│  3. Select Pixel 5 or Pixel 6                           │
│  4. Download API 34 system image                        │
│  5. Finish setup                                        │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 7: Build Project                                  │
│  1. Build → Clean Project                               │
│  2. Build → Rebuild Project                             │
│  ✅ Build successful (check Build tab)                  │
└─────────────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────────┐
│  STEP 8: Run the App                                    │
│  1. Start emulator (or connect phone)                   │
│  2. Click green ▶️ Play button                          │
│  3. Select device from dropdown                         │
│  4. Wait for installation                               │
│  🎉 App launches!                                       │
└─────────────────────────────────────────────────────────┘
```

---

## 📋 Detailed Steps

### ✅ Step 1: Prerequisites

**Check if you have:**
- [ ] Android Studio installed (download from https://developer.android.com/studio)
- [ ] Java/JDK 17 or later
- [ ] Internet connection (for downloading dependencies)

**Install Android Studio:**
1. Download from official website
2. Run installer
3. Follow installation wizard
4. Launch Android Studio
5. Complete setup wizard (download SDK, etc.)

---

### ✅ Step 2: Start Backend Server

**IMPORTANT:** The Android app needs the Flask backend running!

```bash
# Open terminal/command prompt
cd Major-Project

# Activate virtual environment
# Windows:
env\Scripts\activate

# Mac/Linux:
source env/bin/activate

# Start Flask server
python app.py
```

**Verify server is running:**
- Open browser: `http://localhost:5000/api/health`
- Should see: `{"status":"healthy","service":"DeciBel Music API"}`
- ✅ Keep this terminal open while testing!

---

### ✅ Step 3: Open Project in Android Studio

1. **Launch Android Studio**
   - Open the application

2. **Open Project**
   - On welcome screen, click **"Open"**
   - Or if already open: **File → Open**

3. **Navigate to Project**
   - Browse to your project location
   - Path should be: `Major-Project/android/`
   - **Important:** Select the `android` folder, not the parent folder
   - Click **"OK"**

4. **First Time Setup**
   - Android Studio may ask: "Trust Project?" → Click **"Trust"**
   - It will start indexing files
   - This takes 2-5 minutes first time

---

### ✅ Step 4: Gradle Sync

**What happens:**
- Android Studio downloads dependencies
- Syncs project configuration
- Builds project structure

**Steps:**
1. Wait for sync to start automatically
2. Look at bottom status bar: "Gradle sync in progress..."
3. If prompted: Click **"Sync Now"**
4. Wait for completion: "Gradle sync finished"

**If sync fails:**
- Check internet connection
- Try: **File → Sync Project with Gradle Files**
- Or: **File → Invalidate Caches → Invalidate and Restart**

---

### ✅ Step 5: Configure API URL

**Check current configuration:**

1. Open file: `app/build.gradle.kts`
2. Find line ~25:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5000/api\"")
   ```

**For Android Emulator:**
- ✅ Already configured correctly!
- `10.0.2.2` = special address that maps to your computer's localhost
- No changes needed

**For Physical Device:**

1. **Find your computer's IP address:**

   **Windows:**
   ```cmd
   ipconfig
   ```
   Look for "IPv4 Address" (e.g., 192.168.1.100)

   **Mac/Linux:**
   ```bash
   ifconfig
   # or
   ip addr
   ```
   Look for "inet" address (e.g., 192.168.1.100)

2. **Update build.gradle.kts:**
   - Change the line to:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.100:5000/api\"")
   ```
   - Replace `192.168.1.100` with YOUR actual IP

3. **Sync Gradle:**
   - Click the Gradle sync button (🦣) in toolbar
   - Or: **File → Sync Project with Gradle Files**

---

### ✅ Step 6: Setup Android Virtual Device (AVD)

**Only needed if running on emulator**

1. **Open Device Manager**
   - Click device icon (📱) in toolbar
   - Or: **Tools → Device Manager**

2. **Create Virtual Device**
   - Click **"Create Device"**
   - Select: **Pixel 5** or **Pixel 6** (recommended)
   - Click **"Next"**

3. **Select System Image**
   - Choose: **API Level 34** (Android 14)
   - If not installed, click **"Download"**
   - Wait for download (may take 5-10 minutes)
   - Click **"Next"**

4. **Finish Setup**
   - Review configuration
   - Click **"Finish"**
   - Your AVD appears in the list

5. **Start Emulator** (optional now, can start later)
   - Click ▶️ Play button next to your AVD
   - Wait for emulator to boot (1-2 minutes first time)

---

### ✅ Step 7: Build the Project

**Clean Project (recommended first time):**
1. **Build → Clean Project**
2. Wait for completion

**Build Project:**
1. **Build → Rebuild Project**
2. Watch the **Build** tab at bottom
3. Wait for: "BUILD SUCCESSFUL"

**Check for Errors:**
- If you see red errors in Build tab, read them carefully
- Most common: SDK not found → Install from SDK Manager
- Or: Gradle sync failed → Try syncing again

---

### ✅ Step 8: Run the App

**Option A: Run on Emulator**

1. **Start Emulator** (if not running)
   - Device Manager → Click ▶️ Play button
   - Wait for emulator to boot

2. **Run App**
   - Click green **▶️ Play** button in toolbar
   - Or press: `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)
   - Select your emulator from device dropdown if prompted

3. **Watch Installation**
   - Android Studio will:
     - Build the app
     - Install on emulator
     - Launch automatically
   - First install takes 1-2 minutes

**Option B: Run on Physical Device**

1. **Enable Developer Mode:**
   - Phone: **Settings → About Phone**
   - Tap **"Build Number"** 7 times
   - You'll see: "You are now a developer!"

2. **Enable USB Debugging:**
   - Phone: **Settings → Developer Options**
   - Enable **"USB Debugging"**
   - Connect phone via USB

3. **Allow USB Debugging:**
   - Phone will show prompt: "Allow USB debugging?"
   - Check "Always allow from this computer"
   - Tap **"OK"**

4. **Verify Connection:**
   - In Android Studio, check device dropdown (top toolbar)
   - Your phone should appear in the list

5. **Run App:**
   - Select your phone from dropdown
   - Click **▶️ Play** button
   - App installs and launches!

---

## 🎯 Testing the App

Once the app launches:

1. **Check Splash Screen**
   - Should show "Aura" logo briefly

2. **Navigate to Home**
   - Should show trending songs or login screen

3. **Test Search**
   - Go to Search screen
   - Type: "Imagine Dragons Believer"
   - Should show results

4. **Play a Song**
   - Tap on any song
   - Player screen should open
   - Album art, controls should be visible

5. **Test Controls**
   - Play/Pause button works
   - Progress bar is visible
   - Volume control works

---

## 🐛 Common Issues & Fixes

### "Gradle sync failed"
- ✅ Check internet connection
- ✅ **File → Invalidate Caches → Invalidate and Restart**
- ✅ Try syncing again

### "SDK not found"
- ✅ **File → Settings → Appearance & Behavior → System Settings → Android SDK**
- ✅ Install SDK Platform API 34
- ✅ Install SDK Tools

### "Cannot resolve symbol"
- ✅ **File → Invalidate Caches → Invalidate and Restart**
- ✅ Sync Gradle files again
- ✅ Clean and rebuild project

### "Network error" / "Failed to connect"
- ✅ Check backend server is running: `http://localhost:5000/api/health`
- ✅ Verify API_BASE_URL in `app/build.gradle.kts`
- ✅ For emulator: Should be `10.0.2.2:5000`
- ✅ For phone: Should be your computer's IP address
- ✅ Ensure phone and computer on same WiFi network

### App crashes on launch
- ✅ Check **Logcat** tab (bottom panel) for errors
- ✅ Ensure backend server is running
- ✅ Try uninstalling and reinstalling app

### Build errors with Kotlin
- ✅ Check Kotlin version in `build.gradle.kts`
- ✅ Update if needed: `kotlin("android") version "1.9.20"`
- ✅ Sync Gradle again

---

## ✅ Success Checklist

After completing all steps, verify:

- [ ] ✅ Android Studio opens project without errors
- [ ] ✅ Gradle sync completes successfully
- [ ] ✅ Backend server is running
- [ ] ✅ Build completes with "BUILD SUCCESSFUL"
- [ ] ✅ App installs on device/emulator
- [ ] ✅ App launches and shows splash screen
- [ ] ✅ Can navigate to different screens
- [ ] ✅ Search works and shows results
- [ ] ✅ Can play songs
- [ ] ✅ Player screen displays correctly

---

## 🎉 You're All Set!

The app should now be running. Explore:
- 🎵 Search for songs
- ▶️ Play music
- 📋 Create playlists
- ❤️ Like songs
- 👤 View your profile

**Happy coding!** 🚀

For more details, see:
- `SETUP_GUIDE.md` - Comprehensive guide
- `README.md` - Project overview
- `QUICK_START.md` - 5-minute quick start

