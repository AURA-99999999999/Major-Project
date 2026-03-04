"""
Configuration file for Aura Music Streaming App
"""
import os
import logging

# Configure logging for config module
logger = logging.getLogger(__name__)

class Config:
    """Base configuration"""
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'dev-secret-key-change-in-production'
    CORS_ORIGINS = os.environ.get('CORS_ORIGINS', 'http://localhost:3000').split(',')
    
    # YTMusic API configuration
    YTMUSIC_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'

    # Compute cookie file path - works both locally and on Render
    _current_dir = os.path.dirname(os.path.abspath(__file__))
    _cookie_path = os.path.join(_current_dir, "services", "cookies.txt")
    
    # Log cookie file location and existence
    _cookie_exists = os.path.exists(_cookie_path)
    if _cookie_exists:
        logger.info(f"✓ YouTube cookies file found at: {_cookie_path}")
    else:
        logger.warning(f"⚠ YouTube cookies file not found at: {_cookie_path}")
        logger.warning("  Bot detection bypass via cookies will not be available.")
        logger.warning("  The app will continue without cookies (may trigger bot detection on Render).")

    # yt-dlp options - Enhanced with cookie support for bot detection bypass
    # Includes fallback options for resilient extraction across different environments
    YDL_OPTS = {
        'format': 'bestaudio/best',
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extract_flat': False,
        'noplaylist': True,
        'user_agent': YTMUSIC_USER_AGENT,
        'nocheckcertificate': True,  # Bypass SSL certificate verification for HTTPS
        'ignoreerrors': True,  # Continue on extraction errors
        'cookiefile': _cookie_path if _cookie_exists else None,  # Use cookies if available
        'extractor_args': {
            'youtube': {
                'player_client': ['android', 'web', 'ios'],
            }
        },
        'http_headers': {
            'User-Agent': YTMUSIC_USER_AGENT,
        },
    }
    
    logger.debug(f"yt-dlp configuration initialized with cookiefile support: {_cookie_exists}")
