"""
Collaborative Filtering Service - User-based recommendations
Production-ready implementation with caching and scalability optimizations
"""
import logging
import time
import math
import re
from typing import List, Dict, Set, Tuple, Optional
from collections import defaultdict
from services.user_service import get_firestore_client

logger = logging.getLogger(__name__)

try:
    import firebase_admin
    from firebase_admin import firestore
    FIRESTORE_AVAILABLE = True
except ImportError:
    FIRESTORE_AVAILABLE = False


class CollaborativeFilteringService:
    """
    User-based collaborative filtering for music recommendations.
    
    Recommends songs based on listening behavior of users with similar taste profiles.
    Uses artist-level aggregation and cosine similarity for scalability.
    """
    
    # Configuration - Adaptive thresholds
    MIN_USER_PLAYS = 5  # Minimum plays to build profile (relaxed from 10)
    MIN_UNIQUE_ARTISTS = 2  # Minimum unique artists - RELAXED for small datasets
    
    # Adaptive similarity thresholds based on user count
    SIMILARITY_THRESHOLD_TINY = 0.10    # < 20 users
    SIMILARITY_THRESHOLD_SMALL = 0.15   # 20-50 users
    SIMILARITY_THRESHOLD_MEDIUM = 0.25  # 50-100 users
    SIMILARITY_THRESHOLD_LARGE = 0.35   # 100+ users
    MIN_SIMILARITY_THRESHOLD_FALLBACK = 0.05  # Emergency fallback
    
    # User count tiers for adaptive behavior
    USER_TIER_TINY = 20
    USER_TIER_SMALL = 50
    USER_TIER_MEDIUM = 100
    
    TOP_SIMILAR_USERS = 5  # Max similar users to consider
    TOP_SIMILAR_USERS_SMALL = 3  # Min similar users for small datasets
    TOP_ARTISTS_PER_USER = 20  # Keep top N artists in taste vector
    MAX_RECOMMENDATIONS = 20  # Max tracks to return
    MIN_CF_RESULTS = 8  # Small-base target
    TARGET_CF_RESULTS = 12  # Target number for good experience
    MIN_CF_DIVERSITY_ARTISTS = 5
    MAX_ACTIVE_USERS_SCAN = 120
    
    # Adaptive diversity constraints
    MAX_SONGS_PER_ARTIST_CANDIDATE = 5  # Limit per artist in candidate pool
    MAX_SONGS_PER_ARTIST_SMALL = 2  # Small datasets (< 20 users)
    MAX_SONGS_PER_ARTIST_NORMAL = 1  # Normal/large datasets (20+ users)
    MIN_CANDIDATES_FOR_DIVERSITY = 50  # Skip aggressive diversity if fewer
    MIN_UNIQUE_ARTISTS_SMALL = 4  # Minimum artist diversity for small datasets
    MIN_UNIQUE_ARTISTS_NORMAL = 8  # Minimum artist diversity for normal datasets
    ARTIST_FREQUENCY_PENALTY = 0.35  # Penalty factor for repeated artists
    
    MIN_ARTIST_OVERLAP = 1  # RELAXED: just 1 shared artist is enough
    
    # Cache TTLs
    CACHE_TTL_SIMILARITY = 3 * 3600  # 3 hours
    CACHE_TTL_RECOMMENDATIONS = 30 * 60  # 30 minutes (as requested)
    CACHE_TTL_USER_PROFILE = 30 * 60  # 30 minutes (align with recommendations)
    
    def __init__(self, user_service, recommendation_service=None, decay_lambda: float = 0.15):
        """
        Initialize collaborative filtering service.
        
        Args:
            user_service: UserService instance for accessing user data
            recommendation_service: Reserved for backward compatibility (not used by CF)
            decay_lambda: Time decay factor for recency weighting
        """
        self.user_service = user_service
        self.recommendation_service = recommendation_service
        self.decay_lambda = decay_lambda
        
        # Caches: {key: (data, timestamp)}
        self.cache_user_profiles: Dict[str, Tuple[Dict[str, float], float]] = {}
        self.cache_user_profile_meta: Dict[str, Tuple[Dict, float]] = {}
        self.cache_similarity: Dict[str, Tuple[List[Tuple[str, float]], float]] = {}
        self.cache_recommendations: Dict[str, Tuple[List[Dict], float]] = {}
        self.cache_total_users: Optional[Tuple[int, float]] = None
        
        self._firestore_db = None
        self._small_dataset_mode = False
        self._total_user_count = 0
        self._ensure_firestore_client()
        
        logger.info(f"CollaborativeFilteringService initialized with decay_lambda={decay_lambda}")
    
    def _ensure_firestore_client(self) -> None:
        """Get Firestore client from user_service or initialize."""
        if hasattr(self.user_service, 'db') and self.user_service.db:
            self._firestore_db = self.user_service.db
            logger.info("[CF] Using Firestore client from user_service")
        else:
            db = get_firestore_client()
            if db is not None:
                self._firestore_db = db
                if hasattr(self.user_service, 'db'):
                    self.user_service.db = db
                logger.info("[CF] Firestore connected")
            else:
                logger.error("[CF] Firestore not available")
    
    def _get_total_user_count(self) -> int:
        """Get total number of users, with caching."""
        try:
            # Check cache (TTL: 10 minutes)
            if self.cache_total_users:
                count, timestamp = self.cache_total_users
                if time.time() - timestamp < 600:
                    return count
            
            if not self._firestore_db:
                return 0
            
            # Count users
            users_ref = self._firestore_db.collection('users')
            count = sum(1 for _ in users_ref.stream())
            logger.info(f"[CF] total users loaded = {count}")
            
            # Cache result
            self.cache_total_users = (count, time.time())
            
            return count
        except Exception as e:
            logger.error(f"[CF] Error counting users: {str(e)}")
            return 0
    
    def _detect_small_dataset_mode(self) -> bool:
        """Detect if we're operating in small dataset mode."""
        self._total_user_count = self._get_total_user_count()
        self._small_dataset_mode = self._total_user_count < self.USER_TIER_MEDIUM
        
        # Log dataset tier
        if self._total_user_count < self.USER_TIER_TINY:
            tier = "TINY"
        elif self._total_user_count < self.USER_TIER_SMALL:
            tier = "SMALL"
        elif self._total_user_count < self.USER_TIER_MEDIUM:
            tier = "MEDIUM"
        else:
            tier = "LARGE"
        
        logger.info(f"[CF] Dataset tier: {tier} (users={self._total_user_count})")
        
        return self._small_dataset_mode
    
    def _get_adaptive_similarity_threshold(self) -> float:
        """
        Get adaptive similarity threshold based on total user count.
        
        Strategy:
        - < 20 users: Very relaxed (0.10) to ensure results
        - 20-50 users: Relaxed (0.15) for usability
        - 50-100 users: Standard (0.25) for quality
        - 100+ users: Strict (0.35) for precision
        
        Returns:
            Similarity threshold [0.10, 0.35]
        """
        count = self._total_user_count
        
        if count < self.USER_TIER_TINY:
            threshold = self.SIMILARITY_THRESHOLD_TINY
        elif count < self.USER_TIER_SMALL:
            threshold = self.SIMILARITY_THRESHOLD_SMALL
        elif count < self.USER_TIER_MEDIUM:
            threshold = self.SIMILARITY_THRESHOLD_MEDIUM
        else:
            threshold = self.SIMILARITY_THRESHOLD_LARGE
        
        logger.info(f"[CF] Adaptive threshold: {threshold} (users={count})")
        
        return threshold

    def _normalize_artist_name(self, name: str) -> str:
        """Normalize artist names: remove noise/emoji, trim, normalize casing."""
        if not name:
            return ""

        normalized = str(name).strip().lower()

        # Ignore topic channel uploads
        if normalized == "release - topic" or normalized.endswith(" - topic"):
            return ""

        # Remove emoji and non-text noise
        normalized = re.sub(r"[\U00010000-\U0010ffff]", "", normalized)
        normalized = re.sub(r"[^a-z0-9&'\-\.\s]", " ", normalized)
        normalized = re.sub(r"\s+", " ", normalized).strip()

        return normalized

    def _extract_artists_from_play(self, data: Dict) -> List[str]:
        """Extract and normalize artist names from a play record."""
        artists_raw = data.get('artists') or data.get('artist') or data.get('artistName') or []
        artists: List[str] = []

        if isinstance(artists_raw, list):
            for artist in artists_raw:
                if isinstance(artist, dict):
                    name = artist.get('name', '')
                else:
                    name = str(artist)
                normalized = self._normalize_artist_name(name)
                if normalized:
                    artists.append(normalized)
        else:
            normalized = self._normalize_artist_name(str(artists_raw))
            if normalized:
                artists.append(normalized)

        # De-duplicate while preserving order
        seen = set()
        unique_artists = []
        for name in artists:
            if name not in seen:
                seen.add(name)
                unique_artists.append(name)

        return unique_artists

    def _get_user_played_video_ids(self, uid: str) -> Set[str]:
        """Fetch played video IDs for exclusion."""
        if not self._firestore_db:
            return {s.get('videoId') for s in self.user_service.get_user_plays(uid) if s.get('videoId')}

        plays_ref = self._firestore_db.collection('users').document(uid).collection('plays')
        plays_docs = list(plays_ref.stream())
        return {doc.to_dict().get('videoId') for doc in plays_docs if doc.to_dict().get('videoId')}

    def _get_user_liked_video_ids(self, uid: str) -> Set[str]:
        """Fetch liked video IDs for exclusion."""
        liked = self.user_service.get_user_liked_songs(uid)
        return {track.get('videoId') for track in liked if track.get('videoId')}

    def _get_user_playlist_video_ids(self, uid: str) -> Set[str]:
        """Fetch playlist video IDs for exclusion."""
        if not self._firestore_db:
            return set()

        playlist_ids: Set[str] = set()
        try:
            playlists_ref = self._firestore_db.collection('users').document(uid).collection('playlists')
            for playlist_doc in playlists_ref.stream():
                tracks_ref = playlists_ref.document(playlist_doc.id).collection('tracks')
                for track_doc in tracks_ref.stream():
                    data = track_doc.to_dict() or {}
                    video_id = data.get('videoId')
                    if video_id:
                        playlist_ids.add(video_id)
        except Exception as e:
            logger.warning(f"[CF] playlist exclusion read failed for {uid[:8]}: {str(e)}")
        return playlist_ids

    def _get_active_user_ids(self, current_uid: str) -> List[str]:
        """Return active users only, with bounded scans for scalability."""
        if not self._firestore_db:
            return []

        users_ref = self._firestore_db.collection('users')
        active_ids: List[str] = []

        def add_ids_from_query(field_name: str):
            try:
                docs = users_ref.order_by(field_name, direction=firestore.Query.DESCENDING).limit(self.MAX_ACTIVE_USERS_SCAN).stream()
                for doc in docs:
                    if doc.id != current_uid and doc.id not in active_ids:
                        active_ids.append(doc.id)
            except Exception:
                return

        add_ids_from_query('lastActive')
        add_ids_from_query('updatedAt')

        if not active_ids:
            for doc in users_ref.limit(self.MAX_ACTIVE_USERS_SCAN).stream():
                if doc.id != current_uid:
                    active_ids.append(doc.id)

        return active_ids[:self.MAX_ACTIVE_USERS_SCAN]

    def _collect_candidate_signals(self, other_uid: str) -> List[Tuple[Dict, str]]:
        """Collect candidate signals from another user using plays, likes, and playlists only."""
        if not self._firestore_db:
            return []

        signals: List[Tuple[Dict, str]] = []

        try:
            plays_ref = self._firestore_db.collection('users').document(other_uid).collection('plays')
            for doc in plays_ref.stream():
                data = doc.to_dict() or {}
                if data.get('videoId'):
                    signals.append((data, 'play'))
        except Exception:
            pass

        try:
            liked_ref = self._firestore_db.collection('users').document(other_uid).collection('likedSongs')
            for doc in liked_ref.stream():
                data = doc.to_dict() or {}
                if data.get('videoId'):
                    signals.append((data, 'liked'))
        except Exception:
            pass

        try:
            playlists_ref = self._firestore_db.collection('users').document(other_uid).collection('playlists')
            for playlist_doc in playlists_ref.stream():
                tracks_ref = playlists_ref.document(playlist_doc.id).collection('tracks')
                for track_doc in tracks_ref.stream():
                    data = track_doc.to_dict() or {}
                    if data.get('videoId'):
                        signals.append((data, 'playlist'))
        except Exception:
            pass

        return signals

    def _enrich_track_thumbnail(self, track: Dict) -> str:
        """
        Ensure track has artwork thumbnail.
        
        - Prefers existing thumbnail from data
        - Falls back to hqdefault.jpg URL if missing
        - Guarantees artwork for every track
        
        Args:
            track: Track dictionary from Firestore
            
        Returns:
            Thumbnail URL (never empty)
        """
        # Try existing thumbnail first
        thumbnail = track.get('thumbnail', '').strip()
        if thumbnail:
            return thumbnail
        
        # Fallback: generate from videoId
        video_id = track.get('videoId', '').strip()
        if video_id:
            thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
            return thumbnail
        
        # Last resort: empty (should not happen with valid tracks)
        return ""

    def _extract_song_payload(self, data: Dict, score: float) -> Dict:
        """
        Extract track payload with enriched thumbnail.
        
        Ensures every track object includes artwork by using fallback
        hqdefault.jpg URL if thumbnail is missing from Firestore data.
        
        Returns track in standard format matching trending/recommendations:
        {
            'videoId': str,
            'title': str,
            'artists': [str, ...],
            'album': str,
            'duration': int,
            'thumbnail': str (guaranteed via fallback),
            'cf_score': float
        }
        """
        video_id = data.get('videoId', '').strip()
        thumbnail = self._enrich_track_thumbnail(data)
        
        track_payload = {
            'videoId': video_id,
            'title': data.get('title', ''),
            'artists': data.get('artists', []),
            'album': data.get('album', ''),
            'duration': data.get('duration'),
            'thumbnail': thumbnail,
            'cf_score': round(score, 4)
        }
        
        # Log thumbnail enrichment
        if not data.get('thumbnail', '').strip():
            logger.debug(f"[CF] Enriched track {video_id} with fallback thumbnail: {thumbnail}")
        
        return track_payload
    
    def _is_actual_music_track(self, track: Dict) -> bool:
        """
        Validate that track is actual music content, not podcast/interview/etc.
        
        Filters based on:
        1. Duration (60s - 12 minutes)
        2. Title blocklist (interview, podcast, episode, etc.)
        3. Artist validation (reject "Topic" channel + non-music keywords)
        
        Args:
            track: Track dictionary to validate
            
        Returns:
            True if track passes all music validation, False otherwise
        """
        title = (track.get('title') or '').strip().lower()
        duration = track.get('duration')
        artists = track.get('artists') or []
        
        # FILTER 1: Duration validation (60s - 12min = 720s)
        if duration:
            try:
                # Handle both int and string durations
                dur_seconds = int(duration) if isinstance(duration, (int, float)) else 0
                # If it's a string like "4:28", skip for now (will be checked at upload time)
                if dur_seconds > 0:
                    if dur_seconds < 60 or dur_seconds > 720:
                        logger.debug(f"[CF FILTER] Rejected (duration={dur_seconds}s): {track.get('videoId')}")
                        return False
            except (ValueError, TypeError):
                # If we can't parse duration, allow it (user data might be incomplete)
                pass
        
        # FILTER 2: Title blocklist (case-insensitive)
        non_music_keywords = {
            'interview', 'podcast', 'episode', 'ep.', 'ep ', 'talk', 'speech',
            'press meet', 'live interview', 'full interview', 'reaction', 'review',
            'discussion', 'trailer', 'teaser', 'background score', 'bgm',
            'instrumental version', 'karaoke', 'cover by', 'tribute',
            'lofi mix', 'dj mix'
        }
        
        for keyword in non_music_keywords:
            if keyword in title:
                logger.debug(f"[CF FILTER] Rejected (title='{keyword}'): {track.get('videoId')} - {track.get('title')}")
                return False
        
        # FILTER 3: Artist validation
        # Reject if artist is "Topic" channel AND title contains episode/interview
        artist_str = ' '.join(artists).lower()
        
        # Check for Topic channel
        if 'topic' in artist_str:
            # If it's the Topic channel, reject if title suggests non-music
            suspicious_terms = {'episode', 'interview', 'podcast', 'talk', 'speech'}
            if any(term in title for term in suspicious_terms):
                logger.debug(f"[CF FILTER] Rejected (Topic channel + non-music): {track.get('videoId')}")
                return False
        
        # Reject if artist contains news/radio identifiers
        news_radio_markers = {
            'news', 'radio station', 'radio', 'live stream', 'channel',
            'broadcast', 'news channel', 'news today'
        }
        
        for marker in news_radio_markers:
            if marker in artist_str:
                # More lenient: only reject if title also has suspicious keywords
                if any(kw in title for kw in ['interview', 'episode', 'podcast', 'talk']):
                    logger.debug(f"[CF FILTER] Rejected (news/radio + keywords): {track.get('videoId')}")
                    return False
        
        # FILTER 4: Album validation (soft filter - prefer tracks with album)
        # Note: This is informational, not a hard reject
        has_album = bool((track.get('album') or '').strip())
        
        return True

    def _filter_non_music_tracks(self, tracks: List[Dict]) -> List[Dict]:
        """
        Filter out non-music content (podcasts, interviews, etc.)
        
        Applies comprehensive music validation to ensure only actual songs are returned.
        
        Args:
            tracks: List of recommended tracks
            
        Returns:
            Filtered list containing only valid music tracks
        """
        if not tracks:
            return tracks
        
        input_count = len(tracks)
        filtered_tracks = []
        rejected_count = 0
        
        for track in tracks:
            if self._is_actual_music_track(track):
                filtered_tracks.append(track)
            else:
                rejected_count += 1
        
        logger.info(f"[CF FILTER] rejected_non_music={rejected_count} final_tracks={len(filtered_tracks)} (input={input_count})")
        
        return filtered_tracks
    
    def get_cf_recommendations(self, uid: str, exclude_video_ids: Optional[Set[str]] = None, main_rec_ids: Optional[Set[str]] = None) -> List[Dict]:
        """
        Generate collaborative filtering recommendations for a user.
        
        Args:
            uid: User ID
            exclude_video_ids: Set of video IDs to exclude (already consumed)
            main_rec_ids: Set of video IDs from main recommendations to exclude (prevent duplicates)
            
        Returns:
            List of recommended tracks with metadata
        """
        try:
            cache_key = f"cf_recs_{uid}"
            cached = self.cache_recommendations.get(cache_key)
            if cached:
                cached_value, timestamp = cached
                if time.time() - timestamp < self.CACHE_TTL_RECOMMENDATIONS:
                    runtime_excludes = set(exclude_video_ids or set())
                    if main_rec_ids:
                        runtime_excludes |= set(main_rec_ids)
                    if runtime_excludes:
                        return [track for track in cached_value if track.get('videoId') not in runtime_excludes]
                    return cached_value

            if not self._firestore_db:
                self._ensure_firestore_client()
            if not self._firestore_db:
                return []

            self._detect_small_dataset_mode()
            user_profile = self._build_user_taste_vector(uid)
            profile_meta_cached = self.cache_user_profile_meta.get(f"profile_{uid}")
            profile_meta = profile_meta_cached[0] if profile_meta_cached else {}
            logger.info(f"[CF] profile artists={profile_meta.get('artist_count', len(user_profile))} albums={profile_meta.get('album_count', 0)}")
            if not user_profile:
                return []

            exclude_ids = set(exclude_video_ids or set())
            exclude_ids |= self._get_user_played_video_ids(uid)
            exclude_ids |= self._get_user_liked_video_ids(uid)
            if main_rec_ids:
                exclude_ids |= set(main_rec_ids)

            similar_users = self._find_similar_users(uid, user_profile, threshold=0.12)
            logger.info(f"[CF] similar users found = {len(similar_users)}")

            recommendations, metrics = self._generate_recommendations_from_similar_users(
                uid=uid,
                similar_users=similar_users,
                exclude_video_ids=exclude_ids,
                current_user_artists=set(user_profile.keys())
            )
            logger.info(f"[CF] candidate songs gathered = {metrics.get('candidates', 0)}")
            logger.info(f"[CF] after filtering={metrics.get('after_filtering', 0)}")

            if len(recommendations) < 6:
                relaxed_similar = self._find_similar_users(uid, user_profile, threshold=0.08)
                relaxed_recs, relaxed_metrics = self._generate_recommendations_from_similar_users(
                    uid=uid,
                    similar_users=relaxed_similar,
                    exclude_video_ids=exclude_ids,
                    current_user_artists=set(user_profile.keys())
                )
                merged = {track.get('videoId'): track for track in recommendations if track.get('videoId')}
                for track in relaxed_recs:
                    video_id = track.get('videoId')
                    if video_id and video_id not in merged:
                        merged[video_id] = track
                recommendations = sorted(merged.values(), key=lambda t: t.get('cf_score', 0.0), reverse=True)

                if len(recommendations) < 6:
                    trending_from_similar = self._cold_start_fallback(uid, exclude_ids)
                    existing = {track.get('videoId') for track in recommendations if track.get('videoId')}
                    for track in trending_from_similar:
                        video_id = track.get('videoId')
                        if video_id and video_id not in existing:
                            recommendations.append(track)
                            existing.add(video_id)
                        if len(recommendations) >= self.MIN_CF_RESULTS:
                            break

                logger.info(f"[CF] candidate songs gathered = {relaxed_metrics.get('candidates', 0)}")

            recommendations = recommendations[:self.MAX_RECOMMENDATIONS]
            logger.info(f"[CF] final results={len(recommendations)}")

            # Filter out non-music content (podcasts, interviews, etc.)
            recommendations = self._filter_non_music_tracks(recommendations)
            logger.info(f"[CF] after music filtering={len(recommendations)}")

            # Verify artwork enrichment
            if recommendations:
                sample_track = recommendations[0]
                thumbnail_present = bool(sample_track.get('thumbnail', '').strip())
                logger.info(f"[CF] sample track → thumbnail present: {thumbnail_present}")

            self.cache_recommendations[cache_key] = (recommendations, time.time())
            return recommendations

        except Exception as e:
            logger.error(f"[CF] error generating recommendations for {uid[:8]}: {str(e)}", exc_info=True)
            return []
    
    def _build_artist_vector(self, uid: str) -> Dict[str, float]:
        """
        Build weighted artist vector for a user with multi-signal aggregation.
        
        WEIGHTS:
        - Plays: weight = 1
        - Liked songs: weight = 3 (higher weight for explicit preference)
        - Playlist songs: weight = 2 (medium weight for intentional curation)
        
        Returns:
            Dict mapping artist name to weighted score (normalized to [0, 1])
        """
        try:
            if not self._firestore_db:
                return {}
            
            # Initialize artist scores
            artist_scores = defaultdict(float)
            signal_counts = {'plays': 0, 'liked': 0, 'playlists': 0}
            current_time = time.time()
            
            # SIGNAL 1: Plays (weight = 1.0)
            try:
                plays_ref = self._firestore_db.collection('users').document(uid).collection('plays')
                for doc in plays_ref.stream():
                    data = doc.to_dict()
                    artists = self._extract_artists_from_play(data)
                    play_count = data.get('playCount', 1)
                    
                    # Calculate recency boost
                    recency = 1.0
                    last_played_at = data.get('lastPlayedAt')
                    if last_played_at:
                        try:
                            if hasattr(last_played_at, 'timestamp'):
                                ts = last_played_at.timestamp()
                            else:
                                from datetime import datetime
                                ts = datetime.fromisoformat(str(last_played_at).replace('Z', '+00:00')).timestamp()
                            days_ago = (current_time - ts) / 86400
                            recency = math.exp(-self.decay_lambda * days_ago)
                        except:
                            pass
                    
                    # Score: play_count × recency (weight = 1.0)
                    signal_score = play_count * recency
                    for artist in artists:
                        artist_scores[artist] += signal_score
                    
                    signal_counts['plays'] += 1
            except Exception as e:
                logger.warning(f"[CF] Error collecting plays for {uid}: {str(e)}")
            
            # SIGNAL 2: Liked songs (weight = 3.0)
            try:
                liked_ref = self._firestore_db.collection('users').document(uid).collection('likedSongs')
                for doc in liked_ref.stream():
                    data = doc.to_dict()
                    artists = self._extract_artists_from_play(data)
                    
                    # Score: 3.0 (explicit preference indicator)
                    signal_score = 3.0
                    for artist in artists:
                        artist_scores[artist] += signal_score
                    
                    signal_counts['liked'] += 1
            except Exception as e:
                logger.warning(f"[CF] Error collecting likes for {uid}: {str(e)}")
            
            # SIGNAL 3: Playlist songs (weight = 2.0)
            try:
                playlists_ref = self._firestore_db.collection('users').document(uid).collection('playlists')
                for playlist_doc in playlists_ref.stream():
                    playlist_id = playlist_doc.id
                    tracks_ref = playlists_ref.document(playlist_id).collection('tracks')
                    for track_doc in tracks_ref.stream():
                        data = track_doc.to_dict()
                        artists = self._extract_artists_from_play(data)
                        
                        # Score: 2.0 (intentional curation indicator)
                        signal_score = 2.0
                        for artist in artists:
                            artist_scores[artist] += signal_score
                        
                        signal_counts['playlists'] += 1
            except Exception as e:
                logger.warning(f"[CF] Error collecting playlists for {uid}: {str(e)}")
            
            # Validate minimum data
            total_signals = sum(signal_counts.values())
            if total_signals < self.MIN_USER_PLAYS:
                logger.debug(f"[CF] Insufficient signals for {uid}: {total_signals} < {self.MIN_USER_PLAYS}")
                return {}
            
            if not artist_scores:
                return {}
            
            # Normalize to [0, 1]
            max_score = max(artist_scores.values())
            normalized = {artist: score / max_score for artist, score in artist_scores.items()}
            
            # Keep top N artists
            top_artists = dict(
                sorted(normalized.items(), key=lambda x: x[1], reverse=True)[:self.TOP_ARTISTS_PER_USER]
            )
            
            logger.debug(f"[CF] Artist vector: {len(top_artists)} artists, signals: plays={signal_counts['plays']} likes={signal_counts['liked']} playlists={signal_counts['playlists']}")
            
            return top_artists
            
        except Exception as e:
            logger.error(f"[CF] Error building artist vector: {str(e)}")
            return {}
    
    def _build_user_taste_vector(self, uid: str) -> Dict[str, float]:
        """
        Build normalized taste vector for a user using unified profile builder.
        Now uses the same data source as daily_mix_service.
        
        Returns:
            Dict mapping artist name to normalized weight (0-1)
        """
        try:
            cache_key = f"profile_{uid}"
            cached = self.cache_user_profiles.get(cache_key)
            cached_meta = self.cache_user_profile_meta.get(cache_key)
            if cached:
                cached_value, timestamp = cached
                if time.time() - timestamp < self.CACHE_TTL_USER_PROFILE and cached_meta and (time.time() - cached_meta[1] < self.CACHE_TTL_USER_PROFILE):
                    return cached_value

            plays = []
            liked = []
            artist_scores = defaultdict(float)
            album_scores = defaultdict(float)

            if self.recommendation_service:
                signals = self.recommendation_service._fetch_user_signals(uid)
                plays = signals.get('plays', []) or []
                liked = signals.get('liked', []) or []
                weighted = self.recommendation_service._build_weighted_signals_cached(uid, plays, liked)

                for artist_name, score in (weighted.get('artist_scores', {}) or {}).items():
                    normalized_artist = self._normalize_artist_name(str(artist_name))
                    if normalized_artist:
                        artist_scores[normalized_artist] += float(score)

                for album_name, score in (weighted.get('album_scores', {}) or {}).items():
                    normalized_album = str(album_name).strip().lower()
                    if normalized_album:
                        album_scores[normalized_album] += float(score)

            # playlist signal enrichment (reuse existing Firestore data source)
            playlist_tracks_count = 0
            if self._firestore_db:
                try:
                    playlists_ref = self._firestore_db.collection('users').document(uid).collection('playlists')
                    for playlist_doc in playlists_ref.stream():
                        tracks_ref = playlists_ref.document(playlist_doc.id).collection('tracks')
                        for track_doc in tracks_ref.stream():
                            data = track_doc.to_dict() or {}
                            artists = self._extract_artists_from_play(data)
                            for artist_name in artists:
                                artist_scores[artist_name] += 2.0

                            album_name = str(data.get('album', '')).strip().lower()
                            if album_name:
                                album_scores[album_name] += 1.5
                            playlist_tracks_count += 1
                except Exception as e:
                    logger.debug(f"[CF] playlist profile enrichment failed for {uid[:8]}: {str(e)}")

            if not artist_scores:
                return {}

            max_artist_score = max(artist_scores.values()) if artist_scores else 1.0
            artist_vector = {
                artist: score / max_artist_score
                for artist, score in artist_scores.items()
            }
            top_artists = dict(
                sorted(artist_vector.items(), key=lambda item: item[1], reverse=True)[:self.TOP_ARTISTS_PER_USER]
            )

            max_album_score = max(album_scores.values()) if album_scores else 0.0
            album_vector = {}
            if max_album_score > 0:
                album_vector = {
                    album: score / max_album_score
                    for album, score in album_scores.items()
                }

            plays_count = len(plays)
            artist_count = len(top_artists)
            album_count = len(album_vector)
            has_profile = plays_count >= 5 or artist_count >= 2

            logger.info(f"[CF] profile detected: plays={plays_count} artists={artist_count} albums={album_count}")

            profile_meta = {
                'plays_count': plays_count,
                'liked_count': len(liked),
                'playlist_tracks_count': playlist_tracks_count,
                'artist_count': artist_count,
                'album_count': album_count,
                'artist_vector': top_artists,
                'album_vector': album_vector,
                'play_frequency': float(plays_count)
            }

            self.cache_user_profiles[cache_key] = (top_artists if has_profile else {}, time.time())
            self.cache_user_profile_meta[cache_key] = (profile_meta, time.time())
            return top_artists if has_profile else {}

        except Exception as e:
            logger.error(f"[CF] Error building taste vector for {uid}: {str(e)}", exc_info=True)
            return {}
    
    def _soft_similarity_scoring(
        self, 
        user_profile_a: Dict[str, float], 
        user_profile_b: Dict[str, float]
    ) -> float:
        """
        Calculate soft similarity using multiple factors:
        - 0.5 * artist_overlap
        - 0.3 * genre_overlap (placeholder, using artist diversity as proxy)
        - 0.2 * listening_frequency_overlap
        
        Args:
            user_profile_a: First user's taste vector
            user_profile_b: Second user's taste vector
            
        Returns:
            Weighted similarity score [0, 1]
        """
        try:
            set_a = set(user_profile_a.keys())
            set_b = set(user_profile_b.keys())
            
            if not set_a or not set_b:
                return 0.0
            
            # 1. Artist overlap (Jaccard similarity)
            intersection = set_a & set_b
            union = set_a | set_b
            artist_overlap = len(intersection) / len(union) if union else 0.0
            
            # 2. Genre overlap (using artist diversity as proxy)
            # More shared artists = similar genre preferences
            common_artists = intersection
            genre_overlap = len(common_artists) / max(len(set_a), len(set_b)) if common_artists else 0.0
            
            # 3. Listening frequency overlap (compare weights for common artists)
            frequency_overlap = 0.0
            if common_artists:
                weight_diffs = []
                for artist in common_artists:
                    diff = abs(user_profile_a[artist] - user_profile_b[artist])
                    weight_diffs.append(1.0 - diff)  # Convert diff to similarity
                frequency_overlap = sum(weight_diffs) / len(weight_diffs)
            
            # Weighted combination
            score = (
                0.5 * artist_overlap +
                0.3 * genre_overlap +
                0.2 * frequency_overlap
            )
            
            return max(0.0, min(1.0, score))
            
        except Exception as e:
            logger.error(f"[CF] Error computing soft similarity: {str(e)}")
            return 0.0
    
    def _find_similar_users(self, uid: str, user_profile: Dict[str, float], threshold: float = 0.12) -> List[Tuple[str, float]]:
        """
        Find users with similar taste using cosine similarity.
        
        Args:
            uid: Current user ID
            user_profile: User's taste vector
            
        Returns:
            List of (user_id, similarity_score) tuples, sorted by similarity
        """
        try:
            effective_threshold = threshold
            if self._total_user_count < self.USER_TIER_SMALL:
                effective_threshold = min(effective_threshold, 0.10)

            cache_key = f"similar_{uid}_{effective_threshold:.2f}"
            cached = self.cache_similarity.get(cache_key)
            if cached:
                cached_value, timestamp = cached
                if time.time() - timestamp < self.CACHE_TTL_SIMILARITY:
                    return cached_value

            if not self._firestore_db:
                return []

            active_user_ids = self._get_active_user_ids(uid)
            user_artists = set(user_profile.keys())
            current_meta_entry = self.cache_user_profile_meta.get(f"profile_{uid}")
            current_album_vector = (current_meta_entry[0].get('album_vector', {}) if current_meta_entry else {})
            current_album_set = set(current_album_vector.keys())
            current_play_frequency = float(current_meta_entry[0].get('play_frequency', 0.0)) if current_meta_entry else 0.0
            similarities: List[Tuple[str, float]] = []

            for other_uid in active_user_ids:
                other_profile = self._build_user_taste_vector(other_uid)
                if not other_profile:
                    continue

                other_meta_entry = self.cache_user_profile_meta.get(f"profile_{other_uid}")
                other_album_vector = (other_meta_entry[0].get('album_vector', {}) if other_meta_entry else {})
                other_album_set = set(other_album_vector.keys())
                other_play_frequency = float(other_meta_entry[0].get('play_frequency', 0.0)) if other_meta_entry else 0.0

                shared_artists = len(user_artists & set(other_profile.keys()))
                shared_albums = len(current_album_set & other_album_set)

                if shared_artists < 1 and shared_albums < 1:
                    continue

                similarity = self._cosine_similarity(user_profile, other_profile)
                album_union = len(current_album_set | other_album_set)
                album_overlap_ratio = (shared_albums / album_union) if album_union > 0 else 0.0
                similarity += 0.15 * album_overlap_ratio

                if current_play_frequency > 0 and other_play_frequency > 0:
                    play_frequency_ratio = min(current_play_frequency, other_play_frequency) / max(current_play_frequency, other_play_frequency)
                    similarity += 0.05 * play_frequency_ratio

                similarity = max(0.0, min(1.0, similarity))
                if similarity >= effective_threshold:
                    similarities.append((other_uid, similarity))

            similarities.sort(key=lambda item: item[1], reverse=True)
            top_similar = similarities[:self.TOP_SIMILAR_USERS]
            self.cache_similarity[cache_key] = (top_similar, time.time())
            return top_similar

        except Exception as e:
            logger.error(f"[CF] Error finding similar users for {uid}: {str(e)}", exc_info=True)
            return []

    def _jaccard_similarity(self, vec_a: Dict[str, float], vec_b: Dict[str, float]) -> float:
        """Compute Jaccard similarity on artist sets."""
        try:
            set_a = set(vec_a.keys())
            set_b = set(vec_b.keys())

            if not set_a or not set_b:
                return 0.0

            intersection = set_a & set_b
            union = set_a | set_b
            if not union:
                return 0.0

            return len(intersection) / len(union)
        except Exception as e:
            logger.error(f"[CF] Error computing Jaccard similarity: {str(e)}")
            return 0.0
    
    def _cosine_similarity(self, vec_a: Dict[str, float], vec_b: Dict[str, float]) -> float:
        """
        Compute cosine similarity between two taste vectors.
        
        Args:
            vec_a: First taste vector
            vec_b: Second taste vector
            
        Returns:
            Similarity score [0, 1]
        """
        try:
            # Find common artists
            common_artists = set(vec_a.keys()) & set(vec_b.keys())
            
            if not common_artists:
                return 0.0
            
            # Compute dot product
            dot_product = sum(vec_a[artist] * vec_b[artist] for artist in common_artists)
            
            # Compute magnitudes
            mag_a = math.sqrt(sum(v ** 2 for v in vec_a.values()))
            mag_b = math.sqrt(sum(v ** 2 for v in vec_b.values()))
            
            if mag_a == 0 or mag_b == 0:
                return 0.0
            
            # Cosine similarity
            similarity = dot_product / (mag_a * mag_b)
            
            return max(0.0, min(1.0, similarity))  # Clamp to [0, 1]
            
        except Exception as e:
            logger.error(f"[CF] Error computing cosine similarity: {str(e)}")
            return 0.0
    
    def _generate_recommendations_from_similar_users(
        self, 
        uid: str, 
        similar_users: List[Tuple[str, float]],
        exclude_video_ids: Set[str],
        current_user_artists: Set[str]
    ) -> Tuple[List[Dict], Dict[str, int]]:
        """
        Generate CF recommendations using 8-step artist-vector process.
        
        STEP 1: Build current user's complete known set
        STEP 2: Collect candidate songs from similar users (plays, likes, playlists, shared artists)
        STEP 3: Apply multi-signal scoring formula
        STEP 4: Remove known songs (discovery guarantee)
        STEP 5: Apply diversity constraints
        STEP 6: Return top recommendations
        STEP 7: Log attribution and metrics
        STEP 8: Fallback if insufficient results
        
        SCORING FORMULA:
        - Base: similarity_score × 5
        - Liked: +4 per similar user who liked it
        - Played: +3 per frequent play (high play count)
        - Playlist: +2 per similar user in whose playlist it is
        - Shared artist: +2 per match with overlapping artists
        
        Args:
            uid: Current user ID
            similar_users: List of (user_id, similarity_score)
            exclude_video_ids: Video IDs to exclude (current user's plays + likes)
            
        Returns:
            List of recommended tracks for discovery
        """
        try:
            if not self._firestore_db or not similar_users:
                return [], {'candidates': 0, 'after_filtering': 0}

            candidate_scores = defaultdict(lambda: {
                'data': None,
                'artists': [],
                'similarity_sum': 0.0,
                'frequency': 0.0,
                'recency': 0.0,
                'liked_boost': 0.0,
                'source_users': set(),
                'source_signals': set()
            })
            artist_user_support = defaultdict(set)

            for other_uid, user_similarity in similar_users:
                for data, signal in self._collect_candidate_signals(other_uid):
                    video_id = data.get('videoId')
                    if not video_id or video_id in exclude_video_ids:
                        continue

                    artists_norm = self._extract_artists_from_play(data)
                    if not artists_norm:
                        continue

                    entry = candidate_scores[video_id]
                    if entry['data'] is None:
                        entry['data'] = data
                        entry['artists'] = artists_norm

                    play_count = data.get('playCount', 1)
                    try:
                        play_count = max(1, int(play_count))
                    except Exception:
                        play_count = 1

                    timestamp = data.get('lastPlayedAt') or data.get('playedAt') or data.get('likedAt') or data.get('updatedAt')
                    recency = self._calculate_recency_boost(timestamp)

                    entry['similarity_sum'] += user_similarity
                    entry['frequency'] += play_count if signal == 'play' else 1.0
                    entry['recency'] = max(entry['recency'], recency)
                    entry['liked_boost'] = max(entry['liked_boost'], 1.0 if signal == 'liked' else 0.0)
                    entry['source_users'].add(other_uid)
                    entry['source_signals'].add(signal)

                    for artist_name in artists_norm:
                        artist_user_support[artist_name].add(other_uid)

            if not candidate_scores:
                return [], {'candidates': 0, 'after_filtering': 0}

            scored_candidates = []
            for video_id, item in candidate_scores.items():
                data = item['data']
                if not data:
                    continue

                source_user_count = max(1, len(item['source_users']))
                avg_similarity = item['similarity_sum'] / source_user_count
                frequency_weight = min(1.0, float(item['frequency']) / 5.0)
                recency_weight = float(item['recency'])
                liked_boost = float(item['liked_boost'])

                primary_artist = item['artists'][0] if item['artists'] else ''
                support_count = len(artist_user_support.get(primary_artist, set()))
                shared_artist_bonus = min(0.1, 0.03 * support_count)
                new_artist_bonus = 0.08 if primary_artist and primary_artist not in current_user_artists else 0.0

                score = (
                    0.5 * avg_similarity
                    + 0.2 * frequency_weight
                    + 0.2 * recency_weight
                    + 0.1 * liked_boost
                    + shared_artist_bonus
                    + new_artist_bonus
                )

                scored_candidates.append((video_id, score, data, item['artists']))

            scored_candidates.sort(key=lambda row: row[1], reverse=True)

            max_per_artist = 3 if self._total_user_count < self.USER_TIER_SMALL else 2
            artist_counts = defaultdict(int)
            selected: List[Dict] = []
            for video_id, score, data, artists_norm in scored_candidates:
                primary_artist = artists_norm[0] if artists_norm else 'unknown'
                if artist_counts[primary_artist] >= max_per_artist:
                    continue
                selected.append(self._extract_song_payload(data, score))
                artist_counts[primary_artist] += 1
                if len(selected) >= self.MAX_RECOMMENDATIONS:
                    break

            unique_artists = {self._normalize_artist_name((track.get('artists') or [''])[0] if track.get('artists') else '') for track in selected}
            if (len(unique_artists) < self.MIN_CF_DIVERSITY_ARTISTS or len(selected) < self.MIN_CF_RESULTS) and len(selected) < self.MAX_RECOMMENDATIONS:
                for video_id, score, data, artists_norm in scored_candidates:
                    if any(existing.get('videoId') == video_id for existing in selected):
                        continue
                    primary_artist = artists_norm[0] if artists_norm else 'unknown'
                    if artist_counts[primary_artist] >= max_per_artist:
                        continue
                    selected.append(self._extract_song_payload(data, score))
                    artist_counts[primary_artist] += 1
                    if len(selected) >= self.MAX_RECOMMENDATIONS:
                        break

            metrics = {
                'candidates': len(candidate_scores),
                'after_filtering': len(selected)
            }
            return selected, metrics

        except Exception as e:
            logger.error(f"[CF] Error in recommendation generation: {str(e)}", exc_info=True)
            return [], {'candidates': 0, 'after_filtering': 0}
    
    def _calculate_recency_boost(self, last_played_at) -> float:
        """Calculate recency boost for a song based on when it was last played."""
        try:
            if not last_played_at:
                return 1.0
            
            current_time = time.time()
            
            if hasattr(last_played_at, 'timestamp'):
                last_played_ts = last_played_at.timestamp()
            else:
                from datetime import datetime
                last_played_ts = datetime.fromisoformat(
                    str(last_played_at).replace('Z', '+00:00')
                ).timestamp()
            
            days_ago = (current_time - last_played_ts) / 86400
            recency = math.exp(-self.decay_lambda * days_ago)
            
            return recency
        except Exception:
            return 1.0
    
    def _apply_diversity_ranking(
        self,
        track_scores: Dict,
        similar_user_artists: Set[str],
        min_results: int = 5,
        max_results: int = 20
    ) -> List[Dict]:
        """
        Apply diversity-aware ranking with artist frequency penalty and round-robin selection.
        
        Strategy:
        1. Group tracks by artist
        2. Apply artist frequency penalty to scores
        3. Use round-robin selection across artists
        4. Enforce adaptive artist limits
        5. Ensure minimum diversity (4-8 unique artists)
        
        Args:
            track_scores: Dict of {video_id: {score, data, artists, play_count}}
            similar_user_artists: Set of artists from similar users (for fallback)
            min_results: Minimum tracks to return
            max_results: Maximum tracks to return
            
        Returns:
            List of diverse recommendations
        """
        try:
            if not track_scores:
                return []
            
            # STEP 2: Group tracks by primary artist
            tracks_by_artist = defaultdict(list)
            
            for video_id, track_info in track_scores.items():
                artists = track_info.get('artists', [])
                primary_artist = artists[0] if artists else 'Unknown'
                
                tracks_by_artist[primary_artist].append({
                    'videoId': video_id,
                    'score': track_info['score'],
                    'data': track_info['data'],
                    'artists': artists,
                    'play_count': track_info['play_count']
                })
            
            logger.info(f"[CF]   Tracks grouped into {len(tracks_by_artist)} artists")
            
            # Sort tracks within each artist by score
            for artist, tracks in tracks_by_artist.items():
                tracks.sort(key=lambda x: (x['score'], x['play_count']), reverse=True)
            
            # STEP 3: Adaptive artist limit
            max_per_artist = self.MAX_SONGS_PER_ARTIST_SMALL if self._total_user_count < self.USER_TIER_TINY else self.MAX_SONGS_PER_ARTIST_NORMAL
            logger.info(f"[CF]   Max songs per artist: {max_per_artist} (user_count={self._total_user_count})")
            
            # STEP 4: Round-robin artist selection with frequency penalty
            recommendations = []
            artist_counters = defaultdict(int)  # Track how many songs per artist
            sorted_artists = sorted(tracks_by_artist.keys(), key=lambda a: len(tracks_by_artist[a]), reverse=True)
            
            # Multiple passes to fill recommendations
            max_passes = max_results
            for pass_num in range(max_passes):
                added_this_pass = False
                
                for artist in sorted_artists:
                    # Check if artist has reached limit
                    if artist_counters[artist] >= max_per_artist:
                        continue
                    
                    # Get tracks for this artist
                    artist_tracks = tracks_by_artist[artist]
                    
                    # Get next track for this artist
                    track_index = artist_counters[artist]
                    if track_index >= len(artist_tracks):
                        continue
                    
                    track = artist_tracks[track_index]
                    
                    # STEP 2: Apply artist frequency penalty
                    # Adjust score based on how many times this artist already appears
                    penalty_factor = 1.0 / (1.0 + artist_counters[artist] * self.ARTIST_FREQUENCY_PENALTY)
                    adjusted_score = track['score'] * penalty_factor
                    
                    # Add track with enriched thumbnail
                    thumbnail = self._enrich_track_thumbnail(track['data'])
                    rec = {
                        'videoId': track['videoId'],
                        'title': track['data'].get('title', ''),
                        'artists': track['data'].get('artists', []),
                        'thumbnail': thumbnail,
                        'album': track['data'].get('album', ''),
                        'duration': track['data'].get('duration'),
                        'cf_score': round(adjusted_score, 3),
                        'original_score': round(track['score'], 3)
                    }
                    
                    recommendations.append(rec)
                    artist_counters[artist] += 1
                    added_this_pass = True
                    
                    # Stop if we reached max
                    if len(recommendations) >= max_results:
                        break
                
                # Break if we're done or no progress
                if len(recommendations) >= max_results or not added_this_pass:
                    break
            
            # STEP 5: Diversity safety check
            unique_artists = len(set(rec['artists'][0] if rec['artists'] else 'Unknown' for rec in recommendations))
            min_diversity = self.MIN_UNIQUE_ARTISTS_SMALL if self._total_user_count < self.USER_TIER_SMALL else self.MIN_UNIQUE_ARTISTS_NORMAL
            
            logger.info(f"[CF]   Unique artists: {unique_artists} (min target: {min_diversity})")
            logger.info(f"[CF]   Artist distribution: {dict(artist_counters)}")
            
            # Check diversity
            if unique_artists < min_diversity and len(recommendations) < max_results:
                logger.warning(f"[CF] ⚠ Low diversity: {unique_artists} < {min_diversity}, need more variety")
                
                # Try to add more artists (will be handled by expansion logic if needed)
                # This is a signal that we should expand with more variety
            
            return recommendations
            
        except Exception as e:
            logger.error(f"[CF] Error applying diversity ranking: {str(e)}", exc_info=True)
            # Fallback: return simple sorted list
            sorted_tracks = sorted(
                track_scores.items(),
                key=lambda x: (x[1]['score'], x[1]['play_count']),
                reverse=True
            )
            
            fallback_recs = []
            for video_id, track_info in sorted_tracks[:max_results]:
                thumbnail = self._enrich_track_thumbnail(track_info['data'])
                rec = {
                    'videoId': video_id,
                    'title': track_info['data'].get('title', ''),
                    'artists': track_info['data'].get('artists', []),
                    'thumbnail': thumbnail,
                    'album': track_info['data'].get('album', ''),
                    'duration': track_info['data'].get('duration'),
                    'cf_score': round(track_info['score'], 3)
                }
                fallback_recs.append(rec)
            
            return fallback_recs
    
    def _expand_with_artist_tracks(
        self,
        current_recommendations: List[Dict],
        artist_names: Set[str],
        exclude_video_ids: Set[str],
        target_count: int = 5
    ) -> List[Dict]:
        """
        Expand recommendations with tracks from shared artists.
        
        This is a fallback strategy to ensure minimum results while maintaining
        artist variety from similar users' top artists.
        
        Args:
            current_recommendations: Existing recommendations
            artist_names: Set of artist names from similar users
            exclude_video_ids: Video IDs to exclude (all user-known songs)
            target_count: Target number of recommendations
            
        Returns:
            Expanded list of recommendations
        """
        try:
            if len(current_recommendations) >= target_count or not artist_names:
                return current_recommendations
            
            if not self._firestore_db:
                return current_recommendations
            
            logger.info(f"[CF] Expanding with artist tracks from {len(artist_names)} artists")
            
            # Get all tracks from these artists across all users
            artist_tracks = defaultdict(lambda: {'data': None, 'play_count': 0})
            existing_video_ids = {rec.get('videoId') for rec in current_recommendations}
            
            # Search through all user plays for tracks from target artists
            users_ref = self._firestore_db.collection('users')
            
            for user_doc in users_ref.limit(50).stream():  # Increased from 20 for better coverage
                plays_ref = user_doc.reference.collection('plays')
                
                for play_doc in plays_ref.stream():
                    data = play_doc.to_dict()
                    video_id = data.get('videoId')
                    
                    # Skip if already excluded or in recommendations
                    if not video_id or video_id in exclude_video_ids or video_id in existing_video_ids:
                        continue
                    
                    # Check if this track is from target artists
                    track_artists = self._extract_artists_from_play(data)
                    
                    if any(artist in artist_names for artist in track_artists):
                        play_count = data.get('playCount', 1)
                        
                        if artist_tracks[video_id]['data'] is None:
                            artist_tracks[video_id]['data'] = data
                        
                        artist_tracks[video_id]['play_count'] += play_count
            
            logger.info(f"[CF]   Found {len(artist_tracks)} tracks from target artists")
            
            # Sort by play count and add to recommendations
            sorted_artist_tracks = sorted(
                artist_tracks.items(),
                key=lambda x: x[1]['play_count'],
                reverse=True
            )
            
            needed = target_count - len(current_recommendations)
            expanded = list(current_recommendations)
            
            for video_id, track_info in sorted_artist_tracks[:needed]:
                track_data = track_info['data']
                thumbnail = self._enrich_track_thumbnail(track_data)
                
                rec = {
                    'videoId': video_id,
                    'title': track_data.get('title', ''),
                    'artists': track_data.get('artists', []),
                    'thumbnail': thumbnail,
                    'album': track_data.get('album', ''),
                    'duration': track_data.get('duration'),
                    'cf_score': 0.1
                }
                
                expanded.append(rec)
            
            logger.info(f"[CF]   Expanded with {len(expanded) - len(current_recommendations)} artist expansion tracks")
            
            return expanded
            
        except Exception as e:
            logger.error(f"[CF] Error expanding with artist tracks: {str(e)}")
            return current_recommendations
    
    def _cold_start_fallback(self, uid: str, exclude_video_ids: Set[str]) -> List[Dict]:
        """
        Cold-start fallback: Return popular tracks from the database.
        Used when no similar users are found.
        
        Args:
            uid: Current user ID
            exclude_video_ids: Video IDs to exclude
            
        Returns:
            List of fallback recommendations
        """
        try:
            if not self._firestore_db:
                return []

            user_profile = self._build_user_taste_vector(uid)
            if not user_profile:
                return []

            similar_users = self._find_similar_users(uid, user_profile, threshold=0.08)
            if not similar_users:
                return []

            track_scores = defaultdict(lambda: {'data': None, 'play_count': 0, 'user_count': 0})
            for other_uid, _ in similar_users:
                plays_ref = self._firestore_db.collection('users').document(other_uid).collection('plays')
                for play_doc in plays_ref.stream():
                    data = play_doc.to_dict() or {}
                    video_id = data.get('videoId')
                    if not video_id or video_id in exclude_video_ids:
                        continue

                    play_count = data.get('playCount', 1)
                    try:
                        play_count = int(play_count)
                    except Exception:
                        play_count = 1

                    if track_scores[video_id]['data'] is None:
                        track_scores[video_id]['data'] = data
                    track_scores[video_id]['play_count'] += play_count
                    track_scores[video_id]['user_count'] += 1

            sorted_tracks = sorted(
                track_scores.items(),
                key=lambda x: (x[1]['user_count'], x[1]['play_count']),
                reverse=True
            )

            recommendations = []
            for video_id, track_info in sorted_tracks[:15]:
                recommendations.append(self._extract_song_payload(track_info['data'], 0.05))

            return recommendations
        except Exception as e:
            logger.error(f"[CF] Error in cold-start fallback: {str(e)}")
            return []
    
    def get_user_stats(self, uid: str) -> Dict:
        """
        Get collaborative filtering stats for a user (for debugging).
        
        Returns:
            Dict with CF statistics
        """
        try:
            user_profile = self._build_user_taste_vector(uid)
            profile_meta_entry = self.cache_user_profile_meta.get(f"profile_{uid}")
            profile_meta = profile_meta_entry[0] if profile_meta_entry else {}

            plays_count = int(profile_meta.get('plays_count', 0))
            artists_count = int(profile_meta.get('artist_count', len(user_profile)))
            albums_count = int(profile_meta.get('album_count', 0))
            has_profile = plays_count >= 5 or artists_count >= 2

            if not has_profile:
                return {
                    'has_profile': False,
                    'plays_count': plays_count,
                    'artist_count': artists_count,
                    'album_count': albums_count,
                    'message': 'Insufficient profile signals (needs plays>=5 OR artists>=2)'
                }

            similar_users = self._find_similar_users(uid, user_profile)

            return {
                'has_profile': True,
                'top_artists': list(user_profile.keys())[:5],
                'plays_count': plays_count,
                'artist_count': artists_count,
                'album_count': albums_count,
                'similar_users_count': len(similar_users),
                'similarity_scores': [round(sim, 2) for _, sim in similar_users[:5]]
            }
            
        except Exception as e:
            logger.error(f"[CF] Error getting user stats for {uid}: {str(e)}")
            return {
                'has_profile': False,
                'error': str(e)
            }
