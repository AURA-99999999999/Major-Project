# Step-by-Step Setup Guide for Aura Android App

## 📋 Prerequisites Checklist

Before starting, ensure you have:

- [ ] **Android Studio** - Hedgehog (2023.1.1) or later
  - Download from: https://developer.android.com/studio
- [ ] **JDK 17** - Java Development Kit 17 (usually bundled with Android Studio)
- [ ] **Android SDK** - API Level 34 (Android 14)
- [ ] **Kotlin Plugin** - Should be included in Android Studio
- [ ] **Backend Server Running** - Flask server should be running on `localhost:5000`

---

## 🚀 Step-by-Step Setup

### Step 1: Open the Project in Android Studio

1. **Launch Android Studio**
   - Open Android Studio on your computer

2. **Open the Project**
   - Click **"Open"** on the welcome screen (or **File → Open** if already in a project)
   - Navigate to: `Major-Project/android/`
   - Select the **`android`** folder (not the parent `Major-Project` folder)
   - Click **"OK"**

3. **Wait for Indexing**
   - Android Studio will start indexing files
   - This may take a few minutes on first open
   - You'll see "Indexing..." in the bottom status bar

---

### Step 2: Gradle Sync

1. **Check for Gradle Sync Prompt**
   - Android Studio may show a banner: "Gradle files have changed since last project sync"
   - Click **"Sync Now"** if prompted

2. **Manual Sync (if needed)**
   - Go to **File → Sync Project with Gradle Files**
   - Or click the **elephant icon** (🦣) in the toolbar
   - Wait for sync to complete (check bottom status bar)

3. **Resolve Sync Issues (if any)**
   - If you see errors, click on them in the **Build** tab at the bottom
   - Common fixes:
     - Accept Android SDK licenses: Go to **File → Settings → Appearance & Behavior → System Settings → Android SDK → SDK Tools** tab, check "Android SDK Platform-Tools" and "Android SDK Build-Tools"
     - Update Gradle: Android Studio usually prompts to update

---

### Step 3: Install Required SDK Components

1. **Open SDK Manager**
   - Go to **File → Settings** (or **Android Studio → Preferences** on Mac)
   - Navigate to: **Appearance & Behavior → System Settings → Android SDK**

2. **Install SDK Platforms**
   - Go to **SDK Platforms** tab
   - Check **Android 14.0 (API 34)** or **Android 13.0 (API 33)** if 34 not available
   - Click **Apply** and wait for installation

3. **Install SDK Tools**
   - Go to **SDK Tools** tab
   - Ensure these are checked:
     - ✅ Android SDK Build-Tools
     - ✅ Android SDK Platform-Tools
     - ✅ Android SDK Command-line Tools
     - ✅ Google Play services
     - ✅ Intel x86 Emulator Accelerator (HAXM installer) - for emulator
   - Click **Apply** and wait for installation

---

### Step 4: Configure API Base URL

**Important:** The app needs to connect to your Flask backend server.

#### Option A: Using Android Emulator (Recommended for testing)

The default configuration is already set for emulator (`10.0.2.2:5000` = localhost on your computer).

1. **Verify the configuration:**
   - Open: `app/build.gradle.kts`
   - Look for line: `buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:5000/api\"")`
   - If present, you're good! Skip to Step 5.

#### Option B: Using Physical Device

1. **Find your computer's IP address:**
   
   **Windows:**
   - Open Command Prompt
   - Type: `ipconfig`
   - Look for **IPv4 Address** under your active network adapter (e.g., `192.168.1.100`)

   **Mac/Linux:**
   - Open Terminal
   - Type: `ifconfig` or `ip addr`
   - Look for inet address (e.g., `192.168.1.100`)

2. **Update the API URL:**
   - Open: `app/build.gradle.kts`
   - Find line with `API_BASE_URL`
   - Replace `10.0.2.2` with your IP address:
     ```kotlin
     buildConfigField("String", "API_BASE_URL", "\"http://192.168.1.100:5000/api\"")
     ```
   - Replace `192.168.1.100` with your actual IP

3. **Sync Gradle again:**
   - Click the **Gradle sync** button (🦣)

---

### Step 5: Ensure Backend Server is Running

1. **Start your Flask backend:**
   - Open a terminal/command prompt
   - Navigate to `Major-Project/`
   - Activate virtual environment:
     - Windows: `env\Scripts\activate`
     - Mac/Linux: `source env/bin/activate`
   - Run: `python app.py`
   - You should see: `Running on http://127.0.0.1:5000`

2. **Test the backend:**
   - Open browser: `http://localhost:5000/api/health`
   - Should return: `{"status":"healthy","service":"DeciBel Music API"}`

3. **Keep the terminal open** while testing the app

---

### Step 6: Create/Configure Android Virtual Device (AVD) - For Emulator

1. **Open AVD Manager**
   - Click the **phone icon** (📱) in the toolbar, or
   - Go to **Tools → Device Manager**

2. **Create Virtual Device**
   - Click **"Create Device"** button
   - Select a device (recommend: **Pixel 5** or **Pixel 6**)
   - Click **"Next"**

3. **Select System Image**
   - Choose **API 34** (or highest available)
   - If not installed, click **"Download"** next to it
   - Wait for download and installation
   - Click **"Next"**

4. **Verify Configuration**
   - Review settings
   - Click **"Finish"**

5. **Start Emulator** (optional - can do later)
   - Click the **Play button** (▶️) next to your AVD

---

### Step 7: Build the Project

1. **Clean Project** (recommended first time)
   - Go to **Build → Clean Project**
   - Wait for completion

2. **Build Project**
   - Go to **Build → Rebuild Project**
   - Or press: `Ctrl+Shift+F9` (Windows/Linux) or `Cmd+Shift+F9` (Mac)
   - Wait for build to complete (check bottom status bar)

3. **Check for Errors**
   - Look at the **Build** tab at the bottom
   - If you see errors (red text), read them carefully
   - Common issues and fixes:
     - **"SDK not found"**: Reinstall SDK (Step 3)
     - **"Gradle sync failed"**: Check internet connection, try again
     - **"Kotlin version mismatch"**: Sync Gradle files again

---

### Step 8: Run the App

#### Option A: Run on Emulator

1. **Start Emulator** (if not already running)
   - In Device Manager, click **Play** (▶️) next to your AVD
   - Wait for emulator to boot (may take 1-2 minutes first time)

2. **Run the App**
   - Click the **green Play button** (▶️) in the toolbar
   - Or press: `Shift+F10` (Windows/Linux) or `Ctrl+R` (Mac)
   - Select your emulator from the device dropdown if asked

3. **Wait for Installation**
   - Android Studio will build and install the app
   - The app will launch automatically

#### Option B: Run on Physical Device

1. **Enable Developer Options on Phone:**
   - Go to **Settings → About Phone**
   - Tap **Build Number** 7 times
   - You'll see: "You are now a developer!"

2. **Enable USB Debugging:**
   - Go to **Settings → Developer Options**
   - Enable **USB Debugging**
   - Connect phone to computer via USB

3. **Verify Connection:**
   - In Android Studio, check device dropdown (top toolbar)
   - Your device should appear in the list

4. **Run the App:**
   - Select your device from the dropdown
   - Click the **green Play button** (▶️)
   - Accept USB debugging prompt on phone if shown

---

### Step 9: Test the App

1. **Splash Screen**
   - App should show "Aura" logo briefly

2. **Navigate**
   - App should show Home screen or Login screen

3. **Test Search**
   - Go to Search screen
   - Type a song name (e.g., "Imagine Dragons Believer")
   - Results should appear

4. **Test Playback**
   - Tap on a song
   - Should navigate to Player screen
   - Player should show album art, controls, etc.

5. **Check Logcat for Errors**
   - Bottom panel → **Logcat** tab
   - Look for red error messages if something doesn't work

---

## 🔧 Troubleshooting

### Issue: "Gradle sync failed"

**Solutions:**
- Check internet connection
- Go to **File → Invalidate Caches → Invalidate and Restart**
- Check if you're behind a corporate firewall (may need proxy settings)
- Try: **File → Settings → Build, Execution, Deployment → Gradle** → Change "Use Gradle from" to "gradle-wrapper.properties file"

### Issue: "SDK not found"

**Solutions:**
- Go to **File → Project Structure → SDK Location**
- Verify SDK path is correct
- Reinstall SDK from SDK Manager

### Issue: "Cannot resolve symbol" errors

**Solutions:**
- **File → Invalidate Caches → Invalidate and Restart**
- Sync Gradle files again
- Clean and rebuild project

### Issue: App crashes on launch

**Solutions:**
- Check **Logcat** tab for error messages
- Ensure backend server is running
- Check API_BASE_URL is correct
- Try uninstalling and reinstalling the app

### Issue: "Network error" or "Failed to connect"

**Solutions:**
- Verify backend is running: `http://localhost:5000/api/health`
- Check API_BASE_URL in `app/build.gradle.kts`
- For emulator: Should be `10.0.2.2:5000`
- For physical device: Should be your computer's IP address
- Ensure phone/emulator and computer are on same network (for physical device)
- Check firewall isn't blocking port 5000

### Issue: "Build failed" with Kotlin errors

**Solutions:**
- Check Kotlin version in `build.gradle.kts` files
- Update to: `kotlin("android") version "1.9.20"`
- Sync Gradle again

---

## 📱 First Run Checklist

After the app launches:

- [ ] Splash screen appears
- [ ] Home screen loads with trending songs
- [ ] Search screen works and shows results
- [ ] Can play a song (tap on song from search/home)
- [ ] Player screen opens with album art and controls
- [ ] Play/Pause button works
- [ ] Progress bar is visible
- [ ] Volume control works
- [ ] Back button navigates correctly

---

## 🎯 Quick Reference

### Important Files

- **API Configuration:** `app/build.gradle.kts` (line ~25)
- **App Manifest:** `app/src/main/AndroidManifest.xml`
- **Main Activity:** `app/src/main/java/com/aura/music/MainActivity.kt`
- **Application Class:** `app/src/main/java/com/aura/music/AuraApplication.kt`

### Useful Android Studio Shortcuts

- **Build:** `Ctrl+F9` (Windows/Linux) / `Cmd+F9` (Mac)
- **Run:** `Shift+F10` (Windows/Linux) / `Ctrl+R` (Mac)
- **Sync Gradle:** `Ctrl+Shift+O` (Windows/Linux) / `Cmd+Shift+O` (Mac)
- **Open Terminal:** `Alt+F12` (Windows/Linux) / `Option+F12` (Mac)

---

## ✅ Success!

If everything worked:
- 🎉 The app should be running on your device/emulator
- 🎵 You can search for songs
- ▶️ You can play music
- 📱 All screens should be accessible

**Next Steps:**
- Try creating an account (Register screen)
- Create a playlist
- Like songs
- Explore all features!

---

## 📞 Need Help?

If you encounter issues not covered here:

1. Check the **Logcat** tab in Android Studio for detailed error messages
2. Verify your backend server is running and accessible
3. Check the `android/README.md` for additional information
4. Review the `android/IMPLEMENTATION_SUMMARY.md` for feature details

Happy coding! 🚀

