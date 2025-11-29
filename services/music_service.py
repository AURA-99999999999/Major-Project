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
                    'duration': result.get('duration'),
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
            # Get streaming URL from yt-dlp first (more reliable)
            with YoutubeDL(self.ydl_opts) as ydl:
                info = ydl.extract_info(
                    f'https://www.youtube.com/watch?v={video_id}',
                    download=False
                )
            
            if 'url' not in info:
                raise ValueError('Could not extract audio URL')
            
            # Try to get metadata from YTMusic
            try:
                song_info = self.ytmusic.get_song(video_id)
                video_details = song_info.get('videoDetails', {})
                thumbnails = video_details.get('thumbnail', {}).get('thumbnails', [])
                artist_info = video_details.get('author', 'Unknown Artist')
                artist_name = artist_info if isinstance(artist_info, str) else artist_info.get('name', 'Unknown Artist')
            except:
                # Fallback to yt-dlp info
                song_info = {}
                video_details = {}
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
                'url': info['url'],
                'title': info.get('title', video_details.get('title', 'Unknown Title')),
                'duration': info.get('duration', 0),
                'thumbnail': thumbnail,
                'artist': artist_name,
                'artists': [artist_name],
                'album': song_info.get('album', {}).get('name', '') if song_info.get('album') else info.get('album', ''),
                'year': song_info.get('year', '') or info.get('release_year', ''),
                'viewCount': video_details.get('viewCount', '0'),
                'likeCount': video_details.get('likeCount', '0'),
            }
        except Exception as e:
            logger.error(f"Error getting song details: {str(e)}")
            raise
    
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

