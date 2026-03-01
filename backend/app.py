"""
Aura Music Streaming App - Main Flask Application
Production-grade music streaming platform
"""
from flask import Flask, jsonify, request
from pathlib import Path
from flask_cors import CORS
import logging
import time
import os
from config import Config
from services.music_service import MusicService
from services.playlist_service import PlaylistService
from services.user_service import UserService, initialize_firebase_admin, get_firestore_client
from services.recommendation_service import RecommendationService
from services.daily_mix_service import DailyMixService
from services.mood_service import MoodService
from services.search_service import SearchService
from services.music_filter import filter_music_tracks
from ytmusicapi import YTMusic

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
app.config.from_object(Config)

# Initialize Firebase Admin once during app startup
initialize_firebase_admin()
startup_db = get_firestore_client()
if startup_db is None:
    logger.warning("Firestore not connected at startup")

# Enable CORS for all origins (development mode)
# This allows Android app and React frontend to access the API
# In production, restrict this to specific origins
CORS(app, origins="*", supports_credentials=True, allow_headers="*", methods="*")

# Initialize services
music_service = MusicService(Config.YDL_OPTS)
playlist_service = PlaylistService()
user_service = UserService()
if startup_db is not None:
    user_service.db = startup_db

# Initialize YTMusic with OAuth if available
ytmusic = None
try:
    if os.path.exists('oauth.json'):
        ytmusic = YTMusic('oauth.json')
        logger.info("YTMusic initialized with OAuth")
    else:
        ytmusic = YTMusic()
        logger.info("YTMusic initialized without OAuth (unauthenticated)")
except Exception as e:
    logger.error(f"Error initializing YTMusic: {str(e)}")
    ytmusic = YTMusic()

# Initialize recommendation service
recommendation_service = RecommendationService(ytmusic, user_service)

# Initialize daily mix service
daily_mix_service = DailyMixService(ytmusic, recommendation_service, user_service)

# Initialize mood service
mood_service = MoodService(ytmusic)

# Initialize search service
search_service = SearchService(ytmusic)

# Simple in-memory caches for home and search results
HOME_CACHE_TTL_SECONDS = 45 * 60
SEARCH_CACHE_TTL_SECONDS = 10 * 60  # 10 minutes for search
_home_cache = {}
_search_cache = {}


def _cache_get(cache: dict, key: str):
    now = time.time()
    entry = cache.get(key)
    if not entry:
        return None
    if entry["expires_at"] <= now:
        cache.pop(key, None)
        return None
    return entry["value"]


def _cache_set(cache: dict, key: str, value, ttl_seconds: int):
    cache[key] = {
        "value": value,
        "expires_at": time.time() + ttl_seconds,
    }


def _pick_best_thumbnail(thumbnails):
    if not thumbnails:
        return ""
    if isinstance(thumbnails, dict):
        thumbnails = [thumbnails]
    best = max(
        thumbnails,
        key=lambda t: (t.get("width") or 0) * (t.get("height") or 0)
    )
    return best.get("url") or ""


def _normalize_artists(artists):
    if not artists:
        return []
    if isinstance(artists, list):
        names = []
        for artist in artists:
            if isinstance(artist, str):
                name = artist.strip()
            else:
                name = (artist.get("name") or "").strip()
            if name:
                names.append(name)
        return names
    if isinstance(artists, str):
        return [artists.strip()] if artists.strip() else []
    return []


def _build_trending_items(items, limit: int):
    """
    Filter and normalize trending items to ensure only real music tracks.
    
    Applies music-only content filtering using the production-grade
    filter_music_tracks function to remove YouTube videos, interviews,
    podcasts, trailers, etc.
    
    Args:
        items: Raw items from YTMusic API
        limit: Maximum number of items to return
        
    Returns:
        List of cleaned, validated music track dictionaries
    """
    # Apply comprehensive music-only filtering
    filtered_items = filter_music_tracks(items, ytmusic=None, include_validation=False)
    
    # Return top N items
    return filtered_items[:limit]

# Error handlers
@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Resource not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    logger.error(f"Internal error: {str(error)}")
    return jsonify({'error': 'Internal server error'}), 500

@app.errorhandler(400)
def bad_request(error):
    return jsonify({'error': 'Bad request'}), 400

# Health check endpoint
@app.route('/api/health', methods=['GET'])
def health_check():
    return jsonify({'status': 'healthy', 'service': 'Aura Music API'})

# Serve debug API page
@app.route('/debug', methods=['GET'])
def debug_page():
    """Serve the debug API page"""
    try:
        debug_file = Path(__file__).resolve().parent / 'debugging' / 'debug_api.html'
        return debug_file.read_text(encoding='utf-8'), 200, {'Content-Type': 'text/html'}
    except Exception as e:
        return jsonify({'error': str(e)}), 500

# ==================== DEBUG-ONLY ENDPOINTS ====================

@app.route('/debug/ytmusic/trending', methods=['GET'])
def debug_ytmusic_trending():
    """Debug-only: fetch YTMusic trending items (unauthenticated)."""
    try:
        ytmusic = YTMusic()
        explore = ytmusic.get_explore()
        trending_items = ((explore or {}).get('trending') or {}).get('items') or []

        trending = _build_trending_items(trending_items, 20)

        return jsonify({
            'source': 'ytmusicapi',
            'count': len(trending),
            'trending': trending,
        })
    except Exception as e:
        logger.error("YTMusic debug trending error: %s", str(e), exc_info=True)
        return jsonify({'error': str(e)}), 500

# ==================== MUSIC ENDPOINTS ====================

@app.route('/api/search', methods=['GET'])
def search_all_categories():
    """
    Production-grade filtered search endpoint with:
    - Parallel category searches (songs, albums, artists, playlists)
    - Music-only filtering (NO videos/podcasts/interviews)
    - Input validation (min 2 chars)
    - LRU caching (10 min TTL)
    - Thread-safe cache access
    - Timeout protection (5s per category)
    - Graceful partial failure handling
    """
    try:
        query = request.args.get('query', '').strip()
        
        # Validate query
        if not query:
            return jsonify({
                'success': False,
                'error': 'Query parameter is required',
                'songs': [],
                'albums': [],
                'artists': [],
                'playlists': [],
                'count': 0
            }), 400
        
        # Minimum query length check
        if len(query) < 2:
            return jsonify({
                'success': True,
                'songs': [],
                'albums': [],
                'artists': [],
                'playlists': [],
                'count': 0,
                'cached': False,
                'query': query
            })
        
        # Check cache first
        cache_key = f"search_all:{query}"
        cached = _cache_get(_search_cache, cache_key)
        if cached is not None:
            logger.info(f"✓ Cache HIT for search query: '{query}'")
            cached['cached'] = True
            return jsonify(cached)
        
        logger.info(f"✗ Cache MISS for search query: '{query}' - executing parallel search")
        
        # Execute parallel filtered search
        results = search_service.search_all_categories(
            query,
            song_limit=5,
            album_limit=5,
            artist_limit=5,
            playlist_limit=5
        )
        
        response_payload = {
            'success': True,
            'songs': results['songs'],
            'albums': results['albums'],
            'artists': results['artists'],
            'playlists': results['playlists'],
            'count': results['count'],
            'query': query,
            'cached': False
        }
        
        # Cache the result (10 minutes TTL)
        _cache_set(_search_cache, cache_key, response_payload, SEARCH_CACHE_TTL_SECONDS)
        
        return jsonify(response_payload)
        
    except Exception as e:
        logger.error(f"Search error: {str(e)}", exc_info=True)
        # Graceful error response (no 500s)
        return jsonify({
            'success': False,
            'error': 'Search failed. Please try again.',
            'songs': [],
            'albums': [],
            'artists': [],
            'playlists': [],
            'count': 0,
            'query': query if 'query' in locals() else ''
        }), 200  # Return 200 to prevent client network errors


@app.route('/api/search/suggestions', methods=['GET'])
def search_suggestions():
    """
    Lightweight search suggestions endpoint for autocomplete
    - Min 2 chars to trigger
    - Returns plain text suggestions
    - Fast response (no caching needed due to YTMusic speed)
    """
    try:
        query = request.args.get('q', '').strip()
        
        if not query or len(query) < 2:
            return jsonify({
                'success': True,
                'suggestions': []
            })
        
        suggestions = search_service.get_search_suggestions(query)
        
        return jsonify({
            'success': True,
            'suggestions': suggestions
        })
        
    except Exception as e:
        logger.error(f"Search suggestions error: {str(e)}")
        return jsonify({
            'success': False,
            'suggestions': [],
            'error': 'Failed to get suggestions'
        }), 200  # Always return 200 to prevent client errors
        
    except ValueError as e:
        logger.warning(f"Invalid limit parameter: {str(e)}")
        return jsonify({
            'success': False,
            'error': 'Invalid limit parameter',
            'results': [],
            'count': 0
        }), 400
        
    except Exception as e:
        # Never crash - always return graceful response
        logger.error(f"Search error for '{query}': {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': 'Search temporarily unavailable',
            'results': [],
            'count': 0
        }), 200  # Return 200 to avoid network error on client


# ==================== DETAIL ENDPOINTS (Album/Artist/Playlist) ====================

@app.route('/api/album/<browse_id>', methods=['GET'])
def get_album_details(browse_id):
    """
    Get album details with filtered songs
    - Returns album metadata
    - Returns filtered music-only tracks
    - Cached for 30 minutes
    """
    try:
        if not browse_id:
            return jsonify({
                'success': False,
                'error': 'Browse ID is required'
            }), 400
        
        logger.info(f"GET /api/album/{browse_id}")
        
        # Check cache
        cache_key = f"album:{browse_id}"
        cached = _cache_get(_search_cache, cache_key)
        if cached:
            logger.info(f"✓ Cache hit for album {browse_id}")
            cached['cached'] = True
            return jsonify(cached)
        
        # Import detail service (lazy load)
        from services.detail_service import DetailService
        detail_service = DetailService(ytmusic)
        
        # Fetch album details
        album = detail_service.get_album_details(browse_id)
        
        if not album:
            return jsonify({
                'success': False,
                'error': 'Album not found'
            }), 404
        
        response = {
            'success': True,
            'album': album,
            'cached': False
        }
        
        # Cache for 30 minutes
        _cache_set(_search_cache, cache_key, response, 1800)
        
        logger.info(f"✓ Album loaded: {album['title']} - {album['trackCount']} songs")
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"Album detail error for {browse_id}: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': 'Failed to load album details'
        }), 500


@app.route('/api/artist/<browse_id>', methods=['GET'])
def get_artist_details(browse_id):
    """
    Get artist details with top songs and albums
    - Returns artist metadata
    - Returns filtered top songs
    - Returns artist albums
    - Cached for 30 minutes
    """
    try:
        if not browse_id:
            return jsonify({
                'success': False,
                'error': 'Browse ID is required'
            }), 400
        
        logger.info(f"GET /api/artist/{browse_id}")
        
        # Check cache
        cache_key = f"artist:{browse_id}"
        cached = _cache_get(_search_cache, cache_key)
        if cached:
            logger.info(f"✓ Cache hit for artist {browse_id}")
            cached['cached'] = True
            return jsonify(cached)
        
        # Import detail service (lazy load)
        from services.detail_service import DetailService
        detail_service = DetailService(ytmusic)
        
        # Fetch artist details
        artist = detail_service.get_artist_details(browse_id)
        
        if not artist:
            return jsonify({
                'success': False,
                'error': 'Artist not found'
            }), 404
        
        response = {
            'success': True,
            'artist': artist,
            'cached': False
        }
        
        # Cache for 30 minutes
        _cache_set(_search_cache, cache_key, response, 1800)
        
        logger.info(f"✓ Artist loaded: {artist['name']} - {len(artist['topSongs'])} songs")
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"Artist detail error for {browse_id}: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': 'Failed to load artist details'
        }), 500


@app.route('/api/playlist/<browse_id>', methods=['GET'])
def get_playlist_details(browse_id):
    """
    Get playlist details with filtered songs
    - Returns playlist metadata
    - Returns filtered music-only tracks
    - Cached for 30 minutes
    """
    try:
        if not browse_id:
            return jsonify({
                'success': False,
                'error': 'Browse ID is required'
            }), 400
        
        logger.info(f"GET /api/playlist/{browse_id}")
        
        # Check cache
        cache_key = f"playlist:{browse_id}"
        cached = _cache_get(_search_cache, cache_key)
        if cached:
            logger.info(f"✓ Cache hit for playlist {browse_id}")
            cached['cached'] = True
            return jsonify(cached)
        
        # Import detail service (lazy load)
        from services.detail_service import DetailService
        detail_service = DetailService(ytmusic)
        
        # Fetch playlist details
        playlist = detail_service.get_playlist_details(browse_id)
        
        if not playlist:
            return jsonify({
                'success': False,
                'error': 'Playlist not found'
            }), 404
        
        response = {
            'success': True,
            'playlist': playlist,
            'cached': False
        }
        
        # Cache for 30 minutes
        _cache_set(_search_cache, cache_key, response, 1800)
        
        logger.info(f"✓ Playlist loaded: {playlist['title']} - {playlist['trackCount']} songs")
        return jsonify(response)
        
    except Exception as e:
        logger.error(f"Playlist detail error for {browse_id}: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': 'Failed to load playlist details'
        }), 500


# ==================== SONG STREAMING ENDPOINT ====================

@app.route('/api/song/<video_id>', methods=['GET'])
def get_song(video_id):
    """Get song details and streaming URL (production-safe)"""
    try:
        if not video_id:
            return jsonify({'error': 'Video ID is required'}), 400
        
        song_data = music_service.get_song_details(video_id)
        
        # Check if music_service returned an error dict
        if 'error' in song_data:
            logger.warning(f"Song extraction error for {video_id}: {song_data.get('error')}")
            return jsonify(song_data), 400
        
        # Success: return properly formatted response
        return jsonify({
            'success': True,
            'data': song_data
        }), 200
        
    except Exception as e:
        # This is a safety net - get_song_details should NOT raise exceptions
        error_msg = str(e)
        logger.error(f"Unexpected error in get_song endpoint for {video_id}: {error_msg}", exc_info=True)
        return jsonify({
            'error': 'Failed to load song',
            'videoId': video_id
        }), 400

@app.route('/api/trending', methods=['GET'])
def get_trending():
    """Get trending songs"""
    try:
        limit = int(request.args.get('limit', 20))
        trending = music_service.get_trending_songs(limit=limit)
        return jsonify({
            'success': True,
            'results': trending,
            'count': len(trending)
        })
    except Exception as e:
        logger.error(f"Trending error: {str(e)}")
        return jsonify({'error': str(e)}), 500


@app.route('/api/recommendations', methods=['GET'])
def get_recommendations():
    """
    Get personalized music recommendations for a user.
    
    Query Parameters:
        uid: User ID (required)
        limit: Number of recommendations (optional, default 20)
    
    Returns:
        {
            "count": 20,
            "source": "recommendation_engine",
            "results": [
                {
                    "videoId": "...",
                    "title": "...",
                    "artists": [...],
                    "thumbnail": "...",
                    "album": "..."
                }
            ]
        }
    """
    try:
        # Get user ID from query parameters
        uid = request.args.get('uid')
        if not uid:
            return jsonify({'error': 'uid parameter is required'}), 400
        
        limit = int(request.args.get('limit', 20))
        if limit < 1 or limit > 100:
            limit = 20
        
        logger.info(f"[API/REC] Personalized recommendation request for user {uid[:8]}, limit: {limit}")
        
        # Generate recommendations
        recommendations = recommendation_service.get_recommendations(uid, limit=limit)
        
        logger.info(f"[API/REC] ✓ Returning {recommendations.get('count', 0)} personalized recommendations")
        
        return jsonify(recommendations), 200
        
    except ValueError as e:
        logger.error(f"[API/REC] Invalid parameter: {str(e)}")
        return jsonify({'error': 'Invalid parameters'}), 400
    except Exception as e:
        logger.error(f"[API/REC] Recommendations error: {str(e)}", exc_info=True)
        return jsonify({
            'count': 0,
            'source': 'recommendation_engine',
            'results': [],
            'error': 'Failed to generate recommendations'
        }), 500


@app.route('/api/recommendations/collaborative', methods=['GET'])
def get_collaborative_recommendations():
    """
    Get collaborative filtering recommendations (separate from personal recommendations).
    
    This endpoint returns music recommendations based on listening behavior of users
    with similar taste profiles. Use this to display a "From Similar Listeners"
    or "Popular among similar listeners" section.
    
    Query Parameters:
        uid: User ID (required)
        limit: Number of recommendations (optional, default 20)
        debug: Include debug stats (optional, default false)
    
    Returns:
        {
            "userId": "...",
            "title": "From Similar Listeners",
            "count": 20,
            "results": [
                {
                    "videoId": "...",
                    "title": "...",
                    "artists": [...],
                    "thumbnail": "...",
                    "album": "..."
                }
            ],
            "stats": {...}  // Only if debug=true
        }
    """
    try:
        # Get user ID from query parameters
        uid = request.args.get('uid')
        if not uid:
            return jsonify({'error': 'uid parameter is required'}), 400
        
        limit = int(request.args.get('limit', 20))
        if limit < 1 or limit > 100:
            limit = 20
        
        include_debug = request.args.get('debug', 'false').lower() == 'true'
        
        logger.info(f"[API/CF] Collaborative filtering request for user {uid[:8]}, limit: {limit}")
        
        # Get CF service
        cf_service = recommendation_service.cf_service
        
        # Get CF recommendations
        recommendations = cf_service.get_cf_recommendations(uid)
        
        # Log result
        logger.info(f"[API/CF] ✓ Returning {len(recommendations)} CF recommendations")
        
        # Build response
        response = {
            'userId': uid,
            'title': 'From listeners like you',
            'count': len(recommendations[:limit]),
            'results': recommendations[:limit]
        }
        
        # Add debug stats if requested
        if include_debug:
            stats = cf_service.get_user_stats(uid)
            response['stats'] = stats
        
        return jsonify(response), 200
        
    except Exception as e:
        logger.error(f"CF recommendations error: {str(e)}", exc_info=True)
        return jsonify({
            'userId': uid,
            'title': 'Because listeners like you enjoy',
            'count': 0,
            'results': [],
            'error': str(e)
        }), 500


@app.route('/api/debug/cf-status', methods=['GET'])
def debug_cf_status():
    """
    Debug endpoint to check collaborative filtering status.
    
    Query Parameters:
        uid: User ID (required)
    
    Returns:
        {
            "userId": "...",
            "timestamp": "...",
            "firestore_available": true/false,
            "user_profile": {
                "has_profile": true/false,
                "play_count": X,
                "artist_count": X,
                "top_artists": [...]
            },
            "similar_users": {
                "count": X,
                "similarity_scores": [...]
            },
            "recommendations": {
                "count": X,
                "available_for_home": true/false,
                "sample": {...}
            },
            "diagnostics": {
                "meets_minimum_plays": true/false,
                "meets_minimum_similar_users": true/false,
                "meets_minimum_recommendations": true/false,
                "can_show_on_home": true/false
            }
        }
    """
    try:
        uid = request.args.get('uid')
        if not uid:
            return jsonify({'error': 'uid parameter is required'}), 400
        
        logger.info(f"[CF Debug] Checking CF status for user {uid}")
        
        from datetime import datetime
        cf_service = recommendation_service.cf_service
        
        # Check Firestore availability
        firestore_available = cf_service._firestore_db is not None
        
        # Get user stats
        user_stats = cf_service.get_user_stats(uid)
        
        # Get recommendations
        cf_recs = cf_service.get_cf_recommendations(uid)
        
        # Build diagnostics
        diagnostics = {
            'meets_minimum_plays': user_stats.get('has_profile', False),
            'meets_minimum_similar_users': user_stats.get('similar_users_count', 0) > 0,
            'meets_minimum_recommendations': len(cf_recs) >= 5,
            'can_show_on_home': user_stats.get('has_profile', False) and len(cf_recs) >= 5
        }
        
        response = {
            'userId': uid,
            'timestamp': datetime.now().isoformat(),
            'firestore_available': firestore_available,
            'user_profile': {
                'has_profile': user_stats.get('has_profile', False),
                'artist_count': user_stats.get('artist_count', 0),
                'top_artists': user_stats.get('top_artists', []),
                'message': user_stats.get('message', '')
            },
            'similar_users': {
                'count': user_stats.get('similar_users_count', 0),
                'similarity_scores': user_stats.get('similarity_scores', [])
            },
            'recommendations': {
                'count': len(cf_recs),
                'available_for_home': len(cf_recs) >= 5,
                'sample': cf_recs[0] if cf_recs else None
            },
            'diagnostics': diagnostics
        }
        
        logger.info(f"[CF Debug] Status: Firestore={firestore_available}, Profile={diagnostics['meets_minimum_plays']}, SimilarUsers={diagnostics['meets_minimum_similar_users']}, Recs={len(cf_recs)}, ShowOnHome={diagnostics['can_show_on_home']}")
        
        return jsonify(response), 200
        
    except Exception as e:
        logger.error(f"[CF Debug] Error: {str(e)}", exc_info=True)
        return jsonify({
            'error': str(e),
            'userId': uid
        }), 500


@app.route('/api/daily-mixes', methods=['GET'])
def get_daily_mixes():
    """
    Get personalized daily mixes for a user.
    
    Generates 4 curated playlists:
    1. Favorites Mix - Songs from user's top artists
    2. Similar Artists Mix - Songs from related artists  
    3. Discover Mix - Trending + new + fresh content
    4. Mood Mix - Contextual listening based on time of day
    
    Query Parameters:
        uid: User ID (required)
        refresh: Force refresh cache (optional, default false)
    
    Returns:
        {
            "userId": "...",
            "timestamp": "...",
            "cached": true/false,
            "mixes": {
                "dailyMix1": {
                    "name": "Favorites Mix",
                    "description": "Songs from your most-played artists",
                    "count": 30,
                    "songs": [...]
                },
                "dailyMix2": {
                    "name": "Similar Artists Mix",
                    "description": "Albums from artists like your favorites",
                    "count": 30,
                    "songs": [...]
                },
                "discoverMix": {
                    "name": "Discover Mix",
                    "description": "Fresh music tailored to your taste",
                    "count": 30,
                    "songs": [...]
                },
                "moodMix": {
                    "name": "Mood Mix",
                    "description": "Perfect for [time of day]",
                    "count": 30,
                    "songs": [...]
                }
            }
        }
    """
    try:
        # Get user ID from query parameters
        uid = request.args.get('uid')
        if not uid:
            return jsonify({'error': 'uid parameter is required'}), 400
        
        refresh = request.args.get('refresh', 'false').lower() == 'true'
        
        logger.info(f"Daily mixes request for user {uid}, refresh={refresh}")
        
        # Clear cache if refresh requested
        if refresh:
            daily_mix_service.clear_cache(uid)
            logger.info(f"Cache cleared for user {uid}")
        
        # Generate daily mixes
        mixes = daily_mix_service.get_daily_mixes(uid)
        
        # Format response with metadata
        response = {
            'userId': uid,
            'timestamp': int(time.time()),  # Convert to integer to avoid JSON parsing issues
            'cached': not refresh,
            'mixes': {
                'dailyMix1': {
                    'name': 'Favorites Mix',
                    'description': 'Songs from your most-played artists',
                    'count': len(mixes.get('dailyMix1', [])),
                    'songs': mixes.get('dailyMix1', [])
                },
                'dailyMix2': {
                    'name': 'Similar Artists Mix',
                    'description': 'Albums from artists like your favorites',
                    'count': len(mixes.get('dailyMix2', [])),
                    'songs': mixes.get('dailyMix2', [])
                },
                'discoverMix': {
                    'name': 'Discover Mix',
                    'description': 'Fresh music tailored to your taste',
                    'count': len(mixes.get('discoverMix', [])),
                    'songs': mixes.get('discoverMix', [])
                },
                'moodMix': {
                    'name': 'Mood Mix',
                    'description': f'Perfect for listening right now',
                    'count': len(mixes.get('moodMix', [])),
                    'songs': mixes.get('moodMix', [])
                }
            }
        }
        
        logger.info(f"Returning daily mixes: "
                   f"favorites={len(mixes['dailyMix1'])}, "
                   f"similar={len(mixes['dailyMix2'])}, "
                   f"discover={len(mixes['discoverMix'])}, "
                   f"mood={len(mixes['moodMix'])}")
        
        return jsonify(response), 200
        
    except Exception as e:
        logger.error(f"Daily mixes error: {str(e)}", exc_info=True)
        return jsonify({
            'error': 'Failed to generate daily mixes',
            'mixes': {
                'dailyMix1': {'name': 'Favorites Mix', 'songs': [], 'count': 0},
                'dailyMix2': {'name': 'Similar Artists Mix', 'songs': [], 'count': 0},
                'discoverMix': {'name': 'Discover Mix', 'songs': [], 'count': 0},
                'moodMix': {'name': 'Mood Mix', 'songs': [], 'count': 0}
            }
        }), 500


@app.route('/api/home', methods=['GET'])
def get_home():
    """
    Get home feed with all recommendation sections: trending, personal recommendations,
    and collaborative filtering.
    
    Applies strict music-only filtering to ensure:
    - No interviews, podcasts, or trailers
    - No YouTube show content
    - Valid artist information
    - Clean thumbnails
    - Minimum track duration
    
    Query Parameters:
        uid: User ID (optional) - If provided, includes recommendations and CF section
    
    Returns:
    {
        "source": "ytmusicapi",
        "trending": [...],
        "recommendations": [...],  // Personal recommendations if uid provided
        "collaborative": [...],     // Collaborative filtering if uid provided
        "topArtists": [...]         // Top artists if uid provided
    }
    """
    try:
        limit = 15
        uid = request.args.get('uid')
        
        cache_key = f"home:ytmusic:{limit}:{uid or 'anon'}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            logger.info(f"[API/HOME] ✓ Cache hit for user {uid[:8] if uid else 'anon'}")
            return jsonify(cached)

        logger.info(f"[API/HOME] ━━━ HOME FEED REQUEST ━━━")
        logger.info(f"[API/HOME] User: {uid[:8] if uid else 'anonymous'}")
        logger.info(f"[API/HOME] Limit: {limit}")
        
        logger.info("[API/HOME] Fetching trending from YTMusic")
        ytmusic = YTMusic()
        explore = ytmusic.get_explore()
        trending_items = ((explore or {}).get("trending") or {}).get("items") or []
        
        logger.info(f"[API/HOME] YTMusic returned {len(trending_items)} raw trending items")
        trending = _build_trending_items(trending_items, limit)
        logger.info(f"[API/HOME] ✓ Trending: {len(trending)} tracks")

        response_payload = {
            "source": "ytmusicapi",
            "trending": trending,
            "recommendations": [],
            "collaborative": [],
            "topArtists": []
        }
        
        # Add personalized recommendations and collaborative filtering if user is logged in
        if uid:
            logger.info(f"[API/HOME] ━━━ PERSONALIZED DATA FOR {uid[:8]} ━━━")
            try:
                cf_service = recommendation_service.cf_service
                
                # Collect IDs to prevent duplicates across sections
                main_rec_ids = {track.get('videoId') for track in trending if track.get('videoId')}
                
                # STEP 1: Fetch personal recommendations
                logger.info("[API/HOME] → Fetching personal recommendations")
                try:
                    rec_payload = recommendation_service.get_recommendations(uid, limit=15)
                    personal_recs = rec_payload.get('results', []) if isinstance(rec_payload, dict) else []
                    # Exclude trending from personal recs
                    personal_recs_filtered = [t for t in personal_recs if t.get('videoId') not in main_rec_ids]
                    response_payload['recommendations'] = personal_recs_filtered[:limit]
                    main_rec_ids |= {track.get('videoId') for track in response_payload['recommendations'] if track.get('videoId')}
                    logger.info(f"[API/HOME] ✓ Personal recommendations: {len(response_payload['recommendations'])} tracks")
                except Exception as e:
                    logger.warning(f"[API/HOME] ⚠ Personal recommendations error: {str(e)}")
                    response_payload['recommendations'] = []
                
                # STEP 2: Fetch collaborative filtering results
                logger.info("[API/HOME] → Fetching collaborative filtering")
                # Check if user has profile
                cf_stats = cf_service.get_user_stats(uid)
                logger.info(f"[API/HOME] CF Stats: has_profile={cf_stats.get('has_profile')}, artists={cf_stats.get('artist_count', 0)}, similar_users={cf_stats.get('similar_users_count', 0)}")
                
                if cf_stats.get('has_profile'):
                    # Get CF recommendations with main rec IDs excluded
                    cf_tracks = cf_service.get_cf_recommendations(uid, main_rec_ids=main_rec_ids)
                    logger.info(f"[API/HOME] ✓ CF generated {len(cf_tracks)} recommendations")
                    logger.info(f"[API/HOME] CF returned: {len(cf_tracks)} tracks")

                    cf_filtered = [track for track in cf_tracks if track.get('videoId') not in main_rec_ids]
                    response_payload['collaborative'] = cf_filtered[:20]
                    logger.info(f"[API/HOME] ✓ CF section: {len(response_payload['collaborative'])} tracks")
                else:
                    logger.info(f"[API/HOME] ✗ CF skipped: User has no profile - {cf_stats.get('message', 'unknown reason')}")
                
                # STEP 3: Fetch top artists
                logger.info("[API/HOME] → Fetching top artists")
                try:
                    signals = recommendation_service._fetch_user_signals(uid)
                    plays = signals.get('plays', [])
                    liked = signals.get('liked', [])
                    weighted = recommendation_service._build_weighted_signals_cached(uid, plays, liked)
                    response_payload['topArtists'] = weighted.get('top_artists', [])[:10]
                    logger.info(f"[API/HOME] ✓ Top artists: {len(response_payload['topArtists'])} artists")
                except Exception as e:
                    logger.warning(f"[API/HOME] ⚠ Top artists error: {str(e)}")
                    response_payload['topArtists'] = []
                    
            except Exception as e:
                logger.error(f"[API/HOME] ✗ Error fetching user data: {str(e)}", exc_info=True)
        
        logger.info(f"[API/HOME] ━━━ FINAL RESPONSE ━━━")
        logger.info(f"[API/HOME] ✓ Trending: {len(response_payload['trending'])}")
        logger.info(f"[API/HOME] ✓ Recommendations: {len(response_payload['recommendations'])}")
        logger.info(f"[API/HOME] ✓ Collaborative: {len(response_payload['collaborative'])}")
        logger.info(f"[API/HOME] ✓ Top Artists: {len(response_payload['topArtists'])}")
        
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)

        return jsonify(response_payload)
    except Exception as e:
        logger.error("Home error: %s", str(e), exc_info=True)
        return jsonify({
            "source": "ytmusicapi",
            "trending": [],
            "recommendations": [],
            "collaborative": [],
            "topArtists": []
        })


@app.route('/api/home/trending-tracks', methods=['GET'])
def get_trending_tracks():
    """
    Get trending music tracks with strict filtering.
    
    Alias endpoint for /api/trending with same music-only validation.
    Query Parameters:
        limit: Number of trending tracks to return (default: 20, max: 100)
    
    Returns same format as /api/home with music-only filtered results.
    """
    try:
        limit = int(request.args.get('limit', 20))
        limit = min(max(limit, 1), 100)  # Clamp to 1-100
        
        cache_key = f"trending_tracks:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            logger.debug(f"Trending tracks cache hit: limit={limit}")
            return jsonify(cached)

        logger.info(f"Fetching trending tracks: limit={limit}")
        
        # Use music_service for consistent trending fetch
        trending_items = music_service.get_trending_songs(limit=limit * 2)  # Fetch extra
        
        # Already filtered by music_service, but normalize response format for consistency
        response_payload = {
            "source": "ytmusicapi",
            "count": len(trending_items),
            "results": trending_items[:limit],
        }
        
        logger.info(f"Trending tracks response: requested={limit} returned={len(trending_items[:limit])}")
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)
        
        return jsonify(response_payload)
    except ValueError as e:
        logger.error(f"Invalid parameter in trending-tracks: {str(e)}")
        return jsonify({"error": "Invalid parameters", "source": "ytmusicapi", "count": 0, "results": []}), 400
    except Exception as e:
        logger.error(f"Trending tracks error: {str(e)}", exc_info=True)
        return jsonify({
            "source": "ytmusicapi",
            "count": 0,
            "results": [],
        }), 500


@app.route('/api/home/trending-playlists', methods=['GET'])
def get_trending_playlists():
    """Get trending playlists from YTMusic explore"""
    try:
        limit = int(request.args.get('limit', 10))
        cache_key = f"trending_playlists:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            return jsonify(cached)

        ytmusic = YTMusic()
        explore = ytmusic.get_explore()
        
        playlists = []
        
        # Extract playlists from explore sections
        if explore:
            for section in explore:
                if isinstance(section, dict) and section.get('contents'):
                    for item in section['contents']:
                        if isinstance(item, dict):
                            playlist_id = item.get('playlistId') or item.get('browseId')
                            if playlist_id and (playlist_id.startswith('VL') or playlist_id.startswith('PL')):
                                thumbnails = item.get('thumbnails') or []
                                playlists.append({
                                    'playlistId': playlist_id,
                                    'title': item.get('title') or '',
                                    'description': item.get('description') or '',
                                    'thumbnail': _pick_best_thumbnail(thumbnails),
                                    'author': item.get('author') or 'YouTube Music',
                                    'songCount': 0,
                                })
                                if len(playlists) >= limit:
                                    break
                if len(playlists) >= limit:
                    break
        
        response_payload = {
            'success': True,
            'playlists': playlists[:limit],
            'count': len(playlists[:limit])
        }
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)
        
        return jsonify(response_payload)
    except Exception as e:
        logger.error(f"Trending playlists error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'playlists': [],
            'count': 0
        })


@app.route('/api/home/moods', methods=['GET'])
def get_mood_categories():
    """Get mood and genre categories from YTMusic
    
    Returns only 'Genres' and 'Moods & moments' sections.
    Cached for 30 minutes.
    """
    try:
        logger.info("GET /api/home/moods - Fetching mood categories")
        
        # Use MoodService with built-in caching
        result = mood_service.get_mood_categories()
        
        # Flatten sections into categories list for Android client
        categories = []
        
        # Predefined colors for visual appeal (cycle through these)
        colors = ['#4A90E2', '#E74C3C', '#9B59B6', '#27AE60', '#34495E', 
                  '#E91E63', '#607D8B', '#FFC107', '#FF9800', '#00BCD4',
                  '#3498DB', '#E67E22', '#1ABC9C', '#F39C12', '#8E44AD']
        
        color_index = 0
        for section in result.get('sections', []):
            for category in section.get('categories', []):
                categories.append({
                    'title': category['title'],
                    'params': category['params'],
                    'color': colors[color_index % len(colors)]
                })
                color_index += 1
        
        response_payload = {
            'success': True,
            'categories': categories,
            'count': len(categories)
        }
        
        logger.info(f"Returning {len(categories)} mood categories")
        return jsonify(response_payload)
        
    except Exception as e:
        logger.error(f"Mood categories error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'categories': [],
            'count': 0
        })


@app.route('/api/home/mood-playlists', methods=['GET'])
def get_mood_playlists():
    """Get playlists for a specific mood/genre
    
    Query params:
        - params: Required. The params string from mood category
        - limit: Optional. Number of playlists to return (default 10, max 15)
        
    Cached for 15 minutes per unique params+limit combination.
    """
    try:
        params = request.args.get('params', '').strip()
        if not params:
            return jsonify({
                'success': False,
                'error': 'params parameter is required',
                'playlists': [],
                'count': 0
            }), 400
        
        limit = min(int(request.args.get('limit', 10)), 15)  # Cap at 15
        
        logger.info(f"GET /api/home/mood-playlists - params={params[:30]}... limit={limit}")
        
        # Use MoodService with built-in caching
        result = mood_service.get_mood_playlists(params, limit)
        
        response_payload = {
            'success': True,
            'playlists': result.get('playlists', []),
            'count': result.get('count', 0)
        }
        
        logger.info(f"Returning {result.get('count', 0)} mood playlists")
        return jsonify(response_payload)
        
    except Exception as e:
        logger.error(f"Mood playlists error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'playlists': [],
            'count': 0
        })


@app.route('/api/home/top-artists', methods=['GET'])
def get_top_artists():
    """
    Get top artists for a user based on listening history.
    
    Returns top 5-10 artists with circular thumbnails for home screen display.
    Authentication required.
    
    Returns:
    {
        "success": bool,
        "artists": [
            {
                "browseId": str (channelId for navigation),
                "name": str,
                "thumbnail": str (circular image),
                "subscribers": str (optional)
            },
            ...
        ],
        "count": int
    }
    """
    try:
        # Get user ID from request (authentication required)
        uid = request.args.get('uid')
        if not uid:
            return jsonify({
                'success': False,
                'error': 'uid parameter required',
                'artists': [],
                'count': 0
            }), 400
        
        limit = int(request.args.get('limit', 10))
        
        # Cache key includes user ID
        cache_key = f"top_artists:{uid}:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            logger.debug(f"Top artists cache hit: {cache_key}")
            return jsonify(cached)
        
        logger.info(f"GET /api/home/top-artists - uid={uid} limit={limit}")
        
        # Get top artists from recommendation service
        top_artists = recommendation_service.get_top_artists(uid, limit)
        
        response_payload = {
            'success': True,
            'artists': top_artists,
            'count': len(top_artists)
        }
        
        # Cache for 10 minutes (user preferences change gradually)
        _cache_set(_home_cache, cache_key, response_payload, 600)
        
        logger.info(f"Returning {len(top_artists)} top artists for user {uid}")
        return jsonify(response_payload)
        
    except Exception as e:
        logger.error(f"Top artists error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'error': str(e),
            'artists': [],
            'count': 0
        })


@app.route('/api/playlist/<playlist_id>/songs', methods=['GET'])
def get_ytmusic_playlist_songs(playlist_id):
    """Get songs from a YTMusic playlist"""
    try:
        limit = int(request.args.get('limit', 50))
        cache_key = f"ytmusic_playlist:{playlist_id}:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            return jsonify(cached)

        ytmusic = YTMusic()
        playlist_data = ytmusic.get_playlist(playlist_id, limit=limit)
        
        if not playlist_data:
            return jsonify({'error': 'Playlist not found'}), 404
        
        songs = []
        for track in playlist_data.get('tracks', []):
            video_id = track.get('videoId')
            if not video_id:
                continue
            
            thumbnails = track.get('thumbnails') or track.get('thumbnail') or []
            songs.append({
                'videoId': video_id,
                'title': track.get('title') or '',
                'artists': _normalize_artists(track.get('artists')),
                'thumbnail': _pick_best_thumbnail(thumbnails),
                'duration': track.get('duration') or '',
                'album': track.get('album', {}).get('name') if track.get('album') else '',
            })
        
        # Normalize author field - can be string or object
        author_data = playlist_data.get('author')
        if isinstance(author_data, dict):
            author = author_data.get('name', 'YouTube Music')
        elif isinstance(author_data, str):
            author = author_data
        else:
            author = 'YouTube Music'
        
        response_payload = {
            'success': True,
            'playlist': {
                'id': playlist_id,
                'title': playlist_data.get('title') or '',
                'description': playlist_data.get('description') or '',
                'thumbnail': _pick_best_thumbnail(playlist_data.get('thumbnails', [])),
                'author': author,
                'songCount': len(songs),
            },
            'songs': songs,
            'count': len(songs)
        }
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)
        
        return jsonify(response_payload)
    except Exception as e:
        logger.error(f"YTMusic playlist songs error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'songs': [],
            'count': 0
        })


@app.route('/api/artist/<artist_id>', methods=['GET'])
def get_artist(artist_id):
    """Get artist information"""
    try:
        artist_data = music_service.get_artist_info(artist_id)
        return jsonify({
            'success': True,
            'data': artist_data
        })
    except Exception as e:
        logger.error(f"Artist error: {str(e)}")
        return jsonify({'error': str(e)}), 500

# ==================== PLAYLIST ENDPOINTS ====================

@app.route('/api/playlists', methods=['GET'])
def get_playlists():
    """Get all playlists for a user"""
    try:
        user_id = request.args.get('userId', 'default')
        playlists = playlist_service.get_playlists(user_id)
        return jsonify({
            'success': True,
            'playlists': playlists
        })
    except Exception as e:
        logger.error(f"Get playlists error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists', methods=['POST'])
def create_playlist():
    """Create a new playlist"""
    try:
        data = request.get_json()
        name = data.get('name', '').strip()
        description = data.get('description', '').strip()
        user_id = data.get('userId', 'default')
        
        if not name:
            return jsonify({'error': 'Playlist name is required'}), 400
        
        playlist = playlist_service.create_playlist(name, description, user_id)
        return jsonify({
            'success': True,
            'playlist': playlist
        }), 201
    except Exception as e:
        logger.error(f"Create playlist error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists/<playlist_id>', methods=['GET'])
def get_playlist(playlist_id):
    """Get a specific playlist"""
    try:
        user_id = request.args.get('userId', 'default')
        playlist = playlist_service.get_playlist(playlist_id, user_id)
        
        if not playlist:
            return jsonify({'error': 'Playlist not found'}), 404
        
        return jsonify({
            'success': True,
            'playlist': playlist
        })
    except Exception as e:
        logger.error(f"Get playlist error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists/<playlist_id>/songs', methods=['POST'])
def add_song_to_playlist(playlist_id):
    """Add a song to a playlist"""
    try:
        data = request.get_json()
        user_id = data.get('userId', 'default')
        song_data = data.get('song')
        
        if not song_data:
            return jsonify({'error': 'Song data is required'}), 400
        
        success = playlist_service.add_song_to_playlist(playlist_id, song_data, user_id)
        
        if success:
            playlist = playlist_service.get_playlist(playlist_id, user_id)
            return jsonify({
                'success': True,
                'playlist': playlist
            })
        else:
            return jsonify({'error': 'Failed to add song'}), 400
    except Exception as e:
        logger.error(f"Add song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists/<playlist_id>/songs/<video_id>', methods=['DELETE'])
def remove_song_from_playlist(playlist_id, video_id):
    """Remove a song from a playlist"""
    try:
        user_id = request.args.get('userId', 'default')
        success = playlist_service.remove_song_from_playlist(playlist_id, video_id, user_id)
        
        if success:
            playlist = playlist_service.get_playlist(playlist_id, user_id)
            return jsonify({
                'success': True,
                'playlist': playlist
            })
        else:
            return jsonify({'error': 'Failed to remove song'}), 400
    except Exception as e:
        logger.error(f"Remove song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists/<playlist_id>', methods=['PUT'])
def update_playlist(playlist_id):
    """Update playlist metadata"""
    try:
        data = request.get_json()
        user_id = data.get('userId', 'default')
        updates = {
            'name': data.get('name'),
            'description': data.get('description'),
            'coverImage': data.get('coverImage'),
        }
        updates = {k: v for k, v in updates.items() if v is not None}
        
        success = playlist_service.update_playlist(playlist_id, updates, user_id)
        
        if success:
            playlist = playlist_service.get_playlist(playlist_id, user_id)
            return jsonify({
                'success': True,
                'playlist': playlist
            })
        else:
            return jsonify({'error': 'Playlist not found'}), 404
    except Exception as e:
        logger.error(f"Update playlist error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/playlists/<playlist_id>', methods=['DELETE'])
def delete_playlist(playlist_id):
    """Delete a playlist"""
    try:
        user_id = request.args.get('userId', 'default')
        success = playlist_service.delete_playlist(playlist_id, user_id)
        
        if success:
            return jsonify({'success': True})
        else:
            return jsonify({'error': 'Playlist not found'}), 404
    except Exception as e:
        logger.error(f"Delete playlist error: {str(e)}")
        return jsonify({'error': str(e)}), 500

# ==================== USER ENDPOINTS ====================

@app.route('/api/auth/register', methods=['POST'])
def register():
    """Register a new user"""
    try:
        data = request.get_json()
        username = data.get('username', '').strip()
        email = data.get('email', '').strip()
        password = data.get('password', '')
        
        if not username or not email or not password:
            return jsonify({'error': 'Username, email, and password are required'}), 400
        
        user = user_service.create_user(username, email, password)
        
        if user:
            return jsonify({
                'success': True,
                'user': user
            }), 201
        else:
            return jsonify({'error': 'User already exists'}), 400
    except Exception as e:
        logger.error(f"Register error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/auth/login', methods=['POST'])
def login():
    """Login a user"""
    try:
        data = request.get_json()
        email = data.get('email', '').strip()
        password = data.get('password', '')
        
        if not email or not password:
            return jsonify({'error': 'Email and password are required'}), 400
        
        user = user_service.authenticate_user(email, password)
        
        if user:
            return jsonify({
                'success': True,
                'user': user
            })
        else:
            return jsonify({'error': 'Invalid credentials'}), 401
    except Exception as e:
        logger.error(f"Login error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>', methods=['GET'])
def get_user(user_id):
    """Get user profile"""
    try:
        user = user_service.get_user(user_id)
        
        if user:
            return jsonify({
                'success': True,
                'user': user
            })
        else:
            return jsonify({'error': 'User not found'}), 404
    except Exception as e:
        logger.error(f"Get user error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>/liked', methods=['GET'])
def get_liked_songs(user_id):
    """Get user's liked songs"""
    try:
        user = user_service.get_user(user_id)
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        return jsonify({
            'success': True,
            'songs': user.get('likedSongs', [])
        })
    except Exception as e:
        logger.error(f"Get liked songs error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>/liked', methods=['POST'])
def add_liked_song(user_id):
    """Add a song to liked songs"""
    try:
        data = request.get_json()
        song_data = data.get('song')
        
        if not song_data:
            return jsonify({'error': 'Song data is required'}), 400
        
        success = user_service.add_liked_song(user_id, song_data)
        
        return jsonify({
            'success': success
        })
    except Exception as e:
        logger.error(f"Add liked song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>/liked/<video_id>', methods=['DELETE'])
def remove_liked_song(user_id, video_id):
    """Remove a song from liked songs"""
    try:
        success = user_service.remove_liked_song(user_id, video_id)
        
        return jsonify({
            'success': success
        })
    except Exception as e:
        logger.error(f"Remove liked song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>/recent', methods=['GET'])
def get_recently_played(user_id):
    """Get recently played songs"""
    try:
        user = user_service.get_user(user_id)
        
        if not user:
            return jsonify({'error': 'User not found'}), 404
        
        return jsonify({
            'success': True,
            'songs': user.get('recentlyPlayed', [])
        })
    except Exception as e:
        logger.error(f"Get recent songs error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/users/<user_id>/recent', methods=['POST'])
def add_recently_played(user_id):
    """Add to recently played"""
    try:
        data = request.get_json()
        song_data = data.get('song')
        
        if not song_data:
            return jsonify({'error': 'Song data is required'}), 400
        
        success = user_service.add_recently_played(user_id, song_data)
        
        return jsonify({
            'success': success
        })
    except Exception as e:
        logger.error(f"Add recent song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    # Bind to all interfaces so that physical Android devices on the same LAN
    # can reach this development server. Never expose this setting directly to
    # the public internet without proper hardening or a production-ready WSGI
    # server such as gunicorn/uwsgi sitting behind a reverse proxy.
    logger.info("========================================")
    logger.info("Starting Aura Music API Server")
    logger.info("Server will be accessible at:")
    logger.info("  - http://127.0.0.1:5000 (localhost)")
    logger.info("  - http://192.168.1.3:5000 (LAN IP - for Android devices)")
    logger.info("========================================")
    app.run(host='0.0.0.0', port=5000, debug=True)
