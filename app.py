"""
Aura Music Streaming App - Main Flask Application
Production-grade music streaming platform
"""
from flask import Flask, jsonify, request
from flask_cors import CORS
import logging
from config import Config
from services.music_service import MusicService
from services.playlist_service import PlaylistService
from services.user_service import UserService
from services.lastfm_service import LastFmService

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
LASTFM_API_KEY = Config.LASTFM_API_KEY
lastfm_service = LastFmService(LASTFM_API_KEY)

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
        with open('debug_api.html', 'r') as f:
            return f.read(), 200, {'Content-Type': 'text/html'}
    except Exception as e:
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

@app.route('/api/lastfm/top-tracks', methods=['GET'])
def get_lastfm_top_tracks():
    """Get Last.fm top tracks by country (geo.getTopTracks)"""
    try:
        country = request.args.get('country', '').strip()
        location = request.args.get('location', '').strip() or None
        limit = int(request.args.get('limit', 50))
        page = int(request.args.get('page', 1))

        if not country:
            return jsonify({'error': 'Country parameter is required'}), 400

        results, meta = lastfm_service.get_top_tracks(
            country=country,
            location=location,
            limit=limit,
            page=page
        )

        return jsonify({
            'success': True,
            'country': country,
            'location': location,
            'results': results,
            'count': len(results),
            'meta': meta
        })
    except ValueError as e:
        logger.error(f"Last.fm error: {str(e)}")
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Last.fm error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/trending/lastfm', methods=['GET'])
def get_lastfm_trending():
    """Get Last.fm geo.getTopTracks with a compact response shape"""
    try:
        country = request.args.get('country', 'India').strip()
        limit = int(request.args.get('limit', 20))

        if not country:
            return jsonify({'error': 'Country parameter is required'}), 400

        results = lastfm_service.get_geo_top_tracks(country=country, limit=limit)

        return jsonify({
            'success': True,
            'country': country,
            'tracks': results,
            'count': len(results)
        })
    except ValueError as e:
        logger.error(f"Last.fm error: {str(e)}")
        return jsonify({'error': str(e)}), 400
    except Exception as e:
        logger.error(f"Last.fm error: {str(e)}")
        return jsonify({'error': str(e)}), 500

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
