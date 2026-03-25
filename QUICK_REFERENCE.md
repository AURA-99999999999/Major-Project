# Quick Reference - Local vs Production Backend

## 🎯 Current Setup

| Aspect | Local (Debug Build) | Production (Release Build) |
|--------|-------------------|---------------------------|
| **Base URL** | `http://10.0.2.2:5000/` | `https://aura-b7vm.onrender.com/` |
| **Protocol** | HTTP | HTTPS |
| **Cleartext Traffic** | ✅ ALLOWED | ❌ BLOCKED |
| **Environment** | LOCAL_EMULATOR | PRODUCTION |
| **Logging** | ✅ BODY Level | ❌ NONE |
| **Health Check** | ✅ YES | ✅ YES |
| **Build Variant** | `./gradlew buildDebug` | `./gradlew buildRelease` |

---

## 🚀 Build Commands

### Debug Build (Local Backend)
```bash
cd android

# Clean and build
./gradlew clean buildDebug

# Build and install on emulator/device
./gradlew installDebug

# Build and run immediately
./gradlew runDebug
```

### Release Build (Production Backend)
```bash
cd android

# Clean and build
./gradlew clean buildRelease

# Build APK for release
./gradlew assembleRelease

# Create signed APK (requires keystore)
./gradlew bundleRelease
```

---

## 📱 Installation Commands

### Debug APK
```bash
# Install on connected emulator/device
./gradlew installDebug

# Install and launch
./gradlew installDebug
adb shell am start -n com.aura.music/.MainActivity
```

### Release APK
```bash
# Build release APK
./gradlew assembleRelease

# Find APK at: android/app/build/outputs/apk/release/app-release-unsigned.apk

# Install manually
adb install -r android/app/build/outputs/apk/release/app-release-unsigned.apk
```

---

## 🔄 Switching Configurations

### For Emulator Testing

```kotlin
// ✅ Current setting (in build.gradle.kts)
val localBaseUrl = "http://10.0.2.2:5000/"
```

**Run:**
```bash
./gradlew installDebug
```

### For Physical Device Testing (Same WiFi)

1. **Find your PC's IP:**
   ```powershell
   ipconfig
   # Look for IPv4 Address: 192.168.1.x
   ```

2. **Update build.gradle.kts:**
   ```kotlin
   val localBaseUrl = "http://192.168.1.5:5000/"  // Replace with your IP
   ```

3. **Verify Flask accepts connections from network:**
   ```bash
   python app.py
   # Should show: Running on http://0.0.0.0:5000
   ```

4. **Build and test:**
   ```bash
   ./gradlew installDebug
   ```

5. **Verify on device:**
   ```bash
   adb shell curl -v http://192.168.1.5:5000/api/health
   ```

---

## 🧪 Verification Checklist

### Before Building

- [ ] Flask backend running: `python app.py`
- [ ] Backend responds: `curl http://localhost:5000/api/health`
- [ ] Correct URL in `build.gradle.kts`
- [ ] Gradlew is executable: `./gradlew --version`

### After Building

- [ ] APK builds without errors: `./gradlew buildDebug`
- [ ] App installs: `./gradlew installDebug`
- [ ] App launches without crashes
- [ ] Logs show "Backend Health Check: SUCCESS"
- [ ] Can navigate and use app

---

## 📊 BuildConfig Values

### View Current Build Config

Add to activity temporarily:
```kotlin
Log.d("BuildConfig", """
    BASE_URL: ${BuildConfig.BASE_URL}
    API_ENV: ${BuildConfig.API_ENV}
    DEBUG: ${BuildConfig.DEBUG}
    VERSION_NAME: ${BuildConfig.VERSION_NAME}
""".trimIndent())
```

### Expected Debug Output
```
BASE_URL: http://10.0.2.2:5000/
API_ENV: LOCAL_EMULATOR
DEBUG: true
VERSION_NAME: 1.0.0
```

### Expected Release Output
```
BASE_URL: https://aura-b7vm.onrender.com/
API_ENV: PRODUCTION
DEBUG: false
VERSION_NAME: 1.0.0
```

---

## 🔍 Network Configuration

### Files That Control Behavior

| File | Purpose | Local Setting |
|------|---------|----------------|
| `build.gradle.kts` | Base URL & Build variant | `http://10.0.2.2:5000/` |
| `NetworkConfig.kt` | Runtime URL provider | Uses BuildConfig.BASE_URL |
| `AndroidManifest.xml` | Cleartext traffic permission | `usesCleartextTraffic="true"` |
| `network_security_config.xml` | Domain-level exceptions | Allows HTTP for 10.0.0.2 |

---

## 🐛 Troubleshooting Quick Links

### Connection Issues
```bash
# Check Flask is running
ps aux | grep "python app.py"

# Test direct connection
curl http://10.0.2.2:5000/api/health

# From emulator shell
adb shell curl http://10.0.2.2:5000/api/health
```

### Build Issues
```bash
# Clean build
./gradlew clean

# Check gradle wrapper
./gradlew --version

# Force rebuild
./gradlew buildDebug --rerun-tasks
```

### App Crashes
```bash
# View detailed logs
adb logcat | grep -E "aura|ServiceLocator|NetworkConfig"

# Save logs to file
adb logcat > log.txt
```

---

## 📌 Key Configuration Lines

### build.gradle.kts (Line 7)
```kotlin
val localBaseUrl = "http://10.0.2.2:5000/"  // ← Change this for different IP
```

### NetworkConfig.kt (Line 18)
```kotlin
const val activeBaseUrl: String = BuildConfig.BASE_URL  // ← No hardcoding
```

### AndroidManifest.xml (Line 22)
```xml
android:usesCleartextTraffic="true"  <!-- ← Required for HTTP -->
```

### network_security_config.xml (Line 3)
```xml
<domain includeSubdomains="true">10.0.2.2</domain>  <!-- ← Update for your IP -->
```

---

## ✨ Pro Tips

1. **Keep two terminals open:**
   - Terminal 1: Flask backend
   - Terminal 2: Android Studio/ADB

2. **Use Android Studio Profiler:**
   ```
   View > Tool Windows > Profiler
   ```
   Track network requests in real-time

3. **Enable strict logging:**
   ```kotlin
   // In ServiceLocator.kt
   HttpLoggingInterceptor.Level.BODY  // ← Already enabled for debug
   ```

4. **Test API endpoints manually:**
   ```bash
   adb shell
   curl http://10.0.2.2:5000/api/health
   exit
   ```

5. **Compare response times:**
   - Note time in logs for local backend
   - Compare with production to measure improvement

---

## 🎯 Summary

| Task | Command |
|------|---------|
| **Build for Local** | `./gradlew installDebug` |
| **Build for Production** | `./gradlew assembleRelease` |
| **View Logs** | `adb logcat \| grep aura` |
| **Test Connection** | `adb shell curl http://10.0.2.2:5000/api/health` |
| **Install APK** | `adb install app.apk` |
| **View Current Config** | Check `build.gradle.kts` line 7 |

---

**✅ Ready to debug! Start with `./gradlew installDebug` for local testing.**
