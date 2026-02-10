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

    # yt-dlp options - Updated for better compatibility
    YDL_OPTS = {
        'format': 'bestaudio/best',
        'extract_audio': True,
        'audioformat': 'mp3',
        'quiet': False,  # Changed to False for debugging
        'no_warnings': False,  # Changed to False for debugging
        'nocheckcertificate': True,
        'user_agent': YTMUSIC_USER_AGENT,
        'extractor_args': {'youtube': {'player_client': ['android', 'web']}},  # Try multiple clients
        'http_headers': {
            'User-Agent': YTMUSIC_USER_AGENT,
            'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
            'Accept-Language': 'en-us,en;q=0.5',
            'Sec-Fetch-Mode': 'navigate',
        }
    }

