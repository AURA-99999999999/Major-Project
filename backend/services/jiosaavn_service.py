"""AURA JioSaavn integration service.

Implements direct calls to JioSaavn API without needing a separate Flask server.
"""

import base64
import json
import logging
import os
import re
import warnings
from typing import Any, Dict, List, Optional

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

from services.canonical_track_resolver import resolve_canonical_tracks
from utils.jiosaavn_client import JIOSAAVN_API_URL, jiosaavn_request

logger = logging.getLogger(__name__)

# JioSaavn API endpoints
JIOSAAVN_SEARCH_BASE = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query="
JIOSAAVN_SONG_DETAILS_BASE = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0%3F_marker%3D0&_format=json&pids="
JIOSAAVN_ALBUM_DETAILS_BASE = "https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&cc=in&_marker=0%3F_marker%3D0&albumid="
JIOSAAVN_PLAYLIST_DETAILS_BASE = "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&cc=in&_marker=0%3F_marker%3D0&listid="
JIOSAAVN_LYRICS_BASE = "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&ctx=web6dot0&api_version=4&_format=json&_marker=0%3F_marker%3D0&lyrics_id="

# External API for artists and mood playlists only
SAAVN_SUMIT_BASE = "https://saavn.sumit.co/api"

# Network tuning for flaky upstream endpoints.
HTTP_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "5"))
HTTP_RETRIES = int(os.getenv("REQUEST_RETRIES", "2"))

# Shared session for connection pooling
session = requests.Session()
_retry = Retry(
    total=HTTP_RETRIES,
    connect=HTTP_RETRIES,
    read=HTTP_RETRIES,
    backoff_factor=0.5,
    status_forcelist=(429, 500, 502, 503, 504),
    allowed_methods=frozenset(["GET"]),
)
_adapter = HTTPAdapter(max_retries=_retry, pool_connections=20, pool_maxsize=20)
session.mount("https://", _adapter)
session.mount("http://", _adapter)


def _web_search_params(query: str, limit: int) -> Dict[str, str]:
    bounded_limit = max(1, min(int(limit or 20), 50))
    return {
        "__call": "search.getResults",
        "q": query,
        "p": "1",
        "n": str(bounded_limit),
        "api_version": "4",
        "_format": "json",
    }


def _coerce_web_payload(payload: Any) -> Dict[str, Any]:
    """Normalize api.php payloads that may arrive as nested JSON strings."""
    current = payload
    for _ in range(3):
        if isinstance(current, dict):
            return current
        if isinstance(current, str):
            text = current.strip()
            if not text:
                return {}
            try:
                current = json.loads(text)
                continue
            except Exception:
                try:
                    decoded = text.encode("utf-8", errors="ignore").decode("unicode_escape", errors="ignore")
                    current = json.loads(decoded)
                    continue
                except Exception:
                    return {}
        break
    return current if isinstance(current, dict) else {}


def _extract_web_song_results(payload: Any) -> List[Dict[str, Any]]:
    payload_obj = _coerce_web_payload(payload)
    if not isinstance(payload_obj, dict):
        return []

    results = payload_obj.get("results")
    if isinstance(results, dict):
        songs = results.get("songs")
        if isinstance(songs, dict):
            songs_data = songs.get("data")
            if isinstance(songs_data, list):
                return [item for item in songs_data if isinstance(item, dict)]
        if isinstance(songs, list):
            return [item for item in songs if isinstance(item, dict)]

    # Fallback for variant payloads seen on older endpoints.
    songs = payload_obj.get("songs")
    if isinstance(songs, dict):
        songs_data = songs.get("data")
        if isinstance(songs_data, list):
            return [item for item in songs_data if isinstance(item, dict)]
    elif isinstance(songs, list):
        return [item for item in songs if isinstance(item, dict)]

    data_obj = payload_obj.get("data")
    if isinstance(data_obj, list):
        return [item for item in data_obj if isinstance(item, dict)]
    if isinstance(data_obj, dict):
        nested_results = data_obj.get("results")
        if isinstance(nested_results, list):
            return [item for item in nested_results if isinstance(item, dict)]

    return []


def _map_web_song_to_internal(song: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(song, dict):
        return {}

    album = song.get("album")
    album_name = ""
    if isinstance(album, dict):
        album_name = str(album.get("name") or "").strip()
    elif album:
        album_name = str(album).strip()

    artists = song.get("artists")
    primary_artists = song.get("primary_artists")
    if isinstance(artists, list) and artists and not primary_artists:
        artist_names = []
        for item in artists:
            if isinstance(item, dict):
                name = str(item.get("name") or item.get("artist") or "").strip()
            else:
                name = str(item or "").strip()
            if name:
                artist_names.append(name)
        primary_artists = ", ".join(artist_names)

    mapped = dict(song)
    mapped["id"] = str(song.get("id") or song.get("songid") or "")
    mapped["title"] = str(song.get("title") or song.get("song") or song.get("name") or "")
    mapped["song"] = mapped["title"]
    mapped["primary_artists"] = str(primary_artists or song.get("artist") or song.get("singers") or "")
    mapped["artist"] = mapped["primary_artists"]
    mapped["album"] = album_name
    mapped["language"] = str(song.get("language") or "")
    mapped["duration"] = str(song.get("duration") or "")

    image = song.get("image")
    if isinstance(image, list):
        best = image[-1] if image else {}
        mapped["image"] = str(best.get("link") or best.get("url") or "")
    else:
        mapped["image"] = str(image or "")

    stream_url = song.get("stream_url") or song.get("media_url") or song.get("url") or ""
    mapped["stream_url"] = str(stream_url)
    mapped["media_url"] = str(song.get("media_url") or stream_url)
    return mapped


def search_songs_web(query: str, limit: int = 20) -> List[Dict[str, Any]]:
    if not query or not query.strip():
        return []

    payload = jiosaavn_request(JIOSAAVN_API_URL, params=_web_search_params(query, limit))
    if payload is None:
        return []

    songs = _extract_web_song_results(payload)
    return [_map_web_song_to_internal(song) for song in songs if isinstance(song, dict)]


def _fetch_text(url: str) -> Optional[str]:
    try:
        response = session.get(url, timeout=HTTP_TIMEOUT)
        return response.text
    except Exception:
        return None


def _fetch_json(url: str) -> Optional[Any]:
    try:
        response = session.get(url, timeout=HTTP_TIMEOUT)
        return response.json()
    except Exception:
        return None


# ========================================
# Helper Functions
# ========================================

def _decrypt_url(encrypted_url: str) -> str:
    """Decrypt JioSaavn media URL."""
    try:
        from pyDes import des, ECB, PAD_PKCS5
        des_cipher = des(b"38346591", ECB, b"\0\0\0\0\0\0\0\0", pad=None, padmode=PAD_PKCS5)
        enc_url = base64.b64decode(encrypted_url.strip())
        dec_url = des_cipher.decrypt(enc_url, padmode=PAD_PKCS5).decode('utf-8')
        dec_url = dec_url.replace("_96.mp4", "_320.mp4")
        return dec_url
    except Exception as e:
        logger.warning(f"URL decryption failed: {e}")
        return ""


def _format_string(text: str) -> str:
    """Format text by removing HTML entities."""
    if not text:
        return ""
    return (text.encode()
            .decode()
            .replace("&quot;", "'")
            .replace("&amp;", "&")
            .replace("&#039;", "'"))


def _normalize_from_movie_quotes(response_text: str) -> str:
    """Normalize problematic (From "Movie") fragments without corrupting JSON."""
    if not response_text:
        return ""
    pattern = r'\(From "([^"]+)"\)'
    return re.sub(pattern, r"(From '\1')", response_text)


def _parse_json_response(response_text: str, context: str) -> Optional[Any]:
    """
    Parse JSON from flaky upstream payloads safely.

    Strategy:
    1. Parse raw response text as-is.
    2. Parse unicode-escape-decoded text.
    3. Apply From-Movie quote normalization for both forms.
    4. Try raw_decode to recover when there is trailing noise.
    """
    candidates: List[str] = []

    raw = response_text or ""
    if raw:
        candidates.append(raw)

    try:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", DeprecationWarning)
            decoded = raw.encode("utf-8", errors="ignore").decode("unicode_escape", errors="ignore")
        if decoded and decoded != raw:
            candidates.append(decoded)
    except Exception:
        pass

    seen = set()
    decoder = json.JSONDecoder()
    last_error: Optional[Exception] = None

    for candidate in candidates:
        if not candidate:
            continue

        for payload in (candidate, _normalize_from_movie_quotes(candidate)):
            if not payload or payload in seen:
                continue
            seen.add(payload)

            try:
                return json.loads(payload)
            except Exception as exc:
                last_error = exc

            try:
                obj, _ = decoder.raw_decode(payload.lstrip())
                return obj
            except Exception:
                pass

    snippet = raw[:220].replace("\n", " ")
    logger.warning(
        "Failed to parse JioSaavn JSON (%s): %s | snippet=%r",
        context,
        last_error,
        snippet,
    )
    return None


def _format_song(song_data: Dict[str, Any], include_lyrics: bool = False) -> Dict[str, Any]:
    """Format song data with media URL and clean text."""
    if not isinstance(song_data, dict):
        return {}
    
    try:
        # Decrypt media URL
        encrypted_url = song_data.get('encrypted_media_url', '')
        media_url = None
        if encrypted_url:
            media_url = _decrypt_url(encrypted_url)
            if media_url:
                logger.info(f"Decryption success for song: {song_data.get('id', song_data.get('song', ''))}")
            else:
                logger.warning(f"Decryption failed for song: {song_data.get('id', song_data.get('song', ''))}, falling back to original URL.")
            # Adjust quality based on availability
            if song_data.get('320kbps') != "true" and media_url:
                media_url = media_url.replace("_320.mp4", "_160.mp4")
        if not media_url:
            # Fallback to original or preview URL
            media_url = song_data.get('url') or song_data.get('media_url') or song_data.get('media_preview_url', '')
            if not media_url:
                preview_url = song_data.get('media_preview_url', '')
                if preview_url:
                    media_url = preview_url.replace("preview", "aac")
                    if song_data.get('320kbps') == "true":
                        media_url = media_url.replace("_96_p.mp4", "_320.mp4")
                    else:
                        media_url = media_url.replace("_96_p.mp4", "_160.mp4")
                else:
                    media_url = ""
        song_data['media_url'] = media_url
        # Create preview URL
        if media_url:
            preview = media_url.replace("_320.mp4", "_96_p.mp4").replace("_160.mp4", "_96_p.mp4").replace("//aac.", "//preview.")
            song_data['media_preview_url'] = preview
    except Exception as e:
        logger.warning(f"Error processing media URL: {e}")
        song_data['media_url'] = song_data.get('url') or song_data.get('media_url') or song_data.get('media_preview_url', '')
    
    # Format text fields
    for field in ['song', 'music', 'singers', 'starring', 'album', 'primary_artists', 'title']:
        if field in song_data:
            song_data[field] = _format_string(str(song_data.get(field, '')))
    
    # Ensure title field exists (copy from 'song' field if needed)
    if not song_data.get('title') and song_data.get('song'):
        song_data['title'] = song_data['song']
    elif not song_data.get('song') and song_data.get('title'):
        song_data['song'] = song_data['title']
    
    # Enhance image quality
    if 'image' in song_data:
        img = song_data['image']
        if isinstance(img, list):
            # Newer API format: list of {quality, link} dicts — pick highest quality
            best = img[-1] if img else {}
            url = str(best.get('link') or best.get('url') or '').strip()
            song_data['image'] = url.replace("150x150", "500x500").replace("50x50", "500x500") if url else ''
        elif img is None:
            song_data['image'] = ''
        else:
            song_data['image'] = str(img).replace("150x150", "500x500").replace("50x50", "500x500")
    
    # Handle copyright text
    if 'copyright_text' in song_data:
        song_data['copyright_text'] = song_data['copyright_text'].replace("&copy;", "©")
    
    # Add lyrics if requested
    if include_lyrics and song_data.get('has_lyrics') == 'true':
        song_id = song_data.get('id')
        if song_id:
            lyrics_data = get_lyrics(song_id)
            song_data['lyrics'] = lyrics_data.get('lyrics')
    else:
        song_data['lyrics'] = None
    
    return song_data


def _format_album(album_data: Dict[str, Any], include_lyrics: bool = False) -> Dict[str, Any]:
    """Format album data with enhanced song details."""
    if not isinstance(album_data, dict):
        return {}
    
    # Enhance image quality
    if 'image' in album_data:
        img = album_data['image']
        if img:
            album_data['image'] = str(img).replace("150x150", "500x500").replace("50x50", "500x500")
    
    # Format text fields
    for field in ['name', 'primary_artists', 'title']:
        if field in album_data:
            album_data[field] = _format_string(str(album_data.get(field, '')))
    
    # Format songs
    if 'songs' in album_data and isinstance(album_data['songs'], list):
        album_data['songs'] = [_format_song(song, include_lyrics) for song in album_data['songs']]
    
    return album_data


def _format_playlist(playlist_data: Dict[str, Any], include_lyrics: bool = False) -> Dict[str, Any]:
    """Format playlist data with enhanced song details."""
    if not isinstance(playlist_data, dict):
        return {}
    
    # Format text fields
    for field in ['firstname', 'listname']:
        if field in playlist_data:
            playlist_data[field] = _format_string(str(playlist_data.get(field, '')))
    
    # Format songs
    if 'songs' in playlist_data and isinstance(playlist_data['songs'], list):
        playlist_data['songs'] = [_format_song(song, include_lyrics) for song in playlist_data['songs']]
    
    return playlist_data


# ========================================
# Core JioSaavn API Functions
# ========================================

def _extract_song_id_from_url(url: str) -> Optional[str]:
    """Extract song ID from JioSaavn URL."""
    try:
        text = _fetch_text(url) or ""
        
        # Try first pattern
        if '"pid":"' in text:
            return text.split('"pid":"')[1].split('","')[0]
        
        # Try second pattern
        if '"song":{"type":"' in text:
            parts = text.split('"song":{"type":"')[1].split('","image":')[0]
            return parts.split('"id":"')[-1]
        
        return None
    except Exception as e:
        logger.error(f"Failed to extract song ID from URL: {e}")
        return None


def _extract_album_id_from_url(url: str) -> Optional[str]:
    """Extract album ID from JioSaavn URL."""
    try:
        text = _fetch_text(url) or ""
        
        # Try first pattern
        if '"album_id":"' in text:
            return text.split('"album_id":"')[1].split('"')[0]
        
        # Try second pattern
        if '"page_id","' in text:
            return text.split('"page_id","')[1].split('","')[0]
        
        return None
    except Exception as e:
        logger.error(f"Failed to extract album ID from URL: {e}")
        return None


def _extract_playlist_id_from_url(url: str) -> Optional[str]:
    """Extract playlist ID from JioSaavn URL."""
    try:
        text = _fetch_text(url) or ""
        
        # Try first pattern
        if '"type":"playlist","id":"' in text:
            return text.split('"type":"playlist","id":"')[1].split('"')[0]
        
        # Try second pattern
        if '"page_id","' in text:
            return text.split('"page_id","')[1].split('","')[0]
        
        return None
    except Exception as e:
        logger.error(f"Failed to extract playlist ID from URL: {e}")
        return None


def search_songs(
    query: str,
    limit: int = 20,
    include_full_data: bool = True,
    language: Optional[str] = None,
) -> List[Dict[str, Any]]:
    """Search for songs using JioSaavn API directly."""
    if not query or not query.strip():
        return []
    
    logger.info(f"Searching JioSaavn directly for: {query}")
    
    try:
        # Handle direct URLs
        if query.startswith('http') and 'saavn.com' in query:
            if '/song/' in query:
                song_id = _extract_song_id_from_url(query)
                if song_id:
                    song = get_song_details(song_id, language=language)
                    return [song] if song else []
            elif '/album/' in query:
                album_id = _extract_album_id_from_url(query)
                if album_id:
                    album = get_album_details(album_id, language=language)
                    if album and isinstance(album.get('songs'), list):
                        return resolve_canonical_tracks(album['songs'])[:limit]
            elif '/playlist/' in query or '/featured/' in query:
                playlist_id = _extract_playlist_id_from_url(query)
                if playlist_id:
                    playlist = get_playlist_details(playlist_id, language=language)
                    if playlist and isinstance(playlist.get('songs'), list):
                        return resolve_canonical_tracks(playlist['songs'])[:limit]
            return []
        
        songs_data = search_songs_web(query, limit)
        if not songs_data:
            return []
        
        if not include_full_data:
            return resolve_canonical_tracks(songs_data)[:limit]
        
        # Fetch full song details for each result.
        # If detail call fails/times out, keep a lightweight formatted item
        # so UI remains responsive with partial data.
        full_songs = []
        for song in songs_data[:limit]:
            song_id = song.get('id')
            if song_id:
                full_song = get_song_details(song_id, language=language)
                if full_song:
                    full_songs.append(full_song)
                else:
                    full_songs.append(_format_song(dict(song), include_lyrics=False))
        
        return resolve_canonical_tracks(full_songs)[:limit]
        
    except Exception as e:
        logger.error(f"JioSaavn search failed for '{query}': {e}", exc_info=True)
        return []


def search_all_categories(query: str, limit: int = 20, language: Optional[str] = None) -> Dict[str, Any]:
    """Search for songs, albums, and playlists using JioSaavn API."""
    if not query or not query.strip():
        return {"songs": [], "albums": [], "playlists": []}
    
    logger.info(f"Searching all categories in JioSaavn for: {query}")
    use_web_client = str(language or "").strip().lower() == "english"
    
    try:
        songs_data: List[Dict[str, Any]] = []
        albums_data: List[Dict[str, Any]] = []
        playlists_data: List[Dict[str, Any]] = []

        if use_web_client:
            logger.debug("Using JioSaavn web-client session for english multi-search query=%s", query)
            payload = jiosaavn_request(JIOSAAVN_API_URL, params=_web_search_params(query, limit))
            if payload is None:
                logger.debug("English web-client multi-search request failed for query=%s", query)
                return {"songs": [], "albums": [], "playlists": []}
            songs_data = _extract_web_song_results(payload)
        else:
            url = JIOSAAVN_SEARCH_BASE + query
            response_text = _fetch_text(url)
            data = _parse_json_response(response_text or "", f"search_all_categories:{query}")
            if not isinstance(data, dict):
                return {"songs": [], "albums": [], "playlists": []}

            if isinstance(data.get('songs'), dict):
                songs_data = data['songs'].get('data', [])
            elif isinstance(data.get('songs'), list):
                songs_data = data['songs']

            if isinstance(data.get('albums'), dict):
                albums_data = data['albums'].get('data', [])
            elif isinstance(data.get('albums'), list):
                albums_data = data['albums']

            if isinstance(data.get('playlists'), dict):
                playlists_data = data['playlists'].get('data', [])
            elif isinstance(data.get('playlists'), list):
                playlists_data = data['playlists']
        
        # Fetch full song details for each result with fallback to raw song.
        full_songs = []
        for song in songs_data[:limit]:
            song_id = song.get('id')
            if song_id:
                full_song = get_song_details(song_id, language=language)
                if full_song:
                    full_songs.append(full_song)
                else:
                    full_songs.append(_format_song(dict(song), include_lyrics=False))
        
        return {
            "songs": resolve_canonical_tracks(full_songs)[:limit],
            "albums": albums_data[:limit],
            "playlists": playlists_data[:limit]
        }
        
    except Exception as e:
        logger.error(f"JioSaavn search all failed for '{query}': {e}", exc_info=True)
        return {"songs": [], "albums": [], "playlists": []}


def search_playlists_jiosaavn(query: str, limit: int = 5, language: Optional[str] = None) -> List[Dict[str, str]]:
    """
    Search for playlists using JioSaavn API and return playlist IDs and metadata.
    
    Returns list of dicts with 'id', 'title', 'subtitle' keys.
    """
    if not query or not query.strip():
        return []
    
    logger.info(f"Searching JioSaavn playlists for: {query}")
    use_web_client = str(language or "").strip().lower() == "english"
    
    try:
        url = JIOSAAVN_SEARCH_BASE + query
        if use_web_client:
            logger.debug("Using JioSaavn web-client session for english playlist search query=%s", query)
        response_text = _fetch_text(url)
        data = _parse_json_response(response_text or "", f"search_playlists:{query}")
        if not isinstance(data, dict):
            return []
        
        playlists_data = []
        if isinstance(data.get('playlists'), dict):
            playlists_data = data['playlists'].get('data', [])
        elif isinstance(data.get('playlists'), list):
            playlists_data = data['playlists']
        
        result = []
        for playlist in playlists_data[:limit]:
            if isinstance(playlist, dict) and 'id' in playlist:
                result.append({
                    'id': str(playlist.get('id', '')),
                    'title': str(playlist.get('title', '')),
                    'subtitle': str(playlist.get('subtitle', '')),
                    'image': str(playlist.get('image', '')),
                })
        
        return result
        
    except Exception as e:
        logger.error(f"Failed to search playlists in JioSaavn for '{query}': {e}")
        return []


def get_song_details(
    song_id: str,
    include_lyrics: bool = False,
    language: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """Get detailed song information by ID."""
    if not song_id:
        return None
    
    logger.info(f"Fetching song details directly for ID: {song_id}")
    try:
        url = JIOSAAVN_SONG_DETAILS_BASE + song_id
        response_text = _fetch_text(url)
        data = _parse_json_response(response_text or "", f"song_details:{song_id}")
        if not isinstance(data, dict):
            return None
        
        # Extract song data
        if isinstance(data, dict) and song_id in data:
            song_data = data[song_id]
            return _format_song(song_data, include_lyrics)
        
        return None
        
    except Exception as e:
        logger.warning(f"Failed to get song details for '{song_id}': {e}")
        return None


def get_album_details(
    album_id: str,
    include_lyrics: bool = False,
    language: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """Get detailed album information by ID."""
    if not album_id:
        return None
    
    logger.info(f"Fetching album details directly for ID: {album_id}")
    try:
        url = JIOSAAVN_ALBUM_DETAILS_BASE + album_id
        response_text = _fetch_text(url)
        if not response_text:
            return None

        data = _parse_json_response(response_text, f"album_details:{album_id}")
        if not isinstance(data, dict):
            return None
        
        return _format_album(data, include_lyrics)
        
    except Exception as e:
        logger.error(f"Failed to get album details for '{album_id}': {e}", exc_info=True)
        return None


def get_playlist_details(
    playlist_id: str,
    include_lyrics: bool = False,
    language: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """Get detailed playlist information by ID."""
    if not playlist_id:
        return None
    
    logger.info(f"Fetching playlist details directly for ID: {playlist_id}")
    try:
        url = JIOSAAVN_PLAYLIST_DETAILS_BASE + playlist_id
        response_text = _fetch_text(url)
        if not response_text:
            return None

        data = _parse_json_response(response_text, f"playlist_details:{playlist_id}")
        if not isinstance(data, dict):
            return None
        
        return _format_playlist(data, include_lyrics)
        
    except Exception as e:
        logger.error(f"Failed to get playlist details for '{playlist_id}': {e}", exc_info=True)
        return None


def get_lyrics(song_id: str) -> Dict[str, Any]:
    """Get lyrics for a song by ID."""
    if not song_id:
        return {}
    
    logger.info(f"Fetching lyrics directly for song ID: {song_id}")
    
    try:
        url = JIOSAAVN_LYRICS_BASE + song_id
        data = _fetch_json(url)
        
        if isinstance(data, dict) and 'lyrics' in data:
            return data
        
        return {}
        
    except Exception as e:
        logger.error(f"Failed to get lyrics for '{song_id}': {e}", exc_info=True)
        return {}


def get_playlist_songs(playlist_url: str, limit: int = 50, language: Optional[str] = None) -> List[Dict[str, Any]]:
    """Get playlist songs from URL."""
    if not playlist_url or not playlist_url.strip():
        return []
    
    logger.info(f"Fetching playlist songs from URL: {playlist_url}")
    
    # Extract playlist ID from URL
    playlist_id = _extract_playlist_id_from_url(playlist_url)
    if not playlist_id:
        logger.error(f"Could not extract playlist ID from URL: {playlist_url}")
        return []
    
    # Get playlist details
    playlist_data = get_playlist_details(playlist_id, language=language)
    if not playlist_data:
        return []
    
    # Extract songs
    songs = playlist_data.get('songs', [])
    if isinstance(songs, list):
        return resolve_canonical_tracks(songs)[:limit]
    
    return []


def get_album_songs(album_url: str, limit: int = 50, language: Optional[str] = None) -> List[Dict[str, Any]]:
    """Get album songs from URL."""
    if not album_url or not album_url.strip():
        return []
    
    logger.info(f"Fetching album songs from URL: {album_url}")
    
    # Extract album ID from URL
    album_id = _extract_album_id_from_url(album_url)
    if not album_id:
        logger.error(f"Could not extract album ID from URL: {album_url}")
        return []
    
    # Get album details
    album_data = get_album_details(album_id, language=language)
    if not album_data:
        return []
    
    # Extract songs
    songs = album_data.get('songs', [])
    if isinstance(songs, list):
        return resolve_canonical_tracks(songs)[:limit]
    
    return []


def search_artists(query: str, limit: int = 20) -> List[Dict[str, Any]]:
    """Search for artists using Saavn Sumit API (external - artists only)."""
    if not query or not query.strip():
        return []
    
    url = f"{SAAVN_SUMIT_BASE}/search/artists"
    logger.info(f"Calling Saavn Sumit endpoint for artists: {url}")
    
    try:
        resp = session.get(
            url,
            params={"query": query},
            timeout=HTTP_TIMEOUT
        )
        resp.raise_for_status()
        data = resp.json()
        
        if not data.get("success"):
            return []
        
        results = data.get("data", {}).get("results", [])
        artists = []
        
        for item in results[:limit]:
            if not isinstance(item, dict):
                continue
            name = str(item.get("name") or "").strip()
            if not name:
                continue
            
            # Keep image as an array of quality/url objects to match Android SaavnArtistDto.
            image_list: List[Dict[str, str]] = []
            image_field = item.get("image")
            if isinstance(image_field, list):
                for img in image_field:
                    if not isinstance(img, dict):
                        continue
                    quality = str(img.get("quality") or "")
                    url_value = str(img.get("url") or img.get("link") or "")
                    if url_value:
                        image_list.append({"quality": quality, "url": url_value})
            elif isinstance(image_field, str) and image_field.strip():
                # Fallback for providers that return a single URL string.
                image_list.append({"quality": "500x500", "url": image_field.strip()})
            
            artists.append({
                "id": str(item.get("id") or name.lower().replace(" ", "-")),
                "name": name,
                "role": str(item.get("role") or "Artist"),
                "type": str(item.get("type") or "artist"),
                "image": image_list,
                "url": str(item.get("url") or ""),
            })
        
        return artists
    except Exception as exc:
        logger.error(f"Saavn Sumit artist search failed for '{query}': {exc}", exc_info=True)
        return []


def get_mood_playlists(mood: str, limit: int = 20) -> List[Dict[str, Any]]:
    """Get mood playlists using Saavn Sumit API (external - mood playlists only)."""
    if not mood or not mood.strip():
        return []
    
    url = f"{SAAVN_SUMIT_BASE}/search/playlists"
    logger.info(f"Calling Saavn Sumit endpoint for playlists: {url}")
    
    try:
        resp = session.get(
            url,
            params={"query": mood},
            timeout=HTTP_TIMEOUT
        )
        resp.raise_for_status()
        data = resp.json()
        
        if not data.get("success"):
            return []
        
        results = data.get("data", {}).get("results", [])
        playlists = []
        
        for item in results[:limit]:
            if not isinstance(item, dict):
                continue
            title = str(item.get("name") or item.get("title") or "").strip()
            if not title:
                continue
            
            # Extract best image URL
            image_url = ""
            image_field = item.get("image")
            if isinstance(image_field, str):
                image_url = image_field
            elif isinstance(image_field, list) and len(image_field) > 0:
                # Get the highest quality image
                for img in reversed(image_field):
                    if isinstance(img, dict):
                        image_url = str(img.get("url") or img.get("link") or "")
                        if image_url:
                            break
            
            playlists.append({
                "id": str(item.get("id") or title.lower().replace(" ", "-")),
                "title": title,
                "name": title,
                "image": image_url,
                "url": str(item.get("url") or ""),
                "songsCount": int(item.get("songCount") or item.get("list_count") or 0),
            })
        
        return playlists
    except Exception as exc:
        logger.error(f"Saavn Sumit playlist search failed for '{mood}': {exc}", exc_info=True)
        return []


# ========================================
# JioSaavnService Class (for compatibility with existing code)
# ========================================

class JioSaavnService:
    """Service wrapper that delegates to module-level functions."""

    def search_all_categories(self, query: str, limit: int = 20, language: Optional[str] = None) -> Dict[str, Any]:
        return search_all_categories(query, limit, language=language)
    
    def search_songs(self, query: str, limit: int = 20, language: Optional[str] = None) -> List[Dict[str, Any]]:
        return search_songs(query, limit, language=language)
    
    def get_song_details(self, song_url: str, language: Optional[str] = None) -> Dict[str, Any]:
        result = get_song_details(song_url, language=language)
        return result if result else {}
    
    def get_playlist_songs(self, playlist_url: str, limit: int = 50, language: Optional[str] = None) -> List[Dict[str, Any]]:
        return get_playlist_songs(playlist_url, limit, language=language)
    
    def get_playlist_songs_from_url(self, playlist_url: str) -> List[Dict[str, Any]]:
        """Backward-compatible alias."""
        return get_playlist_songs(playlist_url)
    
    def get_album_songs(self, album_url: str, limit: int = 50, language: Optional[str] = None) -> List[Dict[str, Any]]:
        return get_album_songs(album_url, limit, language=language)
    
    def get_lyrics(self, query: str) -> Dict[str, Any]:
        return get_lyrics(query)
    
    def search_artists(self, query: str, limit: int = 20) -> List[Dict[str, Any]]:
        return search_artists(query, limit)
    
    def get_artists(self, query: str) -> List[Dict[str, Any]]:
        """Backward-compatible alias."""
        return search_artists(query)
    
    def get_mood_playlists(self, mood: str, limit: int = 20) -> List[Dict[str, Any]]:
        return get_mood_playlists(mood, limit)
    
    def get_artist_songs(self, artist_name: str) -> List[Dict[str, Any]]:
        """Search for songs by artist name."""
        return search_songs(artist_name, limit=20)
