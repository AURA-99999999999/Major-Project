# Firestore Integration Guide

## 📋 Implementation Summary

A clean, production-ready Firestore setup has been implemented for your Android music streaming app. The implementation follows Android and Firebase best practices.

## 🏗️ What Was Implemented

### 1. **FirestoreRepository.kt**
Location: `android/app/src/main/java/com/aura/music/data/repository/FirestoreRepository.kt`

**Purpose:** Centralized repository for all Firestore operations

**Key Features:**
- ✅ User document creation at `users/{uid}`
- ✅ Automatic timestamp management (`createdAt`, `lastActive`)
- ✅ Idempotent operations (safe to call multiple times)
- ✅ Proper error handling with Result types
- ✅ Clear Logcat logging for debugging
- ✅ Temporary connectivity test function

**Core Functions:**
```kotlin
suspend fun initializeUserDocument(): Result<Unit>
suspend fun updateLastActive(): Result<Unit>
suspend fun testFirestoreConnectivity(): Result<Unit> // TEMPORARY - REMOVE AFTER TESTING
```

### 2. **MainActivity.kt Updates**
Location: `android/app/src/main/java/com/aura/music/MainActivity.kt`

**Changes Made:**
- Added Firestore repository initialization
- Implemented `LaunchedEffect` that triggers on authentication state changes
- Firestore operations run **only** when `AuthState.Authenticated`
- Session tracking to ensure operations run once per login
- Non-blocking error handling (app continues even if Firestore fails)

**Lifecycle:**
```
User Logs In → AuthState.Authenticated → LaunchedEffect triggers
→ FirestoreRepository.initializeUserDocument()
→ Creates/updates user document
→ Runs connectivity test (temporary)
→ Logs all results to Logcat
```

## 🔥 Firestore Document Structure

### Collection: `users`

**Document ID:** User's Firebase Auth UID

**Fields:**
```json
{
  "createdAt": Timestamp,      // Set once when document is created
  "lastActive": Timestamp,     // Updated on every login
  "email": String              // User's email from Firebase Auth
}
```

**Example Document:**
```
users/
  ├─ abc123xyz... (UID)
  │   ├─ createdAt: February 4, 2026 at 10:30:00 AM UTC
  │   ├─ lastActive: February 4, 2026 at 2:45:00 PM UTC
  │   └─ email: "user@example.com"
```

### Collection: `test_connectivity` (TEMPORARY)

**Document ID:** User's Firebase Auth UID

**Fields:**
```json
{
  "userId": String,
  "timestamp": Timestamp,
  "testMessage": "Firestore is working correctly!"
}
```

**⚠️ This collection is for testing only - delete it after verification**

## 🧪 Testing & Verification

### Step 1: Build and Run the App

```bash
cd android
./gradlew clean assembleDebug
./gradlew installDebug
```

Or simply run from Android Studio.

### Step 2: Check Logcat

**Filter by tag:** `MainActivity` or `FirestoreRepository`

**Expected Log Output:**
```
D/MainActivity: User authenticated: abc123xyz...
D/MainActivity: Initiating Firestore setup...
D/FirestoreRepository: Initializing Firestore document for user: abc123xyz...
D/FirestoreRepository: User document does not exist, creating new document
D/FirestoreRepository: ✓ Firestore: Created user document for abc123xyz...
I/MainActivity: Firestore initialization completed successfully
D/FirestoreRepository: ════════════════════════════════════════
D/FirestoreRepository: 🔥 Firestore Test: Starting connectivity test
D/FirestoreRepository: User ID: abc123xyz...
D/FirestoreRepository: ✓ Firestore Test: SUCCESS!
D/FirestoreRepository: ✓ Check Firebase Console > Firestore > test_connectivity collection
D/FirestoreRepository: ════════════════════════════════════════
I/MainActivity: ✓ Firestore connectivity test passed
```

### Step 3: Verify in Firebase Console

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Navigate to **Firestore Database**
4. You should see two collections:

#### Collection: `users`
- Click on `users` collection
- You'll see a document with your user's UID
- Click on the document to see fields:
  - `createdAt`: Timestamp when account was created
  - `lastActive`: Timestamp when user last logged in
  - `email`: User's email address

#### Collection: `test_connectivity`
- Click on `test_connectivity` collection
- You'll see a document with your user's UID
- This confirms Firestore write operations are working

### Step 4: Test Idempotency

**Login → Logout → Login again**

**Expected behavior:**
1. First login: Creates `users/{uid}` document
2. Second login: Updates `lastActive` timestamp only
3. No duplicate documents
4. No errors in Logcat

**Logcat on second login:**
```
D/FirestoreRepository: User document exists, updating lastActive timestamp
D/FirestoreRepository: ✓ Firestore: Updated lastActive for user abc123xyz...
```

## 🧹 Cleanup After Verification

Once you've confirmed everything works, remove the test code:

### 1. Remove Test Function from FirestoreRepository.kt

**Delete these lines (marked with comments):**
```kotlin
// Lines ~110-140 in FirestoreRepository.kt
// From: "TEMPORARY TEST FUNCTION"
// To: "END OF TEST FUNCTION"
```

### 2. Remove Test Call from MainActivity.kt

**Delete these lines:**
```kotlin
// Lines ~126-139 in MainActivity.kt
// The block that calls firestoreRepository.testFirestoreConnectivity()
```

### 3. Delete test_connectivity Collection in Firebase Console

1. Go to Firebase Console > Firestore
2. Click on `test_connectivity` collection
3. Delete the entire collection

## 🛠️ How It Works

### Architecture

```
MainActivity
    ├── LaunchedEffect(authState)
    │   └── Triggers on auth state changes
    │
    ├── FirestoreRepository
    │   ├── initializeUserDocument()
    │   │   ├── Gets current user from FirebaseAuth
    │   │   ├── Checks if document exists
    │   │   ├── Creates document if new user
    │   │   └── Updates lastActive if existing user
    │   │
    │   └── testFirestoreConnectivity() [TEMPORARY]
    │       └── Writes test document
    │
    └── RootNavGraph
        └── Handles navigation (unchanged)
```

### Execution Flow

```mermaid
User opens app
    ↓
AuthViewModel.checkAuthStatus()
    ↓
Is user logged in?
    ├─ No → Show login screen
    └─ Yes → AuthState.Authenticated
        ↓
    LaunchedEffect detects state change
        ↓
    firestoreInitialized == false?
        ├─ Yes → Initialize Firestore
        │   ├─ Create/update user document
        │   ├─ Run connectivity test
        │   └─ Set firestoreInitialized = true
        └─ No → Skip (already initialized)
            ↓
        Continue to app
```

## 🔒 Security & Best Practices

### ✅ What Was Done Right

1. **No Hardcoding:**
   - Uses `FirebaseAuth.getInstance().currentUser` dynamically
   - No mock UIDs or test data in production code

2. **Lifecycle Safety:**
   - `LaunchedEffect` is composition-aware
   - Coroutines are properly scoped
   - Operations cancel automatically on composition exit

3. **Idempotency:**
   - Safe to call multiple times
   - Checks if document exists before creating
   - Only updates `lastActive` on subsequent calls

4. **Separation of Concerns:**
   - All Firestore logic in `FirestoreRepository`
   - MainActivity only orchestrates
   - No business logic in UI layer

5. **Error Handling:**
   - All operations return `Result<Unit>`
   - Failures are logged but don't crash app
   - Clear error messages for debugging

6. **Authentication Guard:**
   - All Firestore operations check `currentUser != null`
   - Runs only after successful authentication
   - No operations while `AuthState.Loading`

## 📝 Firestore Rules (Update in Firebase Console)

Make sure your Firestore security rules allow authenticated users to write their own documents:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can read/write their own document
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Test connectivity (remove after testing)
    match /test_connectivity/{userId} {
      allow write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## 🐛 Troubleshooting

### Problem: "User not authenticated" in logs

**Solution:** Firestore operations are correctly guarded. This means the operation was attempted before login. No action needed.

### Problem: "Permission denied" error

**Solution:** Update Firestore rules (see above section)

### Problem: Document not appearing in Firebase Console

**Checklist:**
1. Check Logcat for success message
2. Verify Firebase project is correct in `google-services.json`
3. Ensure internet connection is active
4. Check Firestore rules allow write access
5. Try refreshing Firebase Console

### Problem: Duplicate documents or multiple writes

**Solution:** This shouldn't happen. The implementation includes:
- `firestoreInitialized` flag to prevent duplicate runs
- Document existence check before creation
- Check if `LaunchedEffect` is being called multiple times (add debug logs)

## 🚀 Production Deployment Checklist

Before shipping to production:

- [ ] Remove `testFirestoreConnectivity()` function
- [ ] Remove test connectivity call from MainActivity
- [ ] Delete `test_connectivity` collection from Firebase
- [ ] Update Firestore security rules
- [ ] Test thoroughly with multiple users
- [ ] Monitor Firestore usage/quota in Firebase Console
- [ ] Add analytics/crash reporting for Firestore errors
- [ ] Consider adding retry logic for network failures

## 📈 Next Steps (Optional Enhancements)

1. **Add User Preferences:**
   ```kotlin
   data class UserPreferences(
       val theme: String,
       val notificationsEnabled: Boolean,
       val favoriteGenres: List<String>
   )
   ```

2. **Track Listening History:**
   ```kotlin
   users/{uid}/listening_history/{trackId}
   ```

3. **Store Playlists:**
   ```kotlin
   users/{uid}/playlists/{playlistId}
   ```

4. **Add Offline Support:**
   ```kotlin
   FirebaseFirestore.getInstance().apply {
       firestoreSettings = firestoreSettings.toBuilder()
           .setPersistenceEnabled(true)
           .build()
   }
   ```

## 📚 References

- [Firestore Documentation](https://firebase.google.com/docs/firestore)
- [Kotlin Coroutines with Firebase](https://firebase.google.com/docs/firestore/query-data/get-data#kotlin+ktx_2)
- [Jetpack Compose Side Effects](https://developer.android.com/jetpack/compose/side-effects)
- [Firebase Auth with Firestore](https://firebase.google.com/docs/auth/android/start)

## 🤝 Support

If you encounter any issues:
1. Check Logcat first (filter by `Firestore` or `MainActivity`)
2. Verify Firebase Console shows the user document
3. Ensure `google-services.json` is up to date
4. Check internet connectivity
5. Review Firestore security rules

---

**Implementation Date:** February 4, 2026
**Status:** ✅ Production-Ready (remove test code after verification)
