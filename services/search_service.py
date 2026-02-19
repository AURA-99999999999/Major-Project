"""
Search Service - Production-grade filtered music search
Implements parallel category-based search with validation and caching
"""
from ytmusicapi import YTMusic
from typing import Dict, List, Optional
import logging
import time
from concurrent.futures import ThreadPoolExecutor, TimeoutError, as_completed
from functools import lru_cache

logger = logging.getLogger(__name__)

class SearchService:
    """
    Handles filtered music-only search across multiple categories
    - Songs
    - Albums
    - Artists
    - Playlists
    
    Excludes:
    - Videos
    - Podcasts
    - Interviews
    - Non-music content
    """
    
    SEARCH_TIMEOUT = 5  # seconds per category
    PARALLEL_WORKERS = 4  # concurrent search threads
    
    def __init__(self, ytmusic: YTMusic):
        self.ytmusic = ytmusic
    
    def search_all_categories(
        self,
        query: str,
        song_limit: int = 5,
        album_limit: int = 5,
        artist_limit: int = 5,
        playlist_limit: int = 5
    ) -> Dict:
        """
        Perform parallel search across all music categories
        
        Args:
            query: Search query string
            song_limit: Max songs to return
            album_limit: Max albums to return
            artist_limit: Max artists to return
            playlist_limit: Max playlists to return
            
        Returns:
            {
                'songs': [...],
                'albums': [...],
                'artists': [...],
                'playlists': [...],
                'count': int,
                'query': str,
                'cached': bool
            }
        """
        start_time = time.time()
        logger.info(f"🔍 Starting parallel search for: '{query}'")
        
        # Define search tasks
        search_tasks = {
            'songs': ('songs', song_limit),
            'albums': ('albums', album_limit),
            'artists': ('artists', artist_limit),
            'playlists': ('playlists', playlist_limit)
        }
        
        results = {
            'songs': [],
            'albums': [],
            'artists': [],
            'playlists': [],
            'count': 0,
            'query': query,
            'cached': False
        }
        
        # Execute searches in parallel with timeout protection
        with ThreadPoolExecutor(max_workers=self.PARALLEL_WORKERS) as executor:
            future_to_category = {
                executor.submit(
                    self._search_category_safe,
                    query,
                    filter_type,
                    limit
                ): category
                for category, (filter_type, limit) in search_tasks.items()
            }
            
            # Collect results as they complete
            for future in as_completed(future_to_category, timeout=self.SEARCH_TIMEOUT * 2):
                category = future_to_category[future]
                try:
                    category_results = future.result(timeout=1)
                    results[category] = category_results
                    logger.info(f"✓ {category}: {len(category_results)} results")
                except TimeoutError:
                    logger.warning(f"⚠️ {category} search timed out")
                except Exception as e:
                    logger.error(f"❌ {category} search failed: {str(e)}")
        
        # Calculate total count
        results['count'] = sum(
            len(results[category]) 
            for category in ['songs', 'albums', 'artists', 'playlists']
        )
        
        execution_time = time.time() - start_time
        logger.info(
            f"⏱️ Search completed in {execution_time:.3f}s - "
            f"Total: {results['count']} results"
        )
        
        return results
    
    def _search_category_safe(
        self,
        query: str,
        filter_type: str,
        limit: int
    ) -> List[Dict]:
        """
        Safely search a single category with error handling
        
        Returns empty list on failure (never crashes parent)
        """
        try:
            raw_results = self.ytmusic.search(
                query,
                filter=filter_type,
                limit=limit
            )
            
            # Map results based on category type
            if filter_type == 'songs':
                return self._map_songs(raw_results)[:limit]
            elif filter_type == 'albums':
                return self._map_albums(raw_results)[:limit]
            elif filter_type == 'artists':
                return self._map_artists(raw_results)[:limit]
            elif filter_type == 'playlists':
                return self._map_playlists(raw_results)[:limit]
            else:
                return []
                
        except Exception as e:
            logger.error(f"Error searching {filter_type}: {str(e)}")
            return []
    
    def _map_songs(self, raw_results: List[Dict]) -> List[Dict]:
        """Map song results to standardized format with validation"""
        songs = []
        for item in raw_results:
            try:
                # Validate required fields
                video_id = item.get('videoId')
                title = item.get('title')
                artists = item.get('artists', [])
                
                if not video_id or not title:
                    continue
                
                # Extract artist names
                artist_names = [
                    artist.get('name', 'Unknown')
                    for artist in artists
                    if isinstance(artist, dict)
                ]
                
                if not artist_names:
                    artist_names = ['Unknown Artist']
                
                # Extract album info including ID
                album_data = item.get('album')
                album_name = None
                album_id = None
                if album_data:
                    if isinstance(album_data, dict):
                        album_name = album_data.get('name')
                        album_id = album_data.get('id')
                    elif isinstance(album_data, str):
                        album_name = album_data
                
                songs.append({
                    'videoId': video_id,
                    'title': title,
                    'artists': artist_names,
                    'thumbnail': self._get_best_thumbnail(item.get('thumbnails', [])),
                    'duration': item.get('duration'),
                    'duration_seconds': item.get('duration_seconds'),
                    'album': album_name,
                    'albumId': album_id,
                    'year': item.get('year'),
                    'type': 'song'
                })
            except Exception as e:
                logger.debug(f"Error mapping song: {str(e)}")
                continue
        
        return songs
    
    def _map_albums(self, raw_results: List[Dict]) -> List[Dict]:
        """Map album results to standardized format with validation"""
        albums = []
        for item in raw_results:
            try:
                # Validate required fields
                browse_id = item.get('browseId')
                title = item.get('title')
                
                if not browse_id or not title:
                    continue
                
                # Extract artist info
                artists = item.get('artists', [])
                artist_names = [
                    artist.get('name', 'Unknown')
                    for artist in artists
                    if isinstance(artist, dict)
                ]
                
                albums.append({
                    'browseId': browse_id,
                    'title': title,
                    'artists': artist_names,
                    'thumbnail': self._get_best_thumbnail(item.get('thumbnails', [])),
                    'year': item.get('year'),
                    'type': item.get('type', 'Album'),
                    'category': 'album'
                })
            except Exception as e:
                logger.debug(f"Error mapping album: {str(e)}")
                continue
        
        return albums
    
    def _map_artists(self, raw_results: List[Dict]) -> List[Dict]:
        """Map artist results to standardized format with validation"""
        artists = []
        for item in raw_results:
            try:
                # Validate required fields
                browse_id = item.get('browseId')
                artist_name = item.get('artist')
                
                if not browse_id or not artist_name:
                    continue
                
                artists.append({
                    'browseId': browse_id,
                    'name': artist_name,
                    'thumbnail': self._get_best_thumbnail(item.get('thumbnails', [])),
                    'subscribers': item.get('subscribers'),
                    'category': 'artist'
                })
            except Exception as e:
                logger.debug(f"Error mapping artist: {str(e)}")
                continue
        
        return artists
    
    def _map_playlists(self, raw_results: List[Dict]) -> List[Dict]:
        """Map playlist results to standardized format with validation"""
        playlists = []
        for item in raw_results:
            try:
                # Validate required fields
                browse_id = item.get('browseId')
                playlist_id = item.get('playlistId')
                title = item.get('title')
                
                if not (browse_id or playlist_id) or not title:
                    continue
                
                # Extract author info
                author = item.get('author')
                author_name = 'YouTube Music'
                if author and isinstance(author, dict):
                    author_name = author.get('name', 'YouTube Music')
                elif isinstance(author, str):
                    author_name = author
                
                playlists.append({
                    'browseId': browse_id,
                    'playlistId': playlist_id or browse_id,
                    'title': title,
                    'author': author_name,
                    'thumbnail': self._get_best_thumbnail(item.get('thumbnails', [])),
                    'itemCount': item.get('itemCount'),
                    'category': 'playlist'
                })
            except Exception as e:
                logger.debug(f"Error mapping playlist: {str(e)}")
                continue
        
        return playlists
    
    def _get_best_thumbnail(self, thumbnails: List[Dict]) -> str:
        """Extract highest quality thumbnail URL"""
        if not thumbnails:
            return ''
        # Get the highest resolution thumbnail (last in list)
        return thumbnails[-1].get('url', '') if thumbnails else ''
    
    def get_search_suggestions(self, query: str) -> List[str]:
        """
        Get search suggestions for autocomplete
        
        Args:
            query: Partial search query
            
        Returns:
            List of suggestion strings
        """
        try:
            if not query or len(query) < 2:
                return []
            
            suggestions = self.ytmusic.get_search_suggestions(query)
            
            # Extract plain text suggestions only
            suggestion_list = []
            for item in suggestions:
                if isinstance(item, str):
                    suggestion_list.append(item)
                elif isinstance(item, dict) and 'text' in item:
                    suggestion_list.append(item['text'])
            
            return suggestion_list[:8]  # Limit to 8 suggestions
            
        except Exception as e:
            logger.error(f"Error getting search suggestions: {str(e)}")
            return []
