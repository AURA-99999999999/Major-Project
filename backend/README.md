# AURA Backend

Flask backend for AURA music streaming and recommendations.

## Quick Start

```bash
pip install -r requirements.txt
python app.py
```

Server: http://localhost:5000/api/

## Tests

```bash
python -m pytest tests
```

If you are not using pytest, you can run individual test scripts in tests/.

## Collaborative Filtering (CF) Feature

The CF engine provides personalized recommendations by identifying users with similar music taste and suggesting tracks they enjoy.

### How CF Works

1. **User Taste Vector Building**: Analyzes user play history to create weighted artist preference vectors (0-1 scale)
   - Factors: Artist play count, recency boost for recent plays
   - Uses Firestore user play history data

2. **Similar User Detection**: Uses **Adaptive Cosine Similarity** that scales with user base
   
   **Cosine Similarity Algorithm** (`collaborative_service.py:_cosine_similarity()`)
   - Computes similarity between two taste vectors (artist → weight mappings)
   - Formula: `similarity = dot_product / (magnitude_a × magnitude_b)`
   - Steps:
     1. Find common artists between both users' taste vectors
     2. Calculate dot product: `Σ(vec_a[artist] × vec_b[artist])` for common artists
     3. Compute magnitudes: `√(Σ(value²))` for each vector
     4. Divide dot product by product of magnitudes, clamp to [0, 1]
   - Example:
     ```
     User A taste: {"Drake": 0.9, "The Weeknd": 0.7, "J.Cole": 0.5}
     User B taste: {"Drake": 0.8, "The Weeknd": 0.6, "Kanye": 0.4}
     
     Common: Drake, The Weeknd
     Dot product: (0.9×0.8) + (0.7×0.6) = 1.14
     Magnitude_A: √(0.81 + 0.49 + 0.25) = 1.209
     Magnitude_B: √(0.64 + 0.36 + 0.16) = 1.077
     Cosine Similarity: 1.14 / (1.209 × 1.077) = 0.875
     ```
   
   **Adaptive Thresholding** (scales with user growth):
   - **< 20 users (TINY)**: Threshold = 0.10 (very relaxed, ensures results)
   - **20-50 users (SMALL)**: Threshold = 0.15 (relaxed for usability)
   - **50-100 users (MEDIUM)**: Threshold = 0.25 (standard quality)
   - **100+ users (LARGE)**: Threshold = 0.35 (strict for precision)
   - **Emergency fallback**: 0.05 (if no matches found)
   
   **Multi-Metric Scoring**:
   - Primary: Cosine similarity (vector-based)
   - Secondary: Jaccard similarity (set overlap)
   - Tertiary: Soft similarity (weighted overlap)
   - **Adaptive boost**: 1.2x multiplier for small datasets with multiple shared artists
   - Uses highest score from all three metrics
   
   **Relaxed Artist Overlap** (adaptive):
   - TINY/SMALL datasets: Accept users with just **1 shared artist**
   - MEDIUM/LARGE datasets: Require **2+ shared artists**
   - Prioritizes discovery over strict matching for new platforms

3. **Recommendation Generation with Diversity Engine**: Multi-strategy system with artist variety enforcement
   
   **Candidate Pool Expansion** (Step 1):
   - Collects tracks from similar users: liked songs, frequent plays, playlist tracks
   - **Pre-filter limit**: Max 5 songs per artist in candidate pool
   - Prevents single-artist dominance before ranking
   
   **Artist Frequency Penalty** (Step 2):
   - Applied during scoring to encourage variety
   - Formula: `adjusted_score = base_score × (1 / (1 + artist_count × 0.35))`
   - Effect:
     - 1st song from artist: 100% score (no penalty)
     - 2nd song: ~74% score (slight penalty)
     - 3rd song: ~59% score (stronger penalty)
   - Maintains relevance while promoting diversity
   
   **Round-Robin Artist Selection** (Step 3-4):
   - Groups tracks by primary artist
   - Sorts tracks within each artist by adjusted score
   - Selects in rotation across artists (Artist A → B → C → A...)
   - **Adaptive limits**:
     - < 20 users: Max 2 songs per artist
     - 20+ users: Max 1 song per artist
   
   **Diversity Safety Check** (Step 5):
   - **Minimum targets**:
     - < 50 users: At least 4 unique artists
     - 50+ users: At least 8 unique artists
   - If below threshold: Expands with tracks from unseen artists
   - Detects and warns about single-artist dominance (> 50% from one artist)
   
   **Final Scoring**: `similarity_score × play_count × recency_boost × artist_penalty`
   
   **Comprehensive Logging**:
   - Candidate artist count
   - Dominant artist frequency
   - Unique artists before/after ranking
   - Final artist distribution
   - Single-artist dominance warnings
   
   **Multi-Strategy Fallback System**:
   1. **Primary**: Aggregate plays from top similar users
   2. **Strategy 1**: If < 5 results → expand with tracks from shared artists
   3. **Strategy 2**: If still < 5 → cold-start fallback (popular tracks)
   4. **Emergency**: For < 20 users → immediate cold-start with artist affinity

### Adaptive Scaling Behavior

The CF engine **automatically adjusts** based on total active users:

| User Count | Threshold | Artist Overlap | Similar Users | Behavior |
|------------|-----------|----------------|---------------|----------|
| **< 20** (TINY) | 0.10 | 1+ artists | Top 3 | Very relaxed, prioritize results |
| **20-50** (SMALL) | 0.15 | 1+ artists | Top 3-5 | Relaxed, discovery-focused |
| **50-100** (MEDIUM) | 0.25 | 2+ artists | Top 5-10 | Standard quality filtering |
| **100+** (LARGE) | 0.35 | 2+ artists | Top 10 | Strict, precision-focused |

**Profile Requirements** (adaptive):
- **TINY/SMALL**: 3 plays OR 1 unique artist
- **MEDIUM/LARGE**: 5 plays OR 2 unique artists

### Guarantees & Safeguards

✅ **Minimum results**: Always returns at least 5 tracks (never empty)  
✅ **No over-filtering**: Diversity constraints skipped if candidates < 30  
✅ **Artist diversity**: 2-3 songs per artist max (adaptive based on dataset size)  
✅ **Cold-start handling**: New users get popular tracks from similar taste profiles  
✅ **Firestore efficiency**: 30-min user profile cache, 3-hour similarity cache

### Performance Optimizations

- **User Profiles Cache**: 30 minutes (artist taste vectors)
- **Similarity Cache**: 3 hours (pre-computed similar user pairs)
- **Recommendations Cache**: 30 minutes (final recommendations)
- **Cold-start Handling**: Returns trending tracks for new users with insufficient play history

### API Integration

- Endpoint: `GET /api/home`
- Response field: `collaborative` (list of track objects)
- Section title: "Users like you also listen to"

### Handling Edge Cases

- Insufficient data → defaults to trending tracks
- No similar users found → uses artist affinity clustering
- User with <5 plays → skipped from CF calculations

## Notes

Avoid auto-generating markdown documentation files in this directory. Keep documentation limited to this README unless explicitly required.
