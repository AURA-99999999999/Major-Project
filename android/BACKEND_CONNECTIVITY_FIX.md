# Backend Connectivity Fix - Complete Guide

## Problem Diagnosed

The Android app was unable to reach the Flask backend API due to multiple configuration issues:

1. **Network Security Configuration Missing**: Android 9+ (API 28+) blocks HTTP cleartext traffic by default. The app had `usesCleartextTraffic="true"` but no proper network security config.

2. **Emulator vs Device IP Confusion**: 
   - **Emulator**: Must use `10.0.2.2` to access host machine's `localhost`
   - **Physical Device**: Must use actual LAN IP like `192.168.1.5`

3. **HTTPS vs HTTP Mismatch**: Flask dev server runs on HTTP, not HTTPS, but the URL might have been configured as HTTPS.

## Fixes Applied

### 1. Network Security Configuration

**Created**: `app/src/main/res/xml/network_security_config.xml`

This file properly allows HTTP cleartext traffic for local development while maintaining security. Android 9+ requires explicit configuration to allow HTTP.

### 2. AndroidManifest.xml Update

**Updated**: `AndroidManifest.xml`

Added `android:networkSecurityConfig="@xml/network_security_config"` to properly reference the network security config file.

### 3. Enhanced Error Logging

**Updated**: 
- `ServiceLocator.kt`: Added comprehensive logging for network configuration
- `SearchViewModel.kt`: Enhanced error messages with troubleshooting guidance

### 4. Configuration Validation

The app now warns about common misconfigurations:
- Using HTTPS with local IP addresses
- Using emulator IP when `API_ENV=DEVICE`
- Using device IP when `API_ENV=EMULATOR`

## Configuration Guide

### For Emulator (Recommended for Development)

**File**: `android/local.properties`

```properties
API_ENV=EMULATOR
API_BASE_URL_EMULATOR=http://10.0.2.2:5000/api/
```

**Why `10.0.2.2`?**
- Android emulator uses a special network mapping
- `10.0.2.2` is a special alias that maps to the host machine's `localhost`
- This is the ONLY way to access your development server from the emulator

### For Physical Device

**File**: `android/local.properties`

```properties
API_ENV=DEVICE
API_BASE_URL_DEVICE=http://192.168.1.5:5000/api/
```

**Important Requirements:**
1. **Same Wi-Fi Network**: Your Android device and development machine MUST be on the same Wi-Fi network
2. **Correct IP Address**: Find your machine's IP address:
   - Windows: `ipconfig` (look for IPv4 Address)
   - Linux/Mac: `ifconfig` or `ip addr`
   - Make sure it's the Wi-Fi adapter IP, not Ethernet
3. **Firewall**: Ensure Windows Firewall allows port 5000

**Finding Your IP Address:**
```bash
# Windows
ipconfig | findstr IPv4

# Linux/Mac
ifconfig | grep "inet "

# Example output: 192.168.1.5 (use this in API_BASE_URL_DEVICE)
```

### Flask Server Configuration

**File**: `app.py` (already correct)

```python
if __name__ == '__main__':
    # Bind to 0.0.0.0 (all interfaces) so devices can connect
    app.run(host='0.0.0.0', port=5000, debug=True)
```

**Critical**: The server MUST bind to `0.0.0.0`, NOT `127.0.0.1` or `localhost`. Only `0.0.0.0` allows connections from other devices on the network.

## Verification Steps

### 1. Verify Flask Server is Running

```bash
cd Major-Project
python app.py
```

You should see:
```
 * Running on http://0.0.0.0:5000
```

### 2. Test from Browser

**From your development machine:**
- `http://localhost:5000/api/health` - Should return JSON

**From another device on same network:**
- `http://192.168.1.5:5000/api/health` - Should return JSON

### 3. Test from Android

1. **Rebuild the app** after changing `local.properties`:
   ```bash
   cd android
   ./gradlew clean
   ./gradlew :app:assembleDebug
   ```

2. **Check Logcat** for network configuration:
   ```
   Filter by: ServiceLocator
   Look for: "Network Configuration" log with base URL
   ```

3. **Make a request** in the app (e.g., search for a song)

4. **Check Logcat** for:
   - Request logs (OkHttp logs)
   - Response logs
   - Any error messages

## Common Issues & Solutions

### Issue: "Server not reachable"

**Possible Causes:**
1. Wrong IP address in `local.properties`
2. Flask server not running
3. Flask server bound to `127.0.0.1` instead of `0.0.0.0`
4. Firewall blocking port 5000
5. Device/emulator not on same network (for physical device)

**Solution:**
- Check Logcat for the actual base URL being used
- Verify Flask server is running and bound to `0.0.0.0`
- Test the URL in a browser first
- For physical device: Ensure same Wi-Fi network

### Issue: "Cleartext traffic not permitted"

**Cause:** Network security config not properly applied

**Solution:**
- Ensure `network_security_config.xml` exists in `res/xml/`
- Ensure `AndroidManifest.xml` references it
- Rebuild the app

### Issue: Works on Emulator but not Physical Device

**Cause:** Different IP addresses needed

**Solution:**
- Emulator: Use `API_ENV=EMULATOR` with `10.0.2.2`
- Physical Device: Use `API_ENV=DEVICE` with `192.168.x.x`

### Issue: Connection Timeout

**Possible Causes:**
1. Firewall blocking port 5000
2. Router blocking device-to-device communication
3. VPN interfering with local network

**Solution:**
- Check Windows Firewall: Allow Python through firewall
- Disable VPN temporarily
- Check router settings for AP isolation

## Logcat Debugging

### Enable Full Logging

The app automatically logs network requests in DEBUG builds. Filter Logcat by:

1. **Network Configuration:**
   ```
   Tag: ServiceLocator
   Look for: "Network Configuration" section
   ```

2. **HTTP Requests/Responses:**
   ```
   Tag: OkHttp
   Look for: Full request/response details
   ```

3. **Errors:**
   ```
   Tag: SearchViewModel or ServiceLocator
   Level: Error
   ```

### Sample Logcat Output (Success)

```
ServiceLocator: ========================================
ServiceLocator: Network Configuration
ServiceLocator: Environment: DEVICE
ServiceLocator: Base URL: http://192.168.1.5:5000/api/
ServiceLocator: Emulator URL: http://10.0.2.2:5000/api/
ServiceLocator: Device URL: http://192.168.1.5:5000/api/
ServiceLocator: ========================================

OkHttp: --> GET http://192.168.1.5:5000/api/health
OkHttp: <-- 200 OK http://192.168.1.5:5000/api/health (123ms)
```

## Production Considerations

⚠️ **Important for Production:**

1. **Remove Cleartext Traffic**: This configuration allows HTTP for development only. Production MUST use HTTPS.

2. **Update Network Security Config**: For production, restrict cleartext to specific domains or remove entirely.

3. **Use Environment Variables**: Consider using different configs for dev/staging/production.

4. **API Gateway**: In production, use a proper API gateway with HTTPS, not direct Flask connections.

## Summary of Changes

### Files Created:
- `app/src/main/res/xml/network_security_config.xml` - Network security configuration

### Files Modified:
- `AndroidManifest.xml` - Added network security config reference
- `ServiceLocator.kt` - Enhanced logging and validation
- `SearchViewModel.kt` - Improved error messages with troubleshooting

### Files to Update Manually:
- `android/local.properties` - Set correct API_ENV and IP addresses

## Quick Start Checklist

- [ ] Flask server running (`python app.py`)
- [ ] Flask bound to `0.0.0.0` (check `app.py`)
- [ ] Correct `API_ENV` in `local.properties` (EMULATOR or DEVICE)
- [ ] Correct IP address in `local.properties`
- [ ] App rebuilt after changing `local.properties`
- [ ] Check Logcat for network configuration
- [ ] Test API endpoint in browser first
- [ ] Verify device/emulator network connectivity

## Success Indicators

✅ App loads data from backend  
✅ Logcat shows successful HTTP requests  
✅ No "server not reachable" errors  
✅ API responses visible in Logcat  
✅ Network configuration logs show correct URL

---

**After applying these fixes, the app should successfully connect to the Flask backend API.**

