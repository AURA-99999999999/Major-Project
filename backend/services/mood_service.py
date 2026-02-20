"""
Mood Service - Handles mood categories and mood-based playlists
Uses YTMusic API for official YouTube Music moods & genres
"""
from ytmusicapi import YTMusic
from typing import List, Dict, Tuple, Optional
import logging
import time

logger = logging.getLogger(__name__)


class MoodService:
    """Service for mood-based music discovery"""
    
    # Cache TTL in seconds
    CACHE_TTL_CATEGORIES = 1800  # 30 minutes
    CACHE_TTL_PLAYLISTS = 900    # 15 minutes
    
    # Sections to include in mood categories
    ALLOWED_SECTIONS = {"Genres", "Moods & moments"}
    
    def __init__(self, ytmusic: YTMusic):
        self.ytmusic = ytmusic
        
        # Cache: {key: (data, timestamp)}
        self.cache_categories: Optional[Tuple[Dict, float]] = None
        self.cache_playlists: Dict[str, Tuple[Dict, float]] = {}
    
    def _is_cache_valid(self, cached_data: Optional[Tuple], ttl: int) -> bool:
        """Check if cached data is still valid"""
        if not cached_data:
            return False
        _, timestamp = cached_data
        return (time.time() - timestamp) < ttl
    
    def get_mood_categories(self) -> Dict:
        """
        Fetch mood categories from YTMusic.
        Only returns 'Genres' and 'Moods & moments' sections.
        
        Returns:
            {
                "sections": [
                    {
                        "sectionTitle": "Genres",
                        "categories": [
                            {"title": "Dance & Electronic", "params": "..."}
                        ]
                    }
                ]
            }
        """
        try:
            # Check cache
            if self._is_cache_valid(self.cache_categories, self.CACHE_TTL_CATEGORIES):
                logger.debug("Returning cached mood categories")
                return self.cache_categories[0]
            
            logger.info("Fetching mood categories from YTMusic")
            raw_data = self.ytmusic.get_mood_categories()
            
            # YTMusic returns a dict like: {"Moods & moments": [...], "Genres": [...]}
            # Process and filter sections
            filtered_sections = []
            
            if isinstance(raw_data, dict):
                for section_title, items in raw_data.items():
                    # Only include specific sections
                    if section_title not in self.ALLOWED_SECTIONS:
                        logger.debug(f"Skipping section: {section_title}")
                        continue
                    
                    categories = []
                    for item in items:
                        title = item.get('title')
                        params = item.get('params')
                        
                        if title and params:
                            categories.append({
                                'title': title,
                                'params': params
                            })
                    
                    if categories:
                        filtered_sections.append({
                            'sectionTitle': section_title,
                            'categories': categories
                        })
            
            result = {
                'sections': filtered_sections
            }
            
            # Cache the result
            self.cache_categories = (result, time.time())
            
            logger.info(f"Fetched {len(filtered_sections)} mood sections with {sum(len(s['categories']) for s in filtered_sections)} total categories")
            return result
            
        except Exception as e:
            logger.error(f"Error fetching mood categories: {str(e)}")
            # Return empty structure on failure (graceful fallback)
            return {'sections': []}
    
    def get_mood_playlists(self, params: str, limit: int = 15) -> Dict:
        """
        Fetch playlists for a specific mood using params.
        
        Args:
            params: The params string from mood category
            limit: Maximum playlists to return (default 15)
            
        Returns:
            {
                "count": 10,
                "playlists": [
                    {
                        "title": "...",
                        "playlistId": "...",
                        "thumbnail": "...",
                        "description": "..."
                    }
                ],
                "source": "ytmusic_moods"
            }
        """
        try:
            # Check cache
            cache_key = f"{params}:{limit}"
            if self._is_cache_valid(self.cache_playlists.get(cache_key), self.CACHE_TTL_PLAYLISTS):
                logger.debug(f"Returning cached mood playlists for params={params[:20]}...")
                return self.cache_playlists[cache_key][0]
            
            logger.info(f"Fetching mood playlists from YTMusic (params={params[:30]}...)")
            raw_data = self.ytmusic.get_mood_playlists(params)
            
            # Extract and format playlists
            playlists = []
            seen_ids = set()
            
            for item in raw_data:
                playlist_id = item.get('playlistId')
                
                # Skip if no ID or duplicate
                if not playlist_id or playlist_id in seen_ids:
                    continue
                
                seen_ids.add(playlist_id)
                
                # Extract best thumbnail
                thumbnail = self._get_best_thumbnail(item.get('thumbnails', []))
                
                playlists.append({
                    'title': item.get('title', 'Unknown'),
                    'playlistId': playlist_id,
                    'thumbnail': thumbnail,
                    'description': item.get('description', '')
                })
                
                # Enforce limit
                if len(playlists) >= limit:
                    break
            
            result = {
                'count': len(playlists),
                'playlists': playlists,
                'source': 'ytmusic_moods'
            }
            
            # Cache the result
            self.cache_playlists[cache_key] = (result, time.time())
            
            logger.info(f"Fetched {len(playlists)} mood playlists")
            return result
            
        except Exception as e:
            logger.error(f"Error fetching mood playlists: {str(e)}")
            # Return empty result on failure
            return {
                'count': 0,
                'playlists': [],
                'source': 'ytmusic_moods'
            }
    
    def _get_best_thumbnail(self, thumbnails: List[Dict]) -> str:
        """Extract the highest resolution thumbnail URL"""
        if not thumbnails:
            return ""
        
        if isinstance(thumbnails, dict):
            thumbnails = [thumbnails]
        
        # Find thumbnail with highest resolution
        best = max(
            thumbnails,
            key=lambda t: (t.get('width', 0) or 0) * (t.get('height', 0) or 0)
        )
        return best.get('url', '')
    
    def clear_cache(self):
        """Clear all cached data (useful for testing)"""
        self.cache_categories = None
        self.cache_playlists.clear()
        logger.info("Mood service cache cleared")
