# Recommendation System - Integration Guide

## Quick Start for Frontend & Android Teams

### Frontend Integration (React)

#### 1. Create a hook for recommendations

```javascript
// src/hooks/useRecommendations.js
import { useState, useEffect } from 'react';
import { api } from '../services/api';

export function useRecommendations(uid, limit = 20) {
  const [recommendations, setRecommendations] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (!uid) return;

    const fetchRecommendations = async () => {
      setLoading(true);
      try {
        const response = await api.get('/api/recommendations', {
          params: { uid, limit }
        });
        setRecommendations(response.data.results || []);
        setError(null);
      } catch (err) {
        console.error('Failed to fetch recommendations:', err);
        setError(err.message);
        setRecommendations([]);
      } finally {
        setLoading(false);
      }
    };

    fetchRecommendations();
  }, [uid, limit]);

  return { recommendations, loading, error };
}
```

#### 2. Use in a component

```javascript
// src/components/RecommendedForYou.jsx
import { useRecommendations } from '../hooks/useRecommendations';
import { SongCard } from './SongCard';

export function RecommendedForYou({ userId }) {
  const { recommendations, loading, error } = useRecommendations(userId, 20);

  if (loading) return <div>Loading recommendations...</div>;
  if (error) return <div>Couldn't load recommendations</div>;
  if (recommendations.length === 0) return null;

  return (
    <div className="recommended-section">
      <h2>Recommended For You</h2>
      <div className="song-grid">
        {recommendations.map(song => (
          <SongCard 
            key={song.videoId} 
            song={song}
            source="recommendations"
          />
        ))}
      </div>
    </div>
  );
}
```

#### 3. Display recommendations on home page

```javascript
// src/pages/Home.jsx
import { useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import { RecommendedForYou } from '../components/RecommendedForYou';

export function Home() {
  const { user } = useContext(AuthContext);

  return (
    <div className="home-page">
      {/* Other sections */}
      
      {user && (
        <RecommendedForYou userId={user.uid} />
      )}
    </div>
  );
}
```

---

### Android Integration (Retrofit/Kotlin)

#### 1. Add data model

```kotlin
// com/example/musicapp/models/RecommendationResponse.kt
import com.google.gson.annotations.SerializedName

data class RecommendationResponse(
    @SerializedName("count")
    val count: Int,
    
    @SerializedName("source")
    val source: String,
    
    @SerializedName("results")
    val results: List<RecommendedSong>
)

data class RecommendedSong(
    @SerializedName("videoId")
    val videoId: String,
    
    @SerializedName("title")
    val title: String,
    
    @SerializedName("artists")
    val artists: List<String>,
    
    @SerializedName("thumbnail")
    val thumbnail: String,
    
    @SerializedName("album")
    val album: String? = null
)
```

#### 2. Add API interface

```kotlin
// com/example/musicapp/network/MusicAPI.kt
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface MusicAPI {
    @GET("api/recommendations")
    fun getRecommendations(
        @Query("uid") userId: String,
        @Query("limit") limit: Int = 20
    ): Call<RecommendationResponse>
}
```

#### 3. Use in ViewModel

```kotlin
// com/example/musicapp/viewmodels/HomeViewModel.kt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import retrofit2.Callback
import retrofit2.Response

class HomeViewModel : ViewModel() {
    private val api = RetrofitClient.apiService
    
    val recommendations = MutableLiveData<RecommendationResponse>()
    val isLoading = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    fun loadRecommendations(userId: String, limit: Int = 20) {
        isLoading.value = true
        
        api.getRecommendations(userId, limit).enqueue(
            object : Callback<RecommendationResponse> {
                override fun onResponse(
                    call: Call<RecommendationResponse>,
                    response: Response<RecommendationResponse>
                ) {
                    isLoading.value = false
                    if (response.isSuccessful) {
                        recommendations.value = response.body()
                        error.value = null
                    } else {
                        error.value = "Failed to load recommendations"
                    }
                }

                override fun onFailure(
                    call: Call<RecommendationResponse>,
                    t: Throwable
                ) {
                    isLoading.value = false
                    error.value = t.message
                }
            }
        )
    }
}
```

#### 4. Use in Activity/Fragment

```kotlin
// com/example/musicapp/ui/HomeFragment.kt
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe

class HomeFragment : Fragment() {
    private val viewModel: HomeViewModel by viewModels()
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        viewModel.loadRecommendations(userId, limit = 20)
        
        viewModel.recommendations.observe(viewLifecycleOwner) { response ->
            // Update UI with recommendations
            displayRecommendations(response.results)
        }
        
        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                showErrorToast(errorMsg)
            }
        }
    }
    
    private fun displayRecommendations(songs: List<RecommendedSong>) {
        // Update RecyclerView or display similar
        recommendationAdapter.submitList(songs)
    }
}
```

---

## API Reference

### Endpoint
```
GET /api/recommendations
```

### Request Parameters
| Parameter | Type | Required | Default | Constraints |
|-----------|------|----------|---------|-------------|
| `uid` | string | ✅ Yes | - | User ID from Firebase |
| `limit` | int | ❌ No | 20 | Min: 1, Max: 100 |

### Response (Success - HTTP 200)
```json
{
    "count": 20,
    "source": "recommendation_engine",
    "results": [
        {
            "videoId": "MxIEUzBJn8U",
            "title": "Song Title",
            "artists": ["Artist 1", "Artist 2"],
            "thumbnail": "https://...",
            "album": "Album Name"
        }
    ]
}
```

### Response (Errors)

**Missing uid (HTTP 400)**
```json
{
    "error": "uid parameter is required"
}
```

**Invalid limit (HTTP 200 - with clamped value)**
```json
{
    "count": 0,
    "source": "recommendation_engine",
    "results": []
}
```

**Server error (HTTP 500 with graceful fallback)**
```json
{
    "count": 0,
    "source": "recommendation_engine",
    "results": []
}
```

---

## Response Fields Explanation

| Field | Type | Description |
|-------|------|-------------|
| `videoId` | string | YouTube Music video ID (use for streaming) |
| `title` | string | Song title |
| `artists` | array | List of artist names |
| `thumbnail` | string | Album art URL |
| `album` | string | Album name (may be null) |

---

## Streaming Integration

After getting recommendations, stream using the existing `/api/song` endpoint:

### Frontend
```javascript
async function playSong(videoId) {
  const response = await api.get(`/api/song/${videoId}`);
  const { url, title, artist } = response.data;
  
  // Play audio
  audioPlayer.src = url;
  audioPlayer.play();
}
```

### Android
```kotlin
api.getSongDetails(videoId).enqueue(
    object : Callback<SongDetailsResponse> {
        override fun onResponse(
            call: Call<SongDetailsResponse>,
            response: Response<SongDetailsResponse>
        ) {
            val streamingUrl = response.body()?.url
            mediaPlayer.setDataSource(streamingUrl)
            mediaPlayer.prepare()
            mediaPlayer.start()
        }

        override fun onFailure(call: Call<SongDetailsResponse>, t: Throwable) {
            // Handle error
        }
    }
)
```

---

## Testing

### Using cURL
```bash
# Get recommendations for user
curl "http://localhost:5000/api/recommendations?uid=user_123&limit=10"

# Get more recommendations
curl "http://localhost:5000/api/recommendations?uid=user_123&limit=50"
```

### Using Postman
1. Create GET request to `http://localhost:5000/api/recommendations`
2. Add params: 
   - `uid` = user_123
   - `limit` = 20
3. Send and check response

### Frontend Testing
```javascript
// In browser console
fetch('http://localhost:5000/api/recommendations?uid=user_123&limit=10')
  .then(r => r.json())
  .then(data => console.log(data))
```

---

## Performance Tips

1. **Cache recommendations** - Show cached results while background refresh happens
2. **Pagination** - Request 20-30 at a time, load more on scroll
3. **Debouncing** - Don't fetch on every route change
4. **User-specific caching** - Cache per uid for 30 minutes
5. **Error handling** - Show cached results on failure

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Always getting empty results | User has < 5 plays. System uses trending (may be slow). |
| Getting 400 error | Check `uid` parameter is present and valid. |
| Getting 500 error | Server error. Check server logs. System returns empty results as fallback. |
| Slow response (5+ sec) | First request is slow due to API calls. Results are cached (100ms on 2nd request). |
| Missing thumbnails | Album art URL may be broken. Use placeholder image. |

---

## Security Notes

✅ **Safe to expose endpoint** - No credentials in responses  
✅ **Firestore rules enforce isolation** - uid-based access control  
✅ **No streaming URLs** - Use separate `/api/song` endpoint  
✅ **Input validated** - limit clamped to 1-100  

---

## Backend Monitoring Checklist

- [ ] Check server logs for `RecommendationService` requests
- [ ] Monitor cache hit rates (should be >80% on repeat requests)
- [ ] Track API call frequency to YTMusic
- [ ] Monitor response times (should be <3 sec first, <200ms cached)
- [ ] Alert on any 500 errors from recommendation endpoint
