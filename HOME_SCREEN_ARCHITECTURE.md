# HomeScreen Upgrade - Architecture Diagram

## 🏗️ Overall Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         ANDROID APP                          │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌────────────────────────────────────────────────────┐    │
│  │              HomeScreen (Compose UI)                │    │
│  │                                                      │    │
│  │  • Trending Songs (existing)                       │    │
│  │  • Trending Playlists Section (NEW)                │    │
│  │  • Mood Categories Chips (NEW)                     │    │
│  │  • Mood Playlists Section (NEW - conditional)      │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
│                     ▼                                         │
│  ┌────────────────────────────────────────────────────┐    │
│  │            HomeViewModel (MVVM)                     │    │
│  │                                                      │    │
│  │  State: HomeUiState.Success                        │    │
│  │    - trending: List<Song>                          │    │
│  │    - trendingPlaylists: List<YTMusicPlaylist>      │    │
│  │    - moodCategories: List<MoodCategory>            │    │
│  │    - moodPlaylists: List<YTMusicPlaylist>          │    │
│  │    - selectedMoodTitle: String                     │    │
│  │                                                      │    │
│  │  Methods:                                           │    │
│  │    - loadHomeData()        (parallel fetch)        │    │
│  │    - selectMood(category)  (fetch mood playlists)  │    │
│  │    - clearMoodSelection()  (clear selection)       │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
│                     ▼                                         │
│  ┌────────────────────────────────────────────────────┐    │
│  │          MusicRepository (Data Layer)               │    │
│  │                                                      │    │
│  │  Methods:                                           │    │
│  │    - getTrendingPlaylists(limit)                   │    │
│  │    - getMoodCategories()                           │    │
│  │    - getMoodPlaylists(params, limit)               │    │
│  │    - getYTMusicPlaylistSongs(playlistId, limit)    │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
│                     ▼                                         │
│  ┌────────────────────────────────────────────────────┐    │
│  │          MusicApi (Retrofit Interface)              │    │
│  │                                                      │    │
│  │  Endpoints:                                         │    │
│  │    GET /api/home/trending-playlists                │    │
│  │    GET /api/home/moods                             │    │
│  │    GET /api/home/mood-playlists?params=XXX         │    │
│  │    GET /api/playlist/{playlistId}/songs            │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
└─────────────────────┼─────────────────────────────────────────┘
                      │ HTTP Requests
                      ▼
┌─────────────────────────────────────────────────────────────┐
│                      FLASK BACKEND                           │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌────────────────────────────────────────────────────┐    │
│  │              app.py (Flask Routes)                  │    │
│  │                                                      │    │
│  │  GET /api/home/trending-playlists                  │    │
│  │    → ytmusic.get_charts()                          │    │
│  │    → Extract trending.items                        │    │
│  │    → Return {playlists, count}                     │    │
│  │                                                      │    │
│  │  GET /api/home/moods                               │    │
│  │    → ytmusic.get_mood_categories()                 │    │
│  │    → Return {categories, count}                    │    │
│  │                                                      │    │
│  │  GET /api/home/mood-playlists?params=XXX           │    │
│  │    → ytmusic.get_mood_playlists(params)            │    │
│  │    → Return {playlists, count}                     │    │
│  │                                                      │    │
│  │  GET /api/playlist/{playlistId}/songs              │    │
│  │    → ytmusic.get_playlist(playlistId)              │    │
│  │    → Return {playlist, songs, count}               │    │
│  │                                                      │    │
│  │  Cache: 45 minutes TTL for all endpoints           │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
│                     ▼                                         │
│  ┌────────────────────────────────────────────────────┐    │
│  │              ytmusicapi (Library)                   │    │
│  │                                                      │    │
│  │  • get_charts() - Trending content                 │    │
│  │  • get_mood_categories() - Mood/genre list         │    │
│  │  • get_mood_playlists(params) - Mood playlists     │    │
│  │  • get_playlist(playlistId) - Playlist details     │    │
│  └──────────────────┬──────────────────────────────────┘    │
│                     │                                         │
└─────────────────────┼─────────────────────────────────────────┘
                      │ API Calls
                      ▼
          ┌─────────────────────────┐
          │    YouTube Music API     │
          │    (Google Servers)      │
          └─────────────────────────┘
```

---

## 🔀 User Flow: Browse Trending Playlists

```
┌──────────────┐
│   User       │
│  Opens App   │
└──────┬───────┘
       │
       ▼
┌──────────────────────────┐
│  HomeScreen Loads        │
│                          │
│  HomeViewModel.          │
│    loadHomeData()        │
└──────┬───────────────────┘
       │
       ▼  Parallel API Calls
┌──────────────────────────────────────────────┐
│  1. GET /api/home (trending songs)           │
│  2. GET /api/home/trending-playlists         │
│  3. GET /api/home/moods                      │
└──────┬───────────────────────────────────────┘
       │
       ▼  Backend → ytmusicapi → YTMusic
┌──────────────────────────┐
│  Responses Received      │
│                          │
│  HomeUiState.Success {   │
│    trending: [...]       │
│    trendingPlaylists: [] │
│    moodCategories: [...]  │
│  }                       │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  UI Renders              │
│                          │
│  • Trending Songs        │
│  • Trending Playlists    │
│  • Mood Chips            │
└──────┬───────────────────┘
       │
       ▼  User taps playlist
┌──────────────────────────┐
│  Navigate to             │
│  PlaylistPreviewScreen   │
│                          │
│  Route:                  │
│  main/playlist-preview/  │
│  {playlistId}            │
└──────┬───────────────────┘
       │
       ▼  On Screen Load
┌──────────────────────────┐
│  Fetch Playlist Songs    │
│                          │
│  GET /api/playlist/      │
│  {playlistId}/songs      │
└──────┬───────────────────┘
       │
       ▼  Backend → ytmusicapi
┌──────────────────────────┐
│  Response:               │
│  {                       │
│    playlist: {...}       │
│    songs: [...]          │
│  }                       │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  Display Playlist        │
│                          │
│  • Header (thumbnail)    │
│  • Song List             │
│  • Play All FAB          │
└──────┬───────────────────┘
       │
       ▼  User taps song
┌──────────────────────────┐
│  Resolve Streaming URL   │
│                          │
│  GET /api/song/          │
│  {videoId}               │
│  (uses yt-dlp)           │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  MusicService.           │
│    playResolvedSong()    │
│                          │
│  Navigate to Player      │
└──────────────────────────┘
```

---

## 🔀 User Flow: Browse by Mood

```
┌──────────────┐
│   User       │
│  on Home     │
└──────┬───────┘
       │
       ▼  Scrolls to mood chips
┌──────────────────────────┐
│  Mood Categories         │
│  Displayed               │
│                          │
│  [Pop] [Rock] [Chill]    │
│  [Jazz] [Electronic]     │
└──────┬───────────────────┘
       │
       ▼  Taps "Chill"
┌──────────────────────────┐
│  HomeViewModel.          │
│    selectMood(category)  │
│                          │
│  category.params =       │
│  "wAEB8gECKAE..."         │
└──────┬───────────────────┘
       │
       ▼  API Call
┌──────────────────────────┐
│  GET /api/home/          │
│  mood-playlists?         │
│  params=wAEB8gECKAE...   │
└──────┬───────────────────┘
       │
       ▼  Backend → ytmusicapi
┌──────────────────────────┐
│  Response:               │
│  {                       │
│    playlists: [          │
│      {title: "Chill..."} │
│      {title: "Relax..."} │
│    ]                     │
│  }                       │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  Update State            │
│                          │
│  HomeUiState.Success {   │
│    ...                   │
│    moodPlaylists: [...]  │
│    selectedMoodTitle:    │
│      "Chill"             │
│  }                       │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  UI Updates              │
│                          │
│  New Section Appears:    │
│  ┌──────────────────┐   │
│  │ Chill    [Clear] │   │
│  │                  │   │
│  │ [Playlist Cards] │   │
│  └──────────────────┘   │
└──────┬───────────────────┘
       │
       ▼  User taps Clear or same chip
┌──────────────────────────┐
│  HomeViewModel.          │
│    clearMoodSelection()  │
│                          │
│  selectedMoodTitle = ""  │
│  moodPlaylists = []      │
└──────┬───────────────────┘
       │
       ▼
┌──────────────────────────┐
│  Section Disappears      │
└──────────────────────────┘
```

---

## 📦 Data Models

### Backend Response (Flask)
```json
{
  "success": true,
  "playlists": [
    {
      "playlistId": "VLRDCLAK5uy_kset8DisdE7LSD42U2uk0UYmm-hZHRc",
      "title": "Chill Hits",
      "description": "The chillest hits...",
      "thumbnail": "https://...",
      "author": "YouTube Music",
      "songCount": 50
    }
  ],
  "count": 10
}
```

### Android DTO
```kotlin
data class YTMusicPlaylistDto(
    val playlistId: String,
    val title: String,
    val description: String? = "",
    val thumbnail: String? = "",
    val author: String? = "YouTube Music",
    val songCount: Int? = 0
)
```

### Android Model
```kotlin
data class YTMusicPlaylist(
    val playlistId: String,
    val title: String,
    val description: String = "",
    val thumbnail: String = "",
    val author: String = "YouTube Music",
    val songCount: Int = 0
)
```

### HomeViewModel State
```kotlin
data class Success(
    val trending: List<Song>,
    val trendingPlaylists: List<YTMusicPlaylist>,
    val moodCategories: List<MoodCategory>,
    val moodPlaylists: List<YTMusicPlaylist>,
    val selectedMoodTitle: String,
    val recommendations: List<Song>
) : HomeUiState()
```

---

## 🔄 Cache Strategy

```
┌─────────────────────────────────────────┐
│         Flask In-Memory Cache            │
│                                          │
│  _home_cache = {}                       │
│                                          │
│  Entry Structure:                       │
│  {                                      │
│    "key": "trending_playlists:10"      │
│    "value": {...response data...}      │
│    "expires_at": timestamp + 2700s     │
│  }                                      │
│                                          │
│  TTL: 45 minutes (2700 seconds)         │
│                                          │
│  Benefits:                              │
│  • Reduces YTMusic API calls            │
│  • Faster response times               │
│  • Prevents rate limiting              │
│                                          │
│  Invalidation:                          │
│  • Automatic after 45 min               │
│  • Manual: Restart Flask server         │
└─────────────────────────────────────────┘
```

---

## 🎯 Navigation Routes

```
Main Graph (main/*)
│
├─ main/home (HomeScreen)
│  ├─ Trending Songs → main/player
│  ├─ Trending Playlist → main/playlist-preview/{id}
│  └─ Mood Playlist → main/playlist-preview/{id}
│
├─ main/search (SearchScreen)
│  └─ Song → main/player
│
├─ main/player (PlayerScreen)
│
├─ main/playlists (PlaylistsScreen - User Playlists)
│  └─ Playlist → main/playlist/{id}
│     └─ Song → main/player
│
├─ main/playlist/{id} (PlaylistDetailScreen - User Playlist)
│  └─ Song → main/player
│
├─ main/playlist-preview/{id} (PlaylistPreviewScreen - YTMusic Playlist) **NEW**
│  ├─ Song → Resolve URL → main/player
│  └─ Play All → Resolve first song → main/player
│
└─ main/profile (ProfileScreen)
```

---

## 🧩 Component Hierarchy

```
HomeScreen
│
├─ LazyColumn
│  │
│  ├─ SectionHeader("Trending Now")
│  │
│  ├─ TrendingRow (songs)
│  │  └─ LazyRow
│  │     └─ TrendingSongCard × N
│  │
│  ├─ SectionHeader("Trending Playlists") **NEW**
│  │
│  ├─ TrendingPlaylistsRow **NEW**
│  │  └─ LazyRow
│  │     └─ YTMusicPlaylistCard × N
│  │
│  ├─ SectionHeader("Explore by Mood") **NEW**
│  │
│  ├─ MoodCategoriesRow **NEW**
│  │  └─ LazyRow
│  │     └─ MoodChip × N
│  │
│  ├─ SectionHeader("[Mood]", "Clear") **NEW (conditional)**
│  │
│  └─ MoodPlaylistsRow **NEW (conditional)**
│     └─ LazyRow
│        └─ YTMusicPlaylistCard × N
│
└─ PlaylistPickerBottomSheet (when song overflow clicked)
```

---

This architecture preserves the existing YTMusic discovery experience while adding powerful new discovery features through trending playlists and mood-based browsing!
