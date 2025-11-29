"""
Configuration file for Aura Music Streaming App
"""
import os

class Config:
    """Base configuration"""
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'dev-secret-key-change-in-production'
    CORS_ORIGINS = os.environ.get('CORS_ORIGINS', 'http://localhost:3000').split(',')
    
    # YTMusic API configuration
    YTMUSIC_USER_AGENT = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36'
    
    # yt-dlp options
    YDL_OPTS = {
        'format': 'bestaudio/best',
        'extract_audio': True,
        'audioformat': 'mp3',
        'quiet': True,
        'no_warnings': True,
        'nocheckcertificate': True,
        'user_agent': YTMUSIC_USER_AGENT
    }

