# 🚀 START HERE - Aura Android App Setup

## Welcome! 👋

This guide will help you open and run the Aura Android app in Android Studio.

---

## ⚡ Choose Your Path

### 📖 I want a detailed step-by-step guide
→ Go to: **[SETUP_GUIDE.md](SETUP_GUIDE.md)**
- Complete instructions with troubleshooting
- Perfect for first-time setup

### ⚡ I want a quick 5-minute setup
→ Go to: **[QUICK_START.md](QUICK_START.md)**
- Fast track setup
- For experienced developers

### 📋 I want a visual step-by-step walkthrough
→ Go to: **[STEP_BY_STEP.md](STEP_BY_STEP.md)**
- Visual flow diagram
- Detailed explanations for each step

---

## 🎯 The Fastest Way (TL;DR)

```bash
1. Start backend server:
   cd Major-Project
   python app.py

2. Open Android Studio → Open → Select "Major-Project/android/" folder

3. Wait for Gradle sync to complete

4. Click ▶️ Play button to run

5. Done! 🎉
```

---

## ✅ Prerequisites Checklist

Before starting, make sure you have:

- [ ] **Android Studio** installed (latest version)
  - Download: https://developer.android.com/studio
- [ ] **JDK 17** (usually bundled with Android Studio)
- [ ] **Backend server running** on `localhost:5000`
- [ ] **Internet connection** (for downloading dependencies)

---

## 📝 Quick Setup Steps

### Step 1: Start Backend Server ⚠️ IMPORTANT

The Android app needs the Flask backend to be running!

```bash
# Terminal 1: Start backend
cd Major-Project
python app.py

# Verify it's running:
# Open browser: http://localhost:5000/api/health
# Should see: {"status":"healthy","service":"DeciBel Music API"}
```

**Keep this terminal open!**

---

### Step 2: Open Project in Android Studio

1. Launch **Android Studio**
2. Click **"Open"** (or File → Open)
3. Navigate to: `Major-Project/android/`
4. Select the **`android`** folder (not the parent folder)
5. Click **"OK"**
6. Wait for indexing and Gradle sync (2-5 minutes first time)

---

### Step 3: Configure API URL

**For Android Emulator (default):**
- ✅ Already configured! No changes needed.
- Uses: `http://10.0.2.2:5000/api`

**For Physical Device:**
1. Find your computer's IP address:
   - Windows: `ipconfig` → Look for IPv4 Address
   - Mac/Linux: `ifconfig` → Look for inet address

2. Open `app/build.gradle.kts`
3. Find line 25, update to:
   ```kotlin
   buildConfigField("String", "API_BASE_URL", "\"http://YOUR_IP:5000/api\"")
   ```
4. Replace `YOUR_IP` with your actual IP (e.g., `192.168.1.100`)
5. Sync Gradle (click 🦣 icon or File → Sync Project with Gradle Files)

---

### Step 4: Create Virtual Device (For Emulator)

**Only if using emulator:**

1. Click **Device Manager** icon (📱) in toolbar
2. Click **"Create Device"**
3. Select **Pixel 5** or **Pixel 6**
4. Download **API 34** system image (if needed)
5. Click **"Finish"**

---

### Step 5: Build & Run

1. **Build the project:**
   - **Build → Clean Project**
   - **Build → Rebuild Project**
   - Wait for "BUILD SUCCESSFUL"

2. **Run the app:**
   - Start emulator OR connect your phone
   - Click green **▶️ Play** button
   - Select your device
   - Wait for installation
   - 🎉 App launches!

---

## 🎯 First Run Checklist

When the app opens, verify:

- [ ] ✅ Splash screen appears
- [ ] ✅ Home screen loads
- [ ] ✅ Search works
- [ ] ✅ Can play songs
- [ ] ✅ Player screen shows correctly

---

## 🐛 Common Issues

### Backend Connection Issues
- ✅ Ensure backend is running: `http://localhost:5000/api/health`
- ✅ Check API_BASE_URL in `app/build.gradle.kts`
- ✅ For phone: Ensure phone and computer on same WiFi

### Build Errors
- ✅ **File → Invalidate Caches → Invalidate and Restart**
- ✅ Sync Gradle files again
- ✅ Check internet connection

### Gradle Sync Failed
- ✅ Check internet connection
- ✅ Try syncing again
- ✅ Check if behind firewall/proxy

---

## 📚 Documentation

- **SETUP_GUIDE.md** - Comprehensive setup guide
- **STEP_BY_STEP.md** - Visual step-by-step walkthrough
- **QUICK_START.md** - 5-minute quick start
- **README.md** - Project overview and features
- **IMPLEMENTATION_SUMMARY.md** - Complete feature list

---

## 🆘 Need Help?

1. Check the **Logcat** tab in Android Studio for errors
2. Verify backend server is running
3. Review the detailed guides above
4. Check troubleshooting sections in SETUP_GUIDE.md

---

## ✨ Ready to Start?

1. **Start your backend server** (Step 1 above)
2. **Open the project** in Android Studio (Step 2)
3. **Follow the setup** (Steps 3-5)
4. **Run and enjoy!** 🎵

**Good luck!** 🚀

