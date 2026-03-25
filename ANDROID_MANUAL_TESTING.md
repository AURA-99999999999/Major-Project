# Android Local Backend - Manual Testing Guide

## 🧪 Test Cases

### Test 1: Health Check (Automatic on App Launch)

When the app launches, it automatically calls:
```
GET http://10.0.2.2:5000/api/health
```

**Expected Response:**
```json
{
  "status": "healthy",
  "service": "aura-music-backend"
}
```

**Check Logs:**
```
Filter: ServiceLocator
Message: "Backend Health Check: SUCCESS"
```

---

### Test 2: Manual Health Check via ADB

```bash
adb shell curl -v http://10.0.2.2:5000/api/health

# Expected:
# < HTTP/1.1 200 OK
# {"status": "healthy", "service": "aura-music-backend"}
```

---

### Test 3: Verify Network Configuration

**In Android Studio Debug Console:**
```kotlin
// Add to MainActivity.onCreate() temporarily:
Log.d("DEBUG", com.aura.music.debug.LocalConnectionTest.testLocalBackendConnection())
```

**Expected Output:**
```
========================================
LOCAL BACKEND CONNECTION TEST
========================================

1. BuildConfig Values:
   BASE_URL: http://10.0.2.2:5000/
   API_ENV: LOCAL_EMULATOR

2. NetworkConfig Values:
   activeBaseUrl: http://10.0.2.2:5000/
   apiEnvironment: LOCAL_EMULATOR
   isLocal(): true
   isProduction(): false

3. URL Validation:
   ✓ Starts with http/https: true
   ✓ Has trailing slash: true
   ✓ Is HTTP (local): true
   ✓ Is HTTPS (production): false

========================================
```

---

### Test 4: API Call Test via Emulator Shell

```bash
# Access emulator shell
adb shell

# Test various endpoints
curl -v http://10.0.2.2:5000/api/health
curl -v http://10.0.2.2:5000/api/trending?limit=5
curl -v http://10.0.2.2:5000/api/search?q=song&limit=10
```

---

### Test 5: Physical Device on Local WiFi

**Find your PC's IP:**
```powershell
ipconfig

# Look for: IPv4 Address . . . . . . . . . . . : 192.168.x.x
```

**Update build.gradle.kts:**
```kotlin
val localBaseUrl = "http://192.168.1.x:5000/"
```

**Verify Flask accepts connections:**
```bash
# In backend terminal
# Should show: Running on http://0.0.0.0:5000 (access from any network)

# From another machine:
curl http://192.168.1.x:5000/api/health
```

**Test from device:**
```bash
adb shell curl -v http://192.168.1.x:5000/api/health
```

---

## 🔧 Testing Configuration

### Enable Debug Logs

**In ServiceLocator.kt** (already enabled in debug builds):
```kotlin
val loggingInterceptor = HttpLoggingInterceptor().apply {
    level = if (BuildConfig.DEBUG) {
        HttpLoggingInterceptor.Level.BODY  // ✅ Full request/response logging
    } else {
        HttpLoggingInterceptor.Level.NONE
    }
}
```

### View Logs in Android Studio

1. Open: Logcat
2. Filter: `aura|ServiceLocator|AppInterceptor`
3. Level: Debug and above

### Example Log Output

```
D/AppInterceptor: Request: GET http://10.0.2.2:5000/api/health
D/Retrofit: --> GET /api/health http/1.1
D/Retrofit: Host: 10.0.2.2:5000
D/Retrofit: User-Agent: okhttp/4.x.x
D/Retrofit: X-APP-KEY: aura-secure-android-key
D/Retrofit: --> END GET

D/Retrofit: <-- 200 OK http/1.0 (125ms)
D/Retrofit: Content-Type: application/json
D/Retrofit: {"status":"healthy","service":"aura-music-backend"}
D/Retrofit: <-- END HTTP

I/ServiceLocator: Backend Health Check: SUCCESS
I/ServiceLocator: Status: healthy
I/ServiceLocator: Service: aura-music-backend
```

---

## ❌ Debugging Failures

### Scenario 1: "Connection refused"

```
E/AppInterceptor: Network error: java.net.ConnectException: Failed to connect to /10.0.2.2:5000
```

**Checklist:**
- [ ] Flask backend is running: `python app.py`
- [ ] Backend shows: "Running on http://0.0.0.0:5000"
- [ ] Emulator can ping host: `adb shell ping 10.0.2.2`

**Solution:**
```bash
# Terminal 1: Start Flask
cd backend
python app.py

# Terminal 2: Check if reachable
adb shell curl http://10.0.2.2:5000/api/health
```

---

### Scenario 2: "SSL Certificate Error"

```
E/AppInterceptor: javax.net.ssl.SSLHandshakeException
```

**Cause:** Still using HTTPS for local backend

**Check:**
```
Logcat: "BASE_URL: https://..."  ❌ WRONG
Logcat: "BASE_URL: http://..."   ✅ CORRECT
```

**Fix:**
1. Verify `build.gradle.kts`:
   ```kotlin
   debug {
       buildConfigField("String", "BASE_URL", "\"$localBaseUrl\"")  // Must use localBaseUrl
   }
   ```

2. Rebuild: `./gradlew clean buildDebug`

---

### Scenario 3: "Network interface not allowed"

```
E/AppInterceptor: java.io.IOException: Cleartext traffic is not permitted
```

**Cause:** network_security_config.xml restrictions

**Fix:** Verify `network_security_config.xml`:
```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain>
    <domain includeSubdomains="true">192.168.1.5</domain>  <!-- Your IP -->
</domain-config>
```

---

### Scenario 4: "Connection timeout"

```
E/AppInterceptor: SocketTimeoutException: timeout
```

**Cause:** Flask not responding or firewall blocking

**Check:**
```bash
# 1. Flask is running
ps aux | grep "python app.py"

# 2. Port 5000 is open
netstat -an | grep 5000

# 3. No firewall blocks emulator
adb shell netstat -an | grep 5000
```

---

## ✅ Successful Connection Checklist

- [ ] Flask running locally: `python app.py`
- [ ] Health check passes on app launch (check Logcat)
- [ ] BuildConfig shows: `BASE_URL: http://10.0.2.2:5000/`
- [ ] NetworkConfig shows: `isLocal(): true`
- [ ] Logs show: "Backend Health Check: SUCCESS"
- [ ] No SSL/TLS errors
- [ ] No connection refused errors
- [ ] App can call other APIs (trending, search, etc.)

---

## 🎯 Running Test Cases Manually

### In Android Studio

Create a test activity or use debug drawer to call:

```kotlin
class DebugActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Test local connection
        Log.d("DEBUG", LocalConnectionTest.testLocalBackendConnection())
        
        // Test individual API calls
        lifecycleScope.launch {
            try {
                val api = ServiceLocator.getMusicApi()
                val health = api.getHealth()
                Log.d("API_TEST", "Health: $health")
                
                val trending = api.getTrending(limit = 5, uid = null)
                Log.d("API_TEST", "Trending count: ${trending.count}")
            } catch (e: Exception) {
                Log.e("API_TEST", "Error: ${e.message}", e)
            }
        }
    }
}
```

### Via ADB Shell

```bash
# Test health endpoint
adb shell curl http://10.0.2.2:5000/api/health

# Test trending endpoint
adb shell curl http://10.0.2.2:5000/api/trending?limit=5

# Test search endpoint
adb shell curl http://10.0.2.2:5000/api/search?q=song&limit=10

# Test with custom header
adb shell curl -H "X-APP-KEY: aura-secure-android-key" http://10.0.2.2:5000/api/health
```

---

## 📊 Expected API Responses

### Health Check Response
```bash
curl http://10.0.2.2:5000/api/health

HTTP/1.0 200 OK
Content-Type: application/json

{
  "status": "healthy",
  "service": "aura-music-backend"
}
```

### Trending Songs Response
```bash
curl http://10.0.2.2:5000/api/trending?limit=5

{
  "songs": [
    {
      "id": "song_1",
      "title": "Song Title",
      "artist": "Artist Name",
      "url": "stream_url",
      ...
    }
  ],
  "count": 5
}
```

### Search Response
```bash
curl http://10.0.2.2:5000/api/search?q=song&limit=10

{
  "songs": [...],
  "count": 10
}
```

---

## 🚀 Performance Baseline

**Expected Response Times (Local Backend):**
- Health check: ~10-50ms
- Trending (5 songs): ~200-500ms
- Search (10 results): ~300-800ms
- User profile: ~100-300ms

**Expected Response Times (Render Backend):**
- Health check: ~100-300ms (cold start)
- Trending: ~1-3s
- Search: ~2-5s

---

**✅ All tests pass when Flask backend running locally and app shows "Backend Health Check: SUCCESS" in logs**
