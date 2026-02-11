"""
Aura Music Streaming App - Main Flask Application
Production-grade music streaming platform
"""
from flask import Flask, jsonify, request
from pathlib import Path
from flask_cors import CORS
import logging
import time
from config import Config
from services.music_service import MusicService
from services.playlist_service import PlaylistService
from services.user_service import UserService
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

# Enable CORS for all origins (development mode)
# This allows Android app and React frontend to access the API
# In production, restrict this to specific origins
CORS(app, origins="*", supports_credentials=True, allow_headers="*", methods="*")

# Initialize services
music_service = MusicService(Config.YDL_OPTS)
playlist_service = PlaylistService()
user_service = UserService()

# Simple in-memory caches for home and search results
HOME_CACHE_TTL_SECONDS = 45 * 60
_home_cache = {}


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
    trending = []
    for item in items:
        video_id = item.get("videoId")
        if not video_id:
            continue

        thumbnails = item.get("thumbnails") or item.get("thumbnail") or []
        trending.append(
            {
                "title": item.get("title") or "",
                "videoId": video_id,
                "artists": _normalize_artists(item.get("artists")),
                "thumbnail": _pick_best_thumbnail(thumbnails),
                "playlistId": item.get("playlistId") or "",
                "views": item.get("views") or "",
            }
        )
        if len(trending) >= limit:
            break
    return trending

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
def search_songs():
    """Search for songs with basic metadata enrichment"""
    try:
        query = request.args.get('query', '').strip()
        limit = int(request.args.get('limit', 20))
        filter_type = request.args.get('filter', 'songs')
        
        if not query:
            return jsonify({'error': 'Query parameter is required'}), 400

        # Enhanced logging for Android connectivity debugging
        client_ip = request.remote_addr
        user_agent = request.headers.get('User-Agent', 'Unknown')
        logger.info(
            "========================================"
        )
        logger.info(
            "SEARCH REQUEST RECEIVED"
        )
        logger.info(
            "Query: %s | Limit: %s | Filter: %s",
            query, limit, filter_type
        )
        logger.info(
            "Client IP: %s | User-Agent: %s",
            client_ip, user_agent
        )
        logger.info(
            "Request URL: %s",
            request.url
        )
        logger.info(
            "========================================"
        )
        
        # Get raw results from music service
        logger.info("📥 Fetching from music API...")
        results = music_service.search_songs(query, limit=limit, filter_type=filter_type)
        logger.info(f"📋 Raw results: {len(results)} songs found")
        logger.info(f"Raw data sample: {results[0] if results else 'No results'}")
        
        # No enrichment: return raw results from streaming API
        return jsonify({
            'success': True,
            'results': results,
            'count': len(results)
        })
    except Exception as e:
        logger.error(f"Search error: {str(e)}")
        return jsonify({'error': str(e)}), 500

# ==================== SONG STREAMING ENDPOINT ====================

@app.route('/api/song/<video_id>', methods=['GET'])
def get_song(video_id):
    """Get song details and streaming URL"""
    try:
        if not video_id:
            return jsonify({'error': 'Video ID is required'}), 400
        
        song_data = music_service.get_song_details(video_id)
        return jsonify({
            'success': True,
            'data': song_data
        })
    except Exception as e:
        logger.error(f"Get song error: {str(e)}")
        return jsonify({'error': str(e)}), 500

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


@app.route('/api/home', methods=['GET'])
def get_home():
    """Get home feed with trending from YTMusic"""
    try:
        limit = 15
        cache_key = f"home:ytmusic:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            return jsonify(cached)

        ytmusic = YTMusic()
        explore = ytmusic.get_explore()
        trending_items = ((explore or {}).get("trending") or {}).get("items") or []

        trending = _build_trending_items(trending_items, limit)
        response_payload = {
            "source": "ytmusicapi",
            "count": len(trending),
            "trending": trending,
        }
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)

        return jsonify(response_payload)
    except Exception as e:
        logger.error("Home error: %s", str(e), exc_info=True)
        return jsonify({
            "source": "ytmusicapi",
            "count": 0,
            "trending": [],
        })


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
    """Get mood and genre categories from YTMusic"""
    try:
        cache_key = "mood_categories"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            return jsonify(cached)

        ytmusic = YTMusic()
        mood_sections = ytmusic.get_mood_categories()
        
        # Predefined colors for visual appeal (cycle through these)
        colors = ['#4A90E2', '#E74C3C', '#9B59B6', '#27AE60', '#34495E', 
                  '#E91E63', '#607D8B', '#FFC107', '#FF9800', '#00BCD4',
                  '#3498DB', '#E67E22', '#1ABC9C', '#F39C12', '#8E44AD']
        
        categories = []
        color_index = 0
        
        # Flatten all sections into a single list
        # Prioritize "Moods & moments" and "For you" sections
        priority_sections = ['Moods & moments', 'For you', 'Genres']
        
        for section_name in priority_sections:
            if section_name in mood_sections:
                for item in mood_sections[section_name]:
                    categories.append({
                        'title': item.get('title', ''),
                        'params': item.get('params', ''),
                        'color': colors[color_index % len(colors)]
                    })
                    color_index += 1
        
        # Add any remaining sections not in priority list
        for section_name, items in mood_sections.items():
            if section_name not in priority_sections:
                for item in items:
                    categories.append({
                        'title': item.get('title', ''),
                        'params': item.get('params', ''),
                        'color': colors[color_index % len(colors)]
                    })
                    color_index += 1
        
        response_payload = {
            'success': True,
            'categories': categories,
            'count': len(categories)
        }
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)
        
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
    """Get playlists for a specific mood/genre"""
    try:
        params = request.args.get('params', '').strip()
        if not params:
            return jsonify({'error': 'params parameter is required'}), 400
        
        limit = int(request.args.get('limit', 10))
        cache_key = f"mood_playlists:{params}:{limit}"
        cached = _cache_get(_home_cache, cache_key)
        if cached is not None:
            return jsonify(cached)

        ytmusic = YTMusic()
        mood_data = ytmusic.get_mood_playlists(params)
        
        playlists = []
        for item in (mood_data or [])[:limit]:
            playlist_id = item.get('playlistId') or item.get('browseId')
            if not playlist_id:
                continue
            
            thumbnails = item.get('thumbnails') or []
            playlists.append({
                'playlistId': playlist_id,
                'title': item.get('title') or '',
                'description': item.get('description') or '',
                'thumbnail': _pick_best_thumbnail(thumbnails),
                'author': item.get('author') or 'YouTube Music',
            })
        
        response_payload = {
            'success': True,
            'playlists': playlists,
            'count': len(playlists)
        }
        _cache_set(_home_cache, cache_key, response_payload, HOME_CACHE_TTL_SECONDS)
        
        return jsonify(response_payload)
    except Exception as e:
        logger.error(f"Mood playlists error: {str(e)}", exc_info=True)
        return jsonify({
            'success': False,
            'playlists': [],
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
