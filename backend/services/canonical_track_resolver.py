import re
from typing import Dict, Iterable, List, Optional, Set


COMPILATION_KEYWORDS = (
    "hits",
    "best",
    "special",
    "collection",
    "jukebox",
    "playlist",
    "valentine",
    "love",
    "tunes",
    "director",
    "top",
    "mix",
)

# Detect variants such as:
#   Sir Osthara (From 'Businessman')
#   Sir Osthara from "Businessman"
FROM_MOVIE_RE = re.compile(r"\(?\s*from\s*['\"].+?['\"]\s*\)?", re.IGNORECASE)
FROM_MOVIE_CAPTURE_RE = re.compile(r"from\s*['\"](.+?)['\"]", re.IGNORECASE)
NON_ALNUM_RE = re.compile(r"[^a-z0-9]+")

ARTIST_ALIASES = {
    "ss thaman": "s thaman",
    "s s thaman": "s thaman",
    "thaman s": "s thaman",
}


def _clean_text(value: str) -> str:
    text = str(value or "").strip().lower()
    text = NON_ALNUM_RE.sub(" ", text)
    return re.sub(r"\s+", " ", text).strip()


def _normalize_artist_identity(value: str) -> str:
    # Normalization moved to shared utility; keep fallback for safety.
    try:
        from services.artist_normalization import normalized_artist_key

        return normalized_artist_key(value)
    except Exception:
        cleaned = _clean_text(value)
        if not cleaned:
            return ""
        return ARTIST_ALIASES.get(cleaned, cleaned)


def _first_artist_name(artists) -> str:
    names: List[str] = []
    if isinstance(artists, list):
        for artist in artists:
            if isinstance(artist, dict):
                raw = artist.get("name") or artist.get("artist")
            else:
                raw = artist
            cleaned = _normalize_artist_identity(str(raw or ""))
            if cleaned:
                names.append(cleaned)
    else:
        artist_text = str(artists or "")
        for part in artist_text.split(","):
            cleaned = _normalize_artist_identity(part)
            if cleaned:
                names.append(cleaned)
    if not names:
        return ""
    # Return the first artist as per original order
    return names[0]


def _song_title(song: Dict) -> str:
    return str(song.get("song") or song.get("title") or song.get("name") or "").strip()


def _song_album(song: Dict) -> str:
    album = song.get("album")
    if isinstance(album, dict):
        return str(album.get("name") or "").strip()
    return str(album or "").strip()


def _song_artists(song: Dict):
    artists = song.get("artists")
    if artists:
        return artists
    return song.get("primary_artists") or song.get("artist") or song.get("singers") or ""


def _song_year(song: Dict) -> Optional[int]:
    raw = song.get("year")
    if raw in (None, ""):
        return None
    try:
        year = int(str(raw).strip()[:4])
    except Exception:
        return None
    if 1900 <= year <= 2100:
        return year
    return None


def _song_play_count(song: Dict) -> int:
    try:
        return int(song.get("global_play_count") or song.get("play_count") or 0)
    except Exception:
        return 0


def _extract_from_movie_name(title: str) -> str:
    match = FROM_MOVIE_CAPTURE_RE.search(str(title or ""))
    if not match:
        return ""
    return _clean_text(match.group(1))


def normalize_song_identity(title, artists) -> str:
    normalized_title = FROM_MOVIE_RE.sub(" ", str(title or "").lower())
    normalized_title = _clean_text(normalized_title)
    primary_artist = _first_artist_name(artists)
    return f"{normalized_title}_{primary_artist}".strip("_")


def is_compilation_album(album_name) -> bool:
    album = str(album_name or "").strip().lower()
    if not album:
        return False
    return any(keyword in album for keyword in COMPILATION_KEYWORDS)


def group_duplicate_tracks(songs: Iterable[Dict]) -> Dict[str, List[Dict]]:
    grouped: Dict[str, List[Dict]] = {}
    for song in songs or []:
        key = normalize_song_identity(_song_title(song), _song_artists(song))
        if not key:
            song_id = str(song.get("id") or "")
            key = song_id or f"fallback_{len(grouped)}"
        grouped.setdefault(key, []).append(song)
    return grouped


def select_canonical_track(songs: List[Dict]) -> Dict:
    if not songs:
        return {}
    if len(songs) == 1:
        return songs[0]

    earliest_year = min((year for year in (_song_year(song) for song in songs) if year is not None), default=None)
    highest_play_count = max((_song_play_count(song) for song in songs), default=0)

    best_song = songs[0]
    best_rank = None

    for index, song in enumerate(songs):
        title = _song_title(song)
        album = _song_album(song)
        year = _song_year(song)
        play_count = _song_play_count(song)

        # Priority scoring in requested order.
        score = 0
        if not is_compilation_album(album):
            score += 5
        if not FROM_MOVIE_RE.search(title or ""):
            score += 4
        if earliest_year is not None and year == earliest_year:
            score += 3
        if highest_play_count > 0 and play_count == highest_play_count:
            score += 1

        # Additional deterministic tie-breaker: album name equals extracted movie title.
        extracted_movie = _extract_from_movie_name(title)
        if extracted_movie and _clean_text(album) == extracted_movie:
            score += 3

        rank_tuple = (score, -(year or 9999), play_count, -index)
        if best_rank is None or rank_tuple > best_rank:
            best_rank = rank_tuple
            best_song = song

    return best_song


def resolve_canonical_tracks(songs: List[Dict]) -> List[Dict]:
    if not songs:
        return []

    grouped = group_duplicate_tracks(songs)
    ordered_keys: List[str] = []
    seen_keys = set()

    for song in songs:
        key = normalize_song_identity(_song_title(song), _song_artists(song))
        if not key:
            key = str(song.get("id") or "") or f"fallback_{len(ordered_keys)}"
        if key not in seen_keys:
            seen_keys.add(key)
            ordered_keys.append(key)

    resolved: List[Dict] = []
    for key in ordered_keys:
        candidates = grouped.get(key, [])
        if candidates:
            resolved.append(select_canonical_track(candidates))

    return resolved


def filter_from_movie_variants(
    songs: List[Dict],
    user_song_names: Optional[Set[str]] = None,
) -> List[Dict]:
    """
    Retain a song whose title carries a '(From "Movie")' / 'from "Movie"' suffix
    only when at least one of the following conditions holds:

    1. **Album match** — the song's metadata album name is the same as the
       movie/film name stated in the title suffix.  This indicates the song
       belongs to the actual original soundtrack album, not a compilation.

    2. **User history match** — the base title (with the 'From …' clause
       stripped) normalises to a value present in ``user_song_names``, meaning
       the user has previously played or liked a version of this track.

    Songs that do not contain the pattern are always kept unchanged.

    Parameters
    ----------
    songs:
        Raw song dicts from JioSaavn or the recommendation pipeline.
    user_song_names:
        A set of ``_clean_text``-normalised base song titles built from the
        user's play and liked-song history.  Pass ``None`` (or an empty set)
        to disable condition 2 and rely solely on the album-match check.
    """
    _user_names: Set[str] = user_song_names or set()
    result: List[Dict] = []

    for song in songs:
        title = _song_title(song)
        movie_match = FROM_MOVIE_CAPTURE_RE.search(title)

        if not movie_match:
            # No 'From Movie' pattern — always include.
            result.append(song)
            continue

        movie_name = _clean_text(movie_match.group(1))
        album = _clean_text(_song_album(song))

        # Condition 1: metadata album equals the movie name in the title.
        if album and movie_name and album == movie_name:
            result.append(song)
            continue

        # Condition 2: the base title is found in the user's listening history.
        if _user_names:
            base_title = _clean_text(FROM_MOVIE_RE.sub(" ", title))
            if base_title and base_title in _user_names:
                result.append(song)
                continue

    return result


# Backward-compatible aliases for existing internal imports.
normalize_song_key = normalize_song_identity
group_duplicate_songs = group_duplicate_tracks
select_canonical_song = select_canonical_track
resolve_canonical_versions = resolve_canonical_tracks
