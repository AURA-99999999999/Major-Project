# HomeScreen Upgrade - Quick Start Guide

## 🚀 Testing the New Features

### Step 1: Start Backend
```bash
cd "c:\Users\Bharat Sri Vastava\Downloads\Telegram Desktop\mjrr\Major-Project"
python app.py
```
Server should start on `http://localhost:5000`

### Step 2: Verify Endpoints
Open browser and test:
- http://localhost:5000/api/home/trending-playlists
- http://localhost:5000/api/home/moods
- http://localhost:5000/api/home/mood-playlists?params=XXXXX (use a params value from moods response)
- http://localhost:5000/api/playlist/VLXXXXXXXXXXX/songs (use a playlistId from trending-playlists)

Expected responses: JSON with success=true

### Step 3: Build Android App
```bash
cd android
./gradlew assembleDebug
```
Or use Android Studio: **Build → Make Project**

### Step 4: Run App
- Install APK on device/emulator
- Login if needed
- You should now see 3 new sections on HomeScreen:
  1. **Trending Playlists** (below trending songs)
  2. **Explore by Mood** (mood chips)
  3. **[Mood Name]** (when you tap a mood chip)

---

## 🎯 What to Test

### HomeScreen Features
1. **Scroll down** - Verify all sections load:
   - ✅ Trending Now (songs)
   - ✅ Trending Playlists
   - ✅ Explore by Mood

2. **Tap a playlist card** → Should open PlaylistPreviewScreen

3. **Tap a mood chip** → Should load playlists for that mood below

4. **Tap same mood again** → Should clear the mood selection

5. **Tap "Clear" button** → Should hide mood playlists section

### PlaylistPreviewScreen Features
1. **Playlist header** should show:
   - Large thumbnail
   - Playlist title
   - Author name
   - Song count

2. **Song list** should show all tracks with:
   - Thumbnails
   - Title
   - Artist
   - Duration (if available)

3. **Tap a song** → Should resolve streaming URL and start playing

4. **Tap Play All FAB** (bottom right) → Should start playing first song

5. **Back button** → Should return to HomeScreen

---

## 🐛 Troubleshooting

### Backend Issues

**Problem:** endpoints return empty arrays
- **Solution:** Check ytmusicapi version: `pip show ytmusicapi`
- Update if needed: `pip install --upgrade ytmusicapi`

**Problem:** 500 errors
- **Solution:** Check Flask logs for stack trace
- Verify ytmusicapi can access YTMusic (not blocked by firewall/VPN)

### Android Issues

**Problem:** Sections don't appear
- **Solution:** Check Logcat for "HomeViewModel" logs
- Verify backend is running and accessible from device
- Check if BASE_URL in RetrofitClient points to correct IP

**Problem:** Playlist preview doesn't load
- **Solution:** Check Logcat for "getYTMusicPlaylistSongs" errors
- Verify playlistId format (should start with "VL" or "PL")

**Problem:** Songs don't play
- **Solution:** Check if yt-dlp is extracting stream URLs correctly
- Verify getSong() repository call succeeds
- Check MusicService logs

---

## 📊 Expected Behavior

### Data Loading Sequence
```
1. HomeScreen opens
2. Loading state shows CircularProgressIndicator
3. Parallel API calls:
   - GET /api/home (trending songs)
   - GET /api/home/trending-playlists
   - GET /api/home/moods
4. Success state renders all sections
5. User taps mood → GET /api/home/mood-playlists?params=XXX
6. Mood playlists section appears
```

### Empty States
- If **trending playlists** is empty → Section hidden
- If **mood categories** is empty → Section hidden
- If **mood playlists** is empty → Still shows section header (might be a slow load)

### Cache Behavior
- All endpoints cache for **45 minutes**
- Second load within 45 min → Instant (served from cache)
- After 45 min → Re-fetches from YTMusic

---

## 🔍 Debug Commands

### Check Backend Logs
```bash
# Look for these log messages:
# "getTrendingPlaylists()"
# "getMoodCategories()"
# "getMoodPlaylists()"
# "getYTMusicPlaylistSongs()"
```

### Check Android Logs
```bash
adb logcat | grep -E "HomeViewModel|MusicRepository|PlaylistPreview"
```

### Test Individual Endpoints
```bash
# Using curl:
curl http://localhost:5000/api/home/trending-playlists
curl http://localhost:5000/api/home/moods
curl "http://localhost:5000/api/home/mood-playlists?params=YOUR_PARAMS_HERE"
curl http://localhost:5000/api/playlist/PLAYLIST_ID/songs
```

---

## ✅ Success Criteria

You've successfully implemented the upgrade if:
- [ ] Trending songs section still works (preserved)
- [ ] Trending playlists section appears and scrolls horizontally
- [ ] Mood chips appear and are tappable
- [ ] Tapping mood chip loads playlists
- [ ] Tapping mood chip again clears selection
- [ ] Tapping playlist card opens PlaylistPreviewScreen
- [ ] PlaylistPreviewScreen shows header + songs
- [ ] Tapping song starts playback
- [ ] Play All FAB works
- [ ] Back navigation works everywhere
- [ ] No crashes or build errors

---

## 📞 Need Help?

Check these files if something breaks:

**Backend:**
- [app.py](app.py) - Endpoint definitions

**Android Data:**
- [MusicApi.kt](android/app/src/main/java/com/aura/music/data/remote/MusicApi.kt) - Retrofit interface
- [MusicRepository.kt](android/app/src/main/java/com/aura/music/data/repository/MusicRepository.kt) - Repository methods

**Android UI:**
- [HomeViewModel.kt](android/app/src/main/java/com/aura/music/ui/viewmodel/HomeViewModel.kt) - State management
- [HomeScreen.kt](android/app/src/main/java/com/aura/music/ui/screens/home/HomeScreen.kt) - UI rendering
- [PlaylistPreviewScreen.kt](android/app/src/main/java/com/aura/music/ui/screens/playlist/PlaylistPreviewScreen.kt) - Playlist preview

**Navigation:**
- [MainGraph.kt](android/app/src/main/java/com/aura/music/navigation/MainGraph.kt) - Route definitions

**Summary:**
- [HOME_SCREEN_UPGRADE_SUMMARY.md](HOME_SCREEN_UPGRADE_SUMMARY.md) - Full implementation details
