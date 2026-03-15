"""
Personalized Music Recommender for AURA.

Combines content-based filtering with weighted signals from a user's
Firestore listening metadata (plays + liked songs).
Uses the JioSaavn API for candidate generation — no external ML services.

Pipeline
--------
1. build_user_profile  — fetch Firestore data, build weighted taste vectors
2. generate_candidates — parallel JioSaavn searches (≤5 concurrent calls)
3. resolve_canonical_tracks — pick original album/movie versions
4. score_candidates    — weighted formula per candidate
5. apply_diversity     — max 2/artist, max 3/album
6. get_recommendations — orchestrates the above + cache + cold-start
"""

import concurrent.futures
import logging
import math
import os
import re
import time
from typing import Dict, List, Optional, Set, Tuple

from services.cache_manager import get_cache
from services.canonical_track_resolver import (
    resolve_canonical_tracks,
    FROM_MOVIE_RE,
    filter_from_movie_variants,
)
from services.jiosaavn_service import search_songs
from services.user_service import get_firestore_client

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# λ for exp(-λ * days); 1 week ≈ 35%, 1 month ≈ 1%
# Override at runtime: RECOMMENDER_DECAY_LAMBDA=0.10 python app.py
LAMBDA_DECAY: float = float(os.environ.get("RECOMMENDER_DECAY_LAMBDA", "0.15"))
CANDIDATE_POOL_SIZE = 100    # hard cap before scoring

# Signal weights when building the taste profile
LIKED_WEIGHT = 2.0           # liked songs carry 2× the weight of a single play
PLAY_WEIGHT = 1.0            # plays: weight = PLAY_WEIGHT * user_play_count

# Candidate scoring formula weights
W_ARTIST = 3.0
W_ALBUM = 2.0
W_STARRING = 1.5
W_LANGUAGE = 1.0
W_POPULARITY = 0.5

# Diversity constraints
MAX_PER_ARTIST = 2
MAX_PER_ALBUM = 3
MAX_PER_STARRING = 2

# Cold-start threshold (fewer plays → use trending fallback)
COLD_START_THRESHOLD = 3

# Cache TTLs (seconds)
CACHE_TTL_WARM = 1800   # 30 min for users with history
CACHE_TTL_COLD = 900    # 15 min for cold-start users
RECOMMENDER_CACHE_VERSION = "v4"


# Artist alias canonicalization for known problematic variants.
# O(1) lookup to keep normalization lightweight.
ARTIST_ALIASES: Dict[str, str] = {
    "ss thaman": "s thaman",
    "s s thaman": "s thaman",
    "thaman s": "s thaman",
}


# ---------------------------------------------------------------------------
# Internal song-field helpers (Firestore docs + JioSaavn responses)
# ---------------------------------------------------------------------------

def _ts(field) -> float:
    """Convert a Firestore Timestamp / int / float to POSIX seconds."""
    if hasattr(field, "timestamp"):
        return field.timestamp()
    if isinstance(field, (int, float)):
        return float(field)
    return 0.0


def _recency_weight(ts_seconds: float, base: float) -> float:
    """Return base * exp(-λ * days_since).  Returns base*0.5 if no timestamp."""
    if ts_seconds <= 0:
        return base * 0.5
    days = (time.time() - ts_seconds) / 86400.0
    return base * math.exp(-LAMBDA_DECAY * days)


def normalize_artist_name(name: str) -> str:
    """
    Canonicalize artist names for internal recommendation logic.

    Steps
    -----
    1) lowercase
    2) remove punctuation (.,-_) and collapse spaces
    3) token-sort for stable ordering
    4) map known aliases to canonical names
    """
    raw = str(name or "").strip().lower()
    if not raw:
        return ""

    # Keep processing cheap and deterministic: simple regex and split/join.
    cleaned = re.sub(r"[\.,\-_]", " ", raw)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if not cleaned:
        return ""

    tokens = [t for t in cleaned.split(" ") if t]
    if not tokens:
        return ""

    normalized = " ".join(sorted(tokens))
    return ARTIST_ALIASES.get(normalized, normalized)


def _artists(song: Dict) -> List[str]:
    """Extract canonical artist tokens from song metadata for internal logic."""
    raw_names: List[str] = []

    # Common fields from Firestore/JioSaavn payloads.
    for field in ("primary_artists", "artist", "singers"):
        v = song.get(field)
        if isinstance(v, list):
            for item in v:
                s = str(item).strip()
                if s:
                    raw_names.append(s)
        else:
            for piece in str(v or "").split(","):
                s = piece.strip()
                if s:
                    raw_names.append(s)

    # Optional explicit artists[] payload (strings or objects).
    artists_field = song.get("artists")
    if isinstance(artists_field, list):
        for item in artists_field:
            if isinstance(item, dict):
                s = str(item.get("name") or item.get("artist") or "").strip()
            else:
                s = str(item).strip()
            if s:
                raw_names.append(s)

    canonical: List[str] = []
    seen: Set[str] = set()
    for name in raw_names:
        norm = normalize_artist_name(name)
        if norm and norm not in seen:
            canonical.append(norm)
            seen.add(norm)

    return canonical


def _album(song: Dict) -> str:
    """Extract album name and normalize (lowercase, trim)."""
    a = song.get("album", "")
    if isinstance(a, dict):
        name = str(a.get("name", "")).strip().lower()
    else:
        name = str(a or "").strip().lower()
    return name


def _starring(song: Dict) -> List[str]:
    """Extract and normalize starring actor names for internal logic."""
    v = song.get("starring") or ""
    raw_names = [s.strip() for s in str(v).split(",") if s.strip()]
    normalized: List[str] = []
    seen: Set[str] = set()
    for name in raw_names:
        norm = name.lower()  # Simple normalization for starring
        if norm and norm not in seen:
            normalized.append(norm)
            seen.add(norm)
    return normalized


def _language(song: Dict) -> str:
    return str(song.get("language") or "").strip().lower()


# ---------------------------------------------------------------------------
# 1. build_user_profile
# ---------------------------------------------------------------------------

def build_user_profile(uid: str) -> Optional[Dict]:
    """
    Fetch the user's play history and liked songs from Firestore.
    Return a profile dict with weighted preference vectors, or None if no data.

    Schema of returned dict
    -----------------------
    top_artists  : List[Tuple[str, float]]  (name_lower, weight) sorted desc
    top_albums   : List[Tuple[str, float]]
    top_languages: List[Tuple[str, float]]
    top_starring : List[Tuple[str, float]]
    artist_w     : Dict[str, float]   raw weights for scoring
    album_w      : Dict[str, float]
    lang_w       : Dict[str, float]
    starring_w   : Dict[str, float]
    lang_prefs   : List[str]          resolved language preferences
    play_count   : int
    played_ids   : Set[str]           IDs of all played songs
    heavy_plays  : Dict[str, int]     songId → user_play_count (for penalty)
    """
    try:
        db = get_firestore_client()
        if not db:
            return None

        plays: List[Dict] = []
        liked: List[Dict] = []

        user_ref = db.collection("users").document(uid)

        for doc in user_ref.collection("plays").stream():
            data = doc.to_dict()
            if data:
                plays.append(data)

        for doc in user_ref.collection("likedSongs").stream():
            data = doc.to_dict()
            if data:
                liked.append(data)

    except Exception as exc:
        logger.error("build_user_profile Firestore error uid=%s: %s", uid, exc)
        return None

    if not plays and not liked:
        return None

    artist_w: Dict[str, float] = {}
    album_w: Dict[str, float] = {}
    lang_w: Dict[str, float] = {}
    starring_w: Dict[str, float] = {}

    def _accumulate(song_list: List[Dict], base_multiplier: float) -> None:
        for song in song_list:
            upc = float(song.get("user_play_count") or song.get("play_count") or 1)
            base = base_multiplier * upc
            ts = _ts(song.get("lastPlayedAt") or song.get("last_played_at") or 0)
            w = _recency_weight(ts, base)

            for a in _artists(song):
                artist_w[a] = artist_w.get(a, 0.0) + w

            alb = _album(song)
            if alb:
                album_w[alb] = album_w.get(alb, 0.0) + w

            lng = _language(song)
            if lng:
                lang_w[lng] = lang_w.get(lng, 0.0) + w

            for s in _starring(song):
                starring_w[s] = starring_w.get(s, 0.0) + w

    _accumulate(plays, PLAY_WEIGHT)
    _accumulate(liked, LIKED_WEIGHT)

    # Resolve language preferences: user-doc first, history-detected second
    lang_prefs: List[str] = []
    try:
        db2 = get_firestore_client()
        if db2:
            user_doc = db2.collection("users").document(uid).get()
            if user_doc.exists:
                ud = user_doc.to_dict() or {}
                raw = ud.get("languagePreferences") or ud.get("language_preferences") or []
                if isinstance(raw, list):
                    lang_prefs = [str(p).strip().lower() for p in raw if str(p).strip()]
    except Exception:
        pass

    if not lang_prefs:
        lang_prefs = [
            k for k, _ in sorted(lang_w.items(), key=lambda x: x[1], reverse=True)[:3]
        ]

    # Build penalty structures
    played_ids: Set[str] = set()
    heavy_plays: Dict[str, int] = {}
    for song in plays:
        sid = str(song.get("id") or song.get("videoId") or "")
        if sid:
            played_ids.add(sid)
            heavy_plays[sid] = int(song.get("user_play_count") or song.get("play_count") or 1)

    # Build normalised base-title set for 'From Movie' variant filtering.
    # Covers every song the user has played or liked, with the 'From ...' clause
    # stripped so that e.g. "Sir Osthara (From 'Businessman')" → "sir osthara".
    user_song_names: Set[str] = set()
    for song in plays + liked:
        raw_title = str(
            song.get("song") or song.get("title") or song.get("name") or ""
        ).strip()
        if raw_title:
            base = FROM_MOVIE_RE.sub(" ", raw_title)
            normalised = re.sub(r"[^a-z0-9]+", " ", base.lower()).strip()
            normalised = re.sub(r"\s+", " ", normalised).strip()
            if normalised:
                user_song_names.add(normalised)

    def _top(d: Dict[str, float], n: int) -> List[Tuple[str, float]]:
        return sorted(d.items(), key=lambda x: x[1], reverse=True)[:n]

    return {
        "top_artists": _top(artist_w, 5),
        "top_albums": _top(album_w, 3),
        "top_languages": _top(lang_w, 3),
        "top_starring": _top(starring_w, 3),
        "artist_w": artist_w,
        "album_w": album_w,
        "lang_w": lang_w,
        "starring_w": starring_w,
        "lang_prefs": lang_prefs,
        "play_count": len(plays),
        "played_ids": played_ids,
        "heavy_plays": heavy_plays,
        "user_song_names": user_song_names,
    }


# ---------------------------------------------------------------------------
# 2. generate_candidates
# ---------------------------------------------------------------------------

def generate_candidates(profile: Dict) -> List[Dict]:
    """
    Build a candidate pool via parallel JioSaavn search calls (≤10 queries).

    Sources (in priority order, never trending playlists)
    -------
    • Artist expansion     — top-5 weighted artists, one query each
    • Artist similarity    — cross-artist query from the top-2 artists
    • Album expansion      — top-3 weighted albums, one query each
    • Starring expansion   — top-1 starring actor from taste profile
    • Language expansion   — top-2 languages from taste profile

    Queries are deduplicated and capped at 10 before dispatch.
    All searches are concurrent (ThreadPoolExecutor) with a 6 s wall-clock
    timeout so a slow JioSaavn shard doesn't stall the homepage.
    """
    queries: List[Tuple[str, int]] = []
    top_artists = profile["top_artists"]   # List[Tuple[name_lower, weight]], up to 5
    top_albums  = profile["top_albums"]    # List[Tuple[name_lower, weight]], up to 3

    # ── Strategy 1: artist expansion — top 5 weighted artists ──────────────
    for name, _ in top_artists[:5]:
        if len(name) > 1:
            queries.append((f"{name} songs", 15))

    # ── Strategy 2: artist similarity — cross-artist discovery ─────────────
    # Searching "{artist1} {artist2} songs" surfaces collaborations and songs
    # that fans of both artists commonly enjoy, giving implicit similarity
    # without needing a dedicated similarity API.
    artist_names = [n for n, _ in top_artists[:2] if len(n) > 1]
    if len(artist_names) == 2:
        queries.append((f"{artist_names[0]} {artist_names[1]} songs", 15))
    elif len(artist_names) == 1:
        # Single top artist: discover related through a "best of" angle
        queries.append((f"best songs like {artist_names[0]}", 15))

    # ── Strategy 3: album expansion — top 3 weighted albums ────────────────
    for album_name, _ in top_albums[:3]:
        if len(album_name) > 1:
            queries.append((f"{album_name} songs", 10))

    # ── Strategy 4: starring actor expansion (top 1) ───────────────────────
    for actor, _ in profile["top_starring"][:1]:
        if len(actor) > 1:
            queries.append((f"{actor} songs", 10))

    # ── Strategy 5: language expansion — top 2 languages ───────────────────
    for lang, _ in profile["top_languages"][:2]:
        if len(lang) > 1:
            queries.append((f"{lang} popular songs", 15))

    # ── Fallback: language-only if profile signals are sparse ───────────────
    if not queries:
        for lang in profile.get("lang_prefs", [])[:2]:
            queries.append((f"{lang} songs", 20))
        if not queries:
            queries.append(("hindi songs", 20))

    # Deduplicate while preserving priority order, then cap
    seen_queries: Set[str] = set()
    unique_queries: List[Tuple[str, int]] = []
    for q, lim in queries:
        key = q.lower().strip()
        if key not in seen_queries:
            seen_queries.add(key)
            unique_queries.append((q, lim))
    queries = unique_queries[:10]  # hard cap — at most 10 concurrent searches

    all_candidates: List[Dict] = []
    seen_ids: Set[str] = set()

    def _fetch(q: str, limit: int) -> List[Dict]:
        try:
            return search_songs(q, limit=limit, include_full_data=True) or []
        except Exception as exc:
            logger.warning("Candidate query '%s' failed: %s", q, exc)
            return []

    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        futures = {pool.submit(_fetch, q, lim): (q, lim) for q, lim in queries}
        try:
            for future in concurrent.futures.as_completed(futures, timeout=6.0):
                try:
                    for song in future.result():
                        sid = str(song.get("id") or "")
                        if sid and sid not in seen_ids:
                            all_candidates.append(song)
                            seen_ids.add(sid)
                except Exception as exc:
                    logger.warning("Candidate future error: %s", exc)
        except concurrent.futures.TimeoutError:
            logger.warning(
                "Candidate generation timed out — using partial results (%d so far)",
                len(all_candidates),
            )

    logger.info(
        "Generated %d unique candidates from %d queries (artists=%d, albums=%d)",
        len(all_candidates), len(queries), len(top_artists[:5]), len(top_albums[:3]),
    )
    return all_candidates[:CANDIDATE_POOL_SIZE]


# ---------------------------------------------------------------------------
# 3. score_candidates
# ---------------------------------------------------------------------------

def score_candidates(candidates: List[Dict], profile: Dict) -> List[Dict]:
    """
    Score each candidate with:

        score = W_ARTIST  * artist_match
              + W_ALBUM   * album_match
              + W_STARRING * starring_match
              + W_LANGUAGE * language_match
              + W_POPULARITY * popularity_score

    All match scores are normalised to [0, 1].
    A play-count penalty is applied for songs the user has played many times.
    """
    artist_w = profile["artist_w"]
    album_w = profile["album_w"]
    lang_w = profile["lang_w"]
    starring_w = profile["starring_w"]
    heavy_plays = profile.get("heavy_plays", {})

    max_artist = max(artist_w.values(), default=1.0) or 1.0
    max_album = max(album_w.values(), default=1.0) or 1.0
    max_lang = max(lang_w.values(), default=1.0) or 1.0
    max_starring = max(starring_w.values(), default=1.0) or 1.0

    play_counts = [
        int(s.get("play_count") or s.get("global_play_count") or 0)
        for s in candidates
    ]
    max_global = max(play_counts, default=1) or 1

    scored: List[Dict] = []

    for song in candidates:
        sid = str(song.get("id") or "")

        # Artist match
        a_score = max(
            (artist_w.get(a.lower(), 0.0) / max_artist for a in _artists(song)),
            default=0.0,
        )

        # Album match
        alb = _album(song)
        alb_score = album_w.get(alb, 0.0) / max_album if alb else 0.0

        # Starring match
        s_score = max(
            (starring_w.get(s.lower(), 0.0) / max_starring for s in _starring(song)),
            default=0.0,
        )

        # Language match
        lng = _language(song)
        l_score = lang_w.get(lng, 0.0) / max_lang if lng else 0.0

        # Popularity (normalised global play count)
        gpc = int(song.get("play_count") or song.get("global_play_count") or 0)
        pop_score = min(1.0, gpc / max_global)

        total = (
            W_ARTIST * a_score
            + W_ALBUM * alb_score
            + W_STARRING * s_score
            + W_LANGUAGE * l_score
            + W_POPULARITY * pop_score
        )

        # Penalty for already heavily-played songs (encourages discovery)
        if sid in heavy_plays:
            penalty = min(0.9, heavy_plays[sid] * 0.1)
            total *= 1.0 - penalty

        entry = dict(song)
        entry["_rec_score"] = total
        scored.append(entry)

    scored.sort(key=lambda x: x.get("_rec_score", 0.0), reverse=True)
    return scored


# ---------------------------------------------------------------------------
# 4. apply_diversity
# ---------------------------------------------------------------------------

def apply_diversity(scored: List[Dict], limit: int = 15) -> List[Dict]:
    """
    Select top-N songs while enforcing:
        • max 2 songs per primary artist (normalized)
        • max 3 songs per album
        • max 2 songs per starring actor
    """
    result: List[Dict] = []
    artist_count: Dict[str, int] = {}
    album_count: Dict[str, int] = {}
    starring_count: Dict[str, int] = {}
    seen: Set[str] = set()

    for song in scored:
        if len(result) >= limit:
            break

        sid = str(song.get("id") or "")
        if not sid or sid in seen:
            continue

        # Check artist diversity (use normalized artist names directly)
        arts = _artists(song)
        primary = arts[0] if arts else "__unknown__"
        if artist_count.get(primary, 0) >= MAX_PER_ARTIST:
            continue

        # Check album diversity
        alb = _album(song) or "__unknown__"
        if album_count.get(alb, 0) >= MAX_PER_ALBUM:
            continue

        # Check starring diversity
        stars = _starring(song)
        primary_star = stars[0] if stars else None
        if primary_star and starring_count.get(primary_star, 0) >= MAX_PER_STARRING:
            continue

        result.append(song)
        seen.add(sid)
        artist_count[primary] = artist_count.get(primary, 0) + 1
        album_count[alb] = album_count.get(alb, 0) + 1
        if primary_star:
            starring_count[primary_star] = starring_count.get(primary_star, 0) + 1

    return result


# ---------------------------------------------------------------------------
# Cold-start fallback
# ---------------------------------------------------------------------------

def _cold_start(
    lang_prefs: List[str],
    limit: int,
    played_ids: Optional[Set[str]] = None,
    user_song_names: Optional[Set[str]] = None,
) -> List[Dict]:
    """
    Fetch language-based discovery songs for users with fewer than
    COLD_START_THRESHOLD plays.  Uses only language-expansion queries
    (never trending playlists or superhits lists).
    """
    queries: List[str] = []
    for lang in lang_prefs[:3]:
        if lang:
            queries.append(f"{lang} trending songs")
    if not queries:
        queries.append("hindi trending songs")

    candidates: List[Dict] = []
    seen: Set[str] = set()
    _played = played_ids or set()

    for q in queries[:3]:
        try:
            for song in search_songs(q, limit=20, include_full_data=True) or []:
                sid = str(song.get("id") or "")
                if sid and sid not in seen and sid not in _played:
                    candidates.append(song)
                    seen.add(sid)
        except Exception as exc:
            logger.warning("Cold-start query '%s' failed: %s", q, exc)

    resolved = resolve_canonical_tracks(candidates)
    resolved = filter_from_movie_variants(resolved, user_song_names)
    return resolved[:limit]


# ---------------------------------------------------------------------------
# 5. get_recommendations  (main entry point)
# ---------------------------------------------------------------------------

def get_recommendations(
    uid: str,
    limit: int = 15,
    lang_fallback: Optional[List[str]] = None,
) -> List[Dict]:
    """
    Orchestrate the full recommendation pipeline.

    Returns a list of raw JioSaavn song dicts (not yet DTO-converted).
    Callers should run results through ``_to_song_dto()`` before sending
    to the Android client.

    Results are cached (CACHE_TTL_WARM for warm users, CACHE_TTL_COLD for
    cold-start users) to keep homepage latency low.
    """
    limit = max(10, min(limit, 15))
    cache = get_cache()
    cache_key = f"prec:{RECOMMENDER_CACHE_VERSION}:{uid}:{limit}"

    cached = cache.get(cache_key)
    if cached is not None:
        logger.debug("Returning cached recommendations for uid=%s", uid)
        return cached

    t0 = time.perf_counter()

    try:
        profile = build_user_profile(uid)

        # ---- Cold start ----
        if profile is None or profile["play_count"] < COLD_START_THRESHOLD:
            play_count = profile["play_count"] if profile else 0
            logger.info("Cold start uid=%s (plays=%d)", uid, play_count)
            prefs = (profile or {}).get("lang_prefs") or lang_fallback or []
            played = (profile or {}).get("played_ids", set())
            song_names = (profile or {}).get("user_song_names", set())
            recs = _cold_start(prefs, limit, played_ids=played, user_song_names=song_names)
            cache.set(cache_key, recs, ttl=CACHE_TTL_COLD)
            return recs

        # ---- Warm path ----
        candidates = generate_candidates(profile)
        candidates = resolve_canonical_tracks(candidates)
        candidates = filter_from_movie_variants(candidates, profile.get("user_song_names"))

        # Hard-filter already-played songs before scoring
        played_ids = profile.get("played_ids", set())
        if played_ids:
            before = len(candidates)
            candidates = [
                c for c in candidates
                if str(c.get("id") or "") not in played_ids
            ]
            logger.debug(
                "Filtered %d already-played songs from candidates for uid=%s",
                before - len(candidates), uid,
            )

        if not candidates:
            logger.warning("No unplayed candidates for uid=%s — returning empty", uid)
            cache.set(cache_key, [], ttl=CACHE_TTL_COLD)
            return []

        scored = score_candidates(candidates, profile)
        recs = apply_diversity(scored, limit=limit)

        elapsed_ms = (time.perf_counter() - t0) * 1000
        logger.info(
            "Personalized recs uid=%s: %d songs in %.0f ms "
            "(candidates=%d, plays=%d)",
            uid, len(recs), elapsed_ms, len(candidates), profile["play_count"],
        )

        cache.set(cache_key, recs, ttl=CACHE_TTL_WARM)
        return recs

    except Exception as exc:
        logger.error("get_recommendations failed uid=%s: %s", uid, exc, exc_info=True)
        return []


# ---------------------------------------------------------------------------
# Public helper — Top Artists
# ---------------------------------------------------------------------------

def get_top_artists(uid: str, limit: int = 5) -> List[Dict]:
    """
    Return the user's top ``limit`` artists derived from the weighted taste
    profile (liked × 2 + plays × playCount, both with exponential time-decay).

    Each entry:
        {"name": str, "weight": float, "rank": int}

    The profile is cached (CACHE_TTL_WARM) so repeated calls within the same
    caching window are effectively free.  Returns an empty list when the user
    has no history (cold-start).
    """
    cache = get_cache()
    cache_key = f"prec_top_artists:{uid}:{limit}"
    cached = cache.get(cache_key)
    if cached is not None:
        return cached

    profile = build_user_profile(uid)
    if not profile:
        return []

    result = [
        {"name": name, "weight": round(weight, 4), "rank": rank}
        for rank, (name, weight) in enumerate(profile["top_artists"][:limit], start=1)
    ]
    cache.set(cache_key, result, ttl=CACHE_TTL_WARM)
    return result
