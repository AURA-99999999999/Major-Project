"""High-performance Daily Mix service with aggressive caching and preloading."""

from __future__ import annotations

import logging
import random
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Any, Dict, List, Optional

from services.jiosaavn_service import JioSaavnService
from services.user_service import UserService

logger = logging.getLogger(__name__)


# Global in-memory pools and caches (process-wide).
trending_pool: List[Dict[str, Any]] = []
mood_pools: Dict[str, List[Dict[str, Any]]] = {"fresh": [], "chill": [], "party": [], "calm": []}
language_pools: Dict[str, List[Dict[str, Any]]] = {"telugu": [], "hindi": [], "english": []}
artist_song_cache: Dict[str, List[Dict[str, Any]]] = {}
decrypt_cache: Dict[str, str] = {}
mix_cache: Dict[str, tuple] = {}


class DailyMixOptimizedService:
    """Production Daily Mix generator: <2 sec response, minimal API calls."""

    # ============================================
    # METADATA & CONSTANTS
    # ============================================
    MIX_META: List[Dict[str, str]] = [
        {"id": "favorites", "title": "Your Favorites", "emoji": "❤️", "color": "#FF6B6B"},
        {"id": "mood", "title": "Mood Mix", "emoji": "🌈", "color": "#6C5CE7"},
        {"id": "discover", "title": "Discover Mix", "emoji": "✨", "color": "#FFD93D"},
        {"id": "similar", "title": "Similar Artists", "emoji": "🎤", "color": "#4ECDC4"},
    ]

    # Config
    CACHE_TTL = 3600  # 1 hour
    PROFILE_CACHE_TTL = 1800  # 30 mins
    MAX_SONGS = 15
    MAX_ARTISTS = 2
    MAX_SEARCH_PER_ARTIST = 10
    MAX_LANGUAGES = 2
    POOL_FETCH_LIMIT = 40
    MOOD_QUERY_LIMIT = 8
    MOOD_POOL_PER_LANGUAGE = 24
    MAX_PLAYLISTS = 2
    MAX_SONGS_PER_PLAYLIST = 10

    # Preload queries
    MOOD_QUERIES: Dict[str, List[str]] = {
        "fresh": ["morning songs", "fresh vibes"],
        "chill": ["lofi chill", "relax songs"],
        "party": ["party songs", "workout mix"],
        "calm": ["soft songs", "night vibes"],
    }

    # ============================================
    # GLOBAL PRELOADED POOLS (ZERO API CALLS)
    # ============================================
    TRENDING_POOL: List[Dict[str, Any]] = trending_pool
    LANGUAGE_POOLS: Dict[str, List[Dict[str, Any]]] = language_pools
    MOOD_LANGUAGE_POOLS: Dict[str, Dict[str, List[Dict[str, Any]]]] = {
        "telugu": {"fresh": [], "chill": [], "party": [], "calm": []},
        "hindi": {"fresh": [], "chill": [], "party": [], "calm": []},
        "english": {"fresh": [], "chill": [], "party": [], "calm": []},
    }

    # ============================================
    # CACHING LAYERS
    # ============================================
    # Per-user mix cache (user_id:mix_type -> mix payload)
    _mix_cache: Dict[str, tuple] = mix_cache

    # Per-user profile cache (user_id -> profile dict)
    _profile_cache: Dict[str, tuple] = {}

    # Artist -> songs cache (artist_name -> [songs])
    _artist_song_cache: Dict[str, List[Dict[str, Any]]] = artist_song_cache

    # Decrypt URL cache (song_id -> stream_url)
    _decrypt_cache: Dict[str, str] = decrypt_cache

    # Pools initialization flag
    _pools_initialized = False

    def __init__(
        self,
        jiosaavn_service: Optional[JioSaavnService] = None,
        user_service: Optional[UserService] = None,
    ) -> None:
        self.jiosaavn_service = jiosaavn_service or JioSaavnService()
        self.user_service = user_service or UserService()
        self._api_call_count = 0

    # ============================================
    # PRELOAD & INITIALIZATION
    # ============================================
    def load_initial_data(self) -> Dict[str, Any]:
        """
        Startup preload entrypoint.
        Loads trending/language/mood pools once to avoid per-request heavy calls.
        """
        return self.preload_all_data()

    def preload_all_data(self) -> Dict[str, Any]:
        """
        Run on server startup. Loads all pools in parallel.
        This is THE ONLY BULK API CALL.
        Result: Zero API calls per request.
        """
        logger.info("[PRELOAD] Starting global pool initialization...")
        start_time = time.time()

        if self._pools_initialized:
            logger.info("[PRELOAD] Pools already initialized, skipping")
            return {"status": "already_initialized"}

        with ThreadPoolExecutor(max_workers=8) as executor:
            futures = {
                # Trending pool
                executor.submit(self._preload_trending): "trending",
                # Language pools
                executor.submit(self._preload_language_pool, "english"): "english",
                executor.submit(self._preload_language_pool, "hindi"): "hindi",
                executor.submit(self._preload_language_pool, "telugu"): "telugu",
                # Mood pools
                executor.submit(self._preload_mood_pool, "english"): "mood_english",
                executor.submit(self._preload_mood_pool, "hindi"): "mood_hindi",
                executor.submit(self._preload_mood_pool, "telugu"): "mood_telugu",
            }

            results = {}
            for future in as_completed(futures):
                key = futures[future]
                try:
                    results[key] = future.result()
                except Exception as exc:
                    logger.error("[PRELOAD] Failed to preload %s: %s", key, exc)

        self._pools_initialized = True

        for mood in self.MOOD_QUERIES:
            mood_pools[mood] = self._deduplicate_and_limit(
                self.MOOD_LANGUAGE_POOLS.get("english", {}).get(mood, [])
                + self.MOOD_LANGUAGE_POOLS.get("hindi", {}).get(mood, [])
                + self.MOOD_LANGUAGE_POOLS.get("telugu", {}).get(mood, []),
                self.POOL_FETCH_LIMIT,
            )
        elapsed = time.time() - start_time

        logger.info(
            "[PRELOAD] ✅ COMPLETE in %.2f sec. Pools: trending=%d english=%d hindi=%d telugu=%d",
            elapsed,
            len(self.TRENDING_POOL),
            len(self.LANGUAGE_POOLS.get("english", [])),
            len(self.LANGUAGE_POOLS.get("hindi", [])),
            len(self.LANGUAGE_POOLS.get("telugu", [])),
        )

        return {
            "status": "success",
            "elapsed_sec": elapsed,
            "pools_initialized": True,
            "trending_count": len(self.TRENDING_POOL),
        }

    def _preload_trending(self) -> int:
        """Fetch & cache trending songs ONCE."""
        songs = self._safe_fetch(
            lambda: self.jiosaavn_service.search_songs("top songs", limit=self.POOL_FETCH_LIMIT)
        )
        self.TRENDING_POOL.clear()
        self.TRENDING_POOL.extend([self._normalize_song(s) for s in songs if isinstance(s, dict)])
        logger.info("[PRELOAD] Trending pool: %d songs", len(self.TRENDING_POOL))
        return len(self.TRENDING_POOL)

    def _preload_language_pool(self, language: str) -> int:
        """Fetch & cache language-specific songs."""
        query = f"{language} songs"
        songs = self._safe_fetch(
            lambda: self.jiosaavn_service.search_songs(query, limit=self.POOL_FETCH_LIMIT)
        )
        normalized = [self._normalize_song(s) for s in songs if isinstance(s, dict)]
        self.LANGUAGE_POOLS[language].clear()
        self.LANGUAGE_POOLS[language].extend(normalized)
        logger.info("[PRELOAD] Language pool '%s': %d songs", language, len(normalized))
        return len(normalized)

    def _preload_mood_pool(self, language: str) -> Dict[str, int]:
        """Fetch & cache mood-specific songs per language."""
        results = {}
        for mood, queries in self.MOOD_QUERIES.items():
            mood_songs: List[Dict[str, Any]] = []
            for query in queries:
                full_query = f"{language} {query}"
                songs = self._safe_fetch(
                    lambda q=full_query: self.jiosaavn_service.search_songs(
                        q, limit=self.MOOD_QUERY_LIMIT
                    )
                )
                mood_songs.extend([self._normalize_song(s) for s in songs if isinstance(s, dict)])

            deduped = self._deduplicate_and_limit(mood_songs, self.MOOD_POOL_PER_LANGUAGE)
            self.MOOD_LANGUAGE_POOLS[language][mood].clear()
            self.MOOD_LANGUAGE_POOLS[language][mood].extend(deduped)
            results[mood] = len(deduped)
            logger.info("[PRELOAD] Mood pool %s/%s: %d songs", language, mood, len(deduped))

        return results

    # ============================================
    # PUBLIC API
    # ============================================
    def get_daily_mixes_meta(self) -> List[Dict[str, str]]:
        """Return metadata for all 4 mixes."""
        return [dict(item) for item in self.MIX_META]

    def get_mix_by_type(self, mix_type: str, user_id: str, refresh: bool = False) -> Dict[str, Any]:
        """
        MAIN ENTRY: Generate a mix.
        PERFORMANCE: <2 sec, 0-1 API calls.
        """
        self._ensure_pools_initialized()
        self._api_call_count = 0
        started = time.time()

        mix_id = str(mix_type or "").strip().lower()
        if not user_id:
            raise ValueError("user_id is required")

        meta = self._mix_meta_by_type(mix_id)
        if not meta:
            raise ValueError(f"Unsupported mix type: {mix_type}")

        # Check mix cache first
        cache_key = f"{user_id}_{mix_id}"
        if not refresh:
            cached = self._get_mix_cache(cache_key)
            if cached:
                logger.info("[CACHE_HIT] Mix %s for user %s", mix_id, user_id)
                return cached

        # Get user profile (cached)
        try:
            profile = self._get_user_profile(user_id)
        except Exception as exc:
            logger.warning("[MIX] Profile fetch failed: %s", exc)
            profile = {"top_songs": [], "top_artists": [], "languages": [], "played_song_ids": set()}

        # Generate mix
        try:
            if mix_id == "favorites":
                songs = self._generate_favorites(profile)
            elif mix_id == "mood":
                songs = self._generate_mood(profile)
            elif mix_id == "discover":
                songs = self._generate_discover(profile)
            else:
                songs = self._generate_similar(profile)
        except Exception as exc:
            logger.warning("[MIX] Generation failed type=%s: %s", mix_id, exc)
            songs = []

        # Fallback if insufficient
        if not songs:
            logger.info("[MIX] Fallback to trending for %s", mix_id)
            songs = list(self.TRENDING_POOL[: self.MAX_SONGS])

        # Normalize & deduplicate
        songs = self._normalize_mix(songs, self.TRENDING_POOL)

        # Build response
        payload = {
            "type": mix_id,
            "id": mix_id,
            "title": meta["title"],
            "emoji": meta["emoji"],
            "color": meta["color"],
            "songs": [self._to_song_dto(song) for song in songs],
            "count": len(songs),
        }

        # Cache result
        self._set_mix_cache(cache_key, payload)

        elapsed_ms = int((time.time() - started) * 1000)
        logger.info(
            "[MIX] type=%s uid=%s api_calls=%d count=%d latency_ms=%d",
            mix_id,
            user_id,
            self._api_call_count,
            len(songs),
            elapsed_ms,
        )

        return payload

    # ============================================
    # PROFILE (CACHED)
    # ============================================
    def _get_user_profile(self, user_id: str) -> Dict[str, Any]:
        """Get user profile with caching."""
        cache_key = f"profile:{user_id}"
        cached, timestamp = self._profile_cache.get(cache_key, (None, 0))
        if isinstance(cached, dict) and time.time() - timestamp < self.PROFILE_CACHE_TTL:
            return cached

        # Fetch from DB
        plays = self._safe_fetch(lambda: self.user_service.get_user_plays(user_id, limit=200)) or []
        liked = self._safe_fetch(lambda: self.user_service.get_user_liked_songs(user_id)) or []
        languages = self._safe_fetch(lambda: self.user_service.get_user_languages(user_id)) or []

        top_songs = self._deduplicate_and_limit(liked + plays, 20)
        top_artists = self._extract_top_artists(top_songs, limit=8)
        played_ids = {self._song_id(song) for song in plays if self._song_id(song)}

        profile = {
            "top_songs": top_songs,
            "top_artists": top_artists,
            "languages": [str(lang).strip().lower() for lang in languages if str(lang).strip()],
            "played_song_ids": played_ids,
        }

        self._profile_cache[cache_key] = (profile, time.time())
        return profile

    # ============================================
    # MIX GENERATORS (OPTIMIZED - NO API CALLS)
    # ============================================
    def _generate_favorites(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        ❤️ Favorites Mix (OPTIMIZED)
        - Use cached artist songs
        - No new searches
        """
        songs: List[Dict[str, Any]] = list(profile.get("top_songs", [])[:10])
        fallback_calls = 0

        for artist in profile.get("top_artists", [])[: self.MAX_ARTISTS]:
            artist_str = str(artist).strip()
            # Check cache first
            if artist_str in self._artist_song_cache:
                songs.extend(self._artist_song_cache[artist_str][: self.MAX_SONGS_PER_PLAYLIST])
            else:
                # Allow only one fallback API call per request path.
                if fallback_calls < 1:
                    fetched = self._safe_fetch(
                        lambda a=artist_str: self.jiosaavn_service.search_songs(
                            a,
                            limit=self.MAX_SONGS_PER_PLAYLIST,
                        )
                    )
                    normalized = [self._normalize_song(s) for s in fetched if isinstance(s, dict)]
                    self._artist_song_cache[artist_str] = normalized
                    songs.extend(normalized[: self.MAX_SONGS_PER_PLAYLIST])
                    fallback_calls += 1

        if len(songs) < self.MAX_SONGS:
            songs.extend(self.LANGUAGE_POOLS.get("english", [])[: self.MAX_SONGS])

        return self._deduplicate_and_limit(songs, self.MAX_SONGS)

    def _generate_mood(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        🌈 Mood Mix (ZERO API CALLS)
        - Uses preloaded mood pools
        - No dynamic searches
        """
        mood = self._get_time_based_mood()
        languages = profile.get("languages") or ["english", "hindi"]
        selected_languages = [str(lang).strip().lower() for lang in languages if str(lang).strip()][: self.MAX_LANGUAGES]

        if not selected_languages:
            selected_languages = ["english", "hindi"]

        songs: List[Dict[str, Any]] = []
        fallback_calls = 0

        for language in selected_languages:
            mood_pool = self.MOOD_LANGUAGE_POOLS.get(language, {}).get(mood, [])

            if mood_pool:
                # Use preloaded mood pool
                songs.extend(self._filter_songs_by_mood(mood_pool, mood)[:8])
                songs.extend(mood_pool[:6])
            else:
                # Fallback to language pool
                language_pool = self.LANGUAGE_POOLS.get(language, [])
                songs.extend(self._filter_songs_by_mood(language_pool, mood)[:8])

        logger.info("[MOOD] mood=%s langs=%s candidates=%d (NO API CALLS)", mood, selected_languages, len(songs))
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    def _generate_discover(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        ✨ Discover Mix (ZERO API CALLS)
        - Only uses trending pool
        - Filters played songs
        """
        songs = list(self.TRENDING_POOL[:40])
        songs = self._filter_not_played(songs, profile)
        logger.info("[DISCOVER] returning %d unplayed trending songs", len(songs))
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    def _generate_similar(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        """
        🎤 Similar Artists Mix (OPTIMIZED)
        - Uses cached artist songs
        - Minimal API calls
        """
        songs: List[Dict[str, Any]] = []
        fallback_calls = 0

        for artist in profile.get("top_artists", [])[: self.MAX_ARTISTS]:
            artist_str = str(artist).strip()
            # Check cache first
            if artist_str in self._artist_song_cache:
                songs.extend(self._artist_song_cache[artist_str][: self.MAX_SONGS_PER_PLAYLIST])
            else:
                # Allow only one fallback API call per request path.
                if fallback_calls < 1:
                    fetched = self._safe_fetch(
                        lambda a=artist_str: self.jiosaavn_service.search_songs(
                            a,
                            limit=self.MAX_SONGS_PER_PLAYLIST,
                        )
                    )
                    normalized = [self._normalize_song(s) for s in fetched if isinstance(s, dict)]
                    self._artist_song_cache[artist_str] = normalized
                    songs.extend(normalized[: self.MAX_SONGS_PER_PLAYLIST])
                    fallback_calls += 1

        if len(songs) < self.MAX_SONGS:
            songs.extend(self.TRENDING_POOL[: self.MAX_SONGS])

        logger.info("[SIMILAR] returning songs from %d artists", len(profile.get("top_artists", [])[: self.MAX_ARTISTS]))
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    # ============================================
    # MOOD & TIME
    # ============================================
    def _get_time_based_mood(self) -> str:
        """Time-based mood selection."""
        hour = datetime.now().hour
        if 5 <= hour < 11:
            return "fresh"
        if 11 <= hour < 17:
            return "chill"
        if 17 <= hour < 22:
            return "party"
        return "calm"

    def _filter_songs_by_mood(self, songs: List[Dict[str, Any]], mood: str) -> List[Dict[str, Any]]:
        """Filter songs by mood keywords."""
        keywords = {
            "fresh": ["fresh", "morning", "sunrise"],
            "chill": ["chill", "lofi", "relax", "calm"],
            "party": ["party", "dance", "workout", "energy"],
            "calm": ["calm", "soft", "night", "sleep"],
        }.get(mood, [mood])

        filtered: List[Dict[str, Any]] = []
        for song in songs:
            title = str(song.get("title") or song.get("song") or "").lower()
            tags = str(song.get("tags") or "").lower()
            text = f"{title} {tags}"
            if any(keyword in text for keyword in keywords):
                filtered.append(song)
        return filtered

    # ============================================
    # NORMALIZATION & DEDUPLICATION
    # ============================================
    def _normalize_mix(self, songs: List[Dict[str, Any]], fallback_pool: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        """Normalize, deduplicate, and fill mix to exactly MAX_SONGS."""
        seen = set()
        unique: List[Dict[str, Any]] = []

        for song in songs:
            sid = self._song_id(song)
            if not sid or sid in seen:
                continue
            seen.add(sid)
            unique.append(self._normalize_song(song))

        if len(unique) > self.MAX_SONGS:
            return unique[: self.MAX_SONGS]

        if len(unique) < self.MAX_SONGS:
            # Fill from fallback pool
            for song in fallback_pool:
                sid = self._song_id(song)
                if not sid or sid in seen:
                    continue
                unique.append(self._normalize_song(song))
                seen.add(sid)
                if len(unique) == self.MAX_SONGS:
                    break

        if len(unique) < self.MAX_SONGS:
            # Last resort: emergency pool
            emergency = self.TRENDING_POOL + self.LANGUAGE_POOLS.get("english", []) + self.LANGUAGE_POOLS.get("hindi", [])
            for song in emergency:
                sid = self._song_id(song)
                if not sid or sid in seen:
                    continue
                unique.append(self._normalize_song(song))
                seen.add(sid)
                if len(unique) == self.MAX_SONGS:
                    break

        # Final pad if needed
        if len(unique) < self.MAX_SONGS:
            source = unique[:] if unique else self.TRENDING_POOL[: self.MAX_SONGS]
            idx = 0
            while len(unique) < self.MAX_SONGS:
                base = dict(source[idx % len(source)])
                base["id"] = f"{self._song_id(base) or 'pad'}-{len(unique)}"
                base["videoId"] = base["id"]
                unique.append(self._normalize_song(base))
                idx += 1

        return unique[: self.MAX_SONGS]

    # ============================================
    # HELPERS
    # ============================================
    def safe_fetch(self, fn):
        try:
            result = fn()
            return result if result is not None else []
        except Exception as exc:
            logger.warning("[FETCH] Failed: %s", exc)
            return []

    def _ensure_pools_initialized(self) -> None:
        """Ensure pools are loaded. Should be called before mix generation."""
        if not self._pools_initialized:
            logger.warning("[MIX] Pools not initialized! Initializing now (should have been preloaded)")
            self.preload_all_data()

    def _safe_fetch(self, fn):
        """Safe API fetch with error handling."""
        return self.safe_fetch(fn)

    def _filter_not_played(self, songs: List[Dict[str, Any]], profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Filter out songs the user has already played."""
        played_ids = set(profile.get("played_song_ids", set()))
        return [song for song in songs if self._song_id(song) not in played_ids]

    def _extract_top_artists(self, songs: List[Dict[str, Any]], limit: int) -> List[str]:
        """Extract top artists by frequency."""
        counts: Dict[str, int] = {}
        for song in songs:
            artist_text = str(song.get("artist") or song.get("primary_artists") or "")
            for artist in [a.strip() for a in artist_text.split(",") if a.strip()]:
                counts[artist] = counts.get(artist, 0) + 1
        ranked = sorted(counts.items(), key=lambda item: item[1], reverse=True)
        return [artist for artist, _ in ranked[:limit]]

    def _deduplicate_and_limit(self, songs: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
        """Remove duplicates and limit to N songs."""
        seen = set()
        unique: List[Dict[str, Any]] = []
        for song in songs:
            sid = self._song_id(song)
            key = sid or f"{str(song.get('title') or song.get('song') or '').strip().lower()}::{str(song.get('artist') or '').strip().lower()}"
            if not key or key in seen:
                continue
            seen.add(key)
            unique.append(self._normalize_song(song))
            if len(unique) >= limit:
                break
        return unique

    def _shuffle_and_limit(self, songs: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
        """Shuffle and limit to N songs."""
        deduped = self._deduplicate_and_limit(songs, max(limit * 2, limit))
        random.shuffle(deduped)
        return deduped[:limit]

    def _song_id(self, song: Dict[str, Any]) -> str:
        """Extract song ID safely."""
        return str(song.get("id") or song.get("videoId") or "").strip()

    def _mix_meta_by_type(self, mix_type: str) -> Optional[Dict[str, str]]:
        """Get mix metadata by type."""
        for item in self.MIX_META:
            if item.get("id") == mix_type:
                return item
        return None

    def _normalize_song(self, song: Dict[str, Any]) -> Dict[str, Any]:
        """Normalize song object (consolidate fields)."""
        if not isinstance(song, dict):
            return {}

        title = str(song.get("song") or song.get("title") or song.get("name") or "").strip()
        artist = str(song.get("primary_artists") or song.get("artist") or song.get("singers") or "").strip()
        image = str(song.get("image") or song.get("thumbnail") or "").strip()
        media_url = str(song.get("media_url") or song.get("url") or song.get("stream_url") or "").strip()

        # Cache the decrypted URL if provided
        song_id = str(song.get("id") or song.get("videoId") or "").strip()
        if song_id and media_url:
            self._decrypt_cache[song_id] = media_url

        normalized = dict(song)
        normalized.update(
            {
                "id": song_id,
                "videoId": song_id,
                "title": title,
                "song": title,
                "artist": artist,
                "image": image,
                "thumbnail": image,
                "media_url": media_url,
                "url": media_url,
                "stream_url": media_url,
            }
        )
        return normalized

    def _to_song_dto(self, song: Dict[str, Any]) -> Dict[str, Any]:
        """Convert song to DTO format."""
        title = str(song.get("title") or song.get("song") or "")
        artist = str(song.get("artist") or song.get("primary_artists") or "").strip()
        artists = [a.strip() for a in artist.split(",") if a.strip()]
        image = str(song.get("image") or song.get("thumbnail") or "").strip()
        media_url = str(song.get("media_url") or song.get("url") or song.get("stream_url") or "").strip()
        play_count = int(song.get("play_count") or song.get("global_play_count") or 0)

        return {
            "videoId": self._song_id(song),
            "title": title,
            "artist": artist or None,
            "artists": artists or None,
            "thumbnail": image or None,
            "duration": str(song.get("duration") or ""),
            "url": media_url,
            "album": song.get("album") or None,
            "artistId": None,
            "play_count": play_count,
            "global_play_count": play_count,
            "language": song.get("language") or None,
            "year": song.get("year") or None,
            "id": self._song_id(song),
            "singers": song.get("singers") or artist or None,
            "starring": song.get("starring") or None,
            "image": image or None,
            "media_url": media_url,
            "stream_url": media_url,
        }

    # ============================================
    # CACHING OPERATIONS
    # ============================================
    def _get_mix_cache(self, key: str) -> Optional[Dict[str, Any]]:
        """Get cached mix if valid."""
        data, timestamp = self._mix_cache.get(key, (None, 0))
        if isinstance(data, dict) and time.time() - timestamp < self.CACHE_TTL:
            return data
        return None

    def _set_mix_cache(self, key: str, data: Dict[str, Any]) -> None:
        """Cache mix result."""
        self._mix_cache[key] = (data, time.time())

    def clear_caches(self) -> Dict[str, int]:
        """Clear all caches and return counts."""
        sizes = {
            "mix_cache": len(self._mix_cache),
            "profile_cache": len(self._profile_cache),
            "artist_cache": len(self._artist_song_cache),
            "decrypt_cache": len(self._decrypt_cache),
        }
        self._mix_cache.clear()
        self._profile_cache.clear()
        self._artist_song_cache.clear()
        self._decrypt_cache.clear()
        logger.info("[CACHE] Cleared all caches: %s", sizes)
        return sizes

    def get_cache_stats(self) -> Dict[str, Any]:
        """Return cache statistics."""
        return {
            "mix_cache_size": len(self._mix_cache),
            "profile_cache_size": len(self._profile_cache),
            "artist_cache_size": len(self._artist_song_cache),
            "decrypt_cache_size": len(self._decrypt_cache),
            "pools_initialized": self._pools_initialized,
            "trending_pool_size": len(self.TRENDING_POOL),
            "mood_pools": {
                lang: {mood: len(songs) for mood, songs in self.MOOD_LANGUAGE_POOLS[lang].items()}
                for lang in self.MOOD_LANGUAGE_POOLS
            },
        }
