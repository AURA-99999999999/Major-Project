# Android Local Backend Debug Setup

## ✅ Changes Made

All changes have been implemented to connect your Android app to a **LOCAL backend** instead of Render. See details below.

---

## 📋 FILES UPDATED

### 1. **android/app/build.gradle.kts**
- ✅ Added `localBaseUrl = "http://10.0.2.2:5000/"` (for emulator)
- ✅ Updated debug build to use **LOCAL backend**
- ✅ Updated release build to use **PRODUCTION** (Render)
- ✅ Set `API_ENV` buildConfig fields

**For Physical Device:** Replace `10.0.2.2` with your local IP (e.g., `192.168.1.5`)

### 2. **android/app/src/main/java/com/aura/music/data/remote/NetworkConfig.kt**
- ✅ Removed HTTPS-only requirement
- ✅ Now supports both `http://` and `https://`
- ✅ Dynamically loads from `BuildConfig`
- ✅ Added helper functions: `isLocal()`, `isProduction()`

### 3. **android/app/src/main/AndroidManifest.xml**
- ✅ Changed `android:usesCleartextTraffic="true"` to allow HTTP traffic

### 4. **android/app/src/main/res/xml/network_security_config.xml**
- ✅ Added domain exceptions for local IPs:
  - `10.0.2.2` (emulator)
  - `127.0.0.1` (localhost)
  - `192.168.x.x` (local network - configure as needed)
- ✅ Production still uses HTTPS

---

## 🚀 QUICK START

### For Android Emulator:

```bash
# 1. Start your Flask backend locally
cd backend
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
pip install -r requirements.txt
python app.py  # Runs on http://localhost:5000
```

### 2. Build and Run Android App:

```bash
cd android
./gradlew build  # Debug build uses http://10.0.2.2:5000/
./gradlew installDebug
```

### For Physical Device (Same WiFi):

**Find your local IP:**
```powershell
ipconfig  # Look for IPv4 Address
# Example: 192.168.1.5
```

**Update in build.gradle.kts:**
```kotlin
val localBaseUrl = "http://192.168.1.5:5000/"
```

**Ensure Flask is accessible:**
```bash
# In your Flask app, make sure to bind to 0.0.0.0
python app.py
# Should print: Running on http://0.0.0.0:5000
```

---

## 🧪 Test Connectivity

When the app launches, ServiceLocator performs automatic health checks:

### Emulator Logs:
```
I/ServiceLocator: ========================================
I/ServiceLocator: Initializing Network Client
I/ServiceLocator: Base URL: http://10.0.2.2:5000/
I/ServiceLocator: ========================================

I/ServiceLocator: ========================================
I/ServiceLocator: Backend Health Check: SUCCESS
I/ServiceLocator: Status: healthy
I/ServiceLocator: Service: aura-music-backend
I/ServiceLocator: ========================================
```

### Failure Case:
```
E/ServiceLocator: Network error after retries for http://10.0.2.2:5000/api/health: Connection refused
```

---

## 🔍 Debug Logging

Retrofit HTTP logging is **enabled for debug builds** with full body logging:

```kotlin
// In ServiceLocator.kt
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY  // ✅ Enabled
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}
```

View logs in Android Studio:
```
Logcat Filter: aura|ServiceLocator|AppInterceptor|Retrofit
```

---

## 📱 Example Request Log

```
I/AppInterceptor: Request: GET http://10.0.2.2:5000/api/health
I/Retrofit: --> GET /api/health http/1.1

I/Retrofit: <-- 200 OK http/1.0 (125ms)
I/Retrofit: Content-Type: application/json
I/Retrofit: {"status": "healthy", "service": "aura-music-backend"}
```

---

## ⚙️ Environment Detection

NetworkConfig automatically detects your environment:

```kotlin
// In logs or by calling:
Log.d("NetworkConfig", NetworkConfig.description())

// Output:
// Environment: LOCAL_EMULATOR | Base URL: http://10.0.2.2:5000/

// OR for Physical Device:
// Environment: LOCAL_EMULATOR | Base URL: http://192.168.1.5:5000/
```

---

## 🚨 Common Issues & Solutions

### Issue 1: "Connection refused" or Timeout
**Cause:** Flask backend not running or using wrong IP

**Solution:**
```bash
# 1. Check Flask is running
# Terminal: cd backend && python app.py
# Should show: Running on http://0.0.0.0:5000

# 2. Verify emulator can reach host
adb shell
ping 10.0.2.2  # Should respond

# 3. For physical device, check same WiFi network
ipconfig  # Your laptop's IP
192.168.1.x  # Physical device should be on same network
```

### Issue 2: "SSL Certificate error" or "Unable to resolve host"
**Cause:** Still trying to use HTTPS or wrong URL

**Solution:**
1. Check `BuildConfig` values: Run `./gradlew build` and verify debug output
2. Verify NetworkConfig using: `Log.d("NetworkConfig", NetworkConfig.description())`

### Issue 3: "Network interface not allowed"
**Cause:** network_security_config.xml restrictions

**Solution:**
Add your IP to `network_security_config.xml`:
```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain>
    <domain includeSubdomains="true">192.168.1.5</domain>  <!-- Add your IP -->
</domain-config>
```

---

## ✅ Checklist Before Testing

- [ ] Flask backend running locally: `python app.py`
- [ ] Backend accessible: `curl http://localhost:5000/api/health`
- [ ] Android app built with debug config: `./gradlew buildDebug`
- [ ] Emulator/Device on same WiFi (for physical device)
- [ ] Correct IP in build.gradle.kts (10.0.2.2 for emulator)
- [ ] BuildConfig.BASE_URL shows local URL (not Render)

---

## 🔄 Switching Back to Production

When ready to deploy:

```bash
# Build release APK (automatically uses Render URL)
./gradlew assembleRelease

# Or just build for production testing
./gradlew buildRelease
```

The release build will automatically use:
```kotlin
BASE_URL = "https://aura-b7vm.onrender.com/"
API_ENV = "PRODUCTION"
```

---

## 📊 Current Configuration

### Debug Build
```
Environment: LOCAL_EMULATOR
Base URL: http://10.0.2.2:5000/
Cleartext Traffic: ✅ ALLOWED
Logging: ✅ ENABLED (BODY level)
Health Check: ✅ YES
```

### Release Build  
```
Environment: PRODUCTION
Base URL: https://aura-b7vm.onrender.com/
Cleartext Traffic: ❌ DISABLED
Logging: ❌ NONE
Health Check: ✅ YES
```

---

## 🎯 Next Steps

1. **Start Flask backend:**
   ```bash
   cd backend && python app.py
   ```

2. **Build Android app:**
   ```bash
   cd android && ./gradlew buildDebug
   ```

3. **Run on emulator/device:**
   ```bash
   ./gradlew installDebug
   ```

4. **Check logs in Android Studio:**
   - Filter: `ServiceLocator` or `AppInterceptor`
   - Look for "Backend Health Check: SUCCESS"
   - Verify base URL shows `http://10.0.2.2:5000/`

5. **If issues:**
   - Check logs for actual error
   - Verify Flask is running
   - Test connection manually: `adb shell curl http://10.0.2.2:5000/api/health`

---

## ❓ Need Help?

**Check these files for configuration:**
- `android/app/build.gradle.kts` - URL configuration
- `android/app/src/main/java/com/aura/music/data/remote/NetworkConfig.kt` - Network rules
- `android/app/src/main/AndroidManifest.xml` - Manifest permissions
- `android/app/src/main/res/xml/network_security_config.xml` - Security rules

**Check backend connectivity:**
```bash
# From emulator shell
adb shell curl -v http://10.0.2.2:5000/api/health

# Should return: {"status": "healthy", "service": "aura-music-backend"}
```

---

**✅ Setup Complete! Your Android app is now configured for local backend debugging.**
