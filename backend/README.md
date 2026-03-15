# Aura Backend

Flask backend for search, playback metadata, playlists, recommendations, and user data.

## Run

```bash
pip install -r requirements.txt
python app.py
```

Backend base URL: `http://localhost:5000/api`

## Main responsibilities

- Serve music search and item detail endpoints
- Build home, recommendation, and daily mix responses
- Read and write user state in Firestore
- Support both Android and web clients

## Test

```bash
pytest -q
```

## Important files

- `app.py`: Flask routes
- `services/jiosaavn_service.py`: provider integration
- `services/personalized_recommender.py`: recommendation engine
- `services/collaborative_service.py`: collaborative filtering
- `services/daily_mix_service.py`: daily mixes
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
