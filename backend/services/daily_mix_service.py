"""
Daily Mix Service - Generates personalized daily mixes using engagement signals
Reuses existing recommender logic and Firestore data
"""
import logging
import time
import os
from typing import List, Dict, Optional, Set
from datetime import datetime, timedelta
from collections import defaultdict, Counter
from services.music_filter import filter_music_tracks

logger = logging.getLogger(__name__)

# Cache configuration
CACHE_TTL_DAILY_MIXES = 86400  # 24 hours


class DailyMixService:
    """
    Generates 4 types of personalized daily mixes:
    1. Favorites Mix - Songs from user's top 5 artists
    2. Similar Artists Mix - Songs from related artists
    3. Discover Mix - Trending + new + similar to taste
    4. Mood Mix - Contextual listening based on time of day
    """
    
    # Artist appearance limit for diversity
    ARTIST_LIMIT_PER_MIX = 3
    MIX_SIZE = 30
    MINIMUM_MIX_SIZE = 15  # Guarantee minimum mix size
    
    def __init__(self, ytmusic, recommendation_service, user_service):
        """
        Initialize DailyMixService
        
        Args:
            ytmusic: YTMusic instance for API calls
            recommendation_service: RecommendationService for shared utilities
            user_service: UserService for Firestore access
        """
        self.ytmusic = ytmusic
        self.rec_service = recommendation_service
        self.user_service = user_service
        self.cache: Dict[str, tuple] = {}  # {cache_key: (data, timestamp)}

    def get_daily_mixes(self, uid: str) -> Dict[str, List[Dict]]:
        """
        Generate all daily mixes for a user
        
        Args:
            uid: User ID
            
        Returns:
            Dict with all 4 mixes:
            {
                "dailyMix1": [...],
                "dailyMix2": [...],
                "discoverMix": [...],
                "moodMix": [...]
            }
        """
        try:
            logger.info(f"Generating daily mixes for user: {uid}")
            
            # Check cache first
            cache_key = f"daily_mixes_{uid}"
            cached_data = self._get_from_cache(cache_key)
            if cached_data:
                logger.info(f"Returning cached mixes for {uid}")
                return cached_data
            
            # Build user profile from engagement signals
            profile = self._build_user_profile(uid)
            logger.info(f"User profile built: artists={len(profile.get('top_artists', []))}, "
                       f"plays={len(profile.get('plays', []))}")
            
            # Generate all mixes
            mixes = {
                "dailyMix1": self._generate_favorites_mix(profile),
                "dailyMix2": self._generate_similar_mix(profile),
                "discoverMix": self._generate_discover_mix(profile),
                "moodMix": self._generate_mood_mix(profile)
            }
            
            # Deduplicate before filtering
            logger.info("Deduplicating tracks before filtering")
            mixes = self._deduplicate_all_mixes(mixes)
            
            # Filter all songs through music_filter with relaxed rules for mixes
            mixes = self._filter_all_mixes(mixes)
            
            # Ensure minimum mix sizes with fallbacks
            mixes = self._ensure_minimum_sizes(mixes)
            
            # Only cache if mixes are not empty
            if self._mixes_valid(mixes):
                self._set_in_cache(cache_key, mixes, CACHE_TTL_DAILY_MIXES)
                logger.info("Cached valid mixes")
            else:
                logger.warning("Skipping cache due to empty/invalid mixes")
            
            logger.info(f"Daily mixes generated: "
                       f"favorites={len(mixes['dailyMix1'])}, "
                       f"similar={len(mixes['dailyMix2'])}, "
                       f"discover={len(mixes['discoverMix'])}, "
                       f"mood={len(mixes['moodMix'])}")
            
            return mixes
            
        except Exception as e:
            logger.error(f"Error generating daily mixes: {str(e)}", exc_info=True)
            # Return trending as fallback
            return self._get_fallback_mixes()

    def _build_user_profile(self, uid: str) -> Dict:
        """
        Build user taste profile from Firestore engagement signals
        
        Returns profile with:
        - top_artists: List of artists weighted by plays/likes
        - plays: User's recent plays
        - liked: User's liked songs
        - artist_listen_count: Dict of artist -> listen count
        """
        try:
            # Fetch user signals from recommendation service
            signals = self.rec_service._fetch_user_signals(uid)
            plays = signals.get('plays', [])
            liked = signals.get('liked', [])
            
            # Build weighted signals
            weighted = self.rec_service._build_weighted_signals(plays, liked)
            top_artists = weighted.get('top_artists', [])
            
            # Build artist listen counts for diversity checking
            artist_listen_count = defaultdict(int)
            for song in plays:
                artist = song.get('artist', 'Unknown')
                artist_listen_count[artist] += 1
            for song in liked:
                artist = song.get('artist', 'Unknown')
                artist_listen_count[artist] += 2  # Like counts as 2x
            
            profile = {
                'top_artists': top_artists[:10],  # Top 10 artists
                'plays': plays,
                'liked': liked,
                'artist_listen_count': dict(artist_listen_count),
                'all_consumed': {s.get('videoId') for s in plays + liked if s.get('videoId')}
            }
            
            logger.info(f"Profile built: {len(profile['top_artists'])} artists, "
                       f"{len(profile['plays'])} plays, {len(profile['liked'])} likes")
            
            return profile
            
        except Exception as e:
            logger.error(f"Error building user profile: {str(e)}")
            return {
                'top_artists': [],
                'plays': [],
                'liked': [],
                'artist_listen_count': {},
                'all_consumed': set()
            }

    def _generate_favorites_mix(self, profile: Dict) -> List[Dict]:
        """
        Generate Favorites Mix: Songs from user's top 5 artists
        """
        try:
            logger.info("Generating Favorites Mix")
            top_artists = profile.get('top_artists', [])[:5]
            all_consumed = profile.get('all_consumed', set())
            
            if not top_artists:
                logger.warning("No top artists for Favorites Mix, using trending")
                return self._get_trending_songs()
            
            songs_dict = {}  # {videoId: song_data}
            
            # Fetch top tracks for each artist (expanded to 25 for better candidate pool)
            for artist in top_artists:
                try:
                    logger.info(f"Fetching tracks for artist: {artist}")
                    query = f"{artist} top tracks"
                    results = self.rec_service._search_songs(query, limit=25)
                    songs_dict.update(results)
                    logger.info(f"Fetched {len(results)} tracks for {artist}")
                except Exception as e:
                    logger.warning(f"Error fetching tracks for {artist}: {str(e)}")
                    continue
            
            logger.info(f"Total candidates before diversity: {len(songs_dict)} songs")
            
            # Ensure diversity and remove overplayed songs
            songs = self._ensure_diversity(
                list(songs_dict.values()),
                profile.get('artist_listen_count', {}),
                all_consumed
            )
            
            logger.info(f"Favorites Mix generated: {len(songs)} songs")
            return songs[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"Error generating Favorites Mix: {str(e)}")
            return []

    def _generate_similar_mix(self, profile: Dict) -> List[Dict]:
        """
        Generate Similar Artists Mix: Songs from related artists
        Safe discovery using 2x-removed recommendations
        """
        try:
            logger.info("Generating Similar Artists Mix")
            top_artists = profile.get('top_artists', [])[:3]  # Use top 3
            all_consumed = profile.get('all_consumed', set())
            
            if not top_artists:
                logger.warning("No top artists for Similar Mix")
                return self._get_trending_songs()
            
            similar_artists = set()
            songs_dict = {}
            
            # Get related artists for top artists
            for artist in top_artists:
                try:
                    logger.info(f"Finding related artists for: {artist}")
                    # Use recommendation service's search to find similar
                    query = f"{artist} similar artists"
                    results = self.rec_service._search_songs(query, limit=15)
                    
                    # Get unique artists from results
                    for song in results.values():
                        if song.get('artist') and song.get('artist') not in top_artists:
                            similar_artists.add(song.get('artist'))
                    
                    songs_dict.update(results)
                    logger.info(f"Found {len(results)} songs for similar to {artist}")
                except Exception as e:
                    logger.warning(f"Error finding similar to {artist}: {str(e)}")
                    continue
            
            logger.info(f"Found {len(similar_artists)} similar artists")
            
            # Fallback: if no similar artists found, use top artists
            if not similar_artists and not songs_dict:
                logger.warning("No similar artists found, falling back to top artists")
                return self._generate_favorites_mix(profile)
            
            # Ensure diversity and remove played songs
            songs = self._ensure_diversity(
                list(songs_dict.values()),
                profile.get('artist_listen_count', {}),
                all_consumed,
                exclude_played=True
            )
            
            logger.info(f"Similar Mix generated: {len(songs)} songs")
            return songs[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"Error generating Similar Mix: {str(e)}")
            return []

    def _generate_discover_mix(self, profile: Dict) -> List[Dict]:
        """
        Generate Discover Mix: Trending + new releases + fresh content
        Introduces new music while respecting taste profile
        """
        try:
            logger.info("Generating Discover Mix")
            all_consumed = profile.get('all_consumed', set())
            
            songs_dict = {}
            
            # Get trending songs as base
            try:
                trending = self.rec_service._get_trending_fallback(country='IN')
                songs_dict.update(trending)
                logger.info(f"Added {len(trending)} trending songs")
            except Exception as e:
                logger.warning(f"Error fetching trending: {str(e)}")
            
            # Add new releases
            try:
                new_releases = self.rec_service._search_songs("new releases", limit=20)
                songs_dict.update(new_releases)
                logger.info(f"Added {len(new_releases)} new releases")
            except Exception as e:
                logger.warning(f"Error fetching new releases: {str(e)}")
            
            # Add recommendations based on taste (but fresh songs)
            try:
                recommendations = self.rec_service._search_songs(
                    "latest in " + (profile.get('top_artists', ['pop'])[0] or 'pop'),
                    limit=20
                )
                songs_dict.update(recommendations)
                logger.info(f"Added {len(recommendations)} taste-based recommendations")
            except Exception as e:
                logger.warning(f"Error fetching taste recommendations: {str(e)}")
            
            # If still low on songs, add global trending as fallback
            if len(songs_dict) < 30:
                try:
                    logger.info("Adding global trending as fallback")
                    global_trending = self._get_trending_songs(limit=30)
                    for song in global_trending:
                        if song.get('videoId'):
                            songs_dict[song['videoId']] = song
                    logger.info(f"Total after fallback: {len(songs_dict)} songs")
                except Exception as e:
                    logger.warning(f"Error fetching global trending fallback: {str(e)}")
            
            # Ensure diversity and remove previously played
            songs = self._ensure_diversity(
                list(songs_dict.values()),
                profile.get('artist_listen_count', {}),
                all_consumed,
                exclude_played=True,
                favor_new=True
            )
            
            logger.info(f"Discover Mix generated: {len(songs)} songs")
            return songs[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"Error generating Discover Mix: {str(e)}")
            return []

    def _generate_mood_mix(self, profile: Dict) -> List[Dict]:
        """
        Generate Mood Mix: Contextual listening based on time of day
        Morning: Calm
        Afternoon: Energetic
        Evening: Chill
        """
        try:
            logger.info("Generating Mood Mix")
            
            # Determine mood based on current hour
            hour = datetime.now().hour
            if hour < 12:
                mood = "calm"
            elif hour < 18:
                mood = "energetic"
            else:
                mood = "chill"
            
            logger.info(f"Current mood: {mood} (hour={hour})")
            
            # Search for mood-based playlists/songs
            try:
                mood_songs = self.rec_service._search_songs(
                    f"{mood} music playlist", 
                    limit=40
                )
                songs = list(mood_songs.values())
                
                # Ensure diversity
                diverse_songs = self._ensure_diversity(
                    songs,
                    profile.get('artist_listen_count', {}),
                    profile.get('all_consumed', set()),
                    exclude_played=False
                )
                
                logger.info(f"Mood Mix generated: {len(diverse_songs)} songs")
                return diverse_songs[:self.MIX_SIZE]
                
            except Exception as e:
                logger.warning(f"Error fetching mood songs: {str(e)}")
                # Fallback to trending
                return self._get_trending_songs()[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"Error generating Mood Mix: {str(e)}")
            return []

    def _ensure_diversity(
        self,
        songs: List[Dict],
        artist_listen_count: Dict[str, int],
        all_consumed: Set[str],
        exclude_played: bool = False,
        favor_new: bool = False
    ) -> List[Dict]:
        """
        Ensure diversity by:
        1. Removing duplicates
        2. Limiting artists to ARTIST_LIMIT_PER_MIX songs each
        3. Optionally excluding previously played songs
        4. Removing low-listen artists if favor_new is True
        
        IMPORTANT: Prevents over-filtering by guaranteeing minimum results
        """
        result = []
        artist_count = defaultdict(int)
        seen_ids = set()
        
        # Sort by various criteria for diversity
        if favor_new:
            # Favor songs from artists with fewer listens
            songs = sorted(
                songs,
                key=lambda s: artist_listen_count.get(s.get('artist', 'Unknown'), 0)
            )
        
        for song in songs:
            video_id = song.get('videoId')
            artist = song.get('artist', 'Unknown')
            
            # Skip if already seen
            if video_id in seen_ids:
                continue
            
            # Skip if consumed
            if exclude_played and video_id in all_consumed:
                continue
            
            # Limit artists per mix
            if artist_count[artist] >= self.ARTIST_LIMIT_PER_MIX:
                continue
            
            result.append(song)
            seen_ids.add(video_id)
            artist_count[artist] += 1
        
        # Prevent over-filtering: if diversity reduces results too much, return more songs
        minimum_results = 20
        if len(result) < minimum_results and len(songs) >= minimum_results:
            logger.warning(f"Diversity reduced results to {len(result)}, returning first {minimum_results} unique songs")
            result = []
            seen_ids = set()
            for song in songs:
                video_id = song.get('videoId')
                if video_id and video_id not in seen_ids:
                    result.append(song)
                    seen_ids.add(video_id)
                    if len(result) >= minimum_results:
                        break
        
        logger.info(f"Diversity ensured: {len(result)} unique songs, "
                   f"{len(artist_count)} artists represented")
        return result

    def _deduplicate_all_mixes(self, mixes: Dict[str, List[Dict]]) -> Dict[str, List[Dict]]:
        """
        Deduplicate tracks in all mixes safely
        """
        deduplicated_mixes = {}
        for mix_name, songs in mixes.items():
            unique = self._dedupe_tracks(songs)
            deduplicated_mixes[mix_name] = unique
            logger.info(f"{mix_name} deduplication: {len(songs)} -> {len(unique)} songs")
        return deduplicated_mixes
    
    def _dedupe_tracks(self, tracks: List[Dict]) -> List[Dict]:
        """
        Remove duplicate tracks by videoId while preserving order
        """
        seen = set()
        unique = []
        for t in tracks:
            vid = t.get('videoId')
            if vid and vid not in seen:
                unique.append(t)
                seen.add(vid)
        return unique
    
    def _filter_all_mixes(self, mixes: Dict[str, List[Dict]]) -> Dict[str, List[Dict]]:
        """
        Apply music_filter to all mixes with relaxed rules for trusted mix source
        """
        try:
            filtered_mixes = {}
            for mix_name, songs in mixes.items():
                logger.info(f"{mix_name} before filter: {len(songs)} songs")
                # Use source="mix" for relaxed filtering rules
                filtered = filter_music_tracks(songs, source="mix")
                filtered_mixes[mix_name] = filtered
                logger.info(f"{mix_name} after filter: {len(filtered)}/{len(songs)} songs")
            return filtered_mixes
        except Exception as e:
            logger.error(f"Error filtering mixes: {str(e)}")
            return mixes

    def _get_trending_songs(self, limit: int = 30) -> List[Dict]:
        """
        Get trending songs as fallback
        """
        try:
            trending = self.rec_service._get_trending_fallback(country='IN')
            return list(trending.values())[:limit]
        except Exception as e:
            logger.error(f"Error getting trending fallback: {str(e)}")
            return []

    def _ensure_minimum_sizes(self, mixes: Dict[str, List[Dict]]) -> Dict[str, List[Dict]]:
        """
        Guarantee minimum mix size by adding fallback trending tracks if needed
        """
        fallback_trending = None
        
        for mix_name, songs in mixes.items():
            if len(songs) < self.MINIMUM_MIX_SIZE:
                logger.warning(f"{mix_name} has only {len(songs)} songs, adding fallback trending")
                if fallback_trending is None:
                    fallback_trending = self._get_trending_songs(limit=50)
                
                # Add trending songs until we reach minimum
                existing_ids = {s.get('videoId') for s in songs if s.get('videoId')}
                for trending_song in fallback_trending:
                    if trending_song.get('videoId') not in existing_ids:
                        songs.append(trending_song)
                        existing_ids.add(trending_song['videoId'])
                        if len(songs) >= self.MINIMUM_MIX_SIZE:
                            break
                
                mixes[mix_name] = songs
                logger.info(f"{mix_name} after fallback: {len(songs)} songs")
        
        return mixes
    
    def _mixes_valid(self, mixes: Dict[str, List[Dict]]) -> bool:
        """
        Check if mixes are valid (not empty)
        """
        for mix_name, songs in mixes.items():
            if len(songs) == 0:
                logger.warning(f"{mix_name} is empty")
                return False
        return True
    
    def _get_fallback_mixes(self) -> Dict[str, List[Dict]]:
        """
        Return fallback mixes (all trending) if generation fails
        """
        trending = self._get_trending_songs(limit=100)
        return {
            "dailyMix1": trending[:30],
            "dailyMix2": trending[30:60] if len(trending) > 30 else trending[:30],
            "discoverMix": trending[60:90] if len(trending) > 60 else trending[:30],
            "moodMix": trending[:30]
        }

    def _get_from_cache(self, key: str) -> Optional[Dict]:
        """Get data from cache if not expired"""
        if key in self.cache:
            data, timestamp = self.cache[key]
            if time.time() - timestamp < CACHE_TTL_DAILY_MIXES:
                return data
            else:
                del self.cache[key]
        return None

    def _set_in_cache(self, key: str, data: Dict, ttl: int) -> None:
        """Store data in cache with TTL"""
        self.cache[key] = (data, time.time())
        logger.info(f"Cached {key} for {ttl} seconds")

    def clear_cache(self, uid: Optional[str] = None) -> None:
        """
        Clear cache for specific user or all
        """
        if uid:
            cache_key = f"daily_mixes_{uid}"
            if cache_key in self.cache:
                del self.cache[cache_key]
                logger.info(f"Cleared cache for user: {uid}")
        else:
            self.cache.clear()
            logger.info("Cleared all cache")
