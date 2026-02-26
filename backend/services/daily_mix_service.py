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
        Generate Similar Artists Mix: Songs from NEW related artists
        
        ROBUST ALGORITHM with multiple fallback layers:
        1. Extract related artists with validation (not channel titles)
        2. Expand candidate pool if insufficient 
        3. Search with multiple formats + retries
        4. Temporarily relax filtering for discovery
        5. Relax diversity limits if empty to prevent 0 songs
        6. Fallback hierarchy (related → genre → collaborators → top artists → taste → trending)
        7. Ensure minimum songs before returning
        8. Comprehensive debug logging
        """
        try:
            logger.info("=" * 80)
            logger.info("[SimilarMix] STARTING SIMILAR ARTISTS MIX GENERATION")
            logger.info("=" * 80)
            
            top_artists = profile.get('top_artists', [])[:3]
            top_artist_names = set(top_artists)
            all_consumed = profile.get('all_consumed', set())
            
            if not top_artists:
                logger.warning("[SimilarMix] No top artists available")
                return self._fallback_trending()
            
            logger.info(f"[SimilarMix] Top artists: {top_artists}")
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 1 & 2: Extract & expand related artists with validation
            # ═══════════════════════════════════════════════════════════════════════════════
            
            related_artists_by_source = {
                'direct': Counter(),      # Direct related artists
                'secondary': Counter(),   # Related to related artists
                'collaborators': Counter(),  # Featured artists
                'album_artists': Counter()   # Artists from albums
            }
            artist_channel_ids = {}
            
            logger.info("[SimilarMix] Task 1 & 2: Extracting & expanding related artists...")
            
            # Primary: Extract from top artists
            for artist_name in top_artists:
                try:
                    logger.info(f"[SimilarMix] Extracting related artists for: {artist_name}")
                    related_list = self._extract_related_artists_robust(artist_name)
                    
                    logger.debug(f"[SimilarMix] Retrieved {len(related_list)} items for {artist_name}")
                    
                    for related_artist, channel_id in related_list:
                        # Filter - exclude original top artists
                        if related_artist.lower() not in {a.lower() for a in top_artist_names}:
                            related_artists_by_source['direct'][related_artist] += 1
                            artist_channel_ids[related_artist] = channel_id
                            logger.debug(f"[SimilarMix]   ✓ {related_artist}")
                    
                except Exception as e:
                    logger.warning(f"[SimilarMix] Error extracting related for {artist_name}: {str(e)}")
                    continue
            
            # Expand if needed: Extract secondary related artists
            if len(related_artists_by_source['direct']) < 5:
                logger.info("[SimilarMix] Expanding: Fetching secondary related artists...")
                try:
                    primary_related = list(related_artists_by_source['direct'].keys())[:3]
                    for primary_artist in primary_related:
                        try:
                            channel_id = artist_channel_ids.get(primary_artist)
                            if not channel_id:
                                continue
                            
                            secondary_list = self._extract_related_artists_robust(primary_artist)
                            for secondary_artist, sec_channel_id in secondary_list[:3]:
                                if secondary_artist.lower() not in {a.lower() for a in top_artist_names}:
                                    if secondary_artist not in related_artists_by_source['direct']:
                                        related_artists_by_source['secondary'][secondary_artist] += 1
                                        artist_channel_ids[secondary_artist] = sec_channel_id
                                        logger.debug(f"[SimilarMix]   ⊙ Secondary: {secondary_artist}")
                        except Exception as e:
                            logger.debug(f"[SimilarMix] Error getting secondary: {str(e)}")
                            continue
                except Exception as e:
                    logger.debug(f"[SimilarMix] Secondary expansion error: {str(e)}")
            
            # Expand: Extract featured artists & collaborators from top artists
            logger.info("[SimilarMix] Expanding: Fetching collaborators & featured artists...")
            try:
                for top_artist in top_artists[:2]:
                    channel_id = None
                    # Find channel ID for this top artist
                    try:
                        search_results = self.rec_service.ytmusic.search(top_artist, filter='artists', limit=1)
                        if search_results:
                            channel_id = search_results[0].get('browseId')
                    except:
                        pass
                    
                    if channel_id:
                        try:
                            artist_songs = self.rec_service._fetch_artist_songs_cached(channel_id)
                            for song in artist_songs[:5]:
                                # Extract featured artists from song
                                featured = song.get('artists', [])
                                for feat_artist_data in featured:
                                    feat_name = feat_artist_data.get('name') if isinstance(feat_artist_data, dict) else str(feat_artist_data)
                                    if feat_name and feat_name.lower() not in {a.lower() for a in top_artist_names}:
                                        related_artists_by_source['collaborators'][feat_name] += 1
                                        logger.debug(f"[SimilarMix]   ◇ Collaborator: {feat_name}")
                        except Exception as e:
                            logger.debug(f"[SimilarMix] Error extracting collaborators: {str(e)}")
            except Exception as e:
                logger.debug(f"[SimilarMix] Collaborator expansion error: {str(e)}")
            
            # Consolidate all artist sources
            all_related_artists = Counter()
            for source_name, source_counter in related_artists_by_source.items():
                all_related_artists.update(source_counter)
                logger.info(f"[SimilarMix] {source_name}: {len(source_counter)} artists")
            
            logger.info(f"[SimilarMix] TOTAL RELATED ARTISTS: {len(all_related_artists)}")
            if all_related_artists:
                logger.info(f"[SimilarMix] Top candidates: {[(a, c) for a, c in all_related_artists.most_common(5)]}")
            
            if not all_related_artists:
                logger.warning("[SimilarMix] No related artists found, using fallback")
                return self._similar_mix_fallback_hierarchy(profile, step="no_related")
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 3: Improve artist search success with retries & multiple formats
            # ═══════════════════════════════════════════════════════════════════════════════
            
            logger.info("[SimilarMix] Task 3: Searching songs with multiple formats...")
            
            ranked_artists = all_related_artists.most_common(15)
            songs_dict = {}
            artist_song_count = defaultdict(int)
            tracks_removed_by_filter = 0
            
            # Discovery diversity: Start relaxed, tighten if needed
            discovery_max_per_artist = 3  # More generous initially
            
            for related_artist_name, frequency_score in ranked_artists:
                try:
                    logger.debug(f"[SimilarMix] Processing: {related_artist_name} (score={frequency_score})")
                    channel_id = artist_channel_ids.get(related_artist_name)
                    
                    artist_songs_found = 0
                    
                    # Try fetching from channel ID (Primary)
                    if channel_id:
                        try:
                            logger.debug(f"[SimilarMix]   Attempt 1: Direct channel fetch")
                            artist_songs = self.rec_service._fetch_artist_songs_cached(channel_id)
                            artist_songs_found = len(artist_songs)
                            logger.debug(f"[SimilarMix]   Found {artist_songs_found} songs")
                        except Exception as e:
                            logger.debug(f"[SimilarMix]   Attempt 1 failed: {str(e)}")
                            artist_songs = []
                    else:
                        artist_songs = []
                    
                    # Fallback 1: Search with multiple formats
                    if len(artist_songs) < 3:
                        for query_format in [
                            f"{related_artist_name} songs",
                            f"{related_artist_name} top tracks",
                            f"{related_artist_name} official",
                            f"{related_artist_name}",
                        ]:
                            if len(artist_songs) >= 3:
                                break
                            try:
                                logger.debug(f"[SimilarMix]   Attempt: '{query_format}'")
                                results = self.rec_service._search_songs(query_format, limit=10)
                                if results:
                                    artist_songs.extend(list(results.values()))
                                    logger.debug(f"[SimilarMix]   Found {len(results)} via: {query_format}")
                            except Exception as e:
                                logger.debug(f"[SimilarMix]   Query failed: {str(e)}")
                                continue
                    
                    if not artist_songs:
                        logger.warning(f"[SimilarMix] No songs found for {related_artist_name} via any method")
                        continue
                    
                    # Add songs with relaxed filtering for Similar Mix
                    songs_before_filter = len(artist_songs)
                    for song in artist_songs:
                        video_id = song.get('videoId')
                        if not video_id:
                            continue
                        
                        # Skip if consumed
                        if video_id in all_consumed:
                            continue
                        
                        # Limit per artist (relaxed initially)
                        if artist_song_count[related_artist_name] >= discovery_max_per_artist:
                            continue
                        
                        songs_dict[video_id] = song
                        artist_song_count[related_artist_name] += 1
                    
                    songs_added = artist_song_count[related_artist_name]
                    logger.debug(f"[SimilarMix]   Added {songs_added} songs from {related_artist_name}")
                    
                except Exception as e:
                    logger.warning(f"[SimilarMix] Error processing {related_artist_name}: {str(e)}")
                    continue
            
            logger.info(f"[SimilarMix] Candidates gathered: {len(songs_dict)} songs from {len(artist_song_count)} artists")
            if not songs_dict:
                logger.critical("[SimilarMix] No songs gathered from any related artist")
                return self._similar_mix_fallback_hierarchy(profile, step="no_songs_from_related")
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 4: Apply filtering with RELAXED rules for discovery
            # ═══════════════════════════════════════════════════════════════════════════════
            
            logger.info("[SimilarMix] Task 4: Applying relaxed filtering for discovery...")
            
            # First pass: Normal filtering
            filtered_songs_list = filter_music_tracks(
                list(songs_dict.values()),
                source="similar_mix"  # Use "similar_mix" source for relaxed rules
            )
            
            logger.info(f"[SimilarMix] After filtering: {len(filtered_songs_list)} / {len(songs_dict)} songs")
            filtered_songs_dict = {s.get('videoId'): s for s in filtered_songs_list}
            
            # If filtering removed too many, use unfiltered as fallback
            if len(filtered_songs_dict) < len(songs_dict) * 0.3:  # If <30% remain
                logger.warning("[SimilarMix] Filtering too aggressive, relaxing restrictions...")
                logger.info(f"[SimilarMix] Using {len(songs_dict)} unfiltered songs as fallback")
                filtered_songs_dict = songs_dict
            
            if not filtered_songs_dict:
                logger.critical("[SimilarMix] No songs after filtering")
                return self._similar_mix_fallback_hierarchy(profile, step="all_filtered_out")
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 5: Apply diversity with relaxation if empty
            # ═══════════════════════════════════════════════════════════════════════════════
            
            logger.info("[SimilarMix] Task 5: Applying diversity layer...")
            
            songs = self._ensure_diversity_resilient(
                list(filtered_songs_dict.values()),
                profile.get('artist_listen_count', {}),
                all_consumed,
                exclude_played=True,
                min_threshold=20,
                mix_type="similar"
            )
            
            logger.info(f"[SimilarMix] After diversity: {len(songs)} songs")
            
            if not songs:
                logger.critical("[SimilarMix] ⚠️ Diversity removed ALL songs!")
                # TASK 5: Relax diversity - allow up to 3 per artist
                logger.info("[SimilarMix] Relaxing diversity limits (allow 3 per artist)...")
                songs = self._ensure_diversity_resilient(
                    list(filtered_songs_dict.values()),
                    profile.get('artist_listen_count', {}),
                    all_consumed,
                    exclude_played=False,  # Relax exclude_played
                    min_threshold=20,
                    max_per_artist=4,      # Relax to 4 per artist
                    mix_type="similar"
                )
                logger.info(f"[SimilarMix] After relaxed diversity: {len(songs)} songs")
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 6 & 7: Fallback hierarchy & ensure minimum output
            # ═══════════════════════════════════════════════════════════════════════════════
            
            if len(songs) < 10:
                logger.warning(f"[SimilarMix] Only {len(songs)} songs, using fallback hierarchy...")
                return self._similar_mix_fallback_hierarchy(profile, step="insufficient")
            
            logger.info(f"[SimilarMix] ✅ SUCCESS: Generated {len(songs)} quality songs")
            logger.info("=" * 80)
            
            final_mix = songs[:20]  # Trim to 20 songs for Similar Mix
            
            # ═══════════════════════════════════════════════════════════════════════════════
            # TASK 8: DEBUG LOGGING SUMMARY
            # ═══════════════════════════════════════════════════════════════════════════════
            
            artist_counts = Counter(s.get('artist') for s in final_mix if s.get('artist'))
            logger.info(f"[SimilarMix] Final Mix Artists: {len(artist_counts)} unique artists")
            logger.info(f"[SimilarMix]   Top artists in mix: {artist_counts.most_common(5)}")
            logger.info(f"[SimilarMix]   Final size: {len(final_mix)} tracks")
            
            return final_mix
            
        except Exception as e:
            logger.error(f"[SimilarMix] 🚨 CRITICAL ERROR: {str(e)}", exc_info=True)
            logger.info("[SimilarMix] Falling back to trending...")
            return self._fallback_trending()

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
        Generate Mood Mix: Contextual + Taste-filtered
        
        Process:
        1. Detect mood based on time of day
        2. Build taste profile from top artists and plays
        3. Fetch mood-based songs
        4. Score songs using hybrid: mood_score * 0.5 + taste_similarity * 0.4 + discovery_bonus * 0.1
        5. Rank and filter by combined score
        6. Include taste-aligned tracks, similar artist tracks, discovery tracks
        """
        try:
            logger.info("Generating Mood Mix")
            
            # Step 1: Detect mood based on time
            hour = datetime.now().hour
            if hour < 12:
                mood = "calm"
                mood_keywords = ["morning calm", "ambient", "peaceful", "soft"]
            elif hour < 18:
                mood = "energetic"
                mood_keywords = ["upbeat", "energetic", "workout", "motivation", "power"]
            else:
                mood = "chill"
                mood_keywords = ["chill", "relaxing", "evening", "acoustic", "lofi"]
            
            logger.info(f"[MoodMix] Detected mood: {mood} (hour={hour})")
            
            # Step 2: Build taste profile
            taste_profile = self._build_taste_profile(profile)
            logger.info(f"[MoodMix] Taste profile: {len(taste_profile.get('top_artists', []))} artists, "
                       f"{len(taste_profile.get('taste_albums', []))} albums")
            
            # Step 3 & 4: Fetch mood songs and score them
            scored_songs = self._fetch_and_score_mood_songs(
                mood_keywords,
                taste_profile,
                profile.get('all_consumed', set())
            )
            
            if not scored_songs:
                logger.warning("[MoodMix] No scored songs, using fallback strategy")
                return self._fallback_mood_mix(profile, taste_profile)
            
            # Step 5: Sort by score and filter
            sorted_songs = sorted(scored_songs, key=lambda x: x['_score'], reverse=True)
            top_titles = [f"{s.get('title', 'Unknown')[:20]}={s['_score']:.2f}" for s in sorted_songs[:5]]
            logger.info(f"[MoodMix] Top 5 scores: {top_titles}")
            
            # Extract just the song data without scores
            songs = [s for s in sorted_songs]
            
            # Ensure diversity
            songs = self._ensure_diversity(
                songs,
                profile.get('artist_listen_count', {}),
                profile.get('all_consumed', set()),
                exclude_played=False
            )
            
            logger.info(f"[MoodMix] Generated: {len(songs)} songs with mood={mood} + taste filtering")
            return songs[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"Error generating Mood Mix: {str(e)}")
            return self._get_trending_songs()
    
    # ==================== HELPER METHODS FOR IMPROVED RECOMMENDATIONS ====================
    
    def _extract_related_artists_robust(self, artist_name: str) -> List[tuple]:
        """
        ROBUST related artist extraction:
        - Extract artist names (NOT channel titles/IDs)
        - Ignore invalid entries
        - Normalize artist names
        - Multiple fallback strategies
        
        Returns: List of (artist_name, channel_id) tuples
        """
        try:
            logger.debug(f"[RelatedArtists·Robust] Extracting for: {artist_name}")
            
            # Strategy 1: Search for artist directly
            try:
                search_results = self.rec_service.ytmusic.search(artist_name, filter='artists', limit=1)
                
                if not search_results:
                    logger.debug(f"[RelatedArtists·Robust] Search found no artist")
                    return []
                
                artist_result = search_results[0]
                channel_id = artist_result.get('browseId')
                
                if not channel_id:
                    logger.debug(f"[RelatedArtists·Robust] No browseId in search result")
                    return []
                
                logger.debug(f"[RelatedArtists·Robust] Found artist with browseId: {channel_id}")
                
                # Fetch artist data
                artist_data = self.rec_service.ytmusic.get_artist(channel_id)
                related_artists = []
                
                # Extract related artists
                if artist_data.get('related', {}).get('results'):
                    for related in artist_data['related']['results'][:15]:  # Up to 15 related
                        if not isinstance(related, dict):
                            continue
                        
                        # Extract artist name - prioritize actual name fields
                        related_name = None
                        for name_field in ['name', 'title']:
                            candidate = related.get(name_field)
                            if candidate and isinstance(candidate, str) and len(candidate) > 0:
                                # Validate it's a real name (not a channel ID or weird string)
                                if not candidate.startswith(('UC', 'RDCLAK', 'RDTM')):
                                    related_name = candidate.strip()
                                    break
                        
                        if not related_name:
                            logger.debug(f"[RelatedArtists·Robust] Skipping invalid entry")
                            continue
                        
                        # Extract ID
                        related_id = None
                        for id_field in ['browseId', 'id', 'channelId']:
                            candidate_id = related.get(id_field)
                            if candidate_id and isinstance(candidate_id, str):
                                related_id = candidate_id
                                break
                        
                        if related_name and related_id:
                            related_artists.append((related_name, related_id))
                            logger.debug(f"[RelatedArtists·Robust]   ✓ {related_name}")
                
                logger.info(f"[RelatedArtists·Robust] Found {len(related_artists)} valid related for {artist_name}")
                return related_artists
                
            except Exception as e:
                logger.debug(f"[RelatedArtists·Robust] Strategy 1 failed: {str(e)}")
                return []
                
        except Exception as e:
            logger.warning(f"[RelatedArtists·Robust] Critical error for {artist_name}: {str(e)}")
            return []
    
    def _ensure_diversity_resilient(
        self,
        songs: List[Dict],
        artist_listen_count: Dict[str, int],
        all_consumed: Set[str],
        exclude_played: bool = False,
        min_threshold: int = 20,
        max_per_artist: int = 3,
        mix_type: str = "standard"
    ) -> List[Dict]:
        """
        RESILIENT diversity enforcement with proper artist extraction:
        
        1. Extract PRIMARY artist correctly (first from artists list)
        2. Normalize artist names (remove Topic, Official, VEVO)
        3. Log artist distribution BEFORE diversity
        4. First pass: Normal diversity limits
        5. If below min_threshold: Relax max_per_artist
        6. If <3 unique artists with >20 candidates: Re-evaluate normalization
        7. If still empty: Return unfiltered unique songs
        8. Never returns empty if input has songs
        """
        try:
            logger.info(f"[Diversity] Starting with {len(songs)} candidate songs")
            
            # ═══════════════════════════════════════════════════════════════════════════
            # TASK 1-2: Extract & normalize primary artist
            # ═══════════════════════════════════════════════════════════════════════════
            
            from services.music_filter import _process_artist_name
            
            # Pre-process: Normalize all artist names in songs
            normalized_songs = []
            artist_distribution = defaultdict(int)
            all_artists_in_candidates = set()
            
            for song in songs:
                # Extract primary artist - use artists list if available
                primary_artist = "Unknown"
                all_song_artists = []
                
                # Try artists list first (Task 1: use track-level artists list)
                artists_list = song.get('artists', [])
                if artists_list:
                    # Process each artist in the list
                    for artist_data in artists_list:
                        if isinstance(artist_data, dict):
                            artist_name = artist_data.get('name', '')
                        else:
                            artist_name = str(artist_data)
                        
                        if artist_name:
                            # Task 2: Normalize the artist name
                            normalized = _process_artist_name(artist_name)
                            if normalized:
                                all_song_artists.append(normalized)
                
                # Fallback: Use artist field if no artists list
                if not all_song_artists:
                    artist_field = song.get('artist', '')
                    if artist_field:
                        if isinstance(artist_field, str):
                            # Could be multi-artist format like "Artist1, Artist2"
                            for part in artist_field.split(','):
                                normalized = _process_artist_name(part.strip())
                                if normalized:
                                    all_song_artists.append(normalized)
                        elif isinstance(artist_field, dict):
                            normalized = _process_artist_name(artist_field.get('name', ''))
                            if normalized:
                                all_song_artists.append(normalized)
                
                # Set primary artist (first in list)
                if all_song_artists:
                    primary_artist = all_song_artists[0]
                    all_artists_in_candidates.update(all_song_artists)
                else:
                    primary_artist = "Unknown"
                    all_artists_in_candidates.add("Unknown")
                
                # Store normalized song
                normalized_song = dict(song)
                normalized_song['_primary_artist'] = primary_artist
                normalized_song['_all_artists'] = all_song_artists
                normalized_songs.append(normalized_song)
                
                # Track distribution
                artist_distribution[primary_artist] += 1
            
            # ═══════════════════════════════════════════════════════════════════════════
            # TASK 5: Log artist distribution before diversity
            # ═══════════════════════════════════════════════════════════════════════════
            
            logger.info(f"[Diversity] Candidates: {len(songs)} songs from {len(all_artists_in_candidates)} unique artists")
            
            # Log top artists distribution
            if artist_distribution:
                sorted_artists = sorted(artist_distribution.items(), key=lambda x: x[1], reverse=True)
                dist_str = ", ".join([f"{artist}→{count}" for artist, count in sorted_artists[:10]])
                logger.info(f"[Diversity] Artist distribution: {dist_str}")
            
            # ═══════════════════════════════════════════════════════════════════════════
            # TASK 6: Prevent false collapse
            # ═══════════════════════════════════════════════════════════════════════════
            
            if len(normalized_songs) > 20 and len(all_artists_in_candidates) < 3:
                logger.warning(f"[Diversity] ⚠️ False collapse detected: {len(normalized_songs)} songs but only {len(all_artists_in_candidates)} artists")
                logger.warning(f"[Diversity] Re-evaluating normalization...")
                # Use raw artist data without normalization
                for song in normalized_songs:
                    # Override to use raw artist names
                    raw_artists = song.get('artists', [])
                    if raw_artists:
                        song['_primary_artist'] = raw_artists[0].get('name') if isinstance(raw_artists[0], dict) else str(raw_artists[0])
            
            # ═══════════════════════════════════════════════════════════════════════════
            # TASK 3-4: Apply diversity tracking with primary + secondary artists
            # ═══════════════════════════════════════════════════════════════════════════
            
            result = []
            artist_count = defaultdict(int)
            seen_ids = set()
            
            current_max = max_per_artist
            
            for pass_num in range(4):  # Multiple passes with relaxing constraints
                if pass_num == 1:
                    current_max = max_per_artist + 1
                    logger.debug(f"[Diversity] Pass 2: Relaxing to {current_max} per artist")
                elif pass_num == 2:
                    current_max = max_per_artist + 2
                    exclude_played = False
                    logger.debug(f"[Diversity] Pass 3: Further relaxing to {current_max} per artist, include played")
                elif pass_num == 3:
                    # Last pass: Fallback to any artist if still insufficient
                    current_max = max_per_artist + 5
                    logger.debug(f"[Diversity] Pass 4: Aggressive relaxation to {current_max} per artist")
                
                result = []
                artist_count = defaultdict(int)
                secondary_artist_count = defaultdict(int)
                seen_ids = set()
                
                for song in normalized_songs:
                    video_id = song.get('videoId')
                    primary_artist = song.get('_primary_artist', 'Unknown')
                    all_song_artists = song.get('_all_artists', [primary_artist])
                    
                    # Skip if already seen
                    if video_id in seen_ids:
                        continue
                    
                    # Skip if consumed (only if exclude_played enforced)
                    if exclude_played and video_id in all_consumed:
                        continue
                    
                    # TASK 3: Count by primary artist, but allow secondary artists for uniqueness
                    # If primary artist hit limit, check if we can use secondary artists
                    if artist_count[primary_artist] >= current_max:
                        # Check if can add via secondary artist
                        used_secondary = False
                        for secondary_artist in all_song_artists[1:]:  # Secondary artists
                            if secondary_artist_count[secondary_artist] < 1:
                                secondary_artist_count[secondary_artist] += 1
                                used_secondary = True
                                break
                        
                        if not used_secondary:
                            # Both primary and secondaries exhausted
                            continue
                    
                    result.append(song)
                    seen_ids.add(video_id)
                    artist_count[primary_artist] += 1
                
                if len(result) >= min_threshold:
                    logger.info(f"[Diversity] Pass {pass_num + 1}: Achieved {len(result)} songs (threshold={min_threshold})")
                    break
            
            if not result and normalized_songs:
                # Last resort: return unique songs without diversity limits
                logger.warning(f"[Diversity] ⚠️ Forced to bypass diversity to prevent empty. Returning unique songs...")
                seen_ids = set()
                for song in normalized_songs:
                    video_id = song.get('videoId')
                    if video_id and video_id not in seen_ids:
                        result.append(song)
                        seen_ids.add(video_id)
                        if len(result) >= min_threshold:
                            break
            
            # ═══════════════════════════════════════════════════════════════════════════
            # TASK 7: Ensure minimum mix size with artist count
            # ═══════════════════════════════════════════════════════════════════════════
            
            result_artists = {song.get('_primary_artist', 'Unknown') for song in result if song.get('_primary_artist')}
            
            logger.info(f"[Diversity] Final result: {len(result)} unique songs, {len(result_artists)} artists")
            if result_artists and len(result_artists) > 0:
                logger.debug(f"[Diversity] Artists: {', '.join(sorted(result_artists)[:10])}")
            
            # Clean up temporary fields before returning
            for song in result:
                song.pop('_primary_artist', None)
                song.pop('_all_artists', None)
            
            return result
            
        except Exception as e:
            logger.error(f"[Diversity] Error in diversity enforcement: {str(e)}", exc_info=True)
            # Last resort: return input as-is
            return songs[:min_threshold] if songs else []
    
    def _similar_mix_fallback_hierarchy(self, profile: Dict, step: str = "unknown") -> List[Dict]:
        """
        CRITICAL FALLBACK HIERARCHY for Similar Mix:
        
        If primary method fails at any step, execute fallback hierarchy:
        1. Related artists (primary - already attempted)
        2. Similar genre artists
        3. Collaborators & featured artists
        4. Lesser-known tracks from top artists
        5. Taste-based recommendations
        6. Trending songs matching taste
        7. Pure trending as last resort
        """
        try:
            logger.info(f"[SimilarMix·Fallback] Activating fallback hierarchy (failed at: {step})")
            all_consumed = profile.get('all_consumed', set())
            songs_dict = {}
            
            # ─────────────────────────────────────────────────────────────────────────────
            # FALLBACK 2: Similar genre artists
            # ─────────────────────────────────────────────────────────────────────────────
            
            logger.info("[SimilarMix·Fallback] Strategy 2: Genre-based artists")
            try:
                top_artists = profile.get('top_artists', [])[:2]
                for genre_query in ['indie artists', 'alternative artists', 'progressive artists']:
                    try:
                        results = self.rec_service._search_songs(f"{genre_query}", limit=15)
                        for song in results.values():
                            video_id = song.get('videoId')
                            if video_id and video_id not in all_consumed:
                                songs_dict[video_id] = song
                        logger.info(f"[SimilarMix·Fallback]   Added {len(results)} from genre search")
                    except:
                        continue
            except Exception as e:
                logger.debug(f"[SimilarMix·Fallback] Genre search failed: {str(e)}")
            
            # ─────────────────────────────────────────────────────────────────────────────
            # FALLBACK 3: Collaborators & featured from top artists
            # ─────────────────────────────────────────────────────────────────────────────
            
            if len(songs_dict) < 20:
                logger.info("[SimilarMix·Fallback] Strategy 3: Collaborators from top artists")
                try:
                    for top_artist in profile.get('top_artists', [])[:3]:
                        try:
                            # Get songs from top artist, extract collaborators
                            results = self.rec_service._search_songs(f"{top_artist} feat", limit=10)
                            for song in results.values():
                                video_id = song.get('videoId')
                                if video_id and video_id not in all_consumed:
                                    songs_dict[video_id] = song
                        except:
                            continue
                    logger.info(f"[SimilarMix·Fallback]   Total songs now: {len(songs_dict)}")
                except Exception as e:
                    logger.debug(f"[SimilarMix·Fallback] Collaborator search failed: {str(e)}")
            
            # ─────────────────────────────────────────────────────────────────────────────
            # FALLBACK 4: Lesser-known tracks from top artists
            # ─────────────────────────────────────────────────────────────────────────────
            
            if len(songs_dict) < 20:
                logger.info("[SimilarMix·Fallback] Strategy 4: Deep cuts from top artists")
                try:
                    for top_artist in profile.get('top_artists', [])[:3]:
                        try:
                            results = self.rec_service._search_songs(f"{top_artist} deep cuts", limit=8)
                            for song in results.values():
                                video_id = song.get('videoId')
                                if video_id and video_id not in all_consumed:
                                    songs_dict[video_id] = song
                        except:
                            continue
                    logger.info(f"[SimilarMix·Fallback]   Total songs now: {len(songs_dict)}")
                except Exception as e:
                    logger.debug(f"[SimilarMix·Fallback] Deep cuts search failed: {str(e)}")
            
            # ─────────────────────────────────────────────────────────────────────────────
            # FALLBACK 5: Taste-based recommendations
            # ─────────────────────────────────────────────────────────────────────────────
            
            if len(songs_dict) < 20:
                logger.info("[SimilarMix·Fallback] Strategy 5: Taste-based recommendations")
                try:
                    taste_profile = self._build_taste_profile(profile)
                    for keyword in taste_profile.get('top_artists', [])[:3]:
                        try:
                            results = self.rec_service._search_songs(f"{keyword} similar", limit=8)
                            for song in results.values():
                                video_id = song.get('videoId')
                                if video_id and video_id not in all_consumed:
                                    songs_dict[video_id] = song
                        except:
                            continue
                    logger.info(f"[SimilarMix·Fallback]   Total songs now: {len(songs_dict)}")
                except Exception as e:
                    logger.debug(f"[SimilarMix·Fallback] Taste search failed: {str(e)}")
            
            # ─────────────────────────────────────────────────────────────────────────────
            # FALLBACK 6: Trending songs (country-specific)
            # ─────────────────────────────────────────────────────────────────────────────
            
            if len(songs_dict) < 20:
                logger.info("[SimilarMix·Fallback] Strategy 6: Trending songs")
                try:
                    trending = self._get_trending_songs(limit=30)
                    for song in trending:
                        video_id = song.get('videoId')
                        if video_id and video_id not in all_consumed:
                            songs_dict[video_id] = song
                    logger.info(f"[SimilarMix·Fallback]   Total songs now: {len(songs_dict)}")
                except Exception as e:
                    logger.debug(f"[SimilarMix·Fallback] Trending failed: {str(e)}")
            
            # Apply diversity and return
            if songs_dict:
                logger.info(f"[SimilarMix·Fallback] Fallback gathered {len(songs_dict)} songs")
                songs = self._ensure_diversity_resilient(
                    list(songs_dict.values()),
                    profile.get('artist_listen_count', {}),
                    all_consumed,
                    exclude_played=False,
                    min_threshold=15,
                    max_per_artist=4,
                    mix_type="fallback"
                )
                logger.info(f"[SimilarMix·Fallback] ✅ Fallback success: {len(songs)} songs")
                return songs[:self.MIX_SIZE]
            else:
                logger.warning("[SimilarMix·Fallback] All fallback strategies exhausted")
                return self._fallback_trending(limit=self.MIX_SIZE)
            
        except Exception as e:
            logger.error(f"[SimilarMix·Fallback] Critical error: {str(e)}", exc_info=True)
            return self._fallback_trending(limit=self.MIX_SIZE)
    
    def _fallback_trending(self, limit: int = 30) -> List[Dict]:
        """
        LAST RESORT: Return trending songs
        """
        try:
            logger.info(f"[Fallback·Trending] Getting {limit} trending songs as ultimate fallback")
            trending = self._get_trending_songs(limit=limit)
            return trending if trending else []
        except Exception as e:
            logger.error(f"[Fallback·Trending] Failed: {str(e)}")
            return []
    
    def _extract_related_artists(self, artist_name: str) -> List[tuple]:
        """
        Extract related artists from YTMusic artist data
        
        Returns: List of (artist_name, channel_id) tuples
        """
        try:
            # Search for the artist directly using artist filter
            search_results = self.rec_service.ytmusic.search(artist_name, filter='artists', limit=1)
            
            if not search_results:
                logger.warning(f"[RelatedArtists] Could not find artist: {artist_name}")
                return []
            
            # Extract the artist's browse ID (channel ID)
            artist_result = search_results[0]
            channel_id = artist_result.get('browseId')
            
            if not channel_id:
                logger.warning(f"[RelatedArtists] No browseId found for {artist_name}")
                return []
            
            # Fetch artist data (which includes related artists)
            artist_data = self.rec_service.ytmusic.get_artist(channel_id)
            
            related_artists = []
            if artist_data.get('related', {}).get('results'):
                for related in artist_data['related']['results'][:10]:  # Top 10 related
                    # Ensure related is a dictionary, not a string
                    if not isinstance(related, dict):
                        continue
                    
                    related_name = related.get('name') or related.get('title')
                    related_id = related.get('id') or related.get('browseId') or related.get('channelId')
                    
                    if related_name and related_id:
                        related_artists.append((related_name, related_id))
            
            logger.info(f"[RelatedArtists] Found {len(related_artists)} related to {artist_name}")
            return related_artists
            
        except Exception as e:
            logger.warning(f"[RelatedArtists] Error extracting for {artist_name}: {str(e)}")
            return []
    
    def _build_taste_profile(self, profile: Dict) -> Dict:
        """
        Build a taste profile from user's listening history
        
        Returns:
        {
            'top_artists': List of top 5 artist names,
            'taste_albums': Set of album names,
            'top_artist_names': Set of original top artist names for matching,
            'artist_listen_count': Dict of artist -> play count
        }
        """
        try:
            taste_profile = {
                'top_artists': profile.get('top_artists', [])[:5],
                'taste_albums': set(),
                'top_artist_names': set(profile.get('top_artists', [])[:5]),
                'artist_listen_count': profile.get('artist_listen_count', {})
            }
            
            # Extract albums from plays
            for play in profile.get('plays', [])[:20]:
                album = play.get('album')
                if album:
                    taste_profile['taste_albums'].add(album.lower())
            
            logger.info(f"[TasteProfile] Built: {len(taste_profile['top_artists'])} artists, "
                       f"{len(taste_profile['taste_albums'])} albums")
            return taste_profile
            
        except Exception as e:
            logger.warning(f"[TasteProfile] Error building profile: {str(e)}")
            return {
                'top_artists': profile.get('top_artists', [])[:5],
                'taste_albums': set(),
                'top_artist_names': set(),
                'artist_listen_count': {}
            }
    
    def _fetch_and_score_mood_songs(self, mood_keywords: List[str], taste_profile: Dict, all_consumed: Set[str]) -> List[Dict]:
        """
        Fetch songs for mood and score them using hybrid scoring:
        final_score = mood_relevance * 0.5 + taste_similarity * 0.4 + discovery_bonus * 0.1
        """
        try:
            scored_songs = []
            seen_ids = set()
            
            # Fetch songs for each mood keyword
            for keyword in mood_keywords:
                try:
                    results = self.rec_service._search_songs(f"{keyword} music", limit=20)
                    
                    for song in results.values():
                        video_id = song.get('videoId')
                        if not video_id or video_id in seen_ids:
                            continue
                        
                        seen_ids.add(video_id)
                        
                        # Score the song
                        mood_score = 0.8  # Base mood match
                        taste_score = self._calculate_taste_similarity(song, taste_profile)
                        discovery_bonus = 0.1 if video_id not in all_consumed else 0.0
                        
                        # Hybrid scoring
                        final_score = (mood_score * 0.5) + (taste_score * 0.4) + (discovery_bonus * 0.1)
                        
                        song['_score'] = final_score
                        song['_mood_score'] = mood_score
                        song['_taste_score'] = taste_score
                        
                        scored_songs.append(song)
                    
                    logger.debug(f"[MoodScore] Added {len(results)} songs for keyword '{keyword}'")
                    
                except Exception as e:
                    logger.warning(f"[MoodScore] Error fetching for '{keyword}': {str(e)}")
                    continue
            
            logger.info(f"[MoodScore] Fetched and scored {len(scored_songs)} total songs")
            return scored_songs
            
        except Exception as e:
            logger.error(f"[MoodScore] Error in scoring: {str(e)}")
            return []
    
    def _calculate_taste_similarity(self, song: Dict, taste_profile: Dict) -> float:
        """
        Calculate taste similarity for a song (0.0 - 1.0)
        
        Factors:
        - Artist match with user's top artists (0.7)
        - Album match with user's taste albums (0.5)
        - Default (new artist in mood zone) (0.3)
        """
        try:
            song_artist = song.get('artist', '').lower()
            song_artists = [a.get('name', '').lower() for a in song.get('artists', [])]
            song_album = song.get('album', '').lower()
            
            # Check for exact artist match
            for top_artist in taste_profile.get('top_artists', []):
                if top_artist.lower() in song_artists or song_artist == top_artist.lower():
                    return 0.9  # Very high match
            
            # Check for album match
            if song_album and song_album in taste_profile.get('taste_albums', set()):
                return 0.7  # Album match
            
            # Partial artist name match (similar composer/artist)
            for top_artist in taste_profile.get('top_artists', []):
                if top_artist.lower()[:4] in song_artist[:4]:  # Check first 4 chars
                    return 0.5
            
            # Default: new artist in this mood zone
            return 0.3
            
        except Exception as e:
            logger.debug(f"[TasteSim] Error calculating: {str(e)}")
            return 0.2

    def _fallback_mood_mix(self, profile: Dict, taste_profile: Dict) -> List[Dict]:
        """
        Fallback strategy for Mood Mix:
        1. Include similar artists' mellow tracks
        2. Include top artist mellow tracks
        3. Include mood-trending songs
        """
        try:
            logger.info("[MoodMix] Engaging fallback strategy")
            all_consumed = profile.get('all_consumed', set())
            songs_dict = {}
            
            # Strategy 1: Similar artists within mood zone
            try:
                top_artists = profile.get('top_artists', [])[:2]
                for artist_name in top_artists:
                    related = self._extract_related_artists(artist_name)
                    for related_name, channel_id in related[:3]:
                        try:
                            artist_songs = self.rec_service._fetch_artist_songs_cached(channel_id)
                            for song in artist_songs[:5]:
                                if song.get('videoId') and song.get('videoId') not in all_consumed:
                                    songs_dict[song['videoId']] = song
                        except Exception as e:
                            logger.debug(f"[MoodFallback] Error from similar artist: {str(e)}")
                            continue
            except Exception as e:
                logger.warning(f"[MoodFallback] Error in similar artists strategy: {str(e)}")
            
            # Strategy 2: Top artist mellow tracks
            try:
                for artist_name in profile.get('top_artists', [])[:3]:
                    mellow_results = self.rec_service._search_songs(f"{artist_name} acoustic", limit=10)
                    songs_dict.update(mellow_results)
            except Exception as e:
                logger.debug(f"[MoodFallback] Error in top artist strategy: {str(e)}")
            
            # Strategy 3: Mood trending
            try:
                trending = self._get_trending_songs(limit=20)
                for song in trending:
                    if song.get('videoId'):
                        songs_dict[song['videoId']] = song
            except Exception as e:
                logger.debug(f"[MoodFallback] Error in trending strategy: {str(e)}")
            
            if not songs_dict:
                logger.warning("[MoodFallback] All strategies failed, using pure trending")
                return self._get_trending_songs()
            
            # Ensure diversity
            songs = self._ensure_diversity(
                list(songs_dict.values()),
                profile.get('artist_listen_count', {}),
                all_consumed,
                exclude_played=False
            )
            
            logger.info(f"[MoodFallback] Generated {len(songs)} songs via fallback strategies")
            return songs[:self.MIX_SIZE]
            
        except Exception as e:
            logger.error(f"[MoodFallback] Critical error: {str(e)}")
            return self._get_trending_songs()

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
