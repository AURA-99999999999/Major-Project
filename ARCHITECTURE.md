# Clean Architecture Diagram - Aura Music

## ASCII Architecture Diagram

```
╔════════════════════════════════════════════════════════════════════════════════════╗
║                            PRESENTATION LAYER (Client)                            ║
╠════════════════════════════════════════════════════════════════════════════════════╣
║                                                                                    ║
║  ┌─────────────────────────────────────────────────────────────────────────────┐  ║
║  │                         Android App (Kotlin)                                │  ║
║  │                                                                             │  ║
║  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │  ║
║  │  │ Search Page  │  │ Discover     │  │ Daily Mix    │  │ Streaming    │   │  ║
║  │  │              │  │ Playlist     │  │ Generation   │  │ Player       │   │  ║
║  │  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘   │  ║
║  │         │                  │                 │                 │           │  ║
║  │         └──────────────────┴─────────────────┴─────────────────┘           │  ║
║  │                          │                                                 │  ║
║  │                  ┌───────▼──────────┐                                      │  ║
║  │                  │  View Models     │                                      │  ║
║  │                  │  (UI State Mgmt) │                                      │  ║
║  │                  └───────┬──────────┘                                      │  ║
║  │                          │                                                 │  ║
║  │                  ┌───────▼──────────┐                                      │  ║
║  │                  │ Retrofit Client  │─────HTTP/REST─────────────┐         │  ║
║  │                  │ (useClients)     │                           │         │  ║
║  │                  └──────────────────┘                           │         │  ║
║  │                          │                                       │         │  ║
║  │              ┌───────────▼──────────────┐                       │         │  ║
║  │              │  Firebase Auth Client   │                       │         │  ║
║  │              │  (Login/Register)       │                       │         │  ║
║  │              └───────────┬──────────────┘                       │         │  ║
║  │                          │                                       │         │  ║
║  └──────────────────────────┼───────────────────────────────────────▏─────────┘  ║
║                             │                                       │            ║
╚═════════════════════════════╪═══════════════════════════════════════╪════════════╝
                              │                                       │
                    [AUTH TOKEN & API CALLS]          [HTTPS REST Calls]
                              │                                       │
╔═════════════════════════════╪═══════════════════════════════════════╪════════════╗
║                       APPLICATION LAYER (Backend)                  │            ║
╠════════════════════════════════════════════════════════════════════════════════════╣
║                                                                                    ║
║  ┌────────────────────────────────────────────────────────────────────────────┐  ║
║  │                         Flask REST API Server                              │  ║
║  │                      (Running on Render)                                   │  ║
║  │                                                                            │  ║
║  │  ┌─────────────────────────────────────────────────────────────────────┐  │  ║
║  │  │                     Controllers / Route Handlers                    │  │  ║
║  │  │                                                                     │  │  ║
║  │  │  POST /auth/login      GET /search?q=artist                        │  │  ║
║  │  │  POST /auth/register   GET /playlists/recommendations              │  │  ║
║  │  │  POST /auth/logout     GET /playlists/daily-mix                    │  │  ║
║  │  │  GET  /user/profile    GET /playlists/{id}/tracks                  │  │  ║
║  │  │  POST /user/update     GET /tracks/stream/{id}                     │  │  ║
║  │  │                                                                     │  │  ║
║  │  └───────────────────┬──────────────────────────────────────────────────┘  │  ║
║  │                      │                                                     │  ║
║  │  ┌───────────────────▼──────────────────────────────────────────────────┐  │  ║
║  │  │                    Business Logic Layer                              │  │  ║
║  │  │                                                                      │  │  ║
║  │  │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │  │  ║
║  │  │  │ RecommendationService                 │  │ DailyMixService │   │  │  ║
║  │  │  │ - Collaborative  │  │ - Genre Balance │  │ - Sleep Timer   │   │  │  ║
║  │  │  │   Filtering      │  │ - Mood Mix      │  │ - Smart Shuffle │   │  │  ║
║  │  │  │ - CF Algorithm   │  │ - Time-based    │  │ - Listening     │   │  │  ║
║  │  │  │ - User Profiles  │  │   Generation    │  │   History       │   │  │  ║
║  │  │  └────────┬─────────┘  └────────┬────────┘  └────────┬────────┘   │  │  ║
║  │  │           │                     │                    │            │  │  ║
║  │  │  ┌────────▼─────────────────────▼────────────────────▼──────────┐ │  │  ║
║  │  │  │              Service Aggregator                             │ │  │  ║
║  │  │  │                                                             │ │  │  ║
║  │  │  │  • StreamService (Track Metadata & URLs)                   │ │  │  ║
║  │  │  │  • SearchService (Query Processing)                        │ │  │  ║
║  │  │  │  • UserProfileService (Listening History & Preferences)   │ │  │  ║
║  │  │  │  • ArtistNormalizationService (Canonical Form)             │ │  │  ║
║  │  │  │  • CacheManager (Redis/Firestore Cache)                    │ │  │  ║
║  │  │  │                                                             │ │  │  ║
║  │  │  └────────┬────────────────────────────────────────────────────┘ │  │  ║
║  │  │           │                                                      │  │  ║
║  │  └───────────┼──────────────────────────────────────────────────────┘  │  ║
║  │              │                                                         │  ║
║  │  ┌───────────▼──────────────────────────────────────────────────────┐  │  ║
║  │  │                    Data Layer                                    │  │  ║
║  │  │                                                                  │  │  ║
║  │  │  ┌──────────────────┐    ┌──────────────────┐                   │  │  ║
║  │  │  │ Firestore        │    │ Cache Manager    │                   │  │  ║
║  │  │  │ Collections:     │    │ (Local Cache)    │                   │  │  ║
║  │  │  │ - users          │    │ - Top Artists    │                   │  │  ║
║  │  │  │ - playlists      │    │ - Recent Searches│                   │  │  ║
║  │  │  │ - tracks         │    │ - User Prefs     │                   │  │  ║
║  │  │  │ - recommendations│    │                  │                   │  │  ║
║  │  │  │ - listening_hist │    │                  │                   │  │  ║
║  │  │  └──────────────────┘    └──────────────────┘                   │  │  ║
║  │  │                                                                  │  │  ║
║  │  └──────────────────────────────────────────────────────────────────┘  │  ║
║  │                                                                        │  ║
║  └────────────────────────────────────────────────────────────────────────┘  ║
║                                                                               ║
╚═══════════════════════════╪════════════════════════════╪═══════════════════════╝
                            │                            │
          [Firestore API Calls]        [Cache Read/Write]
                            │                            │
╔═══════════════════════════╪════════════════════════════╪═══════════════════════╗
║                     EXTERNAL SERVICES LAYER                                    ║
╠════════════════════════════════════════════════════════════════════════════════╣
║                                                                                ║
║  ┌──────────────────────────┐  ┌──────────────────────────┐                   ║
║  │   JioSaavn API Service   │  │   Firebase Services      │                   ║
║  │                          │  │                          │                   ║
║  │ ┌──────────────────────┐ │  │ ┌──────────────────────┐ │                   ║
║  │ │ • Song Search        │ │  │ │ • Authentication     │ │                   ║
║  │ │ • Playlist Lookup    │ │  │ │ • Authorization      │ │                   ║
║  │ │ • Stream URLs        │ │  │ │ • User Data Storage  │ │                   ║
║  │ │ • Artist Info        │ │  │ │ • Real-time Sync     │ │                   ║
║  │ │ • Album Data         │ │  │ │ • Analytics          │ │                   ║
║  │ │ • Charts/Trending    │ │  │ └──────────────────────┘ │                   ║
║  │ │ • Lyrics             │ │  │                          │                   ║
║  │ └──────────────────────┘ │  │ ┌──────────────────────┐ │                   ║
║  │                          │  │ │ • Firestore (NoSQL)  │ │                   ║
║  │  REST API (HTTPS)        │  │ │ • Real-time DB       │ │                   ║
║  │  https://jiosaavn.com/   │  │ │ • Cloud Storage      │ │                   ║
║  │                          │  │ └──────────────────────┘ │                   ║
║  └──────────────────────────┘  └──────────────────────────┘                   ║
║                                                                                ║
╚════════════════════════════════════════════════════════════════════════════════╝
```

---

## Data Flow Diagrams

### User Flow: Search & Recommendation

```
┌───────────────┐
│  User opens   │
│  Search Page  │
└───────┬───────┘
        │
        ▼
┌──────────────────────┐
│ Enter Search Query   │
│ (Artist/Song/Album)  │
└───────┬──────────────┘
        │
        ▼
┌──────────────────────┐      ┌──────────────────────┐
│  Android App sends   │─────▶│  Flask Backend       │
│  GET /search?q=...   │      │  Receives Query      │
└──────────────────────┘      └──────┬───────────────┘
                                     │
                                     ▼
                            ┌──────────────────────┐
                            │ Check Cache First    │
                            │ (CacheManager)       │
                            └──────┬───────────────┘
                                   │
                    ┌──────────────┬──────────────┐
                    │              │              │
               CACHE HIT      CACHE MISS       │
                    │              │              │
                    ▼              ▼              │
            ┌───────────┐  ┌──────────────────┐  │
            │Return     │  │ Query JioSaavn   │  │
            │Cached     │  │ API for Results  │  │
            │Results    │  └────────┬─────────┘  │
            └────┬──────┘           │            │
                 │                  ▼            │
                 │         ┌──────────────────┐  │
                 │         │ Parse & Normalize│  │
                 │         │ Results (Artist  │  │
                 │         │ Disambiguation)  │  │
                 │         └────────┬─────────┘  │
                 │                  │            │
                 │                  ▼            │
                 │         ┌──────────────────┐  │
                 │         │ Store in Cache   │  │
                 │         └────────┬─────────┘  │
                 │                  │            │
                 └──────────┬───────┘            │
                            │                    │
                            ▼                    │
                   ┌──────────────────┐          │
                   │ Return JSON      │          │
                   │ to Android App   │          │
                   └──────┬───────────┘          │
                          │                      │
                          ▼                      │
                   ┌──────────────────┐          │
                   │ Display Results  │          │
                   │ in List View     │          │
                   └──────────────────┘          │
```

### Daily Mix Generation Flow

```
┌──────────────┐
│ User Taps    │
│ "Daily Mix"  │
└──────┬───────┘
       │
       ▼
┌─────────────────────────────┐
│ Android App requests:       │
│ GET /playlists/daily-mix    │
└──────┬──────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Backend Retrieves:           │
│ • User's listening history   │
│ • Favorite artists/genres    │
│ • Listening preferences      │
└──────┬───────────────────────┘
       │
       ▼
┌────────────────────────────────────┐
│ DailyMixService Processing:        │
│                                    │
│ 1. Genre Balance Algorithm         │
│    - Mix user's top genres         │
│    - Add diverse secondary genres  │
│                                    │
│ 2. Mood Mix Selection              │
│    - Extract user listening mood   │
│    - Match similar mood tracks     │
│                                    │
│ 3. Collaborative Filtering Layer   │
│    - Similar users' picks          │
│    - Artist associations           │
└──────┬───────────────────────────────┘
       │
       ▼
┌─────────────────────────────┐
│ Fetch Track Metadata from:  │
│ • Firestore (user prefs)    │
│ • Cache (popular tracks)    │
│ • JioSaavn (stream URLs)    │
└──────┬──────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ Assemble Playlist:         │
│ • Sort by relevance        │
│ • Randomize with algo      │
│ • 50-100 tracks            │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ Store in Firestore:        │
│ - playlists/{id}           │
│ - playlist_tracks/{id}     │
└──────┬─────────────────────┘
       │
       ▼
┌────────────────────────────┐
│ Return JSON response       │
│ with playlist + tracks     │
└──────┬─────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ Android App renders:         │
│ • Playlist name: "Daily Mix" │
│ • Track list with artists    │
│ • Ready to play              │
└──────────────────────────────┘
```

### Streaming Flow

```
┌──────────────────┐
│ User selects     │
│ a track to play  │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────┐
│ Android App sends:           │
│ GET /tracks/stream/{id}      │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Backend:                     │
│ • Verify user auth token     │
│ • Check user subscription    │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Fetch Track Info:            │
│ • Query Firestore/Cache      │
│ • Get JioSaavn stream metadata
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ StreamService:               │
│ • Get direct stream URL      │
│   from JioSaavn API          │
│ • Validate stream quality    │
│ • Get playback metadata      │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Return streaming response:   │
│ {                            │
│   "url": "https://...",      │
│   "duration": 240,           │
│   "quality": "320kbps",      │
│   "codec": "mp3"             │
│ }                            │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Android Exoplayer receives:  │
│ • Stream URL                 │
│ • Metadata                   │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Log listening event:         │
│ POST /user/listening-history │
├─────────────────────────────┬┘
│ Backend updates:            │
│ • User listening history    │
│ • Track play count          │
│ • User profile/preferences  │
│ • Artist popularity metrics │
└─────────────────────────────┘
```

---

## Authentication Flow

```
┌──────────────────┐
│ User enters      │
│ credentials      │
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────┐
│ Android App sends:           │
│ POST /auth/login             │
│ {email, password}            │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Backend validates with:      │
│ Firebase Auth                │
│ • Send credentials to FB     │
│ • Receive ID token           │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Backend creates JWT:         │
│ • Encode user ID, roles      │
│ • Set expiration (24h)       │
│ • Sign with secret           │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Return auth response:        │
│ {                            │
│   "token": "jwt...",         │
│   "user": {...}              │
│ }                            │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Android App stores JWT       │
│ • SharedPreferences          │
│ • Add to all API headers     │
└────────┬─────────────────────┘
         │
         ▼
┌──────────────────────────────┐
│ Future API requests:         │
│ Authorization: Bearer {token}│
└──────────────────────────────┘
```

---

## Architecture Components Explanation

### 1. **Presentation Layer (Android Client)**

**Responsibilities:**
- User interface rendering
- User input handling
- Local app state management
- HTTP request initiation

**Key Components:**
- **Activities/Fragments**: Screen logic
- **ViewModels**: UI state & lifecycle awareness
- **Retrofit Client**: HTTP requests to backend
- **Firebase Auth SDK**: Authentication integration

**Technologies:**
- Kotlin, Jetpack Compose/XML layouts
- MVVM architecture
- Retrofit for REST calls
- Firebase Authentication SDK

---

### 2. **Application Layer (Flask Backend)**

**Responsibilities:**
- Request routing and validation
- Business logic execution
- Data orchestration
- Response formatting

**Sub-layers:**

#### a) **API Controllers/Routes**
```
- POST /auth/login, /auth/register, /auth/logout
- GET /user/profile, POST /user/update
- GET /search?q=query
- GET /playlists/recommendations
- GET /playlists/daily-mix
- GET /playlists/{id}/tracks
- GET /tracks/stream/{id}
- POST /user/listening-history
```

#### b) **Business Logic Services**

**RecommendationService**
- Implements collaborative filtering
- Maintains user-item interaction matrix
- Calculates similarity scores
- Returns top-N recommendations

**DailyMixService**
- Extracts user listening patterns
- Applies genre balancing algorithm
- Implements mood-based selection
- Generates personalized mix

**StreamService**
- Fetches track metadata
- Interfaces with JioSaavn for URLs
- Validates stream availability
- Returns playback information

**SearchService**
- Parses user queries
- Interfaces with JioSaavn search
- Normalizes results
- Applies filtering

**UserProfileService**
- Manages listening history
- Extracts user preferences
- Calculates user-specific metrics
- Stores/retrieves profile data

**ArtistNormalizationService**
- Resolves artist name variations
- Handles duplicate artist entries
- Maintains canonical artist forms

**CacheManager**
- Implements local caching strategy
- Cache invalidation logic
- Fallback mechanisms

#### c) **Data Access Layer**

**Firestore Integration**
- Collections: `users`, `playlists`, `tracks`, `recommendations`, `listening_history`
- Real-time sync capability
- Query optimization

**Cache Layer**
- In-memory cache with TTL
- Recent search results
- Popular tracks
- User preferences

---

### 3. **External Services Layer**

#### a) **JioSaavn API**
**Provides:**
- Song search & metadata
- Playable stream URLs
- Album & artist information
- Playlist data
- Charts & trending tracks

**Integration Pattern:**
- Backend acts as proxy/aggregator
- Caches responses to reduce API calls
- Error handling for unavailable tracks

#### b) **Firebase Services**
**Components:**
1. **Firebase Authentication**
   - User registration/login
   - Password recovery
   - Session management
   - ID token generation

2. **Firestore Database**
   - Primary data store
   - User documents
   - Playlist records
   - Real-time updates

3. **Firebase Analytics** (optional)
   - User event tracking
   - Feature usage metrics

---

## Security Architecture

```
┌─────────────────────────────────────────┐
│         Security Layers                 │
├─────────────────────────────────────────┤
│ 1. Transport: HTTPS/TLS                 │
│    └─ All API calls encrypted           │
│                                         │
│ 2. Authentication: Firebase Auth + JWT  │
│    └─ JWT tokens with expiration        │
│    └─ Firebase ID token verification    │
│                                         │
│ 3. Authorization: JWT Claims            │
│    └─ Role-based access control         │
│    └─ Subscription verification         │
│                                         │
│ 4. Data Protection: Firestore Rules     │
│    └─ User-specific reads               │
│    └─ Owner-only writes                 │
│                                         │
│ 5. Input Validation: Request Guards     │
│    └─ Query sanitization                │
│    └─ Rate limiting                     │
└─────────────────────────────────────────┘
```

---

## Deployment Architecture

```
┌──────────────────────────────────────────┐
│        Production Deployment             │
├──────────────────────────────────────────┤
│                                          │
│  ┌──────────────────────────────────┐   │
│  │   Google Play Store              │   │
│  │   (Android App Distribution)     │   │
│  └──────────┬───────────────────────┘   │
│             │                            │
│             ▼                            │
│  ┌──────────────────────────────────┐   │
│  │   Users' Android Devices         │   │
│  └──────────┬───────────────────────┘   │
│             │                            │
│   HTTPS ───▼────────────────────────┐   │
│             │                        │   │
│             ▼                        │   │
│  ┌──────────────────────────────────┐   │
│  │   Render.com (Flask Backend)     │   │
│  │   • Environment: Python 3.9+     │   │
│  │   • Server: Gunicorn             │   │
│  │   • Instance Type: Standard      │   │
│  │   • Auto-scaling enabled         │   │
│  └──────────┬───────────────────────┘   │
│             │                            │
│             ├──────────────────┬────────┤
│             │                  │        │
│             ▼                  ▼        │
│  ┌─────────────────┐  ┌──────────────┐ │
│  │  Firebase       │  │  JioSaavn    │ │
│  │  Firestore      │  │  API         │ │
│  │  Auth           │  │              │ │
│  └─────────────────┘  └──────────────┘ │
│                                        │
└──────────────────────────────────────────┘
```

---

## Performance Considerations

```
┌─────────────────────────────────────────┐
│      Performance Optimization           │
├─────────────────────────────────────────┤
│                                         │
│  CACHING STRATEGY:                      │
│  • Redis/In-memory: Top 100 tracks      │
│  • TTL: 6 hours for search results      │
│  • User cache: 24 hours                 │
│  • Cache invalidation on new tracks     │
│                                         │
│  API OPTIMIZATION:                      │
│  • Request batching                     │
│  • Lazy loading of playlists            │
│  • Pagination (50 tracks/page)          │
│  • Async image loading (album art)      │
│                                         │
│  DATABASE OPTIMIZATION:                 │
│  • Firestore indexes on queries         │
│  • Denormalization for hot data         │
│  • Batch writes for history             │
│                                         │
│  ANDROID OPTIMIZATION:                  │
│  • RecyclerView pagination              │
│  • Background sync for offline support  │
│  • Efficient image caching              │
│  • Battery optimization (min API calls) │
│                                         │
└─────────────────────────────────────────┘
```

---

## Scalability Architecture

```
As user base grows:

1. HORIZONTAL SCALING
   • Multiple Flask instances on Render
   • Load balancing (automatic via Render)
   • Stateless API design

2. DATABASE SCALING
   • Firestore auto-scales reads/writes
   • Sharding user data if needed
   • Regional replication for latency

3. CACHE SCALING
   • Upgrade to Redis Cluster
   • Distributed cache across regions
   • Cache invalidation protocol

4. EXTERNAL API HANDLING
   • Connection pooling to JioSaavn
   • Rate limit handling
   • Fallback/retry logic
   • Queue system for bulk requests
```

---

## Summary

This clean architecture provides:

✅ **Separation of Concerns**: Each layer has distinct responsibilities

✅ **Testability**: Services can be unit tested independently

✅ **Maintainability**: Changes isolated to specific layers

✅ **Scalability**: Horizontal scaling at backend layer

✅ **Security**: Multi-layer authentication & authorization

✅ **Performance**: Intelligent caching & optimization

✅ **Flexibility**: Easy to swap implementations (e.g., different auth provider)
