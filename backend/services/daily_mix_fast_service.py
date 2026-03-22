from __future__ import annotations

import logging
import random
import time
from datetime import datetime
from typing import Any, Dict, List, Optional

from services.jiosaavn_service import JioSaavnService
from services.user_service import UserService

logger = logging.getLogger(__name__)


class DailyMixFastService:
    """Fast, bounded, cache-first daily mix generator for production use."""

    MIX_META: List[Dict[str, str]] = [
        {"id": "favorites", "title": "Your Favorites", "emoji": "❤️", "color": "#FF6B6B"},
        {"id": "mood", "title": "Mood Mix", "emoji": "🌈", "color": "#6C5CE7"},
        {"id": "discover", "title": "Discover Mix", "emoji": "✨", "color": "#FFD93D"},
        {"id": "similar", "title": "Similar Artists", "emoji": "🎤", "color": "#4ECDC4"},
    ]

    CACHE_TTL = 3600  # 1 hour

    MAX_SONGS = 15
    MAX_ARTISTS = 3
    MAX_SEARCH_PER_ARTIST = 5
    MAX_LANGUAGES = 2
    POOL_FETCH_LIMIT = 40
    MOOD_QUERY_LIMIT = 8
    MOOD_POOL_PER_LANGUAGE = 24

    MOOD_QUERIES: Dict[str, List[str]] = {
        "fresh": ["morning songs", "fresh vibes"],
        "chill": ["lofi chill", "relax songs"],
        "party": ["party songs", "workout mix"],
        "calm": ["soft songs", "night vibes"],
    }

    TRENDING_POOL: List[Dict[str, Any]] = []
    LANGUAGE_POOLS: Dict[str, List[Dict[str, Any]]] = {
        "telugu": [],
        "hindi": [],
        "english": [],
    }
    MOOD_LANGUAGE_POOLS: Dict[str, Dict[str, List[Dict[str, Any]]]] = {
        "telugu": {"fresh": [], "chill": [], "party": [], "calm": []},
        "hindi": {"fresh": [], "chill": [], "party": [], "calm": []},
        "english": {"fresh": [], "chill": [], "party": [], "calm": []},
    }

    def __init__(
        self,
        jiosaavn_service: Optional[JioSaavnService] = None,
        user_service: Optional[UserService] = None,
    ) -> None:
        self.jiosaavn_service = jiosaavn_service or JioSaavnService()
        self.user_service = user_service or UserService()
        self.cache: Dict[str, Any] = {}
        self._api_call_count = 0
        self._pools_initialized = False

    # ----------------------------
    # Cache
    # ----------------------------
    def get_cache(self, key: str) -> Any:
        if key in self.cache:
            data, timestamp = self.cache[key]
            if time.time() - timestamp < self.CACHE_TTL:
                return data
            self.cache.pop(key, None)
        return None

    def set_cache(self, key: str, data: Any) -> None:
        self.cache[key] = (data, time.time())

    # ----------------------------
    # Pool Initialization
    # ----------------------------
    def initialize_pools(self) -> None:
        if self._pools_initialized:
            return

        logger.info("[DAILY_MIX] Initializing shared song pools")
        self.TRENDING_POOL = self._safe_search_songs("top songs", self.POOL_FETCH_LIMIT)
        self.LANGUAGE_POOLS["telugu"] = self._safe_search_songs("telugu songs", self.POOL_FETCH_LIMIT)
        self.LANGUAGE_POOLS["hindi"] = self._safe_search_songs("hindi songs", self.POOL_FETCH_LIMIT)
        self.LANGUAGE_POOLS["english"] = self._safe_search_songs("english songs", self.POOL_FETCH_LIMIT)

        for language in list(self.MOOD_LANGUAGE_POOLS.keys()):
            for mood, mood_queries in self.MOOD_QUERIES.items():
                mood_songs: List[Dict[str, Any]] = []
                for query_fragment in mood_queries:
                    mood_songs.extend(
                        self._safe_search_songs(
                            f"{language} {query_fragment}",
                            self.MOOD_QUERY_LIMIT,
                        )
                    )
                self.MOOD_LANGUAGE_POOLS[language][mood] = self._deduplicate_and_limit(
                    mood_songs,
                    self.MOOD_POOL_PER_LANGUAGE,
                )

        if len(self.TRENDING_POOL) < self.MAX_SONGS:
            backup_queries = ["new songs", "viral songs", "party songs"]
            for query in backup_queries:
                self.TRENDING_POOL.extend(self._safe_search_songs(query, self.POOL_FETCH_LIMIT))
                self.TRENDING_POOL = self._deduplicate_and_limit(self.TRENDING_POOL, self.POOL_FETCH_LIMIT * 2)
                if len(self.TRENDING_POOL) >= self.MAX_SONGS:
                    break
        self._pools_initialized = True
        logger.info(
            "[DAILY_MIX] Pools ready: trending=%d telugu=%d hindi=%d english=%d",
            len(self.TRENDING_POOL),
            len(self.LANGUAGE_POOLS.get("telugu", [])),
            len(self.LANGUAGE_POOLS.get("hindi", [])),
            len(self.LANGUAGE_POOLS.get("english", [])),
        )

    # ----------------------------
    # Public API
    # ----------------------------
    def get_daily_mixes_meta(self) -> List[Dict[str, str]]:
        return [dict(item) for item in self.MIX_META]

    def get_mix_by_type(self, mix_type: str, user_id: str, refresh: bool = False) -> Dict[str, Any]:
        self.initialize_pools()
        self._api_call_count = 0
        started = time.time()

        mix_id = str(mix_type or "").strip().lower()
        if not user_id:
            raise ValueError("user_id is required")

        meta = self._mix_meta_by_type(mix_id)
        if not meta:
            raise ValueError(f"Unsupported mix type: {mix_type}")

        cache_key = f"{user_id}:{mix_id}"
        if not refresh:
            cached = self.get_cache(cache_key)
            if cached:
                logger.info("[CACHE HIT] %s", cache_key)
                return cached

        try:
            profile = self.get_user_profile(user_id)
            if mix_id == "favorites":
                songs = self._generate_favorites(profile)
            elif mix_id == "mood":
                songs = self._generate_mood(profile)
            elif mix_id == "discover":
                songs = self._generate_discover(profile)
            else:
                songs = self._generate_similar(profile)
        except Exception as exc:
            logger.warning("[DAILY_MIX] generation failed type=%s uid=%s: %s", mix_id, user_id, exc)
            songs = []

        if not songs:
            logger.info("[DAILY_MIX] fallback to trending type=%s uid=%s", mix_id, user_id)
            songs = list(self.TRENDING_POOL[: self.MAX_SONGS])

        songs = self._normalize_mix(songs, self.TRENDING_POOL)
        logger.info("%s mix size: %d", mix_id, len(songs))

        payload = {
            "type": mix_id,
            "id": mix_id,
            "title": meta["title"],
            "emoji": meta["emoji"],
            "color": meta["color"],
            "songs": [self._to_song_dto(song) for song in songs],
            "count": len(songs),
        }

        self.set_cache(cache_key, payload)
        elapsed_ms = int((time.time() - started) * 1000)
        logger.info(
            "[DAILY_MIX] type=%s uid=%s calls=%d count=%d duration_ms=%d",
            mix_id,
            user_id,
            self._api_call_count,
            len(songs),
            elapsed_ms,
        )
        return payload

    # ----------------------------
    # Profile
    # ----------------------------
    def get_user_profile(self, user_id: str) -> Dict[str, Any]:
        profile_key = f"profile:{user_id}"
        cached = self.get_cache(profile_key)
        if isinstance(cached, dict):
            return cached

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
        self.set_cache(profile_key, profile)
        return profile

    # ----------------------------
    # Mix Generators
    # ----------------------------
    def _generate_favorites(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        songs: List[Dict[str, Any]] = list(profile.get("top_songs", [])[:10])
        for artist in profile.get("top_artists", [])[: self.MAX_ARTISTS]:
            songs.extend(self._safe_search_songs(str(artist), self.MAX_SEARCH_PER_ARTIST))
        return self._deduplicate_and_limit(songs, self.MAX_SONGS)

    def _generate_mood(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        songs: List[Dict[str, Any]] = []
        mood = self._get_time_based_mood()
        languages = profile.get("languages") or ["english", "hindi"]
        selected_languages = [str(lang).strip().lower() for lang in languages if str(lang).strip()][: self.MAX_LANGUAGES]

        if not selected_languages:
            selected_languages = ["english", "hindi"]

        for language in selected_languages:
            mood_pool = self.MOOD_LANGUAGE_POOLS.get(language, {}).get(mood, [])

            if mood_pool:
                # Strong mood first, then broader language support.
                songs.extend(self._filter_songs_by_mood(mood_pool, mood)[:8])
                songs.extend(mood_pool[:6])
            else:
                language_pool = self.LANGUAGE_POOLS.get(language, [])
                songs.extend(self._filter_songs_by_mood(language_pool, mood)[:8])

            # Lightweight dynamic fallback for freshness and better mood matching.
            dynamic_query = f"{language} {mood} songs"
            songs.extend(self._safe_search_songs(dynamic_query, self.MOOD_QUERY_LIMIT))

        if not songs:
            # Last mood query fallback independent of language.
            for query in self.MOOD_QUERIES.get(mood, []):
                songs.extend(self._safe_search_songs(query, self.MOOD_QUERY_LIMIT))

        logger.info("[DAILY_MIX] mood mix mode=%s langs=%s raw_candidates=%d", mood, selected_languages, len(songs))
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    def _get_time_based_mood(self) -> str:
        hour = datetime.now().hour
        if 5 <= hour < 11:
            return "fresh"
        if 11 <= hour < 17:
            return "chill"
        if 17 <= hour < 22:
            return "party"
        return "calm"

    def _filter_songs_by_mood(self, songs: List[Dict[str, Any]], mood: str) -> List[Dict[str, Any]]:
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

    def _generate_discover(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        songs = list(self.TRENDING_POOL[:40])
        songs = self._filter_not_played(songs, profile)
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    def _generate_similar(self, profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        songs: List[Dict[str, Any]] = []
        for artist in profile.get("top_artists", [])[: self.MAX_ARTISTS]:
            songs.extend(self._safe_search_songs(str(artist), self.MAX_SEARCH_PER_ARTIST))
        return self._shuffle_and_limit(songs, self.MAX_SONGS)

    def _normalize_mix(self, songs: List[Dict[str, Any]], fallback_pool: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
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
            # Preserve quality order: primary songs first, then fallback pool fill.
            for song in fallback_pool:
                sid = self._song_id(song)
                if not sid or sid in seen:
                    continue
                unique.append(self._normalize_song(song))
                seen.add(sid)
                if len(unique) == self.MAX_SONGS:
                    break

        if len(unique) < self.MAX_SONGS:
            emergency_pool: List[Dict[str, Any]] = []
            emergency_pool.extend(self.TRENDING_POOL)
            emergency_pool.extend(self.LANGUAGE_POOLS.get("english", []))
            emergency_pool.extend(self.LANGUAGE_POOLS.get("hindi", []))
            emergency_pool.extend(self.LANGUAGE_POOLS.get("telugu", []))
            emergency_pool.extend(self._safe_search_songs("top songs", self.MAX_SONGS * 2))

            for song in emergency_pool:
                sid = self._song_id(song)
                if not sid or sid in seen:
                    continue
                unique.append(self._normalize_song(song))
                seen.add(sid)
                if len(unique) == self.MAX_SONGS:
                    break

        # Final hard guarantee for exact length.
        if len(unique) < self.MAX_SONGS:
            source = unique[:] if unique else [
                {
                    "id": "fallback-seed-1",
                    "videoId": "fallback-seed-1",
                    "title": "Trending Music",
                    "song": "Trending Music",
                    "artist": "AURA",
                    "image": "",
                    "thumbnail": "",
                    "media_url": "",
                    "url": "",
                    "stream_url": "",
                }
            ]
            idx = 0
            while len(unique) < self.MAX_SONGS:
                base = dict(source[idx % len(source)])
                base["id"] = f"{self._song_id(base) or 'fallback'}-pad-{len(unique)}"
                base["videoId"] = base["id"]
                unique.append(self._normalize_song(base))
                idx += 1

        return unique[: self.MAX_SONGS]

    # ----------------------------
    # Helpers
    # ----------------------------
    def _safe_fetch(self, fn):
        try:
            return fn()
        except Exception as exc:
            logger.warning("[DAILY_MIX] safe fetch failed: %s", exc)
            return []

    def _safe_search_songs(self, query: str, limit: int) -> List[Dict[str, Any]]:
        try:
            self._api_call_count += 1
            songs = self.jiosaavn_service.search_songs(query, limit=limit)
            return [self._normalize_song(song) for song in songs if isinstance(song, dict)]
        except Exception as exc:
            logger.warning("[DAILY_MIX] search failed query=%s: %s", query, exc)
            return []

    def _filter_not_played(self, songs: List[Dict[str, Any]], profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        played_ids = set(profile.get("played_song_ids", set()))
        return [song for song in songs if self._song_id(song) not in played_ids]

    def _extract_top_artists(self, songs: List[Dict[str, Any]], limit: int) -> List[str]:
        counts: Dict[str, int] = {}
        for song in songs:
            artist_text = str(song.get("artist") or song.get("primary_artists") or "")
            for artist in [a.strip() for a in artist_text.split(",") if a.strip()]:
                counts[artist] = counts.get(artist, 0) + 1
        ranked = sorted(counts.items(), key=lambda item: item[1], reverse=True)
        return [artist for artist, _ in ranked[:limit]]

    def _deduplicate_and_limit(self, songs: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
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
        deduped = self._deduplicate_and_limit(songs, max(limit * 2, limit))
        random.shuffle(deduped)
        return deduped[:limit]

    def _song_id(self, song: Dict[str, Any]) -> str:
        return str(song.get("id") or song.get("videoId") or "").strip()

    def _mix_meta_by_type(self, mix_type: str) -> Optional[Dict[str, str]]:
        for item in self.MIX_META:
            if item.get("id") == mix_type:
                return item
        return None

    def _normalize_song(self, song: Dict[str, Any]) -> Dict[str, Any]:
        title = str(song.get("song") or song.get("title") or song.get("name") or "").strip()
        artist = str(song.get("primary_artists") or song.get("artist") or song.get("singers") or "").strip()
        image = str(song.get("image") or song.get("thumbnail") or "").strip()
        media_url = str(song.get("media_url") or song.get("url") or song.get("stream_url") or "").strip()

        normalized = dict(song)
        normalized.update(
            {
                "id": str(song.get("id") or song.get("videoId") or "").strip(),
                "videoId": str(song.get("videoId") or song.get("id") or "").strip(),
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
