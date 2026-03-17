
"""Minimal AURA backend for stable Android debugging."""


from flask import Flask, jsonify, request, g
from flask_cors import CORS
import json
import logging
import os
import re
import sys
import time
from typing import Any, Dict, List
from config import BASE_URL, PORT

from services.jiosaavn_service import JioSaavnService
import services.jiosaavn_service as jio_api
from services.canonical_track_resolver import resolve_canonical_tracks
from services.collaborative_service import CollaborativeFilteringService
from services.daily_mix_service import DailyMixService
from services.user_profile_service import UserProfileService
from services.user_service import UserService, get_firestore_client

app = Flask(__name__)

@app.route("/", methods=["GET"])
def home():
    logger.info("[API] IN  GET /")
    response = jsonify({
        "message": "AURA backend is running",
        "status": "ok"
    })
    logger.info("[API] OUT GET / -> 200")
    return response, 200

@app.route("/health", methods=["GET"])
def health():
    logger.info("[API] IN  GET /health")
    response = jsonify({
        "status": "ok"
    })
    logger.info("[API] OUT GET /health -> 200")
    return response, 200

try:
    from firebase_admin import firestore as admin_firestore
except Exception:
    admin_firestore = None

# Force unbuffered output
sys.stdout = sys.stderr

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    stream=sys.stderr,
    force=True,
)

# Suppress werkzeug logs unless critical
logging.getLogger("werkzeug").setLevel(logging.WARNING)

logger = logging.getLogger(__name__)
logger.info("[STARTUP] Backend starting up...")

app = Flask(__name__)

# Configure Flask app logger
app.logger.setLevel(logging.INFO)
for handler in logging.root.handlers:
    app.logger.addHandler(handler)

cors_origins = os.environ.get("CORS_ORIGINS", "*").split(",")
CORS(app, origins=cors_origins, supports_credentials=True, allow_headers="*", methods="*")

jiosaavn_service = JioSaavnService()
user_profile_service = UserProfileService()
user_service = UserService()
collaborative_service = CollaborativeFilteringService(user_service)
daily_mix_service = DailyMixService(
    jiosaavn_service=jiosaavn_service,
    user_profile_service=user_profile_service,
    collaborative_service=collaborative_service,
    user_service=user_service,
)

FRESH_PICKS_CACHE_TTL_SECONDS = 10 * 60
_fresh_picks_cache: Dict[str, Dict[str, Any]] = {}
FRESH_PICKS_INTERNATIONAL_QUERY = "International: India superhits top 50"
FRESH_PICKS_MAX_RESULTS = 15


def _to_song_dto(song):
    primary_artists = (song.get("primary_artists") or "").strip()
    artists = [a.strip() for a in primary_artists.split(",") if a.strip()]
    # JioSaavn uses 'song' field for song name, fallback to 'title' or 'name'
    title = str(song.get("song") or song.get("title") or song.get("name") or "")
    media_url = song.get("media_url") or song.get("url") or None
    play_count = int(song.get("play_count") or song.get("global_play_count") or 0)
    _raw_image = str(song.get("image") or "").strip()
    _thumbnail = _raw_image if _raw_image.startswith("http") else None
    return {
        "videoId": str(song.get("id") or ""),
        "title": title,
        "artist": primary_artists or None,
        "artists": artists or None,
        "thumbnail": _thumbnail,
        "duration": str(song.get("duration") or ""),
        "url": media_url,
        "album": song.get("album") or None,
        "artistId": None,
        "play_count": play_count,
        "global_play_count": play_count,
        "language": song.get("language") or None,
        "year": song.get("year") or None,
        # Normalized keys required by Android-side model mapping.
        "id": str(song.get("id") or ""),
        "singers": song.get("singers") or primary_artists or None,
        "starring": song.get("starring") or None,
        "image": _thumbnail,
        "media_url": media_url,
    }


def _to_album_dto(album: dict) -> dict:
    """Convert JioSaavn album data to Android AlbumDto format."""
    # Extract artists from music field (comma-separated string)
    artists = []
    if album.get("music"):
        artists = [a.strip() for a in str(album.get("music")).split(",")]
    
    # Extract year from more_info
    year = None
    if isinstance(album.get("more_info"), dict):
        year = album["more_info"].get("year")
    
    return {
        "browseId": str(album.get("id") or ""),
        "title": str(album.get("title") or ""),
        "artists": artists if artists else None,
        "thumbnail": album.get("image"),
        "year": str(year) if year else None,
        "type": album.get("type")
    }


def _to_playlist_dto(playlist: dict) -> dict:
    """Convert JioSaavn playlist data to Android PlaylistSearchDto format."""
    return {
        "browseId": str(playlist.get("id") or ""),
        "playlistId": str(playlist.get("id") or ""),
        "title": str(playlist.get("title") or ""),
        "author": playlist.get("subtitle") or playlist.get("music"),
        "thumbnail": playlist.get("image"),
        "itemCount": None  # Not available in autocomplete results
    }


def _to_artist_dto(artist: dict) -> dict:
    """Convert Saavn artist data to Android ArtistDto format."""
    # Pick the highest quality thumbnail if image array is available.
    thumbnail = None
    image_field = artist.get("image")
    if isinstance(image_field, list) and image_field:
        best = image_field[-1]
        if isinstance(best, dict):
            thumbnail = best.get("url")
    elif isinstance(image_field, str):
        thumbnail = image_field

    return {
        "browseId": str(artist.get("id") or ""),
        "name": str(artist.get("name") or ""),
        "thumbnail": thumbnail,
        "subscribers": artist.get("role") or None,
    }


def _search_songs(query, limit):
    """Helper to search songs and convert to DTO format."""
    songs = jiosaavn_service.search_songs(query, limit)
    songs = resolve_canonical_tracks(songs)
    songs = _dedupe_response_songs(songs)
    return [_to_song_dto(song) for song in songs]


def _dedupe_response_songs(songs: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """
    Final API-layer dedupe safety net for recommendation/search payloads.
    Collapses compilation variants of the same song identity even if upstream
    ranking/caching paths return near-duplicates.
    """
    if not songs:
        return []

    alias_map = {
        "ss thaman": "s thaman",
        "s s thaman": "s thaman",
        "thaman s": "s thaman",
    }

    compilation_keywords = (
        "hits", "best", "special", "collection", "jukebox", "playlist",
        "valentine", "love", "tunes", "director", "top", "mix", "radio",
        "essential", "beloved",
    )

    def _clean_text(value: Any) -> str:
        normalized = re.sub(r"[^a-z0-9]+", " ", str(value or "").lower())
        return re.sub(r"\s+", " ", normalized).strip()

    def _canonical_artist(value: Any) -> str:
        parts = [p for p in _clean_text(value).split(" ") if p]
        if not parts:
            return ""
        joined = " ".join(sorted(parts))
        return alias_map.get(joined, joined)

    def _song_title(song: Dict[str, Any]) -> str:
        return str(song.get("song") or song.get("title") or song.get("name") or "").strip()

    def _song_album(song: Dict[str, Any]) -> str:
        album = song.get("album")
        if isinstance(album, dict):
            return str(album.get("name") or "").strip()
        return str(album or "").strip()

    def _song_artists(song: Dict[str, Any]) -> List[str]:
        artists_value = song.get("artists")
        names: List[str] = []

        if isinstance(artists_value, list) and artists_value:
            for item in artists_value:
                if isinstance(item, dict):
                    raw = item.get("name") or item.get("artist")
                else:
                    raw = item
                cleaned = _canonical_artist(raw)
                if cleaned:
                    names.append(cleaned)

        if not names:
            artist_csv = song.get("primary_artists") or song.get("artist") or song.get("singers") or ""
            for part in str(artist_csv).split(","):
                cleaned = _canonical_artist(part)
                if cleaned:
                    names.append(cleaned)

        if not names:
            return []

        return sorted(set(names))

    def _is_compilation(album_name: str) -> bool:
        lower_album = str(album_name or "").lower()
        return any(keyword in lower_album for keyword in compilation_keywords)

    def _identity(song: Dict[str, Any]) -> str:
        title = _song_title(song)
        title = re.sub(r"\(?\s*from\s*['\"].+?['\"]\s*\)?", " ", title, flags=re.IGNORECASE)
        title_key = _clean_text(title)
        primary_artist = _song_artists(song)
        artist_key = primary_artist[0] if primary_artist else ""
        return f"{title_key}_{artist_key}".strip("_")

    # First occurrence order is kept stable for UX while allowing replacement
    # by a stronger canonical pick inside the same identity group.
    picked_by_key: Dict[str, Dict[str, Any]] = {}
    key_order: List[str] = []

    for current in songs:
        key = _identity(current)
        if not key:
            key = str(current.get("id") or current.get("videoId") or "")
        if not key:
            continue

        if key not in picked_by_key:
            picked_by_key[key] = current
            key_order.append(key)
            continue

        existing = picked_by_key[key]
        current_album = _song_album(current)
        existing_album = _song_album(existing)

        current_score = 0
        existing_score = 0

        if not _is_compilation(current_album):
            current_score += 10
        if not _is_compilation(existing_album):
            existing_score += 10

        current_year = _safe_year(current.get("year")) or 9999
        existing_year = _safe_year(existing.get("year")) or 9999
        current_score += max(0, 3000 - current_year)
        existing_score += max(0, 3000 - existing_year)

        current_plays = _safe_int(current.get("play_count") or current.get("global_play_count"), 0)
        existing_plays = _safe_int(existing.get("play_count") or existing.get("global_play_count"), 0)
        current_score += min(current_plays, 1_000_000) // 10_000
        existing_score += min(existing_plays, 1_000_000) // 10_000

        if current_score > existing_score:
            picked_by_key[key] = current

    return [picked_by_key[key] for key in key_order if key in picked_by_key]


def _search_all(query, limit=20):
    """Search all categories: songs, albums, playlists."""
    from services.jiosaavn_service import search_all_categories
    return search_all_categories(query, limit)


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        if value is None:
            return default
        if isinstance(value, bool):
            return default
        return int(str(value).strip())
    except Exception:
        return default


def _to_string_list(value: Any) -> List[str]:
    if isinstance(value, list):
        return [str(v).strip() for v in value if str(v).strip()]
    if isinstance(value, str):
        return [part.strip() for part in value.split(",") if part.strip()]
    return []


def _join_csv(values: List[str]) -> str:
    return ", ".join([v for v in values if v])


def _safe_year(value: Any) -> int | None:
    try:
        if value is None:
            return None
        year = int(str(value).strip()[:4])
        if 1900 <= year <= time.gmtime().tm_year:
            return year
    except Exception:
        pass
    return None


def _normalize_user_languages(user_languages: List[str]) -> List[str]:
    normalized_languages = {
        str(language).strip().lower()
        for language in user_languages
        if str(language).strip()
    }
    return sorted(normalized_languages)


def _get_live_user_languages(uid: str) -> List[str]:
    normalized_uid = str(uid or "").strip()
    if not normalized_uid or normalized_uid == "guest":
        return []

    db = get_firestore_client()
    if db is not None:
        try:
            user_doc = db.collection("users").document(normalized_uid).get()
            if user_doc.exists:
                data = user_doc.to_dict() or {}
                return _normalize_user_languages(data.get("languagePreferences", []))
            return []
        except Exception as exc:
            logger.warning("Fresh picks live Firestore language fetch failed for uid=%s: %s", normalized_uid, exc)

    try:
        return _normalize_user_languages(user_service.get_user_languages(normalized_uid))
    except Exception as exc:
        logger.warning("Fresh picks fallback language fetch failed for uid=%s: %s", normalized_uid, exc)
        return []


def _fresh_picks_cache_key(user_languages: List[str]) -> str:
    normalized_languages = _normalize_user_languages(user_languages)
    if not normalized_languages:
        return "fresh_picks_none"
    return f"fresh_picks_{'_'.join(normalized_languages)}"


def _filter_fresh_picks_by_languages(songs: List[Dict[str, Any]], user_languages: List[str]) -> List[Dict[str, Any]]:
    normalized_languages = set(_normalize_user_languages(user_languages))
    if not normalized_languages:
        return []

    filtered_songs: List[Dict[str, Any]] = []
    for song in songs:
        language = str(song.get("language") or "").strip().lower()
        if language in normalized_languages:
            filtered_songs.append(song)
    return filtered_songs


def _to_minimal_fresh_pick(song: Dict[str, Any]) -> Dict[str, Any]:
    song_id = str(song.get("videoId") or song.get("id") or "").strip()
    title = str(song.get("title") or "").strip()
    language = str(song.get("language") or "").strip().lower()

    artists_raw = song.get("artists")
    if isinstance(artists_raw, list):
        artists = [str(a).strip() for a in artists_raw if str(a).strip()]
    else:
        artist_csv = str(song.get("artist") or "").strip()
        artists = [a.strip() for a in artist_csv.split(",") if a.strip()] if artist_csv else []

    image = song.get("image") or song.get("thumbnail") or ""
    media_url = song.get("media_url") or song.get("url") or ""

    return {
        "id": song_id,
        "videoId": song_id,
        "title": title,
        "artist": ", ".join(artists) if artists else None,
        "artists": artists,
        "image": image,
        "thumbnail": image,
        "media_url": media_url,
        "url": media_url,
        "language": language,
    }


def _search_playlists_by_query(query: str, limit: int = 3) -> List[Dict[str, Any]]:
    if not query or not query.strip():
        return []

    try:
        url = jio_api.JIOSAAVN_SEARCH_BASE + query
        response = jio_api.session.get(url, timeout=jio_api.HTTP_TIMEOUT)
        response_text = response.text.encode().decode("unicode-escape")
        response_text = re.sub(r'\(From "([^"]+)"\)', r"(From '\\1')", response_text)

        data = json.loads(response_text)
        playlists_data = []
        if isinstance(data.get("playlists"), dict):
            playlists_data = data["playlists"].get("data", [])
        elif isinstance(data.get("playlists"), list):
            playlists_data = data["playlists"]

        return playlists_data[:limit]
    except Exception as exc:
        logger.warning("Fresh picks playlist search failed for '%s': %s", query, exc)
        return []


def _extract_playlist_url(playlist: Dict[str, Any]) -> str:
    if not isinstance(playlist, dict):
        return ""

    # Autocomplete payload typically exposes url; some responses may include perma_url.
    return str(playlist.get("url") or playlist.get("perma_url") or "").strip()


def _pick_first_playlist_url(query: str, preferred_language: str | None = None) -> str:
    playlists = _search_playlists_by_query(query, limit=3)

    normalized_pref = str(preferred_language or "").strip().lower()
    if normalized_pref:
        best_url = ""
        best_score = -1
        for playlist in playlists:
            playlist_url = _extract_playlist_url(playlist)
            if not playlist_url:
                continue

            haystack = " ".join([
                str(playlist.get("title") or ""),
                str(playlist.get("url") or ""),
                str(playlist.get("perma_url") or ""),
                str(playlist.get("subtitle") or ""),
            ]).lower()

            if normalized_pref == "english":
                score = 0
                if "international" in haystack or "english" in haystack:
                    score = 3

                if score > best_score:
                    best_score = score
                    best_url = playlist_url
            else:
                if normalized_pref in haystack:
                    return playlist_url

        if normalized_pref == "english":
            if best_score > 0 and best_url:
                return best_url
            return ""

    for playlist in playlists:
        playlist_url = _extract_playlist_url(playlist)
        if playlist_url:
            return playlist_url
    return ""


def _fetch_playlist_song_dtos(playlist_url: str) -> List[Dict[str, Any]]:
    if not playlist_url:
        return []
    try:
        raw_songs = jiosaavn_service.get_playlist_songs_from_url(playlist_url)[:50]
        return [_to_song_dto(song) for song in raw_songs]
    except Exception as exc:
        logger.warning("Fresh picks playlist fetch failed for '%s': %s", playlist_url, exc)
        return []


def _fresh_picks_queries_for_language(language: str) -> List[str]:
    normalized = str(language or "").strip().lower()
    if normalized == "english":
        # English uses the dedicated international query first.
        # Fallback search improves discovery of the same international playlist.
        return [
            FRESH_PICKS_INTERNATIONAL_QUERY,
            "english: India superhits top 50",
        ]
    return [f"{normalized}: India superhits top 50"]


def _song_matches_preferred_language(song_language: str, preferred_language: str) -> bool:
    song_lang = str(song_language or "").strip().lower()
    preferred = str(preferred_language or "").strip().lower()
    if not song_lang or not preferred:
        return False

    # JioSaavn may return multi-language tags like "hindi, english".
    parts = [p.strip() for p in re.split(r"[,/|&]+", song_lang) if p.strip()]
    candidates = set(parts)
    candidates.add(song_lang)

    if preferred == "english":
        return any(c in {"english", "eng", "en"} or "english" in c for c in candidates)

    return preferred in candidates


def _compute_fresh_picks(user_languages: List[str], limit: int) -> List[Dict[str, Any]]:
    normalized_languages = _normalize_user_languages(user_languages)
    if not normalized_languages:
        return []

    songs_by_language: Dict[str, List[Dict[str, Any]]] = {lang: [] for lang in normalized_languages}
    per_language_seen_ids: Dict[str, set[str]] = {lang: set() for lang in normalized_languages}

    # One playlist search + one playlist fetch per selected language.
    for language in normalized_languages:
        playlist_url = ""
        for query in _fresh_picks_queries_for_language(language):
            playlist_url = _pick_first_playlist_url(query, preferred_language=language)
            if playlist_url:
                break

        if not playlist_url:
            continue

        songs = _fetch_playlist_song_dtos(playlist_url)
        for song in songs:
            song_id = str(song.get("videoId") or song.get("id") or "").strip()
            if not song_id:
                continue

            song_language = str(song.get("language") or "").strip().lower()
            # Keep each language bucket pure, with English alias handling.
            if not _song_matches_preferred_language(song_language, language):
                continue

            if song_id in per_language_seen_ids[language]:
                continue

            per_language_seen_ids[language].add(song_id)
            songs_by_language[language].append(song)

    final_count = max(10, min(limit, FRESH_PICKS_MAX_RESULTS))
    final_songs: List[Dict[str, Any]] = []
    global_seen_ids: set[str] = set()
    cursor = {lang: 0 for lang in normalized_languages}

    # Deterministic round-robin merge so multiple selected languages are represented.
    while len(final_songs) < final_count:
        added_in_round = False
        for language in normalized_languages:
            queue = songs_by_language.get(language, [])
            while cursor[language] < len(queue):
                song = queue[cursor[language]]
                cursor[language] += 1
                song_id = str(song.get("videoId") or song.get("id") or "").strip()
                if not song_id or song_id in global_seen_ids:
                    continue

                global_seen_ids.add(song_id)
                final_songs.append(song)
                added_in_round = True
                break

            if len(final_songs) >= final_count:
                break

        if not added_in_round:
            break

    return [_to_minimal_fresh_pick(song) for song in final_songs]


def _get_fresh_picks(user_languages: List[str], limit: int) -> List[Dict[str, Any]]:
    normalized_languages = _normalize_user_languages(user_languages)
    if not normalized_languages:
        return []

    cache_key = _fresh_picks_cache_key(normalized_languages)
    cached = _fresh_picks_cache.get(cache_key)
    now = time.time()

    if (
        cached
        and cached.get("languages") == normalized_languages
        and (now - cached.get("timestamp", 0)) < FRESH_PICKS_CACHE_TTL_SECONDS
    ):
        cached_songs = _filter_fresh_picks_by_languages(cached.get("songs", []), normalized_languages)
        return cached_songs[:limit]

    songs = _compute_fresh_picks(user_languages=normalized_languages, limit=FRESH_PICKS_MAX_RESULTS)
    songs = _filter_fresh_picks_by_languages(songs, normalized_languages)
    _fresh_picks_cache[cache_key] = {
        "timestamp": now,
        "languages": normalized_languages,
        "songs": songs,
    }
    return songs[:limit]


def _safe_song_document(mapped: Dict[str, Any]) -> Dict[str, Any]:
    artists_list = _to_string_list(mapped.get("primary_artists"))
    starring_list = _to_string_list(mapped.get("starring"))
    primary_artists = _join_csv(artists_list)
    starring_text = _join_csv(starring_list)

    return {
        # Legacy/client-consumed keys
        "id": mapped.get("songId") or "",
        "videoId": mapped.get("songId") or "",
        "title": mapped.get("song") or "",
        "artist": primary_artists,
        "artists": artists_list,
        "thumbnail": mapped.get("image") or "",
        "url": mapped.get("media_url") or "",
        # Normalized keys
        "song": mapped.get("song") or "",
        "album": mapped.get("album") or "",
        "primary_artists": primary_artists,
        "singers": primary_artists,
        # Keep starring as string for API/schema compatibility.
        "starring": starring_text,
        "language": mapped.get("language") or "",
        "year": mapped.get("year") or 0,
        "image": mapped.get("image") or "",
        "media_url": mapped.get("media_url") or "",
        "global_play_count": mapped.get("global_play_count") or 0,
    }


def _normalize_tracking_song(song: Dict[str, Any]) -> Dict[str, Any]:
    song_id = str(song.get("id") or song.get("videoId") or "").strip()
    title = str(song.get("title") or song.get("song") or song.get("name") or "").strip()
    album = str(song.get("album") or "").strip()

    artists_value = (
        song.get("artist")
        or song.get("primary_artists")
        or song.get("singers")
        or song.get("artists")
        or ""
    )
    if isinstance(artists_value, list):
        primary_artists = ", ".join(str(v).strip() for v in artists_value if str(v).strip())
    else:
        primary_artists = str(artists_value).strip()

    starring = _join_csv(_to_string_list(song.get("starring")))
    language = str(song.get("language") or "").strip().lower()
    year = _safe_int(song.get("year"), 0)
    image = str(song.get("image") or song.get("thumbnail") or "").strip()
    media_url = str(song.get("url") or song.get("media_url") or "").strip()
    global_play_count = _safe_int(song.get("play_count") or song.get("global_play_count"), 0)

    return {
        "songId": song_id,
        "song": title,
        "album": album,
        "primary_artists": primary_artists,
        "starring": starring,
        "language": language,
        "year": year,
        "image": image,
        "media_url": media_url,
        "global_play_count": global_play_count,
    }


def _enrich_tracking_song(song: Dict[str, Any], mapped: Dict[str, Any]) -> Dict[str, Any]:
    """Fill missing metadata fields from canonical song details by song ID."""
    song_id = mapped.get("songId") or ""
    if not song_id:
        return mapped

    needs_enrichment = (
        not mapped.get("song")
        or not mapped.get("album")
        or not mapped.get("primary_artists")
        or not mapped.get("starring")
        or not mapped.get("image")
        or not mapped.get("media_url")
        or not mapped.get("language")
        or not mapped.get("year")
    )
    if not needs_enrichment:
        return mapped

    try:
        canonical = jiosaavn_service.get_song_details(song_id)
        if not canonical:
            return mapped

        canonical_mapped = _normalize_tracking_song(canonical)
        return {
            "songId": mapped.get("songId") or canonical_mapped.get("songId") or "",
            "song": mapped.get("song") or canonical_mapped.get("song") or "",
            "album": mapped.get("album") or canonical_mapped.get("album") or "",
            "primary_artists": mapped.get("primary_artists") or canonical_mapped.get("primary_artists") or "",
            "starring": mapped.get("starring") or canonical_mapped.get("starring") or "",
            "language": mapped.get("language") or canonical_mapped.get("language") or "",
            "year": mapped.get("year") or canonical_mapped.get("year") or 0,
            "image": mapped.get("image") or canonical_mapped.get("image") or "",
            "media_url": mapped.get("media_url") or canonical_mapped.get("media_url") or "",
            "global_play_count": max(
                _safe_int(mapped.get("global_play_count"), 0),
                _safe_int(canonical_mapped.get("global_play_count"), 0),
            ),
        }
    except Exception as exc:
        logger.warning("Song enrichment failed for %s: %s", song_id, exc)
        return mapped


@app.before_request
def log_incoming_request():
    g.request_start = time.perf_counter()
    query_string = request.query_string.decode("utf-8") if request.query_string else ""
    logger.info(
        "[API] IN  %s %s%s",
        request.method,
        request.path,
        f"?{query_string}" if query_string else "",
    )


@app.after_request
def log_outgoing_response(response):
    elapsed_ms = 0.0
    start = getattr(g, "request_start", None)
    if start is not None:
        elapsed_ms = (time.perf_counter() - start) * 1000

    logger.info(
        "[API] OUT %s %s -> %s (%.2f ms)",
        request.method,
        request.path,
        response.status_code,
        elapsed_ms,
    )
    return response


@app.route("/api/health", methods=["GET"])
def health_check():
    return jsonify({"status": "ok", "service": "aura-backend"}), 200


@app.route("/api/search", methods=["GET"])
def search_all_categories():
    query = (request.args.get("query") or "").strip()
    if not query:
        return jsonify(
            {
                "success": True,
                "songs": [],
                "albums": [],
                "artists": [],
                "playlists": [],
                "count": 0,
                "query": "",
                "cached": False,
            }
        ), 200

    # Search all categories
    results = _search_all(query, 20)
    
    # Convert to DTO formats
    songs = [_to_song_dto(song) for song in results.get("songs", [])]
    albums = [_to_album_dto(album) for album in results.get("albums", [])]
    playlists = [_to_playlist_dto(playlist) for playlist in results.get("playlists", [])]
    artists = [_to_artist_dto(artist) for artist in jiosaavn_service.get_artists(query)][:20]
    total_count = len(songs) + len(albums) + len(artists) + len(playlists)
    
    return jsonify(
        {
            "success": True,
            "songs": songs,
            "albums": albums,
            "artists": artists,
            "playlists": playlists,
            "count": total_count,
            "query": query,
            "cached": False,
        }
    ), 200


@app.route("/api/search/suggestions", methods=["GET"])
def search_suggestions():
    query = (request.args.get("q") or "").strip()
    if len(query) < 2:
        return jsonify({"success": True, "suggestions": []}), 200

    suggestions = [f"{query} songs", f"{query} hits", f"{query} playlist"]
    return jsonify({"success": True, "suggestions": suggestions}), 200


@app.route("/api/trending", methods=["GET"])
def trending():
    limit = max(1, min(int(request.args.get("limit", 20)), 50))
    uid = (request.args.get("uid") or "").strip()
    
    # Fetch user's language preferences if uid provided
    user_languages = []
    if uid:
        try:
            user_languages = user_service.get_user_languages(uid)
        except Exception as e:
            logger.warning(f"Failed to fetch language preferences for uid={uid}: {e}")
    
    # Fetch more songs to account for filtering
    fetch_limit = limit * 3 if user_languages else limit
    songs = _search_songs("top songs 2024", fetch_limit)
    
    # Filter by user's language preferences if available
    if user_languages:
        songs = [s for s in songs if s.get("language") and s["language"].lower() in [lang.lower() for lang in user_languages]]
    
    # Limit to requested count
    songs = songs[:limit]
    
    return jsonify({"success": True, "results": songs, "count": len(songs)}), 200


@app.route("/api/fresh-picks", methods=["GET"])
@app.route("/api/latest-hits", methods=["GET"])
def fresh_picks():
    uid = (request.args.get("uid") or "").strip()
    limit = max(10, min(int(request.args.get("limit", 15)), 15))

    normalized_languages = _get_live_user_languages(uid)
    songs = _get_fresh_picks(user_languages=normalized_languages, limit=limit)
    return jsonify({"success": True, "songs": songs, "count": len(songs)}), 200


@app.route("/api/recommendations", methods=["GET"])
def recommendations():
    uid = (request.args.get("uid") or "").strip()
    limit = max(10, min(int(request.args.get("limit", 15)), 15))

    if not uid or uid == "guest":
        return jsonify({"source": "personalized", "uid": uid, "results": [], "count": 0}), 200

    try:
        from services.personalized_recommender import get_recommendations as _get_personalized_recs
        lang_fallback: List[str] = []
        try:
            lang_fallback = user_service.get_user_languages(uid)
        except Exception:
            pass

        raw_songs = _get_personalized_recs(uid, limit=limit, lang_fallback=lang_fallback)
        raw_songs = _dedupe_response_songs(raw_songs)
        songs = [_to_song_dto(s) for s in raw_songs]
        songs = _dedupe_response_songs(songs)
        # Album-aware shuffle for display
        from services.personalized_recommender import shuffle_with_album_diversity
        songs = shuffle_with_album_diversity(songs)
        return jsonify({"source": "personalized", "uid": uid, "results": songs, "count": len(songs)}), 200

    except Exception as exc:
        logger.error("recommendations() failed uid=%s: %s", uid, exc, exc_info=True)
        return jsonify({"source": "error", "uid": uid, "results": [], "count": 0}), 200


@app.route("/api/daily-mixes", methods=["GET"])
@app.route("/api/daily-mixes/<uid>", methods=["GET"])
def daily_mixes(uid: str | None = None):
    uid = str(uid or request.args.get("uid") or "").strip()
    refresh = str(request.args.get("refresh", "false")).strip().lower() == "true"

    if not uid:
        return jsonify({"error": "uid is required"}), 400

    try:
        payload = daily_mix_service.get_daily_mixes(uid, refresh=refresh)
        return jsonify(payload), 200
    except Exception as exc:
        logger.error("daily_mixes() failed uid=%s: %s", uid, exc, exc_info=True)
        return jsonify(daily_mix_service._empty_response(uid)), 200


@app.route("/api/recommendations/collaborative", methods=["GET"])
@app.route("/api/recommendations/collaborative/<uid>", methods=["GET"])
def collaborative_recommendations(uid: str | None = None):
    uid = str(uid or request.args.get("uid") or "").strip()
    limit = max(5, min(int(request.args.get("limit", 12)), 20))
    debug_mode = str(request.args.get("debug", "false")).strip().lower() == "true"

    if not uid or uid == "guest":
        payload = {
            "source": "collaborative",
            "title": "From listeners like you",
            "uid": uid,
            "results": [],
            "count": 0,
        }
        if debug_mode:
            payload["stats"] = {
                "has_profile": False,
                "plays_count": 0,
                "artist_count": 0,
                "similar_users_count": 0,
            }
        return jsonify(payload), 200

    try:
        recs = collaborative_service.get_cf_recommendations(uid)[:limit]
        payload = {
            "source": "collaborative",
            "title": "From listeners like you",
            "uid": uid,
            "results": recs,
            "count": len(recs),
        }
        if debug_mode:
            payload["stats"] = collaborative_service.get_user_stats(uid)
        return jsonify(payload), 200
    except Exception as exc:
        logger.error("collaborative_recommendations() failed uid=%s: %s", uid, exc, exc_info=True)
        return jsonify({
            "source": "collaborative",
            "title": "From listeners like you",
            "uid": uid,
            "results": [],
            "count": 0,
            "error": "Failed to fetch collaborative recommendations",
        }), 200


@app.route("/api/debug/cf-status", methods=["GET"])
def collaborative_debug_status():
    uid = (request.args.get("uid") or "").strip()
    if not uid:
        return jsonify({"error": "uid is required"}), 400

    try:
        stats = collaborative_service.get_user_stats(uid)
        recs = collaborative_service.get_cf_recommendations(uid)
        diagnostics = {
            "meets_minimum_plays": int(stats.get("plays_count", 0)) >= collaborative_service.MIN_USER_PLAYS,
            "meets_minimum_similar_users": int(stats.get("similar_users_count", 0)) >= 1,
            "meets_minimum_recommendations": len(recs) >= collaborative_service.MIN_RECOMMENDATIONS,
            "can_show_on_home": len(recs) >= collaborative_service.MIN_RECOMMENDATIONS,
        }
        return jsonify({
            "firestore_available": get_firestore_client() is not None,
            "user_profile": {
                "has_profile": bool(stats.get("has_profile")),
                "artist_count": int(stats.get("artist_count", 0)),
                "plays_count": int(stats.get("plays_count", 0)),
                "top_artists": stats.get("top_artists", []),
            },
            "similar_users": {
                "count": int(stats.get("similar_users_count", 0)),
                "similarity_scores": stats.get("similarity_scores", []),
            },
            "recommendations": {
                "count": len(recs),
                "sample": recs[:5],
            },
            "diagnostics": diagnostics,
        }), 200
    except Exception as exc:
        logger.error("collaborative_debug_status() failed uid=%s: %s", uid, exc, exc_info=True)
        return jsonify({"error": "Failed to fetch CF status", "uid": uid}), 500


@app.route("/api/home", methods=["GET"])
def home():
    uid = (request.args.get("uid") or "").strip()
    
    # Fetch user's language preferences if uid provided
    user_languages = []
    if uid and uid != "guest":
        try:
            user_languages = user_service.get_user_languages(uid)
        except Exception as e:
            logger.warning(f"Failed to fetch language preferences for uid={uid}: {e}")
    
    # For signed-in users, use personalized recommender; guest falls back to trending.
    recommendation_songs = []
    if uid and uid != "guest":
        try:
            from services.personalized_recommender import get_recommendations as _get_personalized_recs
            raw_songs = _get_personalized_recs(uid, limit=12, lang_fallback=user_languages)
            raw_songs = _dedupe_response_songs(raw_songs)
            recommendation_songs = [_to_song_dto(song) for song in raw_songs]
            recommendation_songs = _dedupe_response_songs(recommendation_songs)
        except Exception as exc:
            logger.warning("/api/home personalized path failed for uid=%s: %s", uid, exc)

    if not recommendation_songs:
        fetch_limit = 36 if user_languages else 12
        recommendation_songs = _search_songs("trending songs", fetch_limit)
    
    # Filter by user's language preferences if available
    if user_languages:
        user_langs_lower = [lang.lower() for lang in user_languages]
        recommendation_songs = [s for s in recommendation_songs if s.get("language") and s["language"].lower() in user_langs_lower]
    
    # Final safety-net dedupe before returning the API payload.
    recommendation_songs = _dedupe_response_songs(recommendation_songs)

    # Limit to 12 items each
    recommendation_songs = recommendation_songs[:12]

    collaborative_songs: List[Dict[str, Any]] = []
    collaborative_section = {
        "title": "From listeners like you",
        "tracks": [],
        "count": 0,
    }

    if uid and uid != "guest":
        main_rec_ids = {
            str(song.get("videoId") or song.get("id") or "").strip()
            for song in recommendation_songs
            if str(song.get("videoId") or song.get("id") or "").strip()
        }

        recently_played_ids = {
            str(song.get("videoId") or song.get("id") or "").strip()
            for song in user_service.get_user_plays(uid, limit=50)
            if str(song.get("videoId") or song.get("id") or "").strip()
        }

        try:
            collaborative_songs = collaborative_service.get_cf_recommendations(
                uid,
                exclude_video_ids=recently_played_ids,
                main_rec_ids=main_rec_ids,
            )
            collaborative_songs = collaborative_songs[:12]
            collaborative_section = {
                "title": "From listeners like you",
                "tracks": collaborative_songs,
                "count": len(collaborative_songs),
            }
        except Exception as exc:
            logger.warning("/api/home collaborative path failed for uid=%s: %s", uid, exc)
    
    return jsonify(
        {
            "trending": [],
            "recommendations": recommendation_songs,
            "collaborative": collaborative_songs,
            "collaborative_recommendations": collaborative_section,
        }
    ), 200


@app.route("/api/home/top-artists", methods=["GET"])
def home_top_artists():
    uid = (request.args.get("uid") or "").strip()
    limit = max(1, min(int(request.args.get("limit", 10)), 20))

    if not uid:
        return jsonify({"success": True, "artists": [], "count": 0}), 200

    try:
        top_artist_rows = user_profile_service.get_top_artists(uid, limit=limit)
        artists = []

        for row in top_artist_rows:
            name = str(row.get("artist") or "").strip()
            if not name:
                continue

            image = ""
            try:
                search_results = jiosaavn_service.get_artists(name)
                if search_results:
                    first = search_results[0]
                    image_field = first.get("image")
                    if isinstance(image_field, list) and image_field:
                        best = image_field[-1]
                        if isinstance(best, dict):
                            image = str(best.get("url") or "")
                    elif isinstance(image_field, str):
                        image = image_field
            except Exception as search_exc:
                logger.warning("Top artist image lookup failed for '%s': %s", name, search_exc)

            artists.append({"name": name, "image": image})

        return jsonify({"success": True, "artists": artists, "count": len(artists)}), 200
    except Exception as exc:
        logger.error("Failed to get top artists for uid '%s': %s", uid, exc, exc_info=True)
        return jsonify({"success": False, "artists": [], "count": 0, "error": "Failed to fetch top artists"}), 500


@app.route("/api/artists/search", methods=["GET"])
def artists_search():
    query = (request.args.get("query") or "").strip()
    if not query:
        return jsonify({"success": True, "data": {"results": []}}), 200

    artists = jiosaavn_service.get_artists(query)
    return jsonify({"success": True, "data": {"results": artists}}), 200


@app.route("/api/artists/<artist_name>/songs", methods=["GET"])
def artist_songs(artist_name):
    cleaned_artist = (artist_name or "").strip()
    if not cleaned_artist:
        return jsonify({"artist": "", "songs": [], "albums": []}), 200

    raw_songs = jiosaavn_service.get_artist_songs(cleaned_artist)

    songs = []
    for song in raw_songs:
        # Match ArtistNameSongDto expected by Android.
        songs.append(
            {
                "id": str(song.get("id") or ""),
                "name": str(song.get("song") or song.get("title") or song.get("name") or ""),
                "album": song.get("album"),
                "primary_artists": song.get("primary_artists") or song.get("singers"),
                "singers": song.get("singers"),
                "starring": song.get("starring"),
                "language": song.get("language"),
                "play_count": int(song.get("play_count") or 0),
                "image": song.get("image"),
                "media_url": song.get("media_url") or song.get("url"),
            }
        )

    return jsonify({"artist": cleaned_artist, "songs": songs, "albums": []}), 200


@app.route("/api/moods/playlists", methods=["GET"])
def mood_playlists():
    mood = (request.args.get("mood") or "").strip()
    if not mood:
        return jsonify({"success": True, "mood": "", "playlists": [], "count": 0}), 200

    playlists = jiosaavn_service.get_mood_playlists(mood)
    return jsonify(
        {
            "success": True,
            "mood": mood,
            "playlists": playlists,
            "count": len(playlists),
        }
    ), 200


@app.route("/api/playlists/songs", methods=["GET"])
def get_playlist_songs_by_url():
    playlist_url = (request.args.get("playlist_url") or "").strip()
    
    if not playlist_url:
        return jsonify({"success": False, "data": [], "error": "playlist_url parameter is required"}), 400
    
    logger.info(f"Fetching playlist songs for URL: {playlist_url}")
    songs = jiosaavn_service.get_playlist_songs_from_url(playlist_url)
    
    # Convert to DTO format for Android
    results = [_to_song_dto(song) for song in songs]
    
    return jsonify(
        {
            "success": True,
            "data": results
        }
    ), 200


@app.route("/api/playlist", methods=["GET"])
def get_playlist_by_query():
    """Get playlist details and songs by URL or ID (query parameter)."""
    query = (request.args.get("query") or "").strip()
    
    if not query:
        return jsonify({"success": False, "error": "query parameter is required"}), 400
    
    logger.info(f"Fetching playlist for query: {query}")
    
    # Check if it's a URL or direct ID
    if query.startswith("http"):
        songs = jiosaavn_service.get_playlist_songs(query)
    else:
        # Assume it's a playlist ID
        from services.jiosaavn_service import get_playlist_details
        playlist_data = get_playlist_details(query)
        if playlist_data:
            songs = playlist_data.get("songs", [])
        else:
            songs = []
    
    # Convert to DTO format for Android
    results = [_to_song_dto(song) for song in songs]
    
    return jsonify({
        "success": True,
        "songs": results,
        "count": len(results)
    }), 200


@app.route("/api/playlist/<playlist_id>", methods=["GET"])
def get_playlist_by_id(playlist_id):
    """Get playlist details and songs by ID (path parameter)."""
    if not playlist_id or not playlist_id.strip():
        return jsonify({"success": False, "error": "playlist_id is required"}), 400
    
    logger.info(f"Fetching playlist for ID: {playlist_id}")
    
    from services.jiosaavn_service import get_playlist_details
    playlist_data = get_playlist_details(playlist_id)
    
    if not playlist_data:
        return jsonify({"success": False, "error": "Playlist not found"}), 404
    
    # Extract songs and convert to DTO format
    songs = playlist_data.get("songs", [])
    results = [_to_song_dto(song) for song in songs]
    
    title = str(playlist_data.get("listname") or playlist_data.get("title") or "")
    author = str(playlist_data.get("firstname") or "JioSaavn")
    thumbnail = str(playlist_data.get("image") or "")

    # Return in PlaylistDetailResponse format expected by Android.
    return jsonify(
        {
            "success": True,
            "playlist": {
                "playlistId": str(playlist_id),
                "browseId": str(playlist_id),
                "title": title,
                "author": author,
                "thumbnail": thumbnail,
                "trackCount": len(results),
                "duration": None,
                "description": None,
                "songs": results,
            },
            "cached": False,
        }
    ), 200


@app.route("/api/playlist/<playlist_id>/songs", methods=["GET"])
def get_playlist_songs(playlist_id):
    """Get playlist songs by ID with limit."""
    if not playlist_id or not playlist_id.strip():
        return jsonify({"success": False, "error": "playlist_id is required"}), 400
    
    limit = min(int(request.args.get("limit", 50)), 100)
    
    logger.info(f"Fetching playlist songs for ID: {playlist_id}")
    
    from services.jiosaavn_service import get_playlist_details
    playlist_data = get_playlist_details(playlist_id)
    
    if not playlist_data:
        return jsonify({"success": False, "error": "Playlist not found"}), 404
    
    # Extract songs and convert to DTO format
    songs = playlist_data.get("songs", [])[:limit]
    results = [_to_song_dto(song) for song in songs]
    
    return jsonify({
        "success": True,
        "data": results,
        "count": len(results)
    }), 200


@app.route("/api/album", methods=["GET"])
def get_album_by_query():
    """Get album details and songs by URL or ID (query parameter)."""
    query = (request.args.get("query") or "").strip()
    
    if not query:
        return jsonify({"success": False, "error": "query parameter is required"}), 400
    
    logger.info(f"Fetching album for query: {query}")
    
    # Check if it's a URL or direct ID
    if query.startswith("http"):
        songs = jiosaavn_service.get_album_songs(query)
    else:
        # Assume it's an album ID
        from services.jiosaavn_service import get_album_details
        album_data = get_album_details(query)
        if album_data:
            songs = album_data.get("songs", [])
        else:
            songs = []
    
    # Convert to DTO format for Android
    results = [_to_song_dto(song) for song in songs]
    
    return jsonify({
        "success": True,
        "songs": results,
        "count": len(results)
    }), 200


@app.route("/api/album/<album_id>", methods=["GET"])
def get_album_by_id(album_id):
    """Get album details and songs by ID (path parameter)."""
    if not album_id or not album_id.strip():
        return jsonify({"success": False, "error": "album_id is required"}), 400
    
    logger.info(f"Fetching album for ID: {album_id}")
    
    from services.jiosaavn_service import get_album_details
    album_data = get_album_details(album_id)
    
    if not album_data:
        return jsonify({"success": False, "error": "Album not found"}), 404
    
    # Extract songs and convert to DTO format
    songs = album_data.get("songs", [])
    results = [_to_song_dto(song) for song in songs]
    
    title = str(album_data.get("title") or album_data.get("name") or "")
    primary_artists = str(album_data.get("primary_artists") or album_data.get("music") or "")
    artists = [a.strip() for a in primary_artists.split(",") if a.strip()]
    raw_year = album_data.get("year")
    year_value = None
    try:
        if raw_year not in (None, ""):
            year_value = int(raw_year)
    except Exception:
        year_value = None

    # Return in AlbumDetailResponse format expected by Android.
    return jsonify(
        {
            "success": True,
            "album": {
                "albumId": str(album_id),
                "browseId": str(album_id),
                "title": title,
                "artist": primary_artists,
                "artists": artists,
                "thumbnail": str(album_data.get("image") or ""),
                "year": year_value,
                "trackCount": len(results),
                "duration": None,
                "songs": results,
            },
            "cached": False,
        }
    ), 200


@app.route("/api/song", methods=["GET"])
def get_song_by_query():
    """Get single song details by ID (query parameter)."""
    song_id = (request.args.get("id") or "").strip()
    
    if not song_id:
        return jsonify({"success": False, "error": "id parameter is required"}), 400
    
    logger.info(f"Fetching song details for ID: {song_id}")
    
    from services.jiosaavn_service import get_song_details
    song_data = get_song_details(song_id, include_lyrics=False)
    
    if not song_data:
        return jsonify({"success": False, "error": "Song not found"}), 404
    
    # Convert to DTO format
    result = _to_song_dto(song_data)
    
    return jsonify({
        "success": True,
        "data": result
    }), 200


@app.route("/api/song/<video_id>", methods=["GET"])
def get_song_by_id(video_id):
    """Get single song details by ID (path parameter)."""
    if not video_id or not video_id.strip():
        return jsonify({"success": False, "error": "video_id is required"}), 400
    
    logger.info(f"Fetching song details for video ID: {video_id}")
    
    from services.jiosaavn_service import get_song_details
    song_data = get_song_details(video_id, include_lyrics=False)
    
    if not song_data:
        return jsonify({"success": False, "error": "Song not found"}), 404
    
    # Convert to DTO format
    result = _to_song_dto(song_data)
    
    return jsonify({
        "success": True,
        "data": result
    }), 200


@app.route("/api/lyrics", methods=["GET"])
def get_lyrics_by_query():
    """Get lyrics for a song by ID (query parameter)."""
    song_id = (request.args.get("id") or "").strip()
    
    if not song_id:
        return jsonify({"success": False, "error": "id parameter is required"}), 400
    
    logger.info(f"Fetching lyrics for song ID: {song_id}")
    
    from services.jiosaavn_service import get_lyrics as fetch_lyrics
    lyrics_data = fetch_lyrics(song_id)
    
    if not lyrics_data or not lyrics_data.get("lyrics"):
        return jsonify({
            "success": False,
            "lyrics": None,
            "message": "Lyrics not available"
        }), 404
    
    return jsonify({
        "success": True,
        "lyrics": lyrics_data.get("lyrics"),
        "copyright": lyrics_data.get("copyright")
    }), 200


@app.route("/api/user/<uid>/languages", methods=["GET"])
def get_user_languages(uid):
    """Get user's language preferences."""
    if not uid or not uid.strip():
        return jsonify({
            "success": False,
            "error": "User ID is required"
        }), 400
    
    logger.info(f"Fetching language preferences for user: {uid}")
    
    try:
        languages = user_service.get_user_languages(uid)
        
        return jsonify({
            "success": True,
            "languages": languages,
            "count": len(languages)
        }), 200
    except Exception as e:
        logger.error(f"Error fetching languages for user {uid}: {str(e)}")
        return jsonify({
            "success": False,
            "error": "Failed to fetch language preferences"
        }), 500


@app.route("/api/user/<uid>/languages", methods=["POST"])
def update_user_languages(uid):
    """Update user's language preferences."""
    if not uid or not uid.strip():
        return jsonify({
            "success": False,
            "error": "User ID is required"
        }), 400
    
    # Get languages from request body
    data = request.get_json()
    if not data or 'languages' not in data:
        return jsonify({
            "success": False,
            "error": "languages field is required in request body"
        }), 400
    
    languages = data.get('languages', [])
    
    if not isinstance(languages, list):
        return jsonify({
            "success": False,
            "error": "languages must be an array"
        }), 400
    
    logger.info(f"Updating language preferences for user {uid}: {languages}")
    
    try:
        success, error = user_service.update_user_languages(uid, languages)
        
        if not success:
            return jsonify({
                "success": False,
                "error": error
            }), 400
        
        return jsonify({
            "success": True,
            "message": "Language preferences updated successfully",
            "languages": [lang.lower().strip() for lang in languages]
        }), 200
    except Exception as e:
        logger.error(f"Error updating languages for user {uid}: {str(e)}")
        return jsonify({
            "success": False,
            "error": "Failed to update language preferences"
        }), 500


@app.route("/api/languages/supported", methods=["GET"])
def get_supported_languages():
    """Get list of supported languages."""
    return jsonify({
        "success": True,
        "languages": UserService.SUPPORTED_LANGUAGES,
        "count": len(UserService.SUPPORTED_LANGUAGES)
    }), 200


@app.route("/api/playlists/<playlist_id>", methods=["GET"])
def get_user_playlist(playlist_id):
    user_id = str(request.args.get("userId") or "").strip()

    if not playlist_id or not playlist_id.strip():
        return jsonify({"success": False, "error": "playlistId is required"}), 400
    if not user_id:
        return jsonify({"success": False, "error": "userId is required"}), 400

    db = get_firestore_client()
    if db is None:
        return jsonify({"success": False, "error": "Firestore unavailable"}), 503

    try:
        playlist_ref = db.collection("users").document(user_id).collection("playlists").document(playlist_id)
        playlist_snapshot = playlist_ref.get()
        if not playlist_snapshot.exists:
            return jsonify({"success": False, "error": "Playlist not found"}), 404

        playlist_data = playlist_snapshot.to_dict() or {}
        song_docs = playlist_ref.collection("songs").stream()

        songs = []
        for song_doc in song_docs:
            song_data = song_doc.to_dict() or {}
            song_id = str(song_data.get("videoId") or song_data.get("id") or song_doc.id)
            title = str(song_data.get("title") or song_data.get("song") or song_data.get("name") or "").strip()
            if not song_id or not title:
                continue

            artists = _to_string_list(song_data.get("artists"))
            artist_text = str(song_data.get("artist") or song_data.get("primary_artists") or "").strip()
            if not artists and artist_text:
                artists = [part.strip() for part in artist_text.split(",") if part.strip()]

            songs.append(
                {
                    "videoId": song_id,
                    "title": title,
                    "artist": artist_text or (artists[0] if artists else ""),
                    "artists": artists,
                    "thumbnail": str(song_data.get("thumbnail") or song_data.get("image") or ""),
                    "duration": str(song_data.get("duration") or ""),
                    "url": str(song_data.get("url") or song_data.get("media_url") or ""),
                    "album": str(song_data.get("album") or ""),
                    "artistId": str(song_data.get("artistId") or ""),
                    "play_count": _safe_int(song_data.get("play_count") or song_data.get("global_play_count"), 0),
                    "language": str(song_data.get("language") or "unknown"),
                    "year": str(song_data.get("year") or "unknown"),
                    "starring": str(song_data.get("starring") or ""),
                }
            )

        created_at = playlist_data.get("createdAt")
        created_at_text = ""
        if created_at is not None:
            try:
                created_at_text = created_at.isoformat()
            except Exception:
                created_at_text = str(created_at)

        return jsonify(
            {
                "success": True,
                "playlist": {
                    "id": playlist_id,
                    "name": str(playlist_data.get("name") or "My Playlist"),
                    "description": str(playlist_data.get("description") or "") or None,
                    "userId": user_id,
                    "songs": songs,
                    "coverImage": None,
                    "createdAt": created_at_text or None,
                },
            }
        ), 200
    except Exception as exc:
        logger.error("Failed to get playlist user=%s playlist=%s err=%s", user_id, playlist_id, exc, exc_info=True)
        return jsonify({"success": False, "error": "Failed to fetch playlist"}), 500


@app.route("/api/play", methods=["POST"])
def track_play():
    data = request.get_json(silent=True) or {}
    uid = str(data.get("uid") or "").strip()
    song_payload = data.get("song") or {}

    if not uid:
        return jsonify({"success": False, "error": "uid is required"}), 400
    if not isinstance(song_payload, dict):
        return jsonify({"success": False, "error": "song must be an object"}), 400

    mapped = _enrich_tracking_song(song_payload, _normalize_tracking_song(song_payload))
    song_id = mapped.get("songId") or ""
    if not song_id:
        return jsonify({"success": False, "error": "song.id or song.videoId is required"}), 400

    db = get_firestore_client()
    if db is None or admin_firestore is None:
        return jsonify({"success": False, "error": "Firestore unavailable"}), 503

    logger.info("[PLAY EVENT] user=%s song=%s", uid, song_id)

    doc_ref = db.collection("users").document(uid).collection("plays").document(song_id)
    transaction = db.transaction()

    @admin_firestore.transactional
    def _apply_play(txn):
        snapshot = doc_ref.get(transaction=txn)
        song_doc = _safe_song_document(mapped)
        if snapshot.exists:
            txn.update(
                doc_ref,
                {
                    "user_play_count": admin_firestore.Increment(1),
                    "lastPlayedAt": admin_firestore.SERVER_TIMESTAMP,
                },
            )
        else:
            txn.set(
                doc_ref,
                {
                    **song_doc,
                    "user_play_count": 1,
                    "firstPlayedAt": admin_firestore.SERVER_TIMESTAMP,
                    "lastPlayedAt": admin_firestore.SERVER_TIMESTAMP,
                },
            )

    try:
        _apply_play(transaction)
        return jsonify({"success": True, "songId": song_id}), 200
    except Exception as exc:
        logger.error("Failed to track play user=%s song=%s err=%s", uid, song_id, exc, exc_info=True)
        return jsonify({"success": False, "error": "Failed to track play"}), 500


@app.route("/api/like", methods=["POST"])
def track_like():
    data = request.get_json(silent=True) or {}
    uid = str(data.get("uid") or "").strip()
    song_payload = data.get("song") or {}

    if not uid:
        return jsonify({"success": False, "error": "uid is required"}), 400
    if not isinstance(song_payload, dict):
        return jsonify({"success": False, "error": "song must be an object"}), 400

    mapped = _enrich_tracking_song(song_payload, _normalize_tracking_song(song_payload))
    song_id = mapped.get("songId") or ""
    if not song_id:
        return jsonify({"success": False, "error": "song.id or song.videoId is required"}), 400

    db = get_firestore_client()
    if db is None or admin_firestore is None:
        return jsonify({"success": False, "error": "Firestore unavailable"}), 503

    logger.info("[LIKE EVENT] user=%s song=%s", uid, song_id)

    try:
        doc_ref = db.collection("users").document(uid).collection("likedSongs").document(song_id)
        song_doc = _safe_song_document(mapped)
        doc_ref.set(
            {
                **song_doc,
                "likedAt": admin_firestore.SERVER_TIMESTAMP,
            },
            merge=True,
        )
        return jsonify({"success": True, "songId": song_id}), 200
    except Exception as exc:
        logger.error("Failed to track like user=%s song=%s err=%s", uid, song_id, exc, exc_info=True)
        return jsonify({"success": False, "error": "Failed to track like"}), 500


@app.route("/api/playlists/<playlist_id>/songs", methods=["POST"])
def add_song_to_user_playlist(playlist_id):
    data = request.get_json(silent=True) or {}
    uid = str(data.get("userId") or "").strip()
    song_payload = data.get("song") or {}

    if not playlist_id or not playlist_id.strip():
        return jsonify({"success": False, "error": "playlistId is required"}), 400
    if not uid:
        return jsonify({"success": False, "error": "userId is required"}), 400
    if not isinstance(song_payload, dict):
        return jsonify({"success": False, "error": "song must be an object"}), 400

    mapped = _enrich_tracking_song(song_payload, _normalize_tracking_song(song_payload))
    song_id = mapped.get("songId") or ""
    if not song_id:
        return jsonify({"success": False, "error": "song.videoId is required"}), 400

    db = get_firestore_client()
    if db is None or admin_firestore is None:
        return jsonify({"success": False, "error": "Firestore unavailable"}), 503

    playlist_ref = db.collection("users").document(uid).collection("playlists").document(playlist_id)
    song_ref = playlist_ref.collection("songs").document(song_id)

    song_doc = {
        **_safe_song_document(mapped),
        "addedAt": admin_firestore.SERVER_TIMESTAMP,
    }

    tx = db.transaction()

    @admin_firestore.transactional
    def _apply(txn):
        playlist_snapshot = playlist_ref.get(transaction=txn)
        song_snapshot = song_ref.get(transaction=txn)

        if not playlist_snapshot.exists:
            txn.set(
                playlist_ref,
                {
                    "name": "My Playlist",
                    "createdAt": admin_firestore.SERVER_TIMESTAMP,
                    "songCount": 0,
                },
                merge=True,
            )

        if song_snapshot.exists:
            return

        txn.set(song_ref, song_doc, merge=True)
        txn.set(
            playlist_ref,
            {"songCount": admin_firestore.Increment(1)},
            merge=True,
        )

    try:
        _apply(tx)
        playlist_snapshot = playlist_ref.get()
        playlist_data = playlist_snapshot.to_dict() or {}
        playlist_title = str(playlist_data.get("name") or "My Playlist")
        track_count = int(playlist_data.get("songCount") or 0)

        return jsonify(
            {
                "success": True,
                "playlist": {
                    "id": playlist_id,
                    "name": playlist_title,
                    "description": "",
                    "userId": uid,
                    "songs": [],
                    "coverImage": None,
                    "createdAt": None,
                    "trackCount": track_count,
                },
            }
        ), 200
    except Exception as exc:
        logger.error(
            "Failed to add song to playlist user=%s playlist=%s song=%s err=%s",
            uid,
            playlist_id,
            song_id,
            exc,
            exc_info=True,
        )
        return jsonify({"success": False, "error": "Failed to add song to playlist"}), 500


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=PORT, debug=False)
