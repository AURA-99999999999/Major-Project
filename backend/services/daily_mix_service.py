"""Daily Mix generation for AURA.

Builds four personalized mixes from Firestore listening signals, collaborative
filtering, JioSaavn search, and the existing canonical/diversity pipeline.
"""

from __future__ import annotations

import logging
import math
import random
import time
from collections import Counter
from datetime import datetime
from typing import Any, Dict, List, Optional, Sequence, Set, Tuple

from services.cache_manager import get_cache
from services.canonical_track_resolver import resolve_canonical_tracks
from services.collaborative_service import CollaborativeFilteringService
from services.jiosaavn_service import JioSaavnService
from services.user_profile_service import UserProfileService
from services.user_service import UserService, get_firestore_client

logger = logging.getLogger(__name__)


class DailyMixService:
    MIX_SIZE = 24
    MIN_MIX_SIZE = 20
    MAX_MIX_SIZE = 30
    MAX_SONGS_PER_ARTIST = 2
    MAX_SONGS_PER_ALBUM = 3
    MIN_UNIQUE_ARTISTS = 6
    CACHE_TTL_SECONDS = 24 * 60 * 60
    MOOD_MIX_TTL_SECONDS = 3 * 60 * 60

    MOOD_PLAYLIST_MAP: Dict[str, List[str]] = {
        "morning": ["workout", "energetic", "feel good", "morning motivation"],
        "afternoon": ["chill", "focus", "light music"],
        "evening": ["relax", "melody", "romantic"],
        "night": ["soft songs", "sad songs", "night vibes"],
    }

    MOOD_SUBTITLES: Dict[str, str] = {
        "morning": "Morning Workout Boost",
        "afternoon": "Afternoon Chill Focus",
        "evening": "Evening Relax Session",
        "night": "Night Soft Vibes",
    }

    MOOD_EXCLUDED_VERSION_KEYWORDS: Tuple[str, ...] = (
        "lofi",
        "slowed",
        "reverb",
        "remix",
    )

    LANGUAGE_ALIASES: Dict[str, Set[str]] = {
        "english": {"english", "eng"},
        "hindi": {"hindi", "hin"},
        "telugu": {"telugu", "tel"},
        "tamil": {"tamil", "tam"},
        "kannada": {"kannada", "kan"},
        "malayalam": {"malayalam", "mal"},
        "punjabi": {"punjabi", "punjabi pop", "punjabi-pop"},
        "marathi": {"marathi", "mar"},
        "bengali": {"bengali", "bangla", "ben"},
    }

    MIX_SPECS: Tuple[Dict[str, str], ...] = (
        {
            "key": "dailyMix1",
            "storage_id": "daily_mix_1",
            "name": "Favorites Mix",
            "type": "favorites",
            "description": "Songs from your favorite artists.",
        },
        {
            "key": "dailyMix2",
            "storage_id": "daily_mix_2",
            "name": "Similar Artists Mix",
            "type": "similar_artists",
            "description": "Artists adjacent to what you already love.",
        },
        {
            "key": "dailyMix3",
            "storage_id": "daily_mix_3",
            "name": "Discover Mix",
            "type": "discover",
            "description": "Fresh discovery picks tuned to your taste.",
        },
        {
            "key": "moodMix",
            "storage_id": "mood_mix",
            "name": "Mood Mix",
            "type": "time_based",
            "description": "A time-aware mix for the current part of your day.",
        },
    )

    def __init__(
        self,
        jiosaavn_service: Optional[JioSaavnService] = None,
        user_profile_service: Optional[UserProfileService] = None,
        collaborative_service: Optional[CollaborativeFilteringService] = None,
        user_service: Optional[UserService] = None,
        recommendation_service: Any = None,
        decay_lambda: float = 0.15,
    ) -> None:
        self.jiosaavn_service = jiosaavn_service or JioSaavnService()
        self.user_profile_service = user_profile_service or UserProfileService()
        self.user_service = user_service or UserService()
        self.collaborative_service = collaborative_service or CollaborativeFilteringService(
            self.user_service,
            recommendation_service=recommendation_service,
            decay_lambda=decay_lambda,
        )
        self.recommendation_service = recommendation_service
        self.decay_lambda = decay_lambda
        self.cache = get_cache()
        self.db = getattr(self.user_service, "db", None) or get_firestore_client()

    def get_daily_mixes(self, uid: str, refresh: bool = False) -> Dict[str, Any]:
        uid = str(uid or "").strip()
        if not uid or uid == "guest":
            return self._empty_response(uid)

        if not refresh:
            cached = self._load_cached_response(uid)
            if cached is not None:
                return cached

        generated = self.generate_daily_mixes(uid)
        response = self._response_from_mix_map(uid, generated, cached=False)
        self._store_mixes(uid, generated)
        self.cache.set(self._response_cache_key(uid), response, ttl=self.CACHE_TTL_SECONDS)
        return response

    def generate_daily_mixes(self, uid: str) -> Dict[str, Dict[str, Any]]:
        profile = self._build_user_taste_profile(uid)
        used_ids: Set[str] = set()

        favorites = self._generate_favorites_mix(profile, used_ids)
        used_ids.update(self._song_ids(favorites))

        similar = self._generate_similar_artists_mix(profile, used_ids)
        used_ids.update(self._song_ids(similar))

        discover = self._generate_discover_mix(profile, used_ids)
        used_ids.update(self._song_ids(discover))

        mood_name, mood_description, mood_tracks, mood_time_block, mood_subtitle = self._generate_mood_mix(profile, used_ids)

        specs = {
            "dailyMix1": {**self.MIX_SPECS[0], "songs": favorites},
            "dailyMix2": {**self.MIX_SPECS[1], "songs": similar},
            "dailyMix3": {**self.MIX_SPECS[2], "songs": discover},
            "moodMix": {
                **self.MIX_SPECS[3],
                "name": mood_name,
                "description": mood_description,
                "subtitle": mood_subtitle,
                "timeBlock": mood_time_block,
                "songs": mood_tracks,
            },
        }

        for mix in specs.values():
            songs = mix.get("songs") or []
            mix["count"] = len(songs)
            mix["tracks"] = songs
            mix["generatedAt"] = int(time.time())
            mix["source"] = "daily_mix"

        return specs

    def _build_user_taste_profile(self, uid: str) -> Dict[str, Any]:
        cache_key = f"daily_mix_profile:{uid}"
        cached = self.cache.get(cache_key)
        if isinstance(cached, dict):
            return cached

        plays = self.user_profile_service._fetch_user_plays(uid)
        liked = self.user_profile_service._fetch_liked_songs(uid)
        preferred_languages = list(self.user_service.get_user_languages(uid) or [])

        artist_scores: Counter[str] = Counter()
        artist_display: Dict[str, str] = {}
        language_scores: Counter[str] = Counter()
        consumed_ids: Set[str] = set()
        recent_entries: List[Tuple[float, Dict[str, Any]]] = []

        for entry in plays:
            timestamp = self._entry_timestamp(entry)
            weight = self._entry_weight(entry, timestamp, liked=False)
            recent_entries.append((timestamp, entry))
            song_id = self._song_id(entry)
            if song_id:
                consumed_ids.add(song_id)
            self._accumulate_artist_scores(entry, weight, artist_scores, artist_display)
            self._accumulate_language(entry, weight, language_scores)

        for entry in liked:
            timestamp = self._entry_timestamp(entry)
            weight = self._entry_weight(entry, timestamp, liked=True)
            recent_entries.append((timestamp, entry))
            song_id = self._song_id(entry)
            if song_id:
                consumed_ids.add(song_id)
            self._accumulate_artist_scores(entry, weight, artist_scores, artist_display)
            self._accumulate_language(entry, weight, language_scores)

        sorted_artists = [
            artist_display.get(name, name.title())
            for name, _ in artist_scores.most_common(8)
            if name
        ]
        sorted_languages = [name for name, _ in language_scores.most_common(4) if name]
        recent_entries.sort(key=lambda item: item[0], reverse=True)

        profile = {
            "uid": uid,
            "plays": plays,
            "liked": liked,
            "consumed_ids": consumed_ids,
            "top_artists": sorted_artists,
            "top_artist_norms": {self._normalize_artist_name(name) for name in sorted_artists if name},
            "top_languages": preferred_languages or sorted_languages,
            "recent_entries": [entry for _, entry in recent_entries[:40]],
        }
        self.cache.set(cache_key, profile, ttl=30 * 60)
        return profile

    def _generate_favorites_mix(self, profile: Dict[str, Any], exclude_ids: Set[str]) -> List[Dict[str, Any]]:
        top_artists = profile.get("top_artists", [])[:3]
        candidates: List[Dict[str, Any]] = []

        for artist in top_artists:
            candidates.extend(self._search_songs(f"{artist} songs", limit=12))

        if not candidates:
            candidates = self._get_personalized_candidates(profile, limit=36)

        filtered = [
            song
            for song in candidates
            if self._song_id(song) not in profile["consumed_ids"]
            and self._song_id(song) not in exclude_ids
        ]
        return self._finalize_mix(filtered, profile, limit=self.MIX_SIZE)

    def _generate_similar_artists_mix(self, profile: Dict[str, Any], exclude_ids: Set[str]) -> List[Dict[str, Any]]:
        personalized = self._get_personalized_candidates(profile, limit=36)
        collaborative = self._get_collaborative_candidates(profile, limit=24)

        related_artist_counts: Counter[str] = Counter()
        related_artist_display: Dict[str, str] = {}
        top_artist_norms = set(profile.get("top_artist_norms", set()))

        for song in personalized + collaborative:
            for artist in self._extract_artists(song):
                norm = self._normalize_artist_name(artist)
                if norm and norm not in top_artist_norms:
                    related_artist_counts[norm] += 1
                    related_artist_display.setdefault(norm, artist)

        search_artists = [
            related_artist_display[name]
            for name, _ in related_artist_counts.most_common(3)
            if name in related_artist_display
        ]

        search_results: List[Dict[str, Any]] = []
        for artist in search_artists:
            search_results.extend(self._search_songs(f"{artist} hits", limit=10))

        candidates = collaborative + personalized + search_results
        filtered = [
            song
            for song in candidates
            if self._song_id(song) not in profile["consumed_ids"]
            and self._song_id(song) not in exclude_ids
        ]
        return self._finalize_mix(filtered, profile, limit=self.MIX_SIZE)

    def _generate_discover_mix(self, profile: Dict[str, Any], exclude_ids: Set[str]) -> List[Dict[str, Any]]:
        candidates: List[Dict[str, Any]] = []
        candidates.extend(self._get_collaborative_candidates(profile, limit=30))
        candidates.extend(self._get_personalized_candidates(profile, limit=30))

        preferred_languages = profile.get("top_languages") or []
        for language in preferred_languages[:2]:
            candidates.extend(self._search_songs(f"latest {language} songs", limit=10))

        if not candidates:
            candidates.extend(self._search_songs("trending songs", limit=20))

        filtered = [
            song
            for song in candidates
            if self._song_id(song) not in profile["consumed_ids"]
            and self._song_id(song) not in exclude_ids
        ]
        return self._finalize_mix(filtered, profile, limit=self.MIX_SIZE)

    def _generate_mood_mix(
        self,
        profile: Dict[str, Any],
        exclude_ids: Set[str],
    ) -> Tuple[str, str, List[Dict[str, Any]], str, str]:
        TOTAL_TRACKS = self.MIX_SIZE  # 24

        time_block = self._current_time_block()
        subtitle = self.MOOD_SUBTITLES.get(time_block, "Mood Picks")
        mood_name = "Mood Mix"
        mood_description = subtitle

        # Step 1: Load user language preferences
        uid = profile.get("uid", "")
        user_selected_languages = self.user_service.get_user_languages(uid)
        languages = self._normalize_language_list(
            user_selected_languages or profile.get("top_languages") or []
        )
        if not languages:
            languages = ["hindi"]

        # Step 2: Equal per-language quota
        tracks_per_language = TOTAL_TRACKS // len(languages)

        # Step 3: Mood keywords for the current time block
        mood_keywords = self.MOOD_PLAYLIST_MAP.get(time_block, self.MOOD_PLAYLIST_MAP["night"])

        # Steps 4-8: Language-aware retrieval, strict validation, canon + diversity.
        # Keep API usage bounded while still giving each preferred language a fair pool.
        max_searches_per_lang = max(1, min(5, 8 // len(languages)))
        consumed_ids: Set[str] = profile.get("consumed_ids", set())

        language_pools: Dict[str, List[Dict[str, Any]]] = {}
        for language in languages:
            language_pools[language] = self._retrieve_language_mood_pool(
                language=language,
                mood_keywords=mood_keywords,
                consumed_ids=consumed_ids | exclude_ids,
                max_searches=max_searches_per_lang,
            )

        # Step 9: Select per-language quota
        selected_tracks: List[Dict[str, Any]] = []
        selected_ids: Set[str] = set()

        for language in languages:
            pool = language_pools[language]
            available = [s for s in pool if self._song_id(s) not in selected_ids]
            chosen = available[:tracks_per_language]
            selected_tracks.extend(chosen)
            selected_ids.update(self._song_id(s) for s in chosen)

        # Step 10: Fill any shortfall from remaining per-language pools in round-robin.
        # This prevents one language from taking all leftover slots.
        remaining_needed = TOTAL_TRACKS - len(selected_tracks)
        if remaining_needed > 0:
            remainder_by_language: Dict[str, List[Dict[str, Any]]] = {}
            for language in languages:
                remainder_by_language[language] = [
                    song
                    for song in language_pools.get(language, [])
                    if self._song_id(song) and self._song_id(song) not in selected_ids
                ]

            index = 0
            progressed = True
            while remaining_needed > 0 and progressed:
                progressed = False
                for language in languages:
                    bucket = remainder_by_language.get(language, [])
                    if index >= len(bucket):
                        continue
                    song = bucket[index]
                    sid = self._song_id(song)
                    if sid and sid not in selected_ids:
                        selected_tracks.append(song)
                        selected_ids.add(sid)
                        remaining_needed -= 1
                        progressed = True
                        if remaining_needed <= 0:
                            break
                index += 1

        # Step 11: Shuffle final mix
        random.shuffle(selected_tracks)
        return mood_name, mood_description, selected_tracks[:TOTAL_TRACKS], time_block, subtitle

    def _normalize_language_list(self, languages: Sequence[str]) -> List[str]:
        normalized: List[str] = []
        seen: Set[str] = set()
        for language in languages:
            value = self._canonical_language(str(language))
            if not value or value in seen:
                continue
            seen.add(value)
            normalized.append(value)
        return normalized

    def _canonical_language(self, language: str) -> str:
        value = str(language or "").strip().lower()
        if not value:
            return ""
        for canonical, aliases in self.LANGUAGE_ALIASES.items():
            if value == canonical or value in aliases:
                return canonical
        return value

    def _is_undesirable_mood_version(self, song: Dict[str, Any]) -> bool:
        title = str(song.get("title") or song.get("song") or song.get("name") or "").strip().lower()
        if not title:
            return False
        return any(keyword in title for keyword in self.MOOD_EXCLUDED_VERSION_KEYWORDS)

    def _select_balanced_language_quota_tracks(
        self,
        candidates: Sequence[Dict[str, Any]],
        profile: Dict[str, Any],
        preferred_languages: Sequence[str],
        limit: int,
    ) -> List[Dict[str, Any]]:
        preferred = self._normalize_language_list(preferred_languages)
        if not preferred:
            return self._finalize_mix(candidates, profile, limit=limit)

        strict_candidates = self._filter_songs_by_languages_strict(candidates, preferred)
        strict_candidates = [song for song in strict_candidates if not self._is_undesirable_mood_version(song)]

        buckets: Dict[str, List[Dict[str, Any]]] = {language: [] for language in preferred}
        assigned_ids: Set[str] = set()

        # Build language pools in one pass and spread multi-language songs fairly.
        for song in strict_candidates:
            song_id = self._song_id(song)
            if not song_id or song_id in assigned_ids:
                continue

            song_langs = self._song_languages(song)
            matched_languages = [language for language in preferred if language in song_langs]
            if not matched_languages:
                continue

            chosen_language = min(matched_languages, key=lambda language: len(buckets[language]))
            if len(buckets[chosen_language]) < 40:
                buckets[chosen_language].append(song)
                assigned_ids.add(song_id)

        per_language_quota = limit // len(preferred)
        extra_slots = limit % len(preferred)
        quotas: Dict[str, int] = {language: per_language_quota for language in preferred}
        for index in range(extra_slots):
            quotas[preferred[index]] += 1

        selected: List[Dict[str, Any]] = []
        selected_ids: Set[str] = set()
        bucket_indices: Dict[str, int] = {language: 0 for language in preferred}

        # First pass: satisfy exact per-language quota where supply exists.
        for language in preferred:
            ranked_bucket = sorted(
                buckets.get(language, []),
                key=lambda song: self._preference_score(song, profile),
                reverse=True,
            )
            take = quotas.get(language, 0)
            for song in ranked_bucket[:take]:
                song_id = self._song_id(song)
                if not song_id or song_id in selected_ids:
                    continue
                selected.append(song)
                selected_ids.add(song_id)
                bucket_indices[language] += 1

        # Second pass: fill remaining slots from language buckets in round-robin order.
        while len(selected) < limit:
            progressed = False
            for language in preferred:
                ranked_bucket = sorted(
                    buckets.get(language, []),
                    key=lambda song: self._preference_score(song, profile),
                    reverse=True,
                )
                index = bucket_indices[language]
                while index < len(ranked_bucket):
                    song = ranked_bucket[index]
                    index += 1
                    song_id = self._song_id(song)
                    if not song_id or song_id in selected_ids:
                        continue
                    selected.append(song)
                    selected_ids.add(song_id)
                    bucket_indices[language] = index
                    progressed = True
                    break
                if len(selected) >= limit:
                    break
            if not progressed:
                break

        if len(selected) < limit:
            remainder_pool: List[Dict[str, Any]] = []
            for language in preferred:
                ranked_bucket = sorted(
                    buckets.get(language, []),
                    key=lambda song: self._preference_score(song, profile),
                    reverse=True,
                )
                remainder_pool.extend(ranked_bucket)

            for song in remainder_pool:
                if len(selected) >= limit:
                    break
                song_id = self._song_id(song)
                if not song_id or song_id in selected_ids:
                    continue
                selected.append(song)
                selected_ids.add(song_id)

        return selected[:limit]

    def _current_time_block(self) -> str:
        hour = datetime.now().hour
        if 5 <= hour < 11:
            return "morning"
        if 11 <= hour < 17:
            return "afternoon"
        if 17 <= hour < 21:
            return "evening"
        return "night"

    def _playlist_candidates_for_time_block(self, time_block: str) -> List[Dict[str, Any]]:
        keywords = self.MOOD_PLAYLIST_MAP.get(time_block, self.MOOD_PLAYLIST_MAP["night"])

        # Cap at 5 upstream calls: 2 playlist searches + 2 playlist fetches + optional artist fallback.
        playlist_search_keywords = keywords[:2]
        playlists: List[Dict[str, Any]] = []
        for keyword in playlist_search_keywords:
            try:
                playlists.extend(self.jiosaavn_service.get_mood_playlists(keyword, limit=2) or [])
            except Exception as exc:
                logger.warning("Mood playlist discovery failed for '%s': %s", keyword, exc)

        playlist_urls: List[str] = []
        for playlist in playlists:
            url = str(playlist.get("url") or "").strip()
            if url and url not in playlist_urls:
                playlist_urls.append(url)
            if len(playlist_urls) >= 2:
                break

        songs: List[Dict[str, Any]] = []
        for playlist_url in playlist_urls:
            try:
                songs.extend(
                    [self._to_song_dto(song) for song in self.jiosaavn_service.get_playlist_songs_from_url(playlist_url)[:60]]
                )
            except Exception as exc:
                logger.warning("Mood playlist songs fetch failed for '%s': %s", playlist_url, exc)

        # Keep candidate pool in the requested 80-120 range where possible.
        return songs[:120]

    def _retrieve_language_mood_pool(
        self,
        language: str,
        mood_keywords: Sequence[str],
        consumed_ids: Set[str],
        max_searches: int = 2,
    ) -> List[Dict[str, Any]]:
        """Fetch up to 60 songs for one language matched to the current mood keywords.

        For each keyword (up to *max_searches*) the query is composed as
        ``"{language} {keyword}"`` so that JioSaavn returns language-specific
        playlists rather than generic Hindi/mixed playlists.  Songs are then
        validated strictly against the requested language before being added to
        the pool.
        """
        MAX_POOL = 80
        MIN_LANGUAGE_SUPPLY = 24
        lang_norm = self._canonical_language(language)
        candidate_songs: List[Dict[str, Any]] = []
        fetched_urls: Set[str] = set()

        # Step 4: Language + keyword combined queries
        for keyword in list(mood_keywords)[:max_searches]:
            query = f"{language} {keyword}"
            try:
                playlists = self.jiosaavn_service.get_mood_playlists(query, limit=2) or []
            except Exception as exc:
                logger.warning("Mood playlist search failed for '%s': %s", query, exc)
                playlists = []

            for playlist in playlists[:2]:
                url = str(playlist.get("url") or "").strip()
                if not url or url in fetched_urls:
                    continue
                fetched_urls.add(url)
                try:
                    raw_songs = self.jiosaavn_service.get_playlist_songs_from_url(url)[:30]
                    candidate_songs.extend(self._to_song_dto(s) for s in raw_songs)
                except Exception as exc:
                    logger.warning("Mood playlist songs fetch failed for '%s': %s", url, exc)

            if len(candidate_songs) >= MAX_POOL * 2:
                # Have enough candidates to fill the pool even after strict filtering
                break

        # Secondary retrieval: direct song search when playlist retrieval is sparse.
        if len(candidate_songs) < MIN_LANGUAGE_SUPPLY:
            for keyword in list(mood_keywords)[:max_searches]:
                query = f"{keyword} {language} songs"
                candidate_songs.extend(self._search_songs(query, limit=12))
                if len(candidate_songs) >= MAX_POOL * 2:
                    break

        # Tertiary retrieval: language discovery queries for low-supply languages.
        if len(candidate_songs) < MIN_LANGUAGE_SUPPLY:
            broad_queries = [
                f"latest {language} songs",
                f"{language} hits",
                f"{language} trending songs",
            ]
            for query in broad_queries:
                candidate_songs.extend(self._search_songs(query, limit=12))
                if len(candidate_songs) >= MAX_POOL * 2:
                    break

        # Steps 6-8: Strict language validation + remove undesirable versions + consumed
        validated: List[Dict[str, Any]] = []
        seen_ids: Set[str] = set()

        for song in candidate_songs:
            # Step 6: song must belong to the requested language
            song_langs = self._song_languages(song)
            if lang_norm not in song_langs:
                continue

            # Step 7: skip lofi / slowed / reverb / remix versions
            if self._is_undesirable_mood_version(song):
                continue

            # Step 8: skip already-played and excluded tracks
            sid = self._song_id(song)
            if not sid or sid in consumed_ids or sid in seen_ids:
                continue

            seen_ids.add(sid)
            validated.append(song)

            if len(validated) >= MAX_POOL:
                break

        random.shuffle(validated)

        # Step 8 (cont.): canonicalize and then apply diversity capping per language pool.
        validated = resolve_canonical_tracks(validated)
        validated = self._ensure_diversity_resilient(
            validated,
            artist_listen_count={},
            all_consumed=consumed_ids,
            exclude_played=True,
            min_threshold=min(12, MAX_POOL),
            max_per_artist=self.MAX_SONGS_PER_ARTIST,
            mix_type="mood_mix_language_pool",
            limit=MAX_POOL,
        )

        return validated[:MAX_POOL]

    def _filter_songs_by_languages(
        self,
        songs: Sequence[Dict[str, Any]],
        preferred_languages: Sequence[str],
    ) -> List[Dict[str, Any]]:
        normalized_prefs = {str(lang).strip().lower() for lang in preferred_languages if str(lang).strip()}
        if not normalized_prefs:
            return list(songs)

        filtered: List[Dict[str, Any]] = []
        for song in songs:
            language = str(song.get("language") or "").strip().lower()
            if not language:
                continue
            parts = {part.strip() for part in language.replace("/", ",").replace("|", ",").split(",") if part.strip()}
            if not parts:
                parts = {language}
            if parts & normalized_prefs:
                filtered.append(song)

        return filtered or list(songs)

    def _filter_songs_by_languages_strict(
        self,
        songs: Sequence[Dict[str, Any]],
        preferred_languages: Sequence[str],
    ) -> List[Dict[str, Any]]:
        normalized_prefs = {str(lang).strip().lower() for lang in preferred_languages if str(lang).strip()}
        if not normalized_prefs:
            return list(songs)

        filtered: List[Dict[str, Any]] = []
        for song in songs:
            song_langs = self._song_languages(song)
            if song_langs and (song_langs & normalized_prefs):
                filtered.append(song)

        return filtered

    def _song_languages(self, song: Dict[str, Any]) -> Set[str]:
        raw = str(song.get("language") or "").strip().lower()
        if not raw:
            return set()
        parts = {
            part.strip()
            for part in raw.replace("/", ",").replace("|", ",").replace("&", ",").split(",")
            if part.strip()
        }
        canonical = {self._canonical_language(part) for part in parts if self._canonical_language(part)}
        if canonical:
            return canonical
        single = self._canonical_language(raw)
        return {single} if single else set()

    def _balance_languages(
        self,
        songs: Sequence[Dict[str, Any]],
        preferred_languages: Sequence[str],
        target_size: int,
    ) -> List[Dict[str, Any]]:
        preferred = [str(language).strip().lower() for language in preferred_languages if str(language).strip()]
        if not preferred:
            return list(songs)[:target_size]

        buckets: Dict[str, List[Dict[str, Any]]] = {language: [] for language in preferred}
        remainder: List[Dict[str, Any]] = []

        for song in songs:
            song_langs = self._song_languages(song)
            matched = [language for language in preferred if language in song_langs]
            if matched:
                buckets[matched[0]].append(song)
            else:
                remainder.append(song)

        # Round-robin keeps all preferred languages represented in the candidate pool.
        balanced: List[Dict[str, Any]] = []
        exhausted = False
        index = 0
        while len(balanced) < target_size and not exhausted:
            exhausted = True
            for language in preferred:
                bucket = buckets.get(language, [])
                if index < len(bucket):
                    balanced.append(bucket[index])
                    exhausted = False
                    if len(balanced) >= target_size:
                        break
            index += 1

        if len(balanced) < target_size:
            balanced.extend(remainder[: target_size - len(balanced)])

        return balanced[:target_size]

    def _enforce_near_equal_language_mix(
        self,
        finalized_songs: Sequence[Dict[str, Any]],
        fallback_songs: Sequence[Dict[str, Any]],
        preferred_languages: Sequence[str],
        limit: int,
    ) -> List[Dict[str, Any]]:
        preferred: List[str] = []
        for language in preferred_languages:
            normalized = str(language).strip().lower()
            if normalized and normalized not in preferred:
                preferred.append(normalized)

        if not preferred:
            return list(finalized_songs)[:limit]

        # Primary pool is the finalized ranking; fallback allows filling missing language quotas.
        primary_pool = list(finalized_songs)
        all_pool = primary_pool + [song for song in fallback_songs if self._song_id(song)]

        buckets: Dict[str, List[Dict[str, Any]]] = {language: [] for language in preferred}
        remainder: List[Dict[str, Any]] = []
        seen_pool_ids: Set[str] = set()

        for song in all_pool:
            song_id = self._song_id(song)
            if not song_id or song_id in seen_pool_ids:
                continue
            seen_pool_ids.add(song_id)

            song_langs = self._song_languages(song)
            matched = [language for language in preferred if language in song_langs]
            if matched:
                buckets[matched[0]].append(song)
            else:
                remainder.append(song)

        language_count = len(preferred)
        base_quota = limit // language_count
        remainder_slots = limit % language_count
        quotas: Dict[str, int] = {language: base_quota for language in preferred}
        for index in range(remainder_slots):
            quotas[preferred[index]] += 1

        selected: List[Dict[str, Any]] = []
        selected_ids: Set[str] = set()
        used_per_language: Dict[str, int] = {language: 0 for language in preferred}

        # First pass: satisfy per-language quotas where available.
        for language in preferred:
            quota = quotas.get(language, 0)
            for song in buckets.get(language, []):
                if used_per_language[language] >= quota:
                    break
                song_id = self._song_id(song)
                if not song_id or song_id in selected_ids:
                    continue
                selected.append(song)
                selected_ids.add(song_id)
                used_per_language[language] += 1

        # Second pass: fill remaining slots using round-robin to keep near-equal distribution.
        bucket_indices: Dict[str, int] = {language: 0 for language in preferred}
        exhausted = False
        while len(selected) < limit and not exhausted:
            exhausted = True
            for language in preferred:
                bucket = buckets.get(language, [])
                index = bucket_indices[language]
                while index < len(bucket):
                    song = bucket[index]
                    index += 1
                    song_id = self._song_id(song)
                    if not song_id or song_id in selected_ids:
                        continue
                    selected.append(song)
                    selected_ids.add(song_id)
                    used_per_language[language] += 1
                    exhausted = False
                    break
                bucket_indices[language] = index
                if len(selected) >= limit:
                    break

        # Final fallback for low-supply languages: fill from any remaining songs.
        if len(selected) < limit:
            for song in remainder:
                if len(selected) >= limit:
                    break
                song_id = self._song_id(song)
                if not song_id or song_id in selected_ids:
                    continue
                selected.append(song)
                selected_ids.add(song_id)

        return selected[:limit]

    def _favorite_artist_candidates(self, profile: Dict[str, Any], limit: int) -> List[Dict[str, Any]]:
        top_artists = list(profile.get("top_artists") or [])[:1]
        candidates: List[Dict[str, Any]] = []

        for artist in top_artists:
            try:
                candidates.extend([self._to_song_dto(song) for song in self.jiosaavn_service.get_artist_songs(artist)[:limit]])
            except Exception as exc:
                logger.warning("Favorite-artist candidates failed for '%s': %s", artist, exc)

        if not candidates:
            fallback = self._get_personalized_candidates(profile, limit=limit)
            norm_top_artists = set(profile.get("top_artist_norms") or set())
            candidates = [
                song
                for song in fallback
                if {self._normalize_artist_name(name) for name in self._extract_artists(song)} & norm_top_artists
            ]
        return candidates

    def _boost_favorite_artists(self, songs: Sequence[Dict[str, Any]], profile: Dict[str, Any]) -> List[Dict[str, Any]]:
        top_artist_norms = set(profile.get("top_artist_norms") or set())
        boosted: List[Dict[str, Any]] = []
        for song in songs:
            copy_song = dict(song)
            song_artists = {self._normalize_artist_name(artist) for artist in self._extract_artists(copy_song)}
            if song_artists & top_artist_norms:
                copy_song["_boost_score"] = 2.5
            boosted.append(copy_song)
        return boosted

    def _get_personalized_candidates(self, profile: Dict[str, Any], limit: int) -> List[Dict[str, Any]]:
        cache_key = f"daily_mix_personalized:{profile['uid']}:{limit}"
        cached = self.cache.get(cache_key)
        if isinstance(cached, list):
            return cached

        try:
            from services.personalized_recommender import get_recommendations

            songs = get_recommendations(
                profile["uid"],
                limit=min(15, max(10, limit // 2)),
                lang_fallback=profile.get("top_languages") or [],
            )
            normalized = [self._to_song_dto(song) for song in songs]
            self.cache.set(cache_key, normalized, ttl=30 * 60)
            return normalized
        except Exception as exc:
            logger.warning("Daily mix personalized candidates failed for %s: %s", profile["uid"], exc)
            return []

    def _get_collaborative_candidates(self, profile: Dict[str, Any], limit: int) -> List[Dict[str, Any]]:
        cache_key = f"daily_mix_cf:{profile['uid']}:{limit}"
        cached = self.cache.get(cache_key)
        if isinstance(cached, list):
            return cached

        try:
            songs = self.collaborative_service.get_cf_recommendations(profile["uid"])
            normalized = [self._to_song_dto(song) for song in songs[:limit]]
            self.cache.set(cache_key, normalized, ttl=30 * 60)
            return normalized
        except Exception as exc:
            logger.warning("Daily mix collaborative candidates failed for %s: %s", profile["uid"], exc)
            return []

    def _search_songs(self, query: str, limit: int) -> List[Dict[str, Any]]:
        try:
            results = self.jiosaavn_service.search_songs(query, limit=min(limit, 12))
            return [self._to_song_dto(song) for song in results]
        except Exception as exc:
            logger.warning("Daily mix search failed for '%s': %s", query, exc)
            return []

    def _finalize_mix(
        self,
        songs: Sequence[Dict[str, Any]],
        profile: Dict[str, Any],
        limit: int,
    ) -> List[Dict[str, Any]]:
        if not songs:
            return []

        ranked = sorted(
            [self._to_song_dto(song) for song in songs],
            key=lambda song: self._preference_score(song, profile),
            reverse=True,
        )
        return self._ensure_diversity_resilient(
            ranked,
            artist_listen_count={},
            all_consumed=profile.get("consumed_ids", set()),
            exclude_played=True,
            min_threshold=min(self.MIN_MIX_SIZE, limit),
            max_per_artist=self.MAX_SONGS_PER_ARTIST,
            mix_type="daily_mix",
            limit=min(self.MAX_MIX_SIZE, limit),
        )

    def _preference_score(self, song: Dict[str, Any], profile: Dict[str, Any]) -> float:
        score = 0.0
        top_languages = {str(language).lower() for language in profile.get("top_languages") or []}
        song_language = str(song.get("language") or "").lower()
        if song_language and song_language in top_languages:
            score += 3.0

        top_artist_norms = set(profile.get("top_artist_norms", set()))
        song_artists = {self._normalize_artist_name(artist) for artist in self._extract_artists(song)}
        if song_artists & top_artist_norms:
            score += 2.0

        play_count = self._safe_int(song.get("play_count"), default=0)
        score += min(play_count, 500000) / 500000.0
        score += float(song.get("_boost_score") or 0.0)
        return score

    def _load_cached_response(self, uid: str) -> Optional[Dict[str, Any]]:
        cached = self.cache.get(self._response_cache_key(uid))
        if isinstance(cached, dict):
            mood_mix = ((cached.get("mixes") or {}).get("moodMix") or {}) if isinstance(cached.get("mixes"), dict) else {}
            if not self._is_mood_mix_expired(mood_mix):
                return cached

        if not self.db:
            return None

        try:
            mixes_ref = self.db.collection("users").document(uid).collection("daily_mixes")
            docs = [doc.to_dict() for doc in mixes_ref.stream() if doc.exists]
            docs = [doc for doc in docs if isinstance(doc, dict)]
            if len(docs) < 4:
                return None

            mood_doc = next(
                (
                    doc
                    for doc in docs
                    if str(doc.get("key") or "").strip() == "moodMix"
                    or str(doc.get("storage_id") or "").strip() == "mood_mix"
                ),
                None,
            )
            if self._is_mood_mix_expired(mood_doc):
                return None

            generated_at = max(self._safe_int(doc.get("generatedAt"), 0) for doc in docs)
            if generated_at <= 0 or (time.time() - generated_at) > self.CACHE_TTL_SECONDS:
                return None

            mix_map: Dict[str, Dict[str, Any]] = {}
            for doc in docs:
                key = str(doc.get("key") or "").strip()
                if key:
                    mix_map[key] = doc

            if not mix_map:
                return None

            response = self._response_from_mix_map(uid, mix_map, cached=True)
            self.cache.set(self._response_cache_key(uid), response, ttl=self.CACHE_TTL_SECONDS)
            return response
        except Exception as exc:
            logger.warning("Daily mix Firestore cache read failed for %s: %s", uid, exc)
            return None

    def _store_mixes(self, uid: str, mix_map: Dict[str, Dict[str, Any]]) -> None:
        if not self.db:
            return

        generated_at = int(time.time())
        try:
            batch = self.db.batch()
            mixes_ref = self.db.collection("users").document(uid).collection("daily_mixes")
            for mix in mix_map.values():
                storage_id = str(mix.get("storage_id") or mix.get("key"))
                payload = {
                    "key": mix.get("key"),
                    "name": mix.get("name"),
                    "type": mix.get("type"),
                    "description": mix.get("description"),
                    "subtitle": mix.get("subtitle") or mix.get("description"),
                    "timeBlock": mix.get("timeBlock"),
                    "songs": mix.get("songs") or [],
                    "tracks": mix.get("songs") or [],
                    "count": len(mix.get("songs") or []),
                    "generatedAt": generated_at,
                    "source": "daily_mix",
                    "storage_id": storage_id,
                }
                batch.set(mixes_ref.document(storage_id), payload)
            batch.commit()
        except Exception as exc:
            logger.warning("Daily mix Firestore cache write failed for %s: %s", uid, exc)

    def _response_from_mix_map(self, uid: str, mix_map: Dict[str, Dict[str, Any]], cached: bool) -> Dict[str, Any]:
        timestamp = max(self._safe_int(mix.get("generatedAt"), int(time.time())) for mix in mix_map.values())
        mixes_payload = {
            "dailyMix1": self._mix_response_entry(mix_map.get("dailyMix1")),
            "dailyMix2": self._mix_response_entry(mix_map.get("dailyMix2")),
            "discoverMix": self._mix_response_entry(mix_map.get("dailyMix3")),
            "moodMix": self._mix_response_entry(mix_map.get("moodMix")),
        }
        mix_list = [entry for entry in mixes_payload.values() if entry]
        return {
            "userId": uid,
            "timestamp": timestamp,
            "cached": cached,
            "mixes": mixes_payload,
            "mixList": mix_list,
        }

    def _mix_response_entry(self, mix: Optional[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        if not mix:
            return None
        songs = mix.get("songs") or mix.get("tracks") or []
        subtitle = mix.get("subtitle") or mix.get("description") or ""
        time_block = mix.get("timeBlock") or mix.get("time_block")
        return {
            "name": mix.get("name"),
            "type": mix.get("type"),
            "description": subtitle,
            "subtitle": subtitle,
            "time_block": time_block,
            "count": len(songs),
            "songs": songs,
            "tracks": songs,
            "generatedAt": mix.get("generatedAt"),
            "source": "daily_mix",
        }

    def _is_mood_mix_expired(self, mood_doc: Optional[Dict[str, Any]]) -> bool:
        if not isinstance(mood_doc, dict):
            return True

        generated_at = self._safe_int(mood_doc.get("generatedAt"), 0)
        if generated_at <= 0:
            return True
        if (time.time() - generated_at) > self.MOOD_MIX_TTL_SECONDS:
            return True

        stored_time_block = str(mood_doc.get("timeBlock") or mood_doc.get("time_block") or "").strip().lower()
        return stored_time_block != self._current_time_block()

    def _empty_response(self, uid: str) -> Dict[str, Any]:
        return {
            "userId": uid,
            "timestamp": int(time.time()),
            "cached": False,
            "mixes": {
                "dailyMix1": None,
                "dailyMix2": None,
                "discoverMix": None,
                "moodMix": None,
            },
            "mixList": [],
        }

    def _response_cache_key(self, uid: str) -> str:
        return f"daily_mix_response:{uid}"

    def _entry_timestamp(self, entry: Dict[str, Any]) -> float:
        value = entry.get("timestamp") or entry.get("playedAt") or entry.get("likedAt") or entry.get("lastPlayedAt")
        if hasattr(value, "timestamp"):
            try:
                return float(value.timestamp())
            except Exception:
                return 0.0
        try:
            return float(value)
        except Exception:
            return 0.0

    def _entry_weight(self, entry: Dict[str, Any], timestamp: float, liked: bool) -> float:
        days_since = max(0.0, (time.time() - timestamp) / 86400.0) if timestamp else 365.0
        play_count = self._safe_int(
            entry.get("play_count") or entry.get("playCount") or entry.get("user_play_count") or 1,
            default=1,
        )
        base_weight = float(play_count) * math.exp(-self.decay_lambda * days_since)
        if liked:
            base_weight *= 2.0
        return base_weight

    def _accumulate_artist_scores(
        self,
        entry: Dict[str, Any],
        weight: float,
        scores: Counter[str],
        display_names: Dict[str, str],
    ) -> None:
        for artist in self._extract_artists(entry):
            normalized = self._normalize_artist_name(artist)
            if normalized:
                scores[normalized] += weight
                display_names.setdefault(normalized, artist.strip())

    def _accumulate_language(self, entry: Dict[str, Any], weight: float, scores: Counter[str]) -> None:
        language = str(entry.get("language") or "").strip().lower()
        if language:
            scores[language] += weight

    def _normalize_artist_name(self, name: str) -> str:
        if hasattr(self.user_profile_service, "_normalize_artist_name"):
            return self.user_profile_service._normalize_artist_name(name)
        return str(name or "").strip().lower()

    def _extract_artists(self, song: Dict[str, Any]) -> List[str]:
        artists = song.get("artists") or song.get("artist") or song.get("primary_artists") or song.get("singers") or []
        results: List[str] = []
        if isinstance(artists, list):
            for item in artists:
                if isinstance(item, dict):
                    name = item.get("name") or item.get("artist") or ""
                else:
                    name = str(item)
                if name.strip():
                    results.append(name.strip())
        else:
            for item in str(artists).split(","):
                cleaned = item.strip()
                if cleaned:
                    results.append(cleaned)
        return results

    def _song_id(self, song: Dict[str, Any]) -> str:
        return str(song.get("videoId") or song.get("id") or "").strip()

    def _song_ids(self, songs: Sequence[Dict[str, Any]]) -> Set[str]:
        return {self._song_id(song) for song in songs if self._song_id(song)}

    def _song_identity(self, song: Dict[str, Any]) -> str:
        title = str(song.get("title") or song.get("song") or song.get("name") or "").strip().lower()
        primary_artist = ""
        artists = self._extract_artists(song)
        if artists:
            primary_artist = self._normalize_artist_name(artists[0])
        return f"{title}|{primary_artist}".strip("|")

    def _to_song_dto(self, song: Dict[str, Any]) -> Dict[str, Any]:
        artists = self._extract_artists(song)
        image = song.get("thumbnail") or song.get("image") or ""
        title = str(song.get("title") or song.get("song") or song.get("name") or "").strip()
        video_id = self._song_id(song)
        album = song.get("album")
        if isinstance(album, dict):
            album_value = album.get("name") or album.get("title") or ""
        else:
            album_value = album or ""

        return {
            "videoId": video_id,
            "id": video_id,
            "title": title,
            "song": title,
            "artist": ", ".join(artists) if artists else None,
            "artists": artists or None,
            "singers": song.get("singers") or (", ".join(artists) if artists else None),
            "thumbnail": image or None,
            "image": image or None,
            "duration": str(song.get("duration") or ""),
            "url": song.get("url") or song.get("media_url"),
            "media_url": song.get("media_url") or song.get("url"),
            "album": album_value or None,
            "artistId": song.get("artistId"),
            "play_count": self._safe_int(song.get("play_count"), default=0),
            "language": song.get("language"),
            "year": str(song.get("year") or "") or None,
            "starring": song.get("starring"),
        }

    def _safe_int(self, value: Any, default: int = 0) -> int:
        try:
            return int(value)
        except Exception:
            return default

    def _ensure_diversity_resilient(
        self,
        songs: Sequence[Dict[str, Any]],
        artist_listen_count: Dict[str, int],
        all_consumed: Set[str],
        exclude_played: bool = True,
        min_threshold: int = 15,
        max_per_artist: int = 2,
        mix_type: str = "daily_mix",
        limit: Optional[int] = None,
    ) -> List[Dict[str, Any]]:
        del artist_listen_count
        del mix_type

        target_limit = limit or self.MIX_SIZE
        normalized = [self._to_song_dto(song) for song in songs]
        canonical = resolve_canonical_tracks(normalized)

        unique_candidates: List[Dict[str, Any]] = []
        seen_ids: Set[str] = set()
        seen_identity: Set[str] = set()
        for song in canonical:
            song_id = self._song_id(song)
            if exclude_played and song_id and song_id in all_consumed:
                continue
            identity = self._song_identity(song)
            if song_id and song_id in seen_ids:
                continue
            if identity and identity in seen_identity:
                continue
            if song_id:
                seen_ids.add(song_id)
            if identity:
                seen_identity.add(identity)
            unique_candidates.append(song)

        if not unique_candidates:
            return []

        unique_artists_available = {
            self._normalize_artist_name(self._extract_artists(song)[0])
            for song in unique_candidates
            if self._extract_artists(song)
        }
        unique_target = min(self.MIN_UNIQUE_ARTISTS, len(unique_artists_available), target_limit)

        result: List[Dict[str, Any]] = []
        selected_ids: Set[str] = set()
        artist_counts: Counter[str] = Counter()
        album_counts: Counter[str] = Counter()

        for song in unique_candidates:
            if len(result) >= unique_target:
                break
            artists = self._extract_artists(song)
            primary_artist = self._normalize_artist_name(artists[0]) if artists else "unknown"
            album = str(song.get("album") or "unknown").strip().lower()
            song_id = self._song_id(song)
            if not primary_artist or artist_counts[primary_artist] >= 1:
                continue
            if album_counts[album] >= self.MAX_SONGS_PER_ALBUM:
                continue
            result.append(song)
            selected_ids.add(song_id)
            artist_counts[primary_artist] += 1
            album_counts[album] += 1

        for song in unique_candidates:
            if len(result) >= min(target_limit, self.MAX_MIX_SIZE):
                break
            song_id = self._song_id(song)
            if song_id in selected_ids:
                continue
            artists = self._extract_artists(song)
            primary_artist = self._normalize_artist_name(artists[0]) if artists else "unknown"
            album = str(song.get("album") or "unknown").strip().lower()
            if artist_counts[primary_artist] >= max_per_artist:
                continue
            if album_counts[album] >= self.MAX_SONGS_PER_ALBUM:
                continue
            result.append(song)
            selected_ids.add(song_id)
            artist_counts[primary_artist] += 1
            album_counts[album] += 1

        if len(result) < min_threshold:
            for song in unique_candidates:
                if len(result) >= min(target_limit, self.MAX_MIX_SIZE):
                    break
                song_id = self._song_id(song)
                if song_id in selected_ids:
                    continue
                result.append(song)
                selected_ids.add(song_id)

        return result[: min(target_limit, self.MAX_MIX_SIZE)]
