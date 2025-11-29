"""
DeciBel Music Streaming App - Main Flask Application
Production-grade music streaming platform
"""
from flask import Flask, jsonify, request
from flask_cors import CORS
import logging
from config import Config
from services.music_service import MusicService
from services.playlist_service import PlaylistService
from services.user_service import UserService

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
app.config.from_object(Config)

# Enable CORS for React frontend
CORS(app, origins=app.config['CORS_ORIGINS'], supports_credentials=True)

# Initialize services
music_service = MusicService(Config.YDL_OPTS)
playlist_service = PlaylistService()
user_service = UserService()

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
    return jsonify({'status': 'healthy', 'service': 'DeciBel Music API'})

# ==================== MUSIC ENDPOINTS ====================

@app.route('/api/search', methods=['GET'])
def search_songs():
    """Search for songs"""
    try:
        query = request.args.get('query', '').strip()
        limit = int(request.args.get('limit', 20))
        filter_type = request.args.get('filter', 'songs')
        
        if not query:
            return jsonify({'error': 'Query parameter is required'}), 400
        
        results = music_service.search_songs(query, limit=limit, filter_type=filter_type)
        return jsonify({
            'success': True,
            'results': results,
            'count': len(results)
        })
    except Exception as e:
        logger.error(f"Search error: {str(e)}")
        return jsonify({'error': str(e)}), 500

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
    app.run(debug=True, port=5000)
