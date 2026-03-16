"""
Recommendation Service - Industry-level personalized recommendations
Uses JioSaavn APIs for candidate retrieval while preserving recommendation logic
"""
from typing import List, Dict, Optional, Set, Tuple
import logging
import os
import time
import math
import importlib
from datetime import datetime, timezone
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter
from services.music_filter import filter_music_tracks
from services.collaborative_service import CollaborativeFilteringService
from services.jiosaavn_service import JioSaavnService
from services.user_service import get_firestore_client
from services.artist_normalization import normalized_artist_key

try:
    importlib.import_module('firebase_admin')
    firestore = importlib.import_module('firebase_admin.firestore')
    FIRESTORE_AVAILABLE = True
except Exception:
    firestore = None
    FIRESTORE_AVAILABLE = False

logger = logging.getLogger(__name__)

DEFAULT_SERVICE_ACCOUNT_FILENAME = (
    'aura-music-65802-firebase-adminsdk-fbsvc-86f9c08a71.json'
)


class RecommendationService:
    """
    Generates personalized recommendations using:
    - User play history from Firestore
    - Artist similarity and related songs
    - Trending charts as cold-start fallback
    - Intelligent ranking with scoring weights
    """
    
    # Scoring weights for recommendation sources
    SCORE_WEIGHTS = {
        'song_related': 5,      # From song-based recommendations
        'artist_songs': 3,      # From top artists' songs
        'related_artists': 2,   # From related artists
        'search': 2,            # From search queries
        'trending': 1,          # Trending fallback
    }
    
    # Cache TTL in seconds
    CACHE_TTL_ARTIST = 3600    # 1 hour
    CACHE_TTL_RELATED = 1800   # 30 minutes
    CACHE_TTL_USER_SIGNALS = 600  # 10 minutes
    CACHE_TTL_RECOMMENDATIONS = 3600  # 1 hour - cache final recommendation results
    CACHE_TTL_USER_PROFILE = 1800  # 30 minutes - cache user taste profile
    MAX_CANDIDATE_POOL = 120  # STRICT LIMIT: Maximum candidate pool size
    QUERY_RESULT_LIMIT = 25
    MIN_CANDIDATE_POOL = 60
    HISTORY_QUERY_LIMIT = 8
    MAX_SEARCH_QUERIES = 8  # STRICT LIMIT: Maximum number of search queries to prevent infinite loops

    # Validation limits and filters
    VALIDATION_SCAN_LIMIT = 25
    ALLOWED_MUSIC_VIDEO_TYPES = {
        'MUSIC_VIDEO_TYPE_ATV',
        'MUSIC_VIDEO_TYPE_OMV',
        'MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK',
    }
    TITLE_BLOCKLIST = [
        'interview',
        'trailer',
        'review',
        'press',
        'reaction',
        'behind the scenes',
        'making',
        'podcast',
        'episode',
        'full movie',
        'teaser',
    ]
    
    def __init__(self, user_service, jiosaavn_service: Optional[JioSaavnService] = None):
        self.user_service = user_service
        self.jiosaavn_service = jiosaavn_service or JioSaavnService()
        
        # Cache: {key: (data, timestamp)}
        self.cache_artist: Dict[str, Tuple[Dict, float]] = {}
        self.cache_related: Dict[str, Tuple[List, float]] = {}
        self.cache_user_signals: Dict[str, Tuple[Dict[str, List[Dict]], float]] = {}
        self.cache_user_artists: Dict[str, Tuple[Set[str], float]] = {}
        self.cache_recommendations: Dict[str, Tuple[Dict, float]] = {}  # Cache final recommendations
        self.cache_user_profile: Dict[str, Tuple[Dict, float]] = {}  # Cache user taste profile (weighted signals)
        self.cache_ranking_context: Dict[str, Tuple[Dict, float]] = {}
        
        self.max_workers = 6  # Parallel query fan-out for candidate retrieval
        self._firestore_db = None
        
        # Time-decay configuration for recommendation freshness
        self.decay_lambda = float(os.getenv('RECOMMENDER_DECAY_LAMBDA', '0.15'))
        logger.info(f"RecommendationService initialized with decay_lambda={self.decay_lambda}")
        
        self._ensure_firestore_client()
        
        # Initialize collaborative filtering service with reference to this service
        self.cf_service = CollaborativeFilteringService(
            user_service, 
            recommendation_service=self,  # Pass self for unified profile building
            decay_lambda=self.decay_lambda
        )
        logger.info("Collaborative filtering service initialized")

    def _ensure_firestore_client(self) -> None:
        """Initialize Firebase Admin SDK and Firestore client if possible."""
        if self._firestore_db:
            return

        try:
            # First try to get from user_service if it already has a connection
            if getattr(self.user_service, 'db', None):
                self._firestore_db = self.user_service.db
                logger.info("✅ Firestore connected via user_service")
                return

            # Otherwise try to initialize our own connection
            db = get_firestore_client()
            if db is not None:
                self._firestore_db = db
                if hasattr(self.user_service, 'db'):
                    self.user_service.db = db
                logger.info("✅ Firestore connected successfully")
            else:
                logger.warning("⚠️  Firestore unavailable - check FIREBASE_CREDENTIALS env variable")
        except Exception as e:
            logger.warning(f"⚠️  Firestore client initialization failed: {str(e)}")
    
    def get_recommendations(self, uid: str, limit: int = 20) -> Dict:
        """
        Generate personalized recommendations for a user.
        
        Args:
            uid: User ID
            limit: Number of recommendations to return
            
        Returns:
            Dict with recommended songs
        """
        try:
            start_ts = time.perf_counter()
            cache_hit = False

            # Check cache first for instant response
            cache_key = f"recommendations_{uid}_{limit}"
            cached = self.cache_recommendations.get(cache_key)
            if cached:
                cached_value, timestamp = cached
                if time.time() - timestamp < self.CACHE_TTL_RECOMMENDATIONS:
                    logger.info(f"Returning cached recommendations for {uid} (age: {int(time.time() - timestamp)}s)")
                    cache_hit = True
                    elapsed_ms = (time.perf_counter() - start_ts) * 1000
                    logger.info("Recommendation generation time: %.2f ms", elapsed_ms)
                    logger.info("Cache hit rate: %s", cache_hit)
                    return cached_value
            
            logger.info(f"Generating recommendations for user: {uid}")

            # Dynamic candidate sizing keeps small-limit requests fast while preserving quality.
            candidate_pool_limit = min(self.MAX_CANDIDATE_POOL, max(self.MIN_CANDIDATE_POOL, limit * 8))
            per_query_limit = min(self.QUERY_RESULT_LIMIT, max(10, limit * 3))
            
            # 1. Fetch user signals (plays + liked songs)
            user_signals = self._fetch_user_signals(uid)
            user_plays = user_signals['plays']
            user_liked = user_signals['liked']

            logger.info("User plays count: %d", len(user_plays))
            logger.info("User liked songs: %d", len(user_liked))

            # Create sets for faster lookup and filtering
            played_ids: Set[str] = {
                str(song_id)
                for s in user_plays
                for song_id in [s.get('videoId'), s.get('id')]
                if song_id
            }
            all_consumed: Set[str] = played_ids

            # 2. Build weighted signals (liked songs have 2x weight) - with caching
            weighted_signals = self._build_weighted_signals_cached(uid, user_plays, user_liked)
            top_artists = weighted_signals['top_artists']
            top_albums = weighted_signals['top_albums']
            ranking_context = self._build_ranking_context_cached(uid, user_plays, user_liked, weighted_signals)
            top_starring = ranking_context.get('top_starring', [])
            logger.info("Top artists detected: %s", top_artists)
            logger.info("Top albums detected: %s", top_albums)
            logger.info("Top starring actors detected: %s", top_starring)
            logger.info("Top starring actors: %s", top_starring)
            logger.info(
                "User profile built: %s",
                {
                    'top_artists': top_artists,
                    'top_albums': top_albums,
                    'artist_count': len(weighted_signals.get('artist_scores', {})),
                    'album_count': len(weighted_signals.get('album_scores', {})),
                }
            )
            
            logger.info(f"Weighted artists count: {len(weighted_signals['artist_scores'])}")

            # 3. Gather recommendations using search queries - PARALLEL EXECUTION
            recommendations: Dict[str, Dict] = {}  # {videoId: song_data}
            is_cold_start = len(user_plays) == 0 and len(user_liked) == 0

            if not is_cold_start:
                search_queries = self._build_search_queries(top_artists, top_albums, top_starring)
                logger.info("Recommendation queries: %s", search_queries)
                logger.info(f"Enforcing MAX_SEARCH_QUERIES={self.MAX_SEARCH_QUERIES}, MAX_CANDIDATES={self.MAX_CANDIDATE_POOL}")

                # Map legacy Firestore songs (old API IDs) to JioSaavn by song name + primary artist.
                history_candidates = self._map_user_history_to_saavn_candidates(user_plays, user_liked)
                self._merge_recommendations(
                    recommendations,
                    history_candidates,
                    'song_related',
                    max_candidates=candidate_pool_limit,
                )
                
                # Execute all search queries in parallel for faster results
                query_count = 0
                with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                    futures = {
                        executor.submit(self._search_songs, query, per_query_limit): query
                        for query in search_queries
                    }
                    
                    for future in as_completed(futures):
                        try:
                            query_count += 1
                            search_results = future.result()
                            self._merge_recommendations(
                                recommendations,
                                search_results,
                                'search',
                                max_candidates=candidate_pool_limit,
                            )
                            if len(recommendations) >= candidate_pool_limit:
                                # Candidate cap keeps downstream ranking fast.
                                break
                        except Exception as e:
                            logger.warning(f"Error in parallel search query: {str(e)}")
                
                logger.info(f"Executed {query_count}/{len(search_queries)} search queries, added {len(recommendations)} candidates")
                logger.info("Candidates generated: %d", len(recommendations))

            # Trending fallback layer (cold-start)
            if is_cold_start:
                trending_recommendations = self._get_trending_fallback(
                    per_query_limit=per_query_limit,
                    max_candidates=candidate_pool_limit,
                )
                self._merge_recommendations(
                    recommendations,
                    trending_recommendations,
                    'trending',
                    max_candidates=candidate_pool_limit,
                )
                # Guarantee minimum recommendations with trending fallback
                seen_ids = set(recommendations.keys())
                while len(recommendations) < limit:
                    more = self._get_trending_fallback(per_query_limit=per_query_limit, max_candidates=candidate_pool_limit)
                    for song in more:
                        sid = song.get('videoId') or song.get('id')
                        if sid and sid not in seen_ids:
                            recommendations[sid] = song
                            seen_ids.add(sid)
                        if len(recommendations) >= limit:
                            break
                    if not more:
                        break
                logger.info(f"Added {len(trending_recommendations)} from trending (cold-start)")
                logger.info("Candidates generated: %d", len(recommendations))
            
            # 4. Deduplicate, filter, and rank
            ranked_recommendations = self._deduplicate_and_filter(
                recommendations,
                all_consumed,
                max(limit * 3, 60),
                ranking_context=ranking_context,
                recent_played_titles=ranking_context.get('recent_titles', set())
            )
            final_recommendations = self._apply_readme_diversity_layer(
                ranked_recommendations,
                limit=limit,
                max_per_artist=2,
                max_per_album=3,
            )
            logger.info("Recommendations after filtering: %d", len(final_recommendations))

            # Ensure we return exactly the requested limit
            final_recommendations = final_recommendations[:limit]
            
            final_count = len(final_recommendations)
            logger.info("Final recommendations: %d", final_count)
            
            # Guarantee at least one recommendation (fallback to trending if needed)
            if not final_recommendations:
                trending_recommendations = self._get_trending_fallback(per_query_limit=per_query_limit, max_candidates=1)
                if trending_recommendations:
                    final_recommendations = [trending_recommendations[0]]
            result = {
                'count': len(final_recommendations),
                'source': 'recommendation_engine',
                'results': final_recommendations
            }
            
            # Cache the result for faster subsequent loads
            self.cache_recommendations[cache_key] = (result, time.time())
            logger.info(f"Cached recommendations for {uid} (TTL: {self.CACHE_TTL_RECOMMENDATIONS}s)")
            elapsed_ms = (time.perf_counter() - start_ts) * 1000
            logger.info("Recommendation generation time: %.2f ms", elapsed_ms)
            logger.info("Cache hit rate: %s", cache_hit)
            
            return result
            
        except Exception as e:
            logger.error(f"Error generating recommendations for {uid}: {str(e)}", exc_info=True)
            # Graceful failure: return empty recommendations
            return {
                'count': 0,
                'source': 'recommendation_engine',
                'results': []
            }
    
    def _fetch_user_plays(self, uid: str) -> List[Dict]:
        """
        Fetch user's plays from Firestore.
        Reads from: users/{uid}/plays
        
        Expected document fields:
        - song: song name
        - primary_artists: comma-separated artist names
        - starring: comma-separated actor names
        - album: album name
        - timestamp: Unix timestamp or Firestore timestamp
        - play_count: number of times played (default: 1)
        """
        try:
            self._ensure_firestore_client()
            db = self._firestore_db or getattr(self.user_service, 'db', None)

            if not db:
                logger.warning(f"⚠️  Firestore client not available for user {uid[:8]}. Cannot fetch plays.")
                logger.warning("   Check FIREBASE_CREDENTIALS environment variable")
                return []

            # Read all documents from users/{uid}/plays collection
            plays_ref = db.collection('users').document(uid).collection('plays')
            plays_docs = list(plays_ref.stream())
            
            logger.info(f"🔍 Firestore query: users/{uid[:8]}.../plays → {len(plays_docs)} documents")
            
            # Convert and normalize documents
            plays = []
            for doc in plays_docs:
                doc_data = doc.to_dict()
                if doc_data:
                    normalized = self._normalize_user_signal_doc(doc_data, is_play=True)
                    plays.append(normalized)
            
            if not plays:
                logger.info(f"   ℹ️  No play history found in Firestore for user {uid[:8]}")
                logger.info(f"   Make sure frontend writes to: users/{uid}/plays/<playId>")
            
            return plays
            
        except Exception as e:
            logger.error(f"❌ Firestore error fetching plays for {uid[:8]}: {str(e)}", exc_info=True)
            return []
    
    def _fetch_user_liked_songs(self, uid: str) -> List[Dict]:
        """
        Fetch user's liked songs from Firestore.
        Reads from: users/{uid}/likedSongs
        
        Expected document fields:
        - song: song name
        - primary_artists: comma-separated artist names
        - starring: comma-separated actor names
        - album: album name
        - timestamp: Unix timestamp or Firestore timestamp
        """
        try:
            self._ensure_firestore_client()
            db = self._firestore_db or getattr(self.user_service, 'db', None)

            if not db:
                logger.warning(f"⚠️  Firestore client not available for user {uid[:8]}. Cannot fetch liked songs.")
                logger.warning("   Check FIREBASE_CREDENTIALS environment variable")
                return []

            # Read all documents from users/{uid}/likedSongs collection
            liked_ref = db.collection('users').document(uid).collection('likedSongs')
            liked_docs = list(liked_ref.stream())
            
            logger.info(f"🔍 Firestore query: users/{uid[:8]}.../likedSongs → {len(liked_docs)} documents")
            
            # Convert and normalize documents
            liked = []
            for doc in liked_docs:
                doc_data = doc.to_dict()
                if doc_data:
                    normalized = self._normalize_user_signal_doc(doc_data, is_play=False)
                    liked.append(normalized)
            
            if not liked:
                logger.info(f"   ℹ️  No liked songs found in Firestore for user {uid[:8]}")
                logger.info(f"   Make sure frontend writes to: users/{uid}/likedSongs/<songId>")
            
            return liked
            
        except Exception as e:
            logger.error(f"❌ Firestore error fetching liked songs for {uid[:8]}: {str(e)}", exc_info=True)
            return []

    def _to_unix_timestamp(self, value) -> Optional[float]:
        if value is None:
            return None
        if isinstance(value, (int, float)):
            return float(value)
        if isinstance(value, datetime):
            dt = value
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt.timestamp()
        if hasattr(value, 'timestamp'):
            try:
                return float(value.timestamp())
            except Exception:
                return None
        return None

    def _normalize_user_signal_doc(self, data: Dict, is_play: bool) -> Dict:
        song_name = (
            data.get('song')
            or data.get('title')
            or data.get('name')
            or ''
        )

        primary_artists = (
            data.get('primary_artists')
            or data.get('artist')
            or ''
        )
        if not primary_artists:
            artists_list = self._split_artists(data.get('artists') or '')
            primary_artists = ', '.join(artists_list)

        album_raw = data.get('album')
        if isinstance(album_raw, dict):
            album_name = str(album_raw.get('name') or '').strip()
        else:
            album_name = str(album_raw or '').strip()

        timestamp_value = (
            data.get('timestamp')
            or data.get('playedAt')
            or data.get('likedAt')
        )
        timestamp = self._to_unix_timestamp(timestamp_value)

        if is_play:
            play_count_raw = data.get('user_play_count') or data.get('play_count') or data.get('playCount') or 1
        else:
            play_count_raw = data.get('user_play_count') or data.get('play_count') or 1
        try:
            play_count = max(int(play_count_raw), 1)
        except (TypeError, ValueError):
            play_count = 1

        normalized = {
            'id': str(data.get('id') or data.get('videoId') or ''),
            'videoId': str(data.get('videoId') or data.get('id') or ''),
            'song': str(song_name).strip(),
            'primary_artists': str(primary_artists or '').strip(),
            'starring': str(data.get('starring') or '').strip(),
            'album': album_name,
            'timestamp': timestamp,
            'user_play_count': play_count,
            'play_count': play_count,
            'playCount': play_count,
            # Compatibility fields still used by helper methods.
            'artist': str(data.get('artist') or '').strip(),
            'artists': data.get('artists') or self._split_artists(primary_artists),
            'title': str(data.get('title') or song_name or '').strip(),
        }
        return normalized
    
    def _fetch_user_signals(self, uid: str) -> Dict[str, List[Dict]]:
        """
        Fetch both user plays and liked songs.
        
        Returns:
            Dict with 'plays' and 'liked' keys containing song lists
        """
        try:
            cached = self.cache_user_signals.get(uid)
            if cached:
                cached_value, timestamp = cached
                if time.time() - timestamp < self.CACHE_TTL_USER_SIGNALS:
                    return cached_value

            plays = self._fetch_user_plays(uid)
            liked = self._fetch_user_liked_songs(uid)
            result = {
                'plays': plays,
                'liked': liked
            }
            self.cache_user_signals[uid] = (result, time.time())
            return result
        except Exception as e:
            logger.error(f"Failed to fetch user signals for {uid}: {str(e)}")
            return {
                'plays': [],
                'liked': []
            }

    def _normalize_artist_name(self, artist_name: str) -> str:
        return artist_name.strip().lower()

    def _extract_artist_names(self, artists_field) -> List[str]:
        names: List[str] = []
        if isinstance(artists_field, list):
            for artist in artists_field:
                if isinstance(artist, dict):
                    name = artist.get('name', '')
                else:
                    name = str(artist)
                if name:
                    names.append(name)
        elif isinstance(artists_field, str) and artists_field:
            names.append(artists_field)
        return names

    def _build_user_artist_set(self, uid: str, plays: List[Dict], liked: List[Dict]) -> Set[str]:
        cached = self.cache_user_artists.get(uid)
        if cached:
            cached_set, timestamp = cached
            if time.time() - timestamp < self.CACHE_TTL_USER_SIGNALS:
                return cached_set

        artists: Set[str] = set()
        for entry in plays + liked:
            for name in self._extract_artist_names(entry.get('artists', [])):
                normalized = self._normalize_artist_name(name)
                if normalized:
                    artists.add(normalized)

        self.cache_user_artists[uid] = (artists, time.time())
        return artists

    def _filter_by_user_artists(self, recommendations: List[Dict], user_artists: Set[str]) -> List[Dict]:
        if not recommendations or not user_artists:
            return []

        filtered: List[Dict] = []
        for song in recommendations:
            artists_field = song.get('artists', [])
            names = self._extract_artist_names(artists_field)
            if not names:
                continue
            normalized = {self._normalize_artist_name(name) for name in names if name}
            if normalized.intersection(user_artists):
                filtered.append(song)

        return filtered
    
    def _calculate_time_decay_weight(self, play_timestamp: Optional[float]) -> float:
        """
        Calculate exponential time decay weight for a play event.
        
        Recent plays receive higher weight, older plays decay exponentially.
        Formula: weight = exp(-λ * days_since_play)
        
        Args:
            play_timestamp: Unix timestamp of the play event (seconds)
            
        Returns:
            Decay weight between 0.0 and 1.0
        """
        try:
            # Handle missing timestamp
            if play_timestamp is None:
                logger.debug("Missing timestamp, assigning default weight 0.1")
                return 0.1
            
            # Convert to float if needed
            try:
                play_timestamp = float(play_timestamp)
            except (TypeError, ValueError):
                logger.debug(f"Invalid timestamp type: {type(play_timestamp)}, using default weight")
                return 0.1
            
            # Calculate days since play
            current_time = time.time()
            days_since_play = (current_time - play_timestamp) / 86400.0  # 86400 seconds in a day
            
            # Handle future timestamps (clock skew or invalid data)
            if days_since_play < 0:
                logger.debug(f"Future timestamp detected, clamping to 0 days")
                days_since_play = 0
            
            # Calculate exponential decay: exp(-λ * days)
            decay_weight = math.exp(-self.decay_lambda * days_since_play)
            
            # Log for debugging (can be removed in production)
            if days_since_play > 0:
                logger.debug(f"Decay weight for {days_since_play:.2f} days: {decay_weight:.4f}")
            
            return decay_weight
            
        except Exception as e:
            logger.warning(f"Error calculating time decay: {str(e)}, using default weight 0.1")
            return 0.1
    
    def _build_weighted_signals(
        self,
        plays: List[Dict],
        liked_songs: List[Dict],
        play_weight: float = 1.0,
        liked_weight: float = 2.0
    ) -> Dict:
        """
        Build weighted preference signals from plays and liked songs.
        
        Applies time-decay weighting to favor recent listening behavior.
        Liked songs receive higher base weight (default 2x) to boost their influence.
        
        Time decay formula: weight = base_weight * exp(-λ * days_since_play)
        
        Args:
            plays: List of played songs with timestamps
            liked_songs: List of liked songs with timestamps
            play_weight: Base weight for played songs (default: 1.0)
            liked_weight: Base weight for liked songs (default: 2.0)
            
        Returns:
            Dict containing weighted artist_scores, album_scores, top_artists, top_albums
        """
        try:
            artist_scores: Counter = Counter()
            album_scores: Counter = Counter()
            
            # Track time decay statistics
            total_plays_processed = 0
            total_decay_applied = 0.0
            
            # Process plays with base weight and time decay.
            # Required formula:
            # artist_score += SIGNAL_WEIGHTS['play'] * exp(-lambda * days_since) * log(play_count + 1)
            for play in plays:
                play_count = play.get('user_play_count') or play.get('play_count') or play.get('playCount') or 1
                try:
                    play_count = int(play_count)
                except (TypeError, ValueError):
                    play_count = 1
                
                # Apply time decay based on play timestamp
                play_timestamp = play.get('timestamp') or play.get('playedAt')
                time_decay = self._calculate_time_decay_weight(play_timestamp)
                
                play_frequency_weight = math.log(max(play_count, 0) + 1.0)
                weight = play_weight * play_frequency_weight * time_decay
                
                total_plays_processed += 1
                total_decay_applied += time_decay
                
                # Extract and weight artists
                artists = self._split_artists(
                    play.get('primary_artists') or play.get('artist') or play.get('artists') or ''
                )
                for artist_name in artists:
                    if artist_name:
                        artist_scores[artist_name] += weight
                
                # Extract and weight albums
                album = self._extract_album_name_from_signal(play)
                if album:
                    album_scores[str(album)] += weight
            
            # Log average decay weight for plays
            if total_plays_processed > 0:
                avg_decay = total_decay_applied / total_plays_processed
                logger.info(f"Plays processed: {total_plays_processed}, avg decay weight: {avg_decay:.4f}")
            
            # Process liked songs with higher weight and time decay
            liked_processed = 0
            liked_decay_total = 0.0
            
            for liked in liked_songs:
                # Apply time decay to liked songs as well
                liked_timestamp = liked.get('timestamp') or liked.get('likedAt')
                time_decay = self._calculate_time_decay_weight(liked_timestamp)
                
                # Liked songs get higher base weight
                weight = liked_weight * time_decay
                
                liked_processed += 1
                liked_decay_total += time_decay
                
                # Extract and weight artists
                artists = self._split_artists(
                    liked.get('primary_artists') or liked.get('artist') or liked.get('artists') or ''
                )
                for artist_name in artists:
                    if artist_name:
                        artist_scores[artist_name] += weight
                
                # Extract and weight albums
                album = self._extract_album_name_from_signal(liked)
                if album:
                    album_scores[str(album)] += weight
            
            # Log average decay weight for liked songs
            if liked_processed > 0:
                avg_liked_decay = liked_decay_total / liked_processed
                logger.info(f"Liked songs processed: {liked_processed}, avg decay weight: {avg_liked_decay:.4f}")
            
            # Get top artists and albums
            top_artists = [name for name, _ in artist_scores.most_common(5)]
            top_albums = [name for name, _ in album_scores.most_common(3)]
            
            logger.info(
                f"Built weighted signals with time-decay: {len(artist_scores)} artists, "
                f"{len(album_scores)} albums (top artist: {top_artists[0] if top_artists else 'None'})"
            )
            
            return {
                'artist_scores': dict(artist_scores),
                'album_scores': dict(album_scores),
                'top_artists': top_artists,
                'top_albums': top_albums
            }
            
        except Exception as e:
            logger.error(f"Error building weighted signals: {str(e)}")
            return {
                'artist_scores': {},
                'album_scores': {},
                'top_artists': [],
                'top_albums': []
            }
    
    def _build_weighted_signals_cached(
        self,
        uid: str,
        plays: List[Dict],
        liked_songs: List[Dict]
    ) -> Dict:
        """
        Cached version of _build_weighted_signals for better performance.
        
        Caches the user taste profile (weighted artist/album preferences) to avoid
        recomputing on every request. Cache TTL: 30 minutes.
        
        Args:
            uid: User ID for cache key
            plays: List of played songs with timestamps
            liked_songs: List of liked songs with timestamps
            
        Returns:
            Dict containing weighted artist_scores, album_scores, top_artists, top_albums
        """
        # Check cache first
        cache_key = f"profile_{uid}"
        cached = self.cache_user_profile.get(cache_key)
        if cached:
            cached_value, timestamp = cached
            if time.time() - timestamp < self.CACHE_TTL_USER_PROFILE:
                logger.info(f"Using cached user profile for {uid} (age: {int(time.time() - timestamp)}s)")
                return cached_value
        
        # Compute fresh profile
        result = self._build_weighted_signals(plays, liked_songs)
        
        # Cache the result
        self.cache_user_profile[cache_key] = (result, time.time())
        logger.info(f"Cached user profile for {uid} (TTL: {self.CACHE_TTL_USER_PROFILE}s)")
        
        return result

    def _build_ranking_context_cached(
        self,
        uid: str,
        plays: List[Dict],
        liked_songs: List[Dict],
        weighted_signals: Dict
    ) -> Dict:
        """Cached wrapper for ranking context derived from user profile signals."""
        cache_key = f"ranking_{uid}"
        cached = self.cache_ranking_context.get(cache_key)
        if cached:
            cached_value, timestamp = cached
            if time.time() - timestamp < self.CACHE_TTL_USER_PROFILE:
                return cached_value

        result = self._build_ranking_context(plays, liked_songs, weighted_signals)
        self.cache_ranking_context[cache_key] = (result, time.time())
        return result
    
    def _extract_top_artists_and_albums(self, plays: List[Dict]) -> Tuple[List[str], List[str]]:
        """
        Extract top artists and albums using user_play_count-weighted counts.
        Returns two lists: [top_artists], [top_albums]
        """
        try:
            artist_counter: Counter = Counter()
            album_counter: Counter = Counter()

            for play in plays:
                play_count = play.get('user_play_count') or play.get('playCount') or 1
                try:
                    play_count = int(play_count)
                except (TypeError, ValueError):
                    play_count = 1

                artists = play.get('artists', [])
                if isinstance(artists, list):
                    for artist in artists:
                        if isinstance(artist, dict):
                            artist_name = artist.get('name')
                        else:
                            artist_name = str(artist)
                        if artist_name:
                            artist_counter[artist_name] += play_count
                elif isinstance(artists, str) and artists:
                    artist_counter[artists] += play_count

                album = play.get('album')
                if album:
                    album_counter[str(album)] += play_count

            top_artists = [name for name, _ in artist_counter.most_common(3)]
            top_albums = [name for name, _ in album_counter.most_common(2)]

            logger.debug(
                f"Extracted {len(top_artists)} artists and {len(top_albums)} albums"
            )
            return top_artists, top_albums
        except Exception as e:
            logger.error(f"Error extracting top artists/albums: {str(e)}")
            return [], []

    def _build_search_queries(
        self,
        top_artists: List[str],
        top_albums: List[str],
        top_starring: Optional[List[str]] = None
    ) -> List[str]:
        """Build search queries from top artists, albums, and starring actors."""
        queries: List[str] = []
        seen: Set[str] = set()

        def _push(query_value: str):
            # Enforce strict query limit to prevent infinite loops
            if len(queries) >= self.MAX_SEARCH_QUERIES:
                return
            value = (query_value or '').strip()
            if not value:
                return
            key = self._normalize_text(value)
            if key in seen:
                return
            seen.add(key)
            queries.append(value)

        for artist in top_artists:
            if len(queries) >= self.MAX_SEARCH_QUERIES:
                break
            _push(artist)
            _push(f"{artist} songs")
        for album in top_albums:
            if len(queries) >= self.MAX_SEARCH_QUERIES:
                break
            _push(album)
        for actor in (top_starring or []):
            if len(queries) >= self.MAX_SEARCH_QUERIES:
                break
            _push(f"{actor} songs")
        
        logger.info(f"Generated {len(queries)} search queries (MAX_SEARCH_QUERIES={self.MAX_SEARCH_QUERIES})")
        return queries

    def _normalize_text(self, value: Optional[str]) -> str:
        return (value or '').strip().lower()

    def _split_artists(self, artist_value) -> List[str]:
        if isinstance(artist_value, list):
            output = []
            for item in artist_value:
                if isinstance(item, dict):
                    name = (item.get('name') or '').strip()
                else:
                    name = str(item).strip()
                if name:
                    output.append(name)
            return output
        if isinstance(artist_value, str):
            return [part.strip() for part in artist_value.split(',') if part and part.strip()]
        return []

    def _extract_song_name_from_signal(self, signal: Dict) -> str:
        return (
            signal.get('song')
            or signal.get('title')
            or signal.get('name')
            or ''
        )

    def _extract_album_name_from_signal(self, signal: Dict) -> str:
        album = signal.get('album')
        if isinstance(album, dict):
            return str(album.get('name') or '').strip()
        return str(album or '').strip()

    def _extract_starring_from_signal(self, signal: Dict) -> List[str]:
        starring = signal.get('starring')
        if isinstance(starring, str) and starring.strip():
            return [part.strip() for part in starring.split(',') if part and part.strip()]
        return []

    def _extract_primary_artist_from_signal(self, signal: Dict) -> str:
        candidates = []
        primary_artists = signal.get('primary_artists')
        if isinstance(primary_artists, str) and primary_artists.strip():
            candidates.extend(self._split_artists(primary_artists))

        artist_field = signal.get('artist')
        if isinstance(artist_field, str) and artist_field.strip():
            candidates.extend(self._split_artists(artist_field))

        artists_field = signal.get('artists')
        candidates.extend(self._split_artists(artists_field))

        singers_field = signal.get('singers')
        if isinstance(singers_field, str) and singers_field.strip():
            candidates.extend(self._split_artists(singers_field))

        return candidates[0] if candidates else ''

    def _release_recency_weight(self, release_date: Optional[str]) -> float:
        if not release_date:
            return 0.0
        try:
            parsed = datetime.strptime(release_date[:10], "%Y-%m-%d").replace(tzinfo=timezone.utc)
            days_old = max((datetime.now(timezone.utc) - parsed).days, 0)
            # Required release recency formula.
            return 1.0 / (days_old + 1.0)
        except Exception:
            return 0.0

    def _normalize_play_count(self, play_count) -> float:
        try:
            value = max(float(play_count or 0), 0.0)
            # Required popularity transform: log(play_count + 1)
            return math.log(value + 1.0)
        except Exception:
            return 0.0

    def _build_ranking_context(self, plays: List[Dict], liked_songs: List[Dict], weighted_signals: Dict) -> Dict:
        """
        Build ranking-only context from existing user signals without changing profile extraction flow.
        """
        artist_scores = dict(weighted_signals.get('artist_scores') or {})

        played_artists: Set[str] = set()
        liked_artists: Set[str] = set()
        artist_recentness: Counter = Counter()
        artist_play_frequency: Counter = Counter()
        starring_frequency: Counter = Counter()
        song_frequency: Counter = Counter()
        recent_titles: Set[str] = set()
        recent_play_window: List[Tuple[float, str]] = []

        for play in plays or []:
            play_count_raw = play.get('user_play_count') or play.get('playCount') or play.get('play_count') or 1
            try:
                play_count = max(int(play_count_raw), 1)
            except (TypeError, ValueError):
                play_count = 1

            timestamp = play.get('timestamp') or play.get('playedAt')
            recency = self._calculate_time_decay_weight(timestamp)
            play_score = math.log(play_count + 1.0)

            song_name = self._extract_song_name_from_signal(play)
            if song_name:
                norm_song = self._normalize_text(song_name)
                song_frequency[norm_song] += play_score
                if timestamp:
                    try:
                        recent_play_window.append((float(timestamp), norm_song))
                    except (TypeError, ValueError):
                        pass

            artists = self._split_artists(play.get('primary_artists') or play.get('artist') or play.get('artists') or '')
            for artist in artists:
                norm_artist = self._normalize_text(artist)
                if not norm_artist:
                    continue
                played_artists.add(norm_artist)
                artist_recentness[norm_artist] += recency
                artist_play_frequency[norm_artist] += play_score

            for actor in self._extract_starring_from_signal(play):
                starring_frequency[self._normalize_text(actor)] += 1

        for liked in liked_songs or []:
            artists = self._split_artists(liked.get('primary_artists') or liked.get('artist') or liked.get('artists') or '')
            for artist in artists:
                norm_artist = self._normalize_text(artist)
                if norm_artist:
                    liked_artists.add(norm_artist)
            for actor in self._extract_starring_from_signal(liked):
                starring_frequency[self._normalize_text(actor)] += 1

        # Boost artists appearing in both plays and likes.
        for artist_name, value in list(artist_scores.items()):
            norm_artist = self._normalize_text(artist_name)
            boosted = float(value)
            if norm_artist in played_artists and norm_artist in liked_artists:
                boosted *= 1.3
            artist_scores[artist_name] = boosted

        recent_play_window.sort(key=lambda x: x[0], reverse=True)
        for _, title_key in recent_play_window[:20]:
            recent_titles.add(title_key)

        top_starring = [name for name, _ in starring_frequency.most_common(3) if name]
        normalized_artist_scores = {
            self._normalize_text(name): float(score)
            for name, score in artist_scores.items()
            if self._normalize_text(name)
        }
        return {
            'artist_scores': artist_scores,
            'normalized_artist_scores': normalized_artist_scores,
            'artist_recentness': dict(artist_recentness),
            'artist_play_frequency': dict(artist_play_frequency),
            'starring_frequency': dict(starring_frequency),
            'song_frequency': dict(song_frequency),
            'top_starring': top_starring,
            'recent_titles': recent_titles,
        }

    def _max_value(self, values: List[float]) -> float:
        cleaned = [float(v) for v in values if v is not None]
        if not cleaned:
            return 1.0
        return max(max(cleaned), 1.0)

    def _calculate_recommendation_score(self, song_data: Dict, ranking_context: Dict) -> float:
        """
        Final ranking score using required weighted combination of signals.
        """
        artists = self._split_artists(song_data.get('primary_artists') or song_data.get('artist') or song_data.get('artists') or '')
        artist_scores = ranking_context.get('artist_scores', {})
        normalized_artist_scores = ranking_context.get('normalized_artist_scores', {})
        artist_recentness = ranking_context.get('artist_recentness', {})
        artist_play_frequency = ranking_context.get('artist_play_frequency', {})
        starring_frequency = ranking_context.get('starring_frequency', {})

        artist_affinity_raw = 0.0
        recency_weight_raw = 0.0
        play_frequency_raw = 0.0
        for artist in artists:
            norm_artist = self._normalize_text(artist)
            if not norm_artist:
                continue

            artist_match = float(normalized_artist_scores.get(norm_artist, 0.0))
            artist_affinity_raw = max(artist_affinity_raw, artist_match)
            recency_weight_raw = max(recency_weight_raw, float(artist_recentness.get(norm_artist, 0.0)))
            play_frequency_raw = max(play_frequency_raw, float(artist_play_frequency.get(norm_artist, 0.0)))

        starring_raw = 0.0
        for actor in self._split_artists(song_data.get('starring') or ''):
            starring_raw = max(starring_raw, float(starring_frequency.get(self._normalize_text(actor), 0.0)))

        popularity_raw = self._normalize_play_count(
            song_data.get('global_play_count') or song_data.get('play_count')
        )
        release_raw = self._release_recency_weight(song_data.get('release_date'))

        max_artist_affinity = self._max_value([float(v) for v in artist_scores.values()])
        max_recency = self._max_value([float(v) for v in artist_recentness.values()])
        max_play_frequency = self._max_value([float(v) for v in artist_play_frequency.values()])
        max_starring = self._max_value([float(v) for v in starring_frequency.values()])

        artist_affinity = artist_affinity_raw / max_artist_affinity
        recency_weight = recency_weight_raw / max_recency
        play_frequency = play_frequency_raw / max_play_frequency
        popularity_score = min(popularity_raw / math.log(1_000_000_000.0 + 1.0), 1.0)
        starring_score = starring_raw / max_starring
        release_score = min(release_raw, 1.0)

        return (
            (artist_affinity * 0.40)
            + (recency_weight * 0.25)
            + (play_frequency * 0.15)
            + (popularity_score * 0.10)
            + (starring_score * 0.05)
            + (release_score * 0.05)
        )

    def _compute_metadata_boost(self, song: Dict, query: str, base_score: float) -> float:
        """
        Lightweight pre-rank score from source/query only.
        Final recommendation ranking is computed in _deduplicate_and_filter.
        """
        query_norm = self._normalize_text(query)
        primary_artists = song.get('primary_artists') or song.get('artist') or ''
        artist_overlap = 1.0 if query_norm and query_norm in self._normalize_text(primary_artists) else 0.0
        return float(base_score) + (0.1 * artist_overlap)

    def _map_jiosaavn_song(self, song: Dict, query: str, source: str, base_weight: float) -> Optional[Dict]:
        song_id = str(song.get('id') or song.get('videoId') or '').strip()
        if not song_id:
            return None

        title = (song.get('song') or song.get('title') or '').strip()
        primary_artists = (song.get('primary_artists') or song.get('artist') or '').strip()
        media_url = (song.get('media_url') or song.get('url') or song.get('stream_url') or '').strip()
        image = (song.get('image') or song.get('thumbnail') or '').strip()
        artists = self._split_artists(primary_artists) or ['Unknown']

        mapped = {
            'id': song_id,
            'videoId': song_id,
            'song': title,
            'title': title,
            'primary_artists': primary_artists,
            'artist': artists[0] if artists else 'Unknown',
            'artists': artists,
            'starring': (song.get('starring') or '').strip(),
            'music': (song.get('music') or '').strip(),
            'album': (song.get('album') or '').strip(),
            'language': (song.get('language') or '').strip(),
            'play_count': int(song.get('play_count') or 0),
            'release_date': (song.get('release_date') or '').strip(),
            'media_url': media_url,
            'url': media_url,
            'image': image,
            'thumbnail': image,
            'duration': str(song.get('duration') or ''),
            '_source': source,
        }
        mapped['_score'] = self._compute_metadata_boost(mapped, query, base_weight)
        return mapped

    def _song_matches_title_artist(self, song: Dict, song_name: str, primary_artist: str) -> bool:
        if not song_name or not primary_artist:
            return False
        title_norm = self._normalize_text(song.get('song') or song.get('title'))
        artist_norm = self._normalize_text(song.get('primary_artists') or song.get('artist'))
        return title_norm == self._normalize_text(song_name) and self._normalize_text(primary_artist) in artist_norm

    def _map_user_history_to_saavn_candidates(self, plays: List[Dict], liked_songs: List[Dict]) -> Dict[str, Dict]:
        """
        Map legacy Firestore signals to JioSaavn IDs via: song name + primary artist.
        """
        results: Dict[str, Dict] = {}
        seen_queries: Set[str] = set()
        source_signals = (plays or [])[:10] + (liked_songs or [])[:10]
        signal_queries: List[Tuple[str, str, str]] = []

        for signal in source_signals:
            song_name = self._extract_song_name_from_signal(signal)
            primary_artist = self._extract_primary_artist_from_signal(signal)
            if not song_name or not primary_artist:
                continue

            query = f"{song_name} {primary_artist}".strip()
            query_key = self._normalize_text(query)
            if query_key in seen_queries:
                continue
            seen_queries.add(query_key)
            signal_queries.append((query, song_name, primary_artist))

        if not signal_queries:
            return results

        signal_queries = signal_queries[:self.HISTORY_QUERY_LIMIT]

        with ThreadPoolExecutor(max_workers=min(4, self.max_workers)) as executor:
            futures = {
                executor.submit(self.jiosaavn_service.search_songs, query, 8): (query, song_name, primary_artist)
                for query, song_name, primary_artist in signal_queries
            }

            for future in as_completed(futures):
                query, song_name, primary_artist = futures[future]
                try:
                    song_results = future.result() or []
                    if not song_results:
                        continue

                    exact = [
                        song for song in song_results
                        if self._song_matches_title_artist(song, song_name, primary_artist)
                    ]
                    candidate_pool = exact if exact else song_results
                    best_song = max(candidate_pool, key=lambda item: int(item.get('play_count') or 0), default=None)
                    if not best_song:
                        continue

                    mapped = self._map_jiosaavn_song(best_song, query, 'song_related', self.SCORE_WEIGHTS['song_related'])
                    if mapped and mapped.get('videoId'):
                        results[mapped['videoId']] = mapped
                except Exception as exc:
                    logger.warning("Legacy signal mapping failed for query '%s': %s", query, str(exc))

        return results

    def _search_songs(self, query: str, limit: int = 10) -> Dict[str, Dict]:
        """Search songs from JioSaavn and normalize to recommendation schema."""
        try:
            if not query:
                return {}

            results: Dict[str, Dict] = {}
            search_results = self.jiosaavn_service.search_songs(query, limit=max(limit, 1))

            for item in search_results or []:
                song_dict = self._map_jiosaavn_song(
                    item,
                    query=query,
                    source='search',
                    base_weight=self.SCORE_WEIGHTS['search']
                )
                if song_dict and song_dict.get('videoId'):
                    results[song_dict['videoId']] = song_dict

            return results
        except Exception as e:
            logger.error(f"Search error for query '{query}': {str(e)}")
            return {}
    
    def _expand_from_artists(self, artists: List[Dict]) -> Dict[str, Dict]:
        """
        Expand recommendations from top artists using JioSaavn search.
        """
        recommendations: Dict[str, Dict] = {}
        
        try:
            artist_queries = []
            for artist in artists or []:
                if not isinstance(artist, dict):
                    continue
                query = (
                    artist.get('name')
                    or artist.get('artist')
                    or artist.get('channelId')
                    or ''
                )
                if query:
                    artist_queries.append(query)

            with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                futures = {
                    executor.submit(self._fetch_artist_songs, query): query
                    for query in artist_queries
                }
                
                for future in as_completed(futures):
                    try:
                        songs = future.result()
                        for song in songs:
                            if song.get('videoId'):
                                self._merge_recommendations(
                                    recommendations,
                                    {song['videoId']: song},
                                    'artist_songs'
                                )
                    except Exception as e:
                        logger.warning(f"Error processing artist songs: {str(e)}")
            
            logger.debug(f"Expanded to {len(recommendations)} from artists")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error expanding from artists: {str(e)}")
            return recommendations
    
    def _expand_from_recent_songs(self, recent_plays: List[Dict]) -> Dict[str, Dict]:
        """
        Expand recommendations from recent song plays using song+artist queries.
        """
        recommendations: Dict[str, Dict] = {}
        
        try:
            with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                futures = {
                    executor.submit(self._fetch_song_related, play): play
                    for play in recent_plays
                    if isinstance(play, dict)
                }
                
                for future in as_completed(futures):
                    try:
                        songs = future.result()
                        for song in songs:
                            if song.get('videoId'):
                                self._merge_recommendations(
                                    recommendations,
                                    {song['videoId']: song},
                                    'song_related'
                                )
                    except Exception as e:
                        logger.warning(f"Error processing related songs: {str(e)}")
            
            logger.debug(f"Expanded to {len(recommendations)} from recent songs")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error expanding from recent songs: {str(e)}")
            return recommendations
    
    def _fetch_artist_songs(self, artist_query: str) -> List[Dict]:
        """
        Fetch top songs from an artist name query using JioSaavn.
        """
        try:
            # Check cache
            if artist_query in self.cache_artist:
                cached_data, timestamp = self.cache_artist[artist_query]
                if time.time() - timestamp < self.CACHE_TTL_ARTIST:
                    logger.debug(f"Using cached artist data for {artist_query}")
                    return cached_data

            songs = []
            search_results = self.jiosaavn_service.search_songs(artist_query, limit=20)
            for item in search_results:
                mapped = self._map_jiosaavn_song(
                    item,
                    query=artist_query,
                    source='artist_songs',
                    base_weight=self.SCORE_WEIGHTS['artist_songs']
                )
                if mapped:
                    songs.append(mapped)

            # Cache the result
            self.cache_artist[artist_query] = (songs, time.time())
            
            logger.debug(f"Fetched {len(songs)} songs for artist query {artist_query}")
            return songs
            
        except Exception as e:
            logger.error(f"Error fetching artist songs for {artist_query}: {str(e)}")
            return []
    
    def _fetch_artist_songs_cached(self, artist_query: str) -> List[Dict]:
        """Fetch artist songs with cache support (compat wrapper)."""
        try:
            if artist_query in self.cache_artist:
                cached_data, timestamp = self.cache_artist[artist_query]
                if time.time() - timestamp < self.CACHE_TTL_ARTIST:
                    return cached_data

            return self._fetch_artist_songs(artist_query)
            
        except Exception as e:
            logger.warning(f"Error fetching cached artist {artist_query}: {str(e)}")
            return []
    
    def _fetch_song_related(self, play_signal: Dict) -> List[Dict]:
        """
        Fetch related songs by querying JioSaavn with song name + primary artist.
        """
        song_name = self._extract_song_name_from_signal(play_signal)
        primary_artist = self._extract_primary_artist_from_signal(play_signal)
        query = f"{song_name} {primary_artist}".strip()
        if not query:
            return []

        cache_key = f"related::{self._normalize_text(query)}"
        try:
            # Check cache
            if cache_key in self.cache_related:
                cached_data, timestamp = self.cache_related[cache_key]
                if time.time() - timestamp < self.CACHE_TTL_RELATED:
                    logger.debug(f"Using cached related songs for {query}")
                    return cached_data

            logger.debug(f"Fetching related songs for query {query}")
            songs = []
            related_results = self.jiosaavn_service.search_songs(query, limit=15)
            for item in related_results:
                mapped = self._map_jiosaavn_song(
                    item,
                    query=query,
                    source='song_related',
                    base_weight=self.SCORE_WEIGHTS['song_related']
                )
                if mapped:
                    songs.append(mapped)
            
            # Limit to prevent bloat
            songs = songs[:15]
            
            # Cache the result
            self.cache_related[cache_key] = (songs, time.time())
            
            logger.debug(f"Fetched {len(songs)} related songs for query {query}")
            return songs
            
        except Exception as e:
            logger.error(f"Error fetching related songs for {query}: {str(e)}")
            return []
    
    def _get_trending_fallback(
        self,
        country: str = 'IN',
        per_query_limit: Optional[int] = None,
        max_candidates: Optional[int] = None,
    ) -> Dict[str, Dict]:
        """
        Get trending songs as a fallback for cold-start users using JioSaavn.

        Trending score uses play_count + release_date as requested.
        """
        del country  # country-specific charts are not used in JioSaavn query mode.
        recommendations: Dict[str, Dict] = {}
        try:
            if per_query_limit is None:
                per_query_limit = self.QUERY_RESULT_LIMIT
            if max_candidates is None:
                max_candidates = self.MAX_CANDIDATE_POOL

            trending_queries = [
                'trending hindi songs',
                'latest tamil songs',
                'top india songs',
                'new bollywood songs',
            ]

            with ThreadPoolExecutor(max_workers=min(4, self.max_workers)) as executor:
                futures = {
                    executor.submit(self.jiosaavn_service.search_songs, query, per_query_limit): query
                    for query in trending_queries
                }

                for future in as_completed(futures):
                    query = futures[future]
                    try:
                        songs = future.result() or []
                    except Exception as search_exc:
                        logger.warning("Trending query failed for '%s': %s", query, str(search_exc))
                        songs = []

                    for item in songs:
                        mapped = self._map_jiosaavn_song(
                            item,
                            query=query,
                            source='trending',
                            base_weight=self.SCORE_WEIGHTS['trending']
                        )
                        if not mapped or not mapped.get('videoId'):
                            continue

                        # Trending boost uses play_count + release_date signal.
                        mapped['_score'] += (
                            self._normalize_play_count(mapped.get('play_count')) * 0.7
                            + self._release_recency_weight(mapped.get('release_date')) * 0.3
                        )
                        self._merge_recommendations(
                            recommendations,
                            {mapped['videoId']: mapped},
                            'trending',
                            max_candidates=max_candidates,
                        )
                        if len(recommendations) >= max_candidates:
                            break

            logger.info("Built trending fallback candidates: %d", len(recommendations))
            return recommendations
        except Exception as e:
            logger.error(f"Error fetching trending fallback: {str(e)}")
            return {}
    
    def _extract_song_data(self, item: Dict) -> Optional[Dict]:
        """
        Extract song data from provider API response.
        Validates required fields and ignores non-song items.
        """
        try:
            # Skip if not a song (could be playlist, artist, etc.)
            if item.get('resultType') and item['resultType'] != 'song':
                return None
            
            # Check for required videoId
            video_id = item.get('videoId') or item.get('id')
            if not video_id:
                return None
            
            # Extract artists
            artists = []
            if item.get('artists'):
                if isinstance(item['artists'], list):
                    for artist in item['artists']:
                        if isinstance(artist, dict):
                            artists.append(artist.get('name', 'Unknown'))
                        else:
                            artists.append(str(artist))
            
            # Extract thumbnail with strict fallback order
            thumbnail = ''
            if item.get('thumbnails'):
                thumbnails = item['thumbnails']
                if isinstance(thumbnails, list) and thumbnails:
                    thumbnail = thumbnails[0].get('url', '')
                elif isinstance(thumbnails, dict):
                    thumbnail = thumbnails.get('url', '')

            if not thumbnail:
                thumbnail = item.get('thumbnail', '')

            if not thumbnail and video_id:
                thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
            
            return {
                'videoId': video_id,
                'title': item.get('title', item.get('name', 'Unknown')),
                'artists': artists or ['Unknown'],
                'thumbnail': thumbnail,
                'album': item.get('album', ''),
            }
            
        except Exception as e:
            logger.debug(f"Error extracting song data: {str(e)}")
            return None

    def _is_title_allowed(self, title: str) -> bool:
        """
        Lightweight title filter to avoid obvious non-music content.
        Uses shared filtering logic from music_filter module.
        """
        from services.music_filter import _is_title_allowed as filter_is_title_allowed
        return filter_is_title_allowed(title)

    def _is_valid_music_video(self, song_data: Dict) -> bool:
        """
        Validate recommendation is playable in JioSaavn context.
        """
        media_url = (song_data.get('media_url') or song_data.get('url') or '').strip()
        return bool(media_url)
    
    def _merge_recommendations(
        self,
        main_dict: Dict[str, Dict],
        additions: Dict[str, Dict],
        source: str,
        max_candidates: Optional[int] = None,
    ) -> None:
        """
        Merge new recommendations into main dict.
        Updates scores if song already exists from different source.
        """
        for video_id, song_data in additions.items():
            if max_candidates is not None and len(main_dict) >= max_candidates and video_id not in main_dict:
                break
            if video_id in main_dict:
                # Accumulate scores from multiple sources
                main_dict[video_id]['_score'] += song_data.get('_score', 0)

                # Keep richer version with higher play_count for duplicate IDs.
                existing_play_count = int(main_dict[video_id].get('play_count') or 0)
                incoming_play_count = int(song_data.get('play_count') or 0)
                if incoming_play_count > existing_play_count:
                    preserved_score = main_dict[video_id].get('_score', 0)
                    preserved_sources = main_dict[video_id].get('_sources', [])
                    main_dict[video_id] = {**song_data, '_score': preserved_score, '_sources': preserved_sources}

                # Update source list
                sources = main_dict[video_id].get('_sources', [])
                if source not in sources:
                    sources.append(source)
                main_dict[video_id]['_sources'] = sources
            else:
                # New song
                song_data['_sources'] = [source]
                main_dict[video_id] = song_data

    def _apply_readme_diversity_layer(
        self,
        recommendations: List[Dict],
        limit: int,
        max_per_artist: int = 2,
        max_per_album: int = 3
    ) -> List[Dict]:
        """
        Apply README diversity rules after ranking:
        - max 2 songs per artist
        - max 3 songs per album
        """
        if not recommendations:
            return []

        artist_counts: Dict[str, int] = {}
        album_counts: Dict[str, int] = {}
        output: List[Dict] = []

        for song in recommendations:
            artists = self._split_artists(
                song.get('primary_artists') or song.get('artist') or song.get('artists') or ''
            )
            album = self._normalize_text(song.get('album') or '')

            if artists:
                if any(artist_counts.get(self._normalize_text(name), 0) >= max_per_artist for name in artists):
                    continue

            if album and album_counts.get(album, 0) >= max_per_album:
                continue

            output.append(song)

            for name in artists:
                key = self._normalize_text(name)
                artist_counts[key] = artist_counts.get(key, 0) + 1

            if album:
                album_counts[album] = album_counts.get(album, 0) + 1

            if len(output) >= limit:
                break

        return output
    
    def _apply_diversity_constraints(
        self,
        recommendations: List[Dict],
        target_count: int = 25,
        artist_repeat_penalty: float = 0.15,
        album_repeat_penalty: float = 0.1,
        max_consecutive_artist: int = 2,
        max_total_per_artist: int = 3
    ) -> List[Dict]:
        """
        Apply soft diversity re-ranking to reduce artist/album dominance without removing songs.
        
        Args:
            recommendations: Ranked list of song recommendations
            max_per_artist: Maximum songs per artist (default: 2)
            max_per_album: Maximum songs per album (default: 3)
            
        Returns:
            Filtered list maintaining ranking order with diversity applied
        """
        if not recommendations:
            return []

        try:
            scored: List[Dict] = []
            artist_occurrence = {}
            album_occurrence = {}

            for song in recommendations:
                artists = song.get('artists', [])
                album = song.get('album', '')

                if not isinstance(artists, list):
                    artists = [str(artists)] if artists else []

                normalized_artists = []
                for artist in artists:
                    if isinstance(artist, dict):
                        name = artist.get('name', '').strip()
                    else:
                        name = str(artist).strip()
                    if name:
                        normalized_artists.append(name)

                album_name = str(album).strip() if album else ''

                artist_repeat_count = 0
                for name in normalized_artists:
                    artist_repeat_count = max(artist_repeat_count, artist_occurrence.get(name, 0))

                album_repeat_count = album_occurrence.get(album_name, 0) if album_name else 0

                base_score = float(song.get('_score', 0))
                final_score = base_score - (artist_repeat_penalty * artist_repeat_count)
                final_score -= (album_repeat_penalty * album_repeat_count)

                scored.append({
                    **song,
                    '_final_score': final_score
                })

                for name in normalized_artists:
                    artist_occurrence[name] = artist_occurrence.get(name, 0) + 1
                if album_name:
                    album_occurrence[album_name] = album_occurrence.get(album_name, 0) + 1

            scored.sort(key=lambda x: x.get('_final_score', 0), reverse=True)

            reranked = self._apply_sliding_window_diversity(
                scored,
                max_consecutive_artist=max_consecutive_artist,
                max_total_per_artist=max_total_per_artist
            )

            for item in reranked:
                item.pop('_final_score', None)

            return reranked[:target_count]

        except Exception as e:
            logger.error(f"Error applying diversity constraints: {str(e)}", exc_info=True)
            return recommendations[:target_count]

    def _apply_sliding_window_diversity(
        self,
        recommendations: List[Dict],
        max_consecutive_artist: int,
        max_total_per_artist: int
    ) -> List[Dict]:
        if not recommendations:
            return []

        remaining = recommendations[:]
        result: List[Dict] = []
        artist_counts = {}
        consecutive_artist = None
        consecutive_count = 0

        while remaining:
            chosen_index = None
            for idx, song in enumerate(remaining):
                artists = song.get('artists', [])
                if not isinstance(artists, list):
                    artists = [str(artists)] if artists else []

                primary_artist = None
                for artist in artists:
                    if isinstance(artist, dict):
                        name = artist.get('name', '').strip()
                    else:
                        name = str(artist).strip()
                    if name:
                        primary_artist = name
                        break

                if primary_artist is None:
                    chosen_index = idx
                    break

                total_count = artist_counts.get(primary_artist, 0)
                if total_count >= max_total_per_artist:
                    continue

                if primary_artist == consecutive_artist and consecutive_count >= max_consecutive_artist:
                    continue

                chosen_index = idx
                break

            if chosen_index is None:
                chosen_index = 0

            chosen = remaining.pop(chosen_index)
            result.append(chosen)

            artists = chosen.get('artists', [])
            if not isinstance(artists, list):
                artists = [str(artists)] if artists else []
            primary_artist = None
            for artist in artists:
                if isinstance(artist, dict):
                    name = artist.get('name', '').strip()
                else:
                    name = str(artist).strip()
                if name:
                    primary_artist = name
                    break

            if primary_artist:
                artist_counts[primary_artist] = artist_counts.get(primary_artist, 0) + 1
                if primary_artist == consecutive_artist:
                    consecutive_count += 1
                else:
                    consecutive_artist = primary_artist
                    consecutive_count = 1
            else:
                consecutive_artist = None
                consecutive_count = 0

        return result

    def _backfill_results(
        self,
        current: List[Dict],
        fallback: List[Dict],
        target_count: int
    ) -> List[Dict]:
        existing_ids = {song.get('videoId') for song in current if song.get('videoId')}
        for song in fallback:
            video_id = song.get('videoId')
            if not video_id or video_id in existing_ids:
                continue
            current.append(song)
            existing_ids.add(video_id)
            if len(current) >= target_count:
                break
        return current
    
    def _deduplicate_and_filter(
        self,
        recommendations: Dict[str, Dict],
        consumed_ids: Set[str],
        limit: int,
        ranking_context: Optional[Dict] = None,
        recent_played_titles: Optional[Set[str]] = None
    ) -> List[Dict]:
        """
        Deduplicate, filter out consumed songs, and return top N by score.
        """
        ranking_context = ranking_context or {}
        recent_played_titles = recent_played_titles or set()

        # Filter out consumed songs
        filtered = {
            vid: data for vid, data in recommendations.items()
            if vid not in consumed_ids
        }

        # Novelty enforcement: remove songs whose title was already played recently.
        filtered = {
            vid: data
            for vid, data in filtered.items()
            if self._normalize_text(data.get('song') or data.get('title') or '') not in recent_played_titles
        }

        # Apply final ranking score using required signal blend.
        for _, song_data in filtered.items():
            song_data['_score'] = self._calculate_recommendation_score(song_data, ranking_context)
        
        # Sort by score (descending)
        sorted_recs = sorted(
            filtered.items(),
            key=lambda x: x[1].get('_score', 0),
            reverse=True
        )
        
        # Clean up and return top N (validated as real music tracks)
        result = []
        validation_calls = 0
        for video_id, song_data in sorted_recs:
            if len(result) >= limit:
                break

            title = song_data.get('title', '')
            if not self._is_title_allowed(title):
                logger.info(
                    "Recommendation skipped: reason=title_filter videoId=%s title=%s",
                    video_id,
                    title
                )
                continue

            if validation_calls >= self.VALIDATION_SCAN_LIMIT:
                logger.info(
                    "Recommendation validation limit reached: limit=%s collected=%s",
                    self.VALIDATION_SCAN_LIMIT,
                    len(result)
                )
                break

            validation_calls += 1
            if not self._is_valid_music_video(song_data):
                continue

            image = (song_data.get('image') or song_data.get('thumbnail') or '').strip()
            media_url = (song_data.get('media_url') or song_data.get('url') or '').strip()
            primary_artists = (song_data.get('primary_artists') or song_data.get('artist') or '').strip()
            artist_list = song_data.get('artists', [])
            if not artist_list:
                artist_list = self._split_artists(primary_artists)

            # Remove internal scoring fields
            cleaned = {
                'id': song_data.get('id') or video_id,
                'videoId': song_data.get('videoId') or video_id,
                'song': song_data.get('song') or song_data.get('title') or '',
                'title': song_data.get('title') or song_data.get('song') or '',
                'artist': song_data.get('artist') or (artist_list[0] if artist_list else 'Unknown'),
                'primary_artists': primary_artists,
                'artists': artist_list,
                'starring': song_data.get('starring', ''),
                'album': song_data.get('album', ''),
                'language': song_data.get('language', ''),
                'play_count': int(song_data.get('play_count') or 0),
                'release_date': song_data.get('release_date', ''),
                'media_url': media_url,
                'url': media_url,
                'image': image,
                'thumbnail': image,
                'duration': song_data.get('duration', ''),
            }
            result.append(cleaned)
        logger.info("Final clean recommendations count=%s", len(result))
        return result
    
    def get_top_artists(self, uid: str, limit: int = 10) -> List[Dict]:
        """
        Build top artists from Firestore user plays using JioSaavn metadata.

        Data source:
            users/{uid}/plays

        Artist extraction priority for each play:
            1. primary_artists
            2. singers

        Output is Android home-screen compatible:
            [{'name': str, 'image': str}, ...]
        """
        try:
            from services.saavn_artist_service import fetch_artist_metadata

            # STEP 1: Fetch user plays from Firestore users/{uid}/plays
            plays = self._fetch_user_plays(uid)
            logger.info("Building top artists from %d user plays", len(plays))

            # STEP 2/3/4: Extract artists (primary_artists -> singers), split comma values,
            # normalize for counting, and build frequency map.
            normalized_counts: Dict[str, int] = {}
            display_counts: Dict[str, Counter[str]] = {}

            for play in plays:
                if not isinstance(play, dict):
                    continue

                artist_string = play.get('primary_artists') or play.get('singers')
                if not artist_string or not isinstance(artist_string, str):
                    continue

                artist_candidates = [name.strip() for name in artist_string.split(',') if name and name.strip()]
                if not artist_candidates:
                    continue

                for artist_name in artist_candidates:
                    normalized_name = normalized_artist_key(artist_name)
                    if not normalized_name:
                        continue
                    normalized_counts[normalized_name] = normalized_counts.get(normalized_name, 0) + 1
                    display_counts.setdefault(normalized_name, Counter())[artist_name.strip()] += 1

            if not normalized_counts:
                logger.info("Top artist candidates: {}")
                logger.info("Final top artists returned: %d", 0)
                return []

            # STEP 5: Sort artists by frequency descending and take top candidates.
            sorted_artist_counts = sorted(
                normalized_counts.items(),
                key=lambda x: x[1],
                reverse=True
            )

            artist_counts_log = {
                (display_counts.get(name) or Counter()).most_common(1)[0][0]
                if (display_counts.get(name) or Counter())
                else name: count
                for name, count in sorted_artist_counts
            }
            logger.info("Top artist candidates: %s", artist_counts_log)

            top_candidates = sorted_artist_counts[:max(limit, 10)]

            # STEP 6/7/8/9/10: Fetch JioSaavn artist metadata and skip invalid artists.
            # Internal object includes play_count for traceability; API response remains
            # Android-compatible with only name/image fields.
            response_artists: List[Dict] = []
            for normalized_name, play_count in top_candidates:
                counter = display_counts.get(normalized_name) or Counter()
                display_name = counter.most_common(1)[0][0] if counter else normalized_name
                metadata = fetch_artist_metadata(display_name)
                if not metadata:
                    logger.info("Skipping artist with no Saavn metadata: %s", display_name)
                    continue

                normalized_artist = {
                    'name': display_name,  # Always use original display name
                    'image': metadata.get('image', ''),
                    'play_count': play_count
                }

                response_artists.append({
                    'name': normalized_artist['name'],
                    'image': normalized_artist['image']
                })

                if len(response_artists) >= limit:
                    break

            logger.info("Final top artists returned: %d", len(response_artists))
            return response_artists
            
        except Exception as e:
            logger.error(f"Error getting top artists: {str(e)}", exc_info=True)
            return []
    
    def prefetch_recommendations_async(self, uid: str, limit: int = 20) -> None:
        """
        Background prefetch for recommendations (Pro Optimization).
        
        This method can be triggered when a user plays a song to pre-warm
        the recommendation cache for the next home screen load.
        
        Runs in background - does not block the current request.
        
        Usage:
            # In your play tracking endpoint:
            from threading import Thread
            Thread(target=recommendation_service.prefetch_recommendations_async, 
                   args=(user_id,), daemon=True).start()
        
        Args:
            uid: User ID
            limit: Number of recommendations to prefetch
        """
        try:
            # Check if already cached and fresh
            cache_key = f"recommendations_{uid}_{limit}"
            cached = self.cache_recommendations.get(cache_key)
            if cached:
                _, timestamp = cached
                # Only prefetch if cache is older than 10 minutes
                if time.time() - timestamp < 600:
                    logger.info(f"Skipping prefetch for {uid} - cache is fresh")
                    return
            
            logger.info(f"Background prefetch: Warming recommendation cache for {uid}")
            self.get_recommendations(uid, limit)
            logger.info(f"Background prefetch: Successfully cached recommendations for {uid}")
            
        except Exception as e:
            # Silent failure - this is background prefetch, don't affect user experience
            logger.warning(f"Background prefetch failed for {uid}: {str(e)}")