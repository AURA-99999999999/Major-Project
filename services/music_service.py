"""
Music Service - Handles all music-related operations
"""
from ytmusicapi import YTMusic
from yt_dlp import YoutubeDL
from typing import List, Dict, Optional
import logging

logger = logging.getLogger(__name__)

class MusicService:
    """Service class for music operations"""
    
    def __init__(self, ydl_opts: dict):
        self.ytmusic = YTMusic()
        self.ydl_opts = ydl_opts
    
    def search_songs(self, query: str, limit: int = 20, filter_type: str = 'songs') -> List[Dict]:
        """Search for songs"""
        try:
            results = self.ytmusic.search(query, filter=filter_type, limit=limit)
            formatted_results = []
            
            for result in results:
                formatted_results.append({
                    'videoId': result.get('videoId'),
                    'title': result.get('title', 'Unknown'),
                    'artists': [artist.get('name', 'Unknown') for artist in result.get('artists', [])],
                    'thumbnail': self._get_best_thumbnail(result.get('thumbnails', [])),
                    'duration': result.get('duration'),  # Keep as string (e.g., "4:28")
                    'duration_seconds': result.get('duration_seconds'),  # Numeric duration
                    'album': result.get('album', {}).get('name') if result.get('album') else None,
                    'year': result.get('year'),
                })
            
            return formatted_results
        except Exception as e:
            logger.error(f"Error searching songs: {str(e)}")
            raise
    
    def get_song_details(self, video_id: str) -> Dict:
        """Get detailed song information and streaming URL"""
        try:
            logger.info(f"Fetching song details for video ID: {video_id}")
            
            # yt-dlp is the only source for playback and metadata in this path.
            with YoutubeDL(self.ydl_opts) as ydl:
                info = ydl.extract_info(
                    f'https://www.youtube.com/watch?v={video_id}',
                    download=False
                )
            
            if not info:
                raise ValueError('Failed to extract video information')
            
            # Try to get direct audio URL
            audio_url = None
            if 'url' in info:
                audio_url = info['url']
            elif 'formats' in info:
                # Find best audio format
                audio_formats = [f for f in info['formats'] if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                if audio_formats:
                    # Sort by quality
                    audio_formats.sort(key=lambda x: x.get('abr', 0) or 0, reverse=True)
                    audio_url = audio_formats[0]['url']
            
            if not audio_url:
                logger.error(f"Could not extract audio URL for {video_id}")
                raise ValueError('Could not extract audio URL from this video')
            
            logger.info(f"Successfully extracted audio URL for {video_id}")
            
            thumbnails = info.get('thumbnails', [])
            artist_name = info.get('uploader', 'Unknown Artist')

            # Get thumbnail
            thumbnail = ''
            if thumbnails:
                thumbnail = self._get_best_thumbnail(thumbnails)
            elif info.get('thumbnail'):
                thumbnail = info['thumbnail']
            
            return {
                'videoId': video_id,
                'url': audio_url,
                'title': info.get('title', 'Unknown Title'),
                'duration': info.get('duration', 0),
                'thumbnail': thumbnail,
                'artist': artist_name,
                'artists': [artist_name],
                'album': info.get('album', ''),
                'year': info.get('release_year', ''),
                'viewCount': info.get('view_count', 0),
                'likeCount': info.get('like_count', 0),
            }
        except Exception as e:
            logger.error(f"Error getting song details for {video_id}: {str(e)}", exc_info=True)
            raise ValueError(f"Failed to load song: {str(e)}")
    
    def get_trending_songs(self, limit: int = 20) -> List[Dict]:
        """Get trending songs"""
        try:
            charts = self.ytmusic.get_charts(country='US', limit=limit)
            trending = []
            
            for song in charts.get('songs', [])[:limit]:
                trending.append({
                    'videoId': song.get('videoId'),
                    'title': song.get('title', 'Unknown'),
                    'artists': [artist.get('name', 'Unknown') for artist in song.get('artists', [])],
                    'thumbnail': self._get_best_thumbnail(song.get('thumbnails', [])),
                    'duration': song.get('duration'),
                    'album': song.get('album', {}).get('name') if song.get('album') else None,
                })
            
            return trending
        except Exception as e:
            logger.error(f"Error getting trending songs: {str(e)}")
            return []
    
    def get_artist_info(self, artist_id: str) -> Dict:
        """Get artist information"""
        try:
            artist = self.ytmusic.get_artist(artist_id)
            return {
                'id': artist_id,
                'name': artist.get('name', 'Unknown Artist'),
                'thumbnail': self._get_best_thumbnail(artist.get('thumbnails', [])),
                'description': artist.get('description', ''),
                'songs': artist.get('songs', {}).get('results', [])[:10],
                'albums': artist.get('albums', {}).get('results', [])[:10],
            }
        except Exception as e:
            logger.error(f"Error getting artist info: {str(e)}")
            raise
    
    def _get_best_thumbnail(self, thumbnails: List[Dict]) -> str:
        """Get the best quality thumbnail"""
        if not thumbnails:
            return ''
        # Get the highest resolution thumbnail
        return thumbnails[-1].get('url', '') if thumbnails else ''

