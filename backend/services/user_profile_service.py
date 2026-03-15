"""
User Profile Service - Pure Firestore data processing
Calculates user preferences from play history and liked songs
NO EXTERNAL API CALLS - Internal data only
"""
from typing import List, Dict, Optional
import logging
import time
import math
import re
from datetime import datetime, timezone
from collections import Counter
from services.user_service import get_firestore_client
from services.cache_manager import get_cache

logger = logging.getLogger(__name__)


class UserProfileService:
    """Service for building user taste profiles from Firestore data only."""
    
    # Scoring weights
    LIKED_SONG_WEIGHT = 2.0  # Liked songs count twice
    PLAY_WEIGHT = 1.0
    
    # Time decay parameters (30 days half-life)
    TIME_DECAY_DAYS = 30

    # Bump when profile-shaping logic changes to avoid stale cached responses.
    PROFILE_CACHE_VERSION = "v2"

    # Canonical artist aliases (after punctuation cleanup + token sorting)
    ARTIST_ALIASES: Dict[str, str] = {
        "ss thaman": "s thaman",
        "s s thaman": "s thaman",
        "thaman s": "s thaman",
    }
    
    def __init__(self):
        self.cache = get_cache()
        logger.info("UserProfileService initialized (Firestore-only)")
    
    def get_user_profile(self, uid: str) -> Dict:
        """
        Build comprehensive user profile from Firestore data.
        
        Args:
            uid: User ID
            
        Returns:
            User profile with top artists, albums, genres, and stats
        """
        cache_key = f"user_profile:{self.PROFILE_CACHE_VERSION}:{uid}"
        cached = self.cache.get(cache_key)
        if cached:
            return cached
        
        logger.info(f"Building user profile for uid: {uid}")
        start_time = time.perf_counter()
        
        # Fetch user data from Firestore
        plays = self._fetch_user_plays(uid)
        liked_songs = self._fetch_liked_songs(uid)
        
        # Calculate top artists with weighted scoring
        top_artists = self._calculate_top_artists(plays, liked_songs)
        
        # Calculate top albums
        top_albums = self._calculate_top_albums(plays, liked_songs)
        
        # Calculate top genres/languages
        top_genres = self._calculate_top_genres(plays, liked_songs)
        
        # Calculate starring actors
        top_starring = self._calculate_top_starring(plays, liked_songs)
        
        profile = {
            'uid': uid,
            'top_artists': top_artists[:20],  # Top 20 artists
            'top_albums': top_albums[:10],    # Top 10 albums
            'top_genres': top_genres[:5],     # Top 5 genres
            'top_starring': top_starring[:10],  # Top 10 actors
            'total_plays': len(plays),
            'total_likes': len(liked_songs),
            'generated_at': time.time()
        }
        
        elapsed_ms = (time.perf_counter() - start_time) * 1000
        logger.info(
            f"User profile built in {elapsed_ms:.2f}ms | "
            f"artists={len(top_artists)} | albums={len(top_albums)} | "
            f"plays={len(plays)} | likes={len(liked_songs)}"
        )
        
        # Cache for 10 minutes
        self.cache.set(cache_key, profile, ttl=600)
        return profile
    
    def get_top_artists(self, uid: str, limit: int = 10) -> List[Dict]:
        """
        Get user's top artists only.
        
        Args:
            uid: User ID
            limit: Number of artists to return
            
        Returns:
            List of artist dictionaries with name and score
        """
        profile = self.get_user_profile(uid)
        return profile['top_artists'][:limit]
    
    def _fetch_user_plays(self, uid: str) -> List[Dict]:
        """Fetch play history from Firestore."""
        try:
            db = get_firestore_client()
            if not db:
                return []
            
            plays_ref = db.collection('users').document(uid).collection('plays')
            plays_docs = plays_ref.stream()
            
            plays = []
            for doc in plays_docs:
                data = doc.to_dict()
                if data:
                    plays.append(data)
            
            logger.debug(f"Fetched {len(plays)} plays for user {uid}")
            return plays
        except Exception as e:
            logger.error(f"Error fetching plays for {uid}: {e}")
            return []
    
    def _fetch_liked_songs(self, uid: str) -> List[Dict]:
        """Fetch liked songs from Firestore."""
        try:
            db = get_firestore_client()
            if not db:
                return []
            
            liked_ref = db.collection('users').document(uid).collection('likedSongs')
            liked_docs = liked_ref.stream()
            
            liked = []
            for doc in liked_docs:
                data = doc.to_dict()
                if data:
                    liked.append(data)
            
            logger.debug(f"Fetched {len(liked)} liked songs for user {uid}")
            return liked
        except Exception as e:
            logger.error(f"Error fetching liked songs for {uid}: {e}")
            return []
    
    def _calculate_top_artists(
        self, 
        plays: List[Dict], 
        liked_songs: List[Dict]
    ) -> List[Dict]:
        """Calculate top artists with weighted scoring and time decay."""
        artist_scores: Dict[str, float] = {}
        artist_play_count: Dict[str, int] = {}
        
        current_time = time.time()
        
        # Process plays
        for play in plays:
            artists = self._extract_artists(play)
            timestamp = play.get('timestamp') or play.get('playedAt') or current_time
            
            # Convert Firestore timestamp if needed
            if hasattr(timestamp, 'timestamp'):
                timestamp = timestamp.timestamp()
            
            decay_factor = self._time_decay(timestamp, current_time)
            
            for artist in artists:
                artist_norm = self._normalize_artist_name(artist)
                if artist_norm:
                    score = self.PLAY_WEIGHT * decay_factor
                    artist_scores[artist_norm] = artist_scores.get(artist_norm, 0) + score
                    artist_play_count[artist_norm] = artist_play_count.get(artist_norm, 0) + 1
        
        # Process liked songs (higher weight)
        for liked in liked_songs:
            artists = self._extract_artists(liked)
            timestamp = liked.get('timestamp') or liked.get('likedAt') or current_time
            
            if hasattr(timestamp, 'timestamp'):
                timestamp = timestamp.timestamp()
            
            decay_factor = self._time_decay(timestamp, current_time)
            
            for artist in artists:
                artist_norm = self._normalize_artist_name(artist)
                if artist_norm:
                    score = self.LIKED_SONG_WEIGHT * decay_factor
                    artist_scores[artist_norm] = artist_scores.get(artist_norm, 0) + score
                    artist_play_count[artist_norm] = artist_play_count.get(artist_norm, 0) + 1
        
        # Sort by score
        sorted_artists = sorted(
            artist_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        return [
            {
                'artist': artist,
                'score': round(score, 2),
                'play_count': artist_play_count.get(artist, 0)
            }
            for artist, score in sorted_artists
        ]
    
    def _calculate_top_albums(
        self,
        plays: List[Dict],
        liked_songs: List[Dict]
    ) -> List[Dict]:
        """Calculate top albums with weighted scoring."""
        album_scores: Dict[str, float] = {}
        
        for play in plays:
            album = self._extract_album(play)
            if album:
                album_norm = self._normalize_text(album)
                album_scores[album_norm] = album_scores.get(album_norm, 0) + self.PLAY_WEIGHT
        
        for liked in liked_songs:
            album = self._extract_album(liked)
            if album:
                album_norm = self._normalize_text(album)
                album_scores[album_norm] = album_scores.get(album_norm, 0) + self.LIKED_SONG_WEIGHT
        
        sorted_albums = sorted(
            album_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        return [
            {'album': album, 'score': round(score, 2)}
            for album, score in sorted_albums
        ]
    
    def _calculate_top_genres(
        self,
        plays: List[Dict],
        liked_songs: List[Dict]
    ) -> List[Dict]:
        """Calculate top genres/languages."""
        genre_scores: Dict[str, float] = {}
        
        for play in plays:
            genre = self._extract_genre(play)
            if genre:
                genre_norm = genre.lower()
                genre_scores[genre_norm] = genre_scores.get(genre_norm, 0) + self.PLAY_WEIGHT
        
        for liked in liked_songs:
            genre = self._extract_genre(liked)
            if genre:
                genre_norm = genre.lower()
                genre_scores[genre_norm] = genre_scores.get(genre_norm, 0) + self.LIKED_SONG_WEIGHT
        
        sorted_genres = sorted(
            genre_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        return [
            {'genre': genre, 'score': round(score, 2)}
            for genre, score in sorted_genres
        ]
    
    def _calculate_top_starring(
        self,
        plays: List[Dict],
        liked_songs: List[Dict]
    ) -> List[Dict]:
        """Calculate top starring actors."""
        starring_scores: Dict[str, float] = {}
        
        for play in plays:
            actors = self._extract_starring(play)
            for actor in actors:
                actor_norm = self._normalize_text(actor)
                if actor_norm:
                    starring_scores[actor_norm] = starring_scores.get(actor_norm, 0) + self.PLAY_WEIGHT
        
        for liked in liked_songs:
            actors = self._extract_starring(liked)
            for actor in actors:
                actor_norm = self._normalize_text(actor)
                if actor_norm:
                    starring_scores[actor_norm] = starring_scores.get(actor_norm, 0) + self.LIKED_SONG_WEIGHT
        
        sorted_starring = sorted(
            starring_scores.items(),
            key=lambda x: x[1],
            reverse=True
        )
        
        return [
            {'actor': actor, 'score': round(score, 2)}
            for actor, score in sorted_starring
        ]
    
    def _time_decay(self, timestamp: float, current_time: float) -> float:
        """Calculate time decay factor (exponential decay)."""
        age_days = (current_time - timestamp) / 86400  # Convert to days
        half_life = self.TIME_DECAY_DAYS
        decay = math.exp(-0.693 * age_days / half_life)
        return max(0.1, min(1.0, decay))  # Clamp between 0.1 and 1.0
    
    def _extract_artists(self, song: Dict) -> List[str]:
        """Extract artist names from song data."""
        artists = []
        
        # Try different fields (check 'artists' plural first, then fallbacks)
        artist_data = song.get('artists') or song.get('primary_artists') or song.get('artist')
        
        if artist_data:
            if isinstance(artist_data, str):
                # String: split by comma
                artists.extend([a.strip() for a in artist_data.split(',') if a.strip()])
            elif isinstance(artist_data, list):
                # List: could be list of strings OR list of dicts with 'name' field
                for item in artist_data:
                    if isinstance(item, dict):
                        # Extract 'name' field from dict
                        name = item.get('name', '')
                        if name:
                            artists.append(str(name).strip())
                    elif item:
                        # Direct string value
                        artists.append(str(item).strip())
        
        return [a for a in artists if a]
    
    def _extract_album(self, song: Dict) -> Optional[str]:
        """Extract album name from song data."""
        album = song.get('album')
        if isinstance(album, dict):
            return str(album.get('name', '')).strip()
        return str(album or '').strip()
    
    def _extract_genre(self, song: Dict) -> Optional[str]:
        """Extract genre/language from song data."""
        return song.get('language') or song.get('genre') or ''
    
    def _extract_starring(self, song: Dict) -> List[str]:
        """Extract starring actors from song data."""
        starring = song.get('starring', '')
        if isinstance(starring, str) and starring.strip():
            return [a.strip() for a in starring.split(',') if a.strip()]
        return []
    
    def _normalize_text(self, text: str) -> str:
        """Normalize text for consistent comparison."""
        return (text or '').strip().lower()

    def _normalize_artist_name(self, name: str) -> str:
        """Canonicalize artist names so common variants map to one identity."""
        if not name:
            return ''

        raw = name.strip().lower()
        cleaned = re.sub(r"[\.,\-_]", " ", raw)
        cleaned = re.sub(r"\s+", " ", cleaned).strip()
        if not cleaned:
            return ''

        tokens = cleaned.split()
        normalized = " ".join(sorted(tokens))
        return self.ARTIST_ALIASES.get(normalized, normalized)
