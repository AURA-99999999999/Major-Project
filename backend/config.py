"""
Configuration file for Aura Music Streaming App
"""
import os

class Config:
    """Base configuration"""
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'dev-secret-key-change-in-production'
    CORS_ORIGINS = os.environ.get('CORS_ORIGINS', 'http://localhost:3000').split(',')
    
    
    # YTMusic API configuration
    YTMUSIC_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

    # yt-dlp options - Production-safe configuration for public YouTube Music streaming
    # No cookies required: we only stream public, non-login content
    YDL_OPTS = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extract_flat': False,
        'noplaylist': True,
        'user_agent': YTMUSIC_USER_AGENT,
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'web', 'ios'],
            }
        },
        'http_headers': {
            'User-Agent': YTMUSIC_USER_AGENT,
        },
    }

