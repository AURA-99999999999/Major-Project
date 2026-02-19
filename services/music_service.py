"""
Music Service - Handles all music-related operations
"""
from ytmusicapi import YTMusic
from yt_dlp import YoutubeDL
from typing import List, Dict, Optional
import logging
import os
from services.music_filter import filter_music_tracks

logger = logging.getLogger(__name__)

class MusicService:
    """Service class for music operations"""
    
    def __init__(self, ydl_opts: dict):
        # Try to use OAuth if available, otherwise use unauthenticated
        oauth_path = 'oauth.json'
        if os.path.exists(oauth_path):
            logger.info("Using authenticated YTMusic with oauth.json")
            self.ytmusic = YTMusic(oauth_path)
        else:
            logger.warning("oauth.json not found - using unauthenticated YTMusic. Run setup_ytmusic_oauth.py to authenticate.")
            self.ytmusic = YTMusic()
        
        self.ydl_opts = ydl_opts
    
    def search_songs(self, query: str, limit: int = 20, filter_type: str = 'songs') -> List[Dict]:
        """Search for songs"""
        try:
            results = self.ytmusic.search(query, filter=filter_type, limit=limit)
            formatted_results = []
            
            for result in results:
                # Extract album info including ID
                album_data = result.get('album')
                album_name = None
                album_id = None
                if album_data:
                    if isinstance(album_data, dict):
                        album_name = album_data.get('name')
                        album_id = album_data.get('id')
                    elif isinstance(album_data, str):
                        album_name = album_data
                
                formatted_results.append({
                    'videoId': result.get('videoId'),
                    'title': result.get('title', 'Unknown'),
                    'artists': [artist.get('name', 'Unknown') for artist in result.get('artists', [])],
                    'thumbnail': self._get_best_thumbnail(result.get('thumbnails', [])),
                    'duration': result.get('duration'),  # Keep as string (e.g., "4:28")
                    'duration_seconds': result.get('duration_seconds'),  # Numeric duration
                    'album': album_name,
                    'albumId': album_id,
                    'year': result.get('year'),
                })
            
            return formatted_results
        except Exception as e:
            logger.error(f"Error searching songs: {str(e)}")
            raise
    
    def get_song_details(self, video_id: str) -> Dict:
        """
        Get song details and streaming URL (production-safe).
        Never throws unhandled exceptions.
        
        Returns error dict on failure: {'error': str, 'videoId': str}
        """
        try:
            logger.info(f"Fetching song details for video ID: {video_id}")
            
            # First try: Use YTMusic API to get song info (faster, more reliable)
            try:
                song_info = self.ytmusic.get_song(video_id)
                logger.debug(f"YTMusic API response received for {video_id}")
                
                # Extract streaming formats
                streaming_data = song_info.get('streamingData', {})
                formats = streaming_data.get('adaptiveFormats', [])
                logger.debug(f"Found {len(formats)} adaptive formats for {video_id}")
                
                # Find best audio format
                audio_url = None
                best_format = None
                highest_bitrate = 0
                
                for fmt in formats:
                    # Look for audio-only formats
                    if fmt.get('mimeType', '').startswith('audio/'):
                        bitrate = fmt.get('bitrate', 0)
                        if bitrate > highest_bitrate:
                            highest_bitrate = bitrate
                            best_format = fmt
                            audio_url = fmt.get('url')
                
                if audio_url:
                    logger.info(f"Successfully extracted audio URL from YTMusic API for {video_id}")
                    
                    # Get video details for metadata
                    video_details = song_info.get('videoDetails', {})
                    
                    return {
                        'videoId': video_id,
                        'url': audio_url,
                        'title': video_details.get('title', 'Unknown Title'),
                        'duration': int(video_details.get('lengthSeconds', 0)),
                        'thumbnail': self._get_best_thumbnail(video_details.get('thumbnail', {}).get('thumbnails', [])),
                        'artist': video_details.get('author', 'Unknown Artist'),
                        'artists': [video_details.get('author', 'Unknown Artist')],
                        'album': '',
                        'year': '',
                        'viewCount': int(video_details.get('viewCount', 0)),
                        'likeCount': 0,
                    }
            except Exception as ytmusic_error:
                logger.warning(f"YTMusic API failed: {str(ytmusic_error)}, falling back to yt-dlp")
            
            # Fallback: Use yt-dlp for video extraction (NO cookies, production-safe config)
            url = f'https://www.youtube.com/watch?v={video_id}'
            
            try:
                logger.info(f"Attempting yt-dlp extraction for {video_id}")
                with YoutubeDL(self.ydl_opts) as ydl:
                    info = ydl.extract_info(url, download=False)
                logger.info(f"yt-dlp extraction succeeded for {video_id}")
            except Exception as ydl_error:
                error_msg = str(ydl_error)
                logger.error(f"yt-dlp extraction failed for {video_id}: {error_msg}")
                # Return error dict (safe, won't crash)
                return {
                    'error': f'Could not extract audio for this video',
                    'videoId': video_id
                }
            
            if not info:
                logger.error(f"No info extracted for {video_id}")
                return {
                    'error': 'Failed to extract video information',
                    'videoId': video_id
                }
            
            # Extract best audio URL from yt-dlp results
            audio_url = None
            
            # Try direct url first
            if info.get('url'):
                audio_url = info['url']
                logger.info(f"Using direct URL for {video_id}")
            # Then try formats list
            elif info.get('formats'):
                audio_formats = [
                    f for f in info['formats'] 
                    if f.get('acodec') != 'none' and f.get('vcodec') == 'none'
                ]
                if audio_formats:
                    # Sort by bitrate (highest first)
                    audio_formats.sort(key=lambda x: x.get('abr', 0) or 0, reverse=True)
                    audio_url = audio_formats[0].get('url')
                    bitrate = audio_formats[0].get('abr', 'unknown')
                    logger.info(f"Selected audio format (bitrate: {bitrate}) for {video_id}")
            
            if not audio_url:
                logger.error(f"Could not extract any audio URL for {video_id}")
                return {
                    'error': 'This video does not have streamable audio',
                    'videoId': video_id
                }
            
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
            # Final safety net: never let unhandled exceptions escape
            error_msg = str(e)
            logger.error(f"Unhandled error getting song details for {video_id}: {error_msg}", exc_info=True)
            return {
                'error': f'Failed to load song',
                'videoId': video_id
            }
    
    def get_trending_songs(self, limit: int = 20) -> List[Dict]:
        """
        Get trending songs with strict music-only filtering.
        
        Uses YTMusic.get_explore() to fetch trending data, then applies
        production-grade filtering to remove non-music content (interviews,
        podcasts, trailers, etc).
        
        Args:
            limit: Maximum number of trending songs to return
            
        Returns:
            List of validated music track dictionaries
        """
        try:
            logger.info(f"Fetching trending songs: limit={limit}")
            
            # Fetch raw trending data from YTMusic
            # get_explore returns: {'trending': {'items': [...]}, 'new_releases': {...}, ...}
            explore = self.ytmusic.get_explore()
            raw_items = explore.get('trending', {}).get('items', [])
            
            # Fetch extra items to account for filtering rejections
            raw_items = raw_items[:limit * 3] if raw_items else []
            
            logger.debug(f"YTMusic returned {len(raw_items)} raw trending items")
            
            # Apply strict music-only filtering
            # Note: We disable deep validation here for performance (get_song calls are expensive)
            # The title blocklist provides sufficient quality filtering for trending
            filtered_items = filter_music_tracks(raw_items, ytmusic=None, include_validation=False)
            
            # Return top N items
            result = filtered_items[:limit]
            logger.info(f"Trending songs result: requested={limit} returned={len(result)}")
            
            return result
        except Exception as e:
            logger.error(f"Error getting trending songs: {str(e)}", exc_info=True)
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

