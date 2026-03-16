"""
Collaborative Filtering Service for AURA.

User-based CF built on Firestore listening history and JioSaavn metadata.
"""

from __future__ import annotations

import logging
import math
import time
from collections import defaultdict
from typing import Any, Dict, List, Optional, Set, Tuple

from services.cache_manager import get_cache
from services.canonical_track_resolver import normalize_song_identity, resolve_canonical_tracks
from services.jiosaavn_service import search_songs
from services.user_service import get_firestore_client

logger = logging.getLogger(__name__)

try:
    from services.personalized_recommender import normalize_artist_name as _normalize_artist_name_shared
except Exception:  # pragma: no cover
    _normalize_artist_name_shared = None

try:
    from services.artist_normalization import normalized_artist_key as _normalized_artist_key
except Exception:  # pragma: no cover
    _normalized_artist_key = None


class CollaborativeFilteringService:
    MIN_USER_PLAYS = 5
    TOP_SIMILAR_USERS = 12
    MAX_RECOMMENDATIONS = 20
    MIN_RECOMMENDATIONS = 5

    CACHE_TTL_USER_PROFILE = 30 * 60
    CACHE_TTL_SIMILARITY = 3 * 3600
    CACHE_TTL_RECOMMENDATIONS = 30 * 60

    def __init__(self, user_service, recommendation_service=None, decay_lambda: float = 0.15):
        self.user_service = user_service
        self.recommendation_service = recommendation_service
        self.decay_lambda = decay_lambda
        self.cache = get_cache()
        self._db = getattr(user_service, "db", None) or get_firestore_client()

    def _normalize_artist_name(self, name: str) -> str:
        # Internal comparison key only (never for display).
        if _normalized_artist_key:
            return _normalized_artist_key(name)
        if _normalize_artist_name_shared:
            return _normalize_artist_name_shared(name)
        value = str(name or "").strip().lower()
        return value

    def _song_id(self, song: Dict[str, Any]) -> str:
        return str(song.get("id") or song.get("videoId") or "").strip()

    def _song_title(self, song: Dict[str, Any]) -> str:
        return str(song.get("title") or song.get("song") or song.get("name") or "").strip()

    def _extract_artists(self, song: Dict[str, Any]) -> List[str]:
        artists_raw = song.get("artists") or song.get("artist") or song.get("primary_artists") or []
        names: List[str] = []

        if isinstance(artists_raw, list):
            for artist in artists_raw:
                if isinstance(artist, dict):
                    name = artist.get("name") or artist.get("artist") or ""
                else:
                    name = str(artist)
                normalized = self._normalize_artist_name(name)
                if normalized:
                    names.append(normalized)
        else:
            for name in str(artists_raw).split(","):
                normalized = self._normalize_artist_name(name)
                if normalized:
                    names.append(normalized)

        deduped: List[str] = []
        seen = set()
        for name in names:
            if name not in seen:
                seen.add(name)
                deduped.append(name)
        return deduped

    def _normalize_track_schema(self, song: Dict[str, Any]) -> Dict[str, Any]:
        """Ensure payload shape remains Android SongDto-compatible."""
        if not isinstance(song, dict):
            return {}

        artists_raw = song.get("artists")
        artists: List[str] = []
        if isinstance(artists_raw, list):
            for item in artists_raw:
                if isinstance(item, dict):
                    name = item.get("name") or item.get("artist") or ""
                else:
                    name = str(item)
                normalized = self._normalize_artist_name(name)
                if normalized:
                    artists.append(normalized)
        elif isinstance(artists_raw, str):
            for name in artists_raw.split(","):
                normalized = self._normalize_artist_name(name)
                if normalized:
                    artists.append(normalized)

        if not artists:
            artists = self._extract_artists(song)

        image = song.get("image") or song.get("thumbnail") or ""
        media_url = song.get("media_url") or song.get("url") or ""
        title = self._song_title(song)
        song_id = self._song_id(song)
        primary_artists = ", ".join(artists)

        normalized_song = dict(song)
        normalized_song.update(
            {
                "id": song_id,
                "videoId": song_id,
                "song": title,
                "title": title,
                "artists": artists,
                "primary_artists": primary_artists,
                "artist": primary_artists,
                "image": image,
                "thumbnail": image,
                "url": media_url,
                "media_url": media_url,
            }
        )
        return normalized_song

    def _to_epoch(self, value: Any) -> float:
        if value is None:
            return 0.0
        if hasattr(value, "timestamp"):
            try:
                return float(value.timestamp())
            except Exception:
                return 0.0
        if isinstance(value, (int, float)):
            return float(value)
        return 0.0

    def _days_since(self, ts: float) -> float:
        if ts <= 0:
            return 365.0
        return max(0.0, (time.time() - ts) / 86400.0)

    def _recency_boost(self, song: Dict[str, Any]) -> float:
        ts = self._to_epoch(
            song.get("lastPlayedAt")
            or song.get("last_played_at")
            or song.get("playedAt")
            or song.get("timestamp")
        )
        days = self._days_since(ts)
        return math.exp(-self.decay_lambda * days)

    def _safe_int(self, value: Any, default: int = 0) -> int:
        try:
            return int(value)
        except Exception:
            return default

    def _fetch_user_plays(self, uid: str) -> List[Dict[str, Any]]:
        if not self._db:
            return []
        plays: List[Dict[str, Any]] = []
        try:
            plays_ref = self._db.collection("users").document(uid).collection("plays")
            for doc in plays_ref.stream():
                data = doc.to_dict() or {}
                if data:
                    plays.append(data)
        except Exception as exc:
            logger.warning("[CF] plays read failed uid=%s: %s", uid, exc)
        return plays

    def _get_total_users(self) -> int:
        key = "cf_total_users"
        cached = self.cache.get(key)
        if cached is not None:
            return int(cached)

        if not self._db:
            return 0

        count = 0
        try:
            users_ref = self._db.collection("users")
            count = sum(1 for _ in users_ref.stream())
        except Exception:
            count = 0

        self.cache.set(key, count, ttl=10 * 60)
        return count

    def _adaptive_threshold(self, total_users: int) -> float:
        if total_users < 20:
            return 0.10
        if total_users < 50:
            return 0.15
        if total_users < 100:
            return 0.25
        return 0.35

    def _required_overlap(self, total_users: int) -> int:
        return 1 if total_users < 20 else 2

    def _song_record_to_cf_track(self, song: Dict[str, Any]) -> Dict[str, Any]:
        artists = self._extract_artists(song)
        primary_artists = ", ".join(artists)
        song_id = self._song_id(song)
        image = song.get("image") or song.get("thumbnail") or ""
        media_url = song.get("media_url") or song.get("url") or ""

        return {
            "id": song_id,
            "videoId": song_id,
            "song": self._song_title(song),
            "title": self._song_title(song),
            "artists": artists,
            "primary_artists": primary_artists,
            "artist": primary_artists,
            "album": song.get("album") or "",
            "image": image,
            "thumbnail": image,
            "duration": song.get("duration") or "",
            "url": media_url,
            "media_url": media_url,
            "language": song.get("language") or "",
            "year": song.get("year") or "",
            "play_count": self._safe_int(song.get("global_play_count") or song.get("play_count") or song.get("user_play_count"), 0),
            "global_play_count": self._safe_int(song.get("global_play_count") or song.get("play_count") or song.get("user_play_count"), 0),
            "lastPlayedAt": song.get("lastPlayedAt") or song.get("playedAt") or song.get("timestamp"),
        }

    def _build_user_taste_vector(self, uid: str) -> Dict[str, float]:
        cache_key = f"user_profile_{uid}"
        cached = self.cache.get(cache_key)
        if isinstance(cached, dict) and "vector" in cached:
            return cached.get("vector", {})

        plays = self._fetch_user_plays(uid)
        artist_scores: Dict[str, float] = defaultdict(float)

        for song in plays:
            artists = self._extract_artists(song)
            if not artists:
                continue

            base_weight = float(
                song.get("user_play_count")
                or song.get("play_count")
                or 1
            )
            weight = base_weight * self._recency_boost(song)
            for artist in artists:
                artist_scores[artist] += weight

        if not artist_scores:
            meta = {"plays_count": len(plays), "artist_count": 0}
            self.cache.set(cache_key, {"vector": {}, "meta": meta}, ttl=self.CACHE_TTL_USER_PROFILE)
            return {}

        max_score = max(artist_scores.values()) or 1.0
        vector = {k: v / max_score for k, v in artist_scores.items()}

        vector = dict(sorted(vector.items(), key=lambda it: it[1], reverse=True)[:50])
        meta = {"plays_count": len(plays), "artist_count": len(vector)}
        self.cache.set(cache_key, {"vector": vector, "meta": meta}, ttl=self.CACHE_TTL_USER_PROFILE)
        return vector

    def _profile_meta(self, uid: str) -> Dict[str, Any]:
        cached = self.cache.get(f"user_profile_{uid}")
        if isinstance(cached, dict):
            return cached.get("meta", {}) or {}
        return {}

    def _candidate_user_ids(self, uid: str) -> List[str]:
        if not self._db:
            return []

        users: List[str] = []
        try:
            users_ref = self._db.collection("users")
            for doc in users_ref.limit(200).stream():
                if doc.id != uid:
                    users.append(doc.id)
        except Exception as exc:
            logger.warning("[CF] user scan failed: %s", exc)
        return users

    def _jaccard_similarity(self, vec_a: Dict[str, float], vec_b: Dict[str, float]) -> float:
        set_a = set(vec_a.keys())
        set_b = set(vec_b.keys())
        if not set_a or not set_b:
            return 0.0
        return len(set_a & set_b) / len(set_a | set_b)

    def _cosine_similarity(self, vec_a: Dict[str, float], vec_b: Dict[str, float]) -> float:
        common = set(vec_a.keys()) & set(vec_b.keys())
        if not common:
            return 0.0
        dot = sum(vec_a[k] * vec_b[k] for k in common)
        mag_a = math.sqrt(sum(v * v for v in vec_a.values()))
        mag_b = math.sqrt(sum(v * v for v in vec_b.values()))
        if mag_a <= 0 or mag_b <= 0:
            return 0.0
        return max(0.0, min(1.0, dot / (mag_a * mag_b)))

    def _soft_overlap_similarity(self, vec_a: Dict[str, float], vec_b: Dict[str, float]) -> float:
        set_a = set(vec_a.keys())
        set_b = set(vec_b.keys())
        if not set_a or not set_b:
            return 0.0
        shared = len(set_a & set_b)
        total = len(set_a | set_b)
        if total == 0:
            return 0.0
        return shared / total

    def _pair_similarity(self, uid_a: str, uid_b: str, vec_a: Dict[str, float], vec_b: Dict[str, float], total_users: int) -> float:
        pair_key = "_".join(sorted([uid_a, uid_b]))
        cache_key = f"similarity_{pair_key}"
        cached = self.cache.get(cache_key)
        if cached is not None:
            return float(cached)

        cosine = self._cosine_similarity(vec_a, vec_b)
        jaccard = self._jaccard_similarity(vec_a, vec_b)
        soft = self._soft_overlap_similarity(vec_a, vec_b)

        similarity = max(cosine, (0.60 * cosine) + (0.25 * jaccard) + (0.15 * soft))
        if total_users < 20:
            similarity *= 1.2

        similarity = max(0.0, min(1.0, similarity))
        self.cache.set(cache_key, similarity, ttl=self.CACHE_TTL_SIMILARITY)
        return similarity

    def _find_similar_users(self, uid: str, user_profile: Dict[str, float], threshold: Optional[float] = None) -> List[Tuple[str, float]]:
        total_users = self._get_total_users()
        min_overlap = self._required_overlap(total_users)
        min_threshold = threshold if threshold is not None else self._adaptive_threshold(total_users)

        similar: List[Tuple[str, float]] = []
        for other_uid in self._candidate_user_ids(uid):
            other_profile = self._build_user_taste_vector(other_uid)
            if not other_profile:
                continue

            shared = len(set(user_profile.keys()) & set(other_profile.keys()))
            if shared < min_overlap:
                continue

            score = self._pair_similarity(uid, other_uid, user_profile, other_profile, total_users)
            if score >= min_threshold:
                similar.append((other_uid, score))

        similar.sort(key=lambda it: it[1], reverse=True)
        return similar[: self.TOP_SIMILAR_USERS]

    def _build_exclude_ids(self, uid: str, exclude_video_ids: Optional[Set[str]], main_rec_ids: Optional[Set[str]]) -> Set[str]:
        ids = set(exclude_video_ids or set())
        ids |= set(main_rec_ids or set())
        for song in self._fetch_user_plays(uid):
            sid = self._song_id(song)
            if sid:
                ids.add(sid)
        return ids

    def _generate_candidates(self, similar_users: List[Tuple[str, float]], exclude_ids: Set[str]) -> List[Tuple[float, Dict[str, Any], str]]:
        score_by_identity: Dict[str, float] = defaultdict(float)
        best_track_by_identity: Dict[str, Dict[str, Any]] = {}
        artist_track_count: Dict[str, int] = defaultdict(int)

        for other_uid, similarity in similar_users:
            plays = self._fetch_user_plays(other_uid)
            artist_seen_local: Dict[str, int] = defaultdict(int)

            for play in plays:
                sid = self._song_id(play)
                if not sid or sid in exclude_ids:
                    continue

                cf_track = self._song_record_to_cf_track(play)
                artists = self._extract_artists(cf_track)
                if not artists:
                    continue
                primary_artist = artists[0]

                if artist_track_count[primary_artist] >= 5:
                    continue

                play_count = max(1, self._safe_int(play.get("user_play_count") or play.get("play_count"), 1))
                recency = self._recency_boost(play)

                artist_seen_local[primary_artist] += 1
                per_artist_occ = artist_seen_local[primary_artist]
                artist_penalty = 1.0 if per_artist_occ == 1 else 1.0 / (1.0 + 0.30 * (per_artist_occ - 1))

                score = similarity * play_count * recency * artist_penalty
                identity = normalize_song_identity(cf_track.get("title"), cf_track.get("artists") or cf_track.get("primary_artists") or "")
                if not identity:
                    identity = sid

                score_by_identity[identity] += score
                if identity not in best_track_by_identity or score > score_by_identity.get(identity, 0):
                    best_track_by_identity[identity] = cf_track

                artist_track_count[primary_artist] += 1

        if not best_track_by_identity:
            return []

        raw_tracks = list(best_track_by_identity.values())
        canonical_tracks = resolve_canonical_tracks(raw_tracks)
        canonical_ids = {self._song_id(track) for track in canonical_tracks if self._song_id(track)}

        candidates: List[Tuple[float, Dict[str, Any], str]] = []
        for identity, track in best_track_by_identity.items():
            sid = self._song_id(track)
            if canonical_ids and sid and sid not in canonical_ids:
                continue
            artists = self._extract_artists(track)
            primary_artist = artists[0] if artists else "unknown"
            candidates.append((score_by_identity[identity], track, primary_artist))

        candidates.sort(key=lambda row: row[0], reverse=True)
        return candidates

    def _diversity_targets(self, total_users: int) -> Tuple[int, int]:
        if total_users < 20:
            return 2, 4
        if total_users < 50:
            return 1, 6
        return 1, 8

    def _apply_diversity(self, candidates: List[Tuple[float, Dict[str, Any], str]], total_users: int) -> List[Dict[str, Any]]:
        if not candidates:
            return []

        max_per_artist, min_unique_artists = self._diversity_targets(total_users)
        penalty_curve = [1.0, 0.74, 0.59]

        grouped: Dict[str, List[Tuple[float, Dict[str, Any]]]] = defaultdict(list)
        for score, track, artist in candidates:
            grouped[artist].append((score, track))

        for artist in grouped:
            grouped[artist].sort(key=lambda x: x[0], reverse=True)

        ordered_artists = sorted(grouped.keys(), key=lambda a: grouped[a][0][0], reverse=True)
        artist_picks: Dict[str, int] = defaultdict(int)
        idx_by_artist: Dict[str, int] = defaultdict(int)

        selected: List[Dict[str, Any]] = []

        while len(selected) < self.MAX_RECOMMENDATIONS:
            added = False
            for artist in ordered_artists:
                if artist_picks[artist] >= max_per_artist:
                    continue
                idx = idx_by_artist[artist]
                if idx >= len(grouped[artist]):
                    continue

                raw_score, track = grouped[artist][idx]
                idx_by_artist[artist] += 1

                penalty_idx = min(artist_picks[artist], len(penalty_curve) - 1)
                adjusted_score = raw_score * penalty_curve[penalty_idx]

                candidate = dict(track)
                candidate["cf_score"] = round(adjusted_score, 4)
                selected.append(candidate)
                artist_picks[artist] += 1
                added = True

                if len(selected) >= self.MAX_RECOMMENDATIONS:
                    break

            if not added:
                break

        if not selected:
            return []

        def _artist_of(track: Dict[str, Any]) -> str:
            artists = self._extract_artists(track)
            return artists[0] if artists else "unknown"

        artist_distribution: Dict[str, int] = defaultdict(int)
        for track in selected:
            artist_distribution[_artist_of(track)] += 1

        dominant_artist = None
        dominant_count = 0
        for artist, count in artist_distribution.items():
            if count > dominant_count:
                dominant_artist = artist
                dominant_count = count

        if dominant_artist and dominant_count > max(1, int(0.5 * len(selected))):
            rebalanced: List[Dict[str, Any]] = []
            dominant_kept = 0
            max_dominant = max(1, int(0.5 * len(selected)))
            for track in selected:
                artist = _artist_of(track)
                if artist == dominant_artist:
                    if dominant_kept < max_dominant:
                        rebalanced.append(track)
                        dominant_kept += 1
                else:
                    rebalanced.append(track)
            selected = rebalanced

        unique_artists = {_artist_of(track) for track in selected if _artist_of(track) != "unknown"}
        if len(unique_artists) < min_unique_artists:
            for _, track, artist in candidates:
                if artist in unique_artists:
                    continue
                if any(self._song_id(t) == self._song_id(track) for t in selected):
                    continue
                selected.append(dict(track))
                unique_artists.add(artist)
                if len(unique_artists) >= min_unique_artists or len(selected) >= self.MAX_RECOMMENDATIONS:
                    break

        return selected[: self.MAX_RECOMMENDATIONS]

    def _cold_start_fallback(self, uid: str, exclude_ids: Set[str]) -> List[Dict[str, Any]]:
        languages = []
        try:
            languages = [str(l).strip().lower() for l in (self.user_service.get_user_languages(uid) or []) if str(l).strip()]
        except Exception:
            languages = []

        queries: List[str] = []
        for lang in languages[:3]:
            queries.append(f"{lang} trending songs")
        if not queries:
            queries = ["hindi trending songs", "telugu trending songs", "english trending songs"]

        candidates: List[Dict[str, Any]] = []
        seen: Set[str] = set()

        for query in queries:
            try:
                for track in search_songs(query, limit=20, include_full_data=True) or []:
                    sid = self._song_id(track)
                    if not sid or sid in exclude_ids or sid in seen:
                        continue
                    seen.add(sid)
                    candidates.append(track)
            except Exception as exc:
                logger.warning("[CF] cold-start query failed (%s): %s", query, exc)

        resolved = resolve_canonical_tracks(candidates)
        output = []
        for track in resolved:
            sid = self._song_id(track)
            if not sid or sid in exclude_ids:
                continue
            item = self._song_record_to_cf_track(track)
            item["cf_score"] = 0.05
            output.append(item)
            if len(output) >= self.MAX_RECOMMENDATIONS:
                break

        return output

    def get_cf_recommendations(
        self,
        uid: str,
        exclude_video_ids: Optional[Set[str]] = None,
        main_rec_ids: Optional[Set[str]] = None,
    ) -> List[Dict[str, Any]]:
        if not uid:
            return []

        cache_key = f"cf_recommendations_{uid}"
        cached = self.cache.get(cache_key)
        if isinstance(cached, list) and cached:
            runtime_exclude = self._build_exclude_ids(uid, exclude_video_ids, main_rec_ids)
            normalized_cached = [self._normalize_track_schema(s) for s in cached]
            return [s for s in normalized_cached if self._song_id(s) not in runtime_exclude]

        user_profile = self._build_user_taste_vector(uid)
        profile_meta = self._profile_meta(uid)
        plays_count = int(profile_meta.get("plays_count", 0))

        exclude_ids = self._build_exclude_ids(uid, exclude_video_ids, main_rec_ids)

        total_users = self._get_total_users()

        if plays_count < self.MIN_USER_PLAYS or not user_profile:
            recs = self._cold_start_fallback(uid, exclude_ids)
            # Guarantee minimum recommendations with fallback, deduplication
            seen_ids = {self._song_id(r) for r in recs}
            while len(recs) < self.MIN_RECOMMENDATIONS:
                more = [r for r in self._cold_start_fallback(uid, exclude_ids) if self._song_id(r) not in seen_ids]
                if not more:
                    break
                for r in more:
                    sid = self._song_id(r)
                    if sid and sid not in seen_ids:
                        recs.append(r)
                        seen_ids.add(sid)
                    if len(recs) >= self.MIN_RECOMMENDATIONS:
                        break
            recs = recs[: self.MAX_RECOMMENDATIONS]
            self.cache.set(cache_key, recs, ttl=self.CACHE_TTL_RECOMMENDATIONS)
            return recs

        similar_users = self._find_similar_users(uid, user_profile)

        if not similar_users:
            recs = self._cold_start_fallback(uid, exclude_ids)
            self.cache.set(cache_key, recs, ttl=self.CACHE_TTL_RECOMMENDATIONS)
            return recs

        candidates = self._generate_candidates(similar_users, exclude_ids)
        recs = self._apply_diversity(candidates, total_users)

        if len(recs) < self.MIN_RECOMMENDATIONS:
            # Guarantee minimum recommendations with fallback, deduplication
            seen_ids = {self._song_id(r) for r in recs}
            while len(recs) < self.MIN_RECOMMENDATIONS:
                more = [r for r in self._cold_start_fallback(uid, exclude_ids) if self._song_id(r) not in seen_ids]
                if not more:
                    break
                for r in more:
                    sid = self._song_id(r)
                    if sid and sid not in seen_ids:
                        recs.append(r)
                        seen_ids.add(sid)
                    if len(recs) >= self.MIN_RECOMMENDATIONS:
                        break

        recs = [self._normalize_track_schema(s) for s in recs]
        recs = [s for s in recs if self._song_id(s) not in exclude_ids]
        recs = recs[: self.MAX_RECOMMENDATIONS]
        # Guarantee at least one recommendation (fallback to trending if needed)
        if not recs:
            fallback = self._cold_start_fallback(uid, set())
            if fallback:
                recs = [self._normalize_track_schema(fallback[0])]
        self.cache.set(cache_key, recs, ttl=self.CACHE_TTL_RECOMMENDATIONS)
        return recs

    def get_user_stats(self, uid: str) -> Dict[str, Any]:
        profile = self._build_user_taste_vector(uid)
        meta = self._profile_meta(uid)
        similar = self._find_similar_users(uid, profile) if profile else []
        return {
            "has_profile": bool(profile),
            "plays_count": int(meta.get("plays_count", 0)),
            "artist_count": int(meta.get("artist_count", 0)),
            "similar_users_count": len(similar),
            "top_artists": list(profile.keys())[:5],
            "similarity_scores": [round(score, 4) for _, score in similar[:5]],
        }
