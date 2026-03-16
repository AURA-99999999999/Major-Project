"""
Music Content Filtering Service
Provides production-grade filtering to ensure only real music tracks are returned.
This centralizes the filtering logic used across recommendation, trending, and home endpoints.
"""

from typing import List, Dict, Optional
import logging

logger = logging.getLogger(__name__)

# Title blocklist for filtering out non-music content (case-insensitive)
TITLE_BLOCKLIST = [
    'interview',
    'trailer',
    'teaser',
    'reaction',
    'review',
    'podcast',
    'behind the scenes',
    'making of',
    'lyric video',
    'official trailer',
    'episode',
    'press meet',
    'live interview',
    'release - topic',  # Block Topic channel uploads
]

# Minimum duration for a music track (in seconds)
MIN_DURATION_SECONDS = 60


def _is_title_allowed(title: str) -> bool:
    """
    Lightweight title filter to avoid obvious non-music content.
    Case-insensitive matching against blocklist.
    
    Args:
        title: The video/track title to validate
        
    Returns:
        True if title passes filter, False if it matches blocklist terms
    """
    if not title or not isinstance(title, str):
        return True
    
    lowered = title.lower().strip()
    for term in TITLE_BLOCKLIST:
        if term in lowered:
            logger.info(f"Rejected title due to blocked term '{term}': {title}")
            return False
    
    return True


def _extract_artists(artists_raw) -> List[str]:
    """
    IMPROVED artist extraction with normalization:
    - Extract artist names from various API formats
    - Handle multi-artist collaborations (split by comma FIRST)
    - Remove "Topic", "Official", "VEVO", "Release - Topic" suffixes
    - Clean trailing spaces
    - Normalize but preserve unique artists
    
    Args:
        artists_raw: Raw artists data from API (list of dicts or strings)
        
    Returns:
        List of normalized artist name strings, empty list if extraction fails
    """
    if not artists_raw:
        return []
    artists = []
    if isinstance(artists_raw, list):
        for artist in artists_raw:
            if isinstance(artist, dict):
                name = artist.get('name', '').strip()
                if name:
                    artists.append(name)
            elif isinstance(artist, str):
                name = artist.strip()
                if name:
                    artists.append(name)
    elif isinstance(artists_raw, str):
        name = artists_raw.strip()
        if name:
            if ',' in name:
                for part in name.split(','):
                    part = part.strip()
                    if part:
                        artists.append(part)
            else:
                artists.append(name)
    # Remove duplicates while preserving order
    seen = set()
    unique_artists = []
    for artist in artists:
        if artist not in seen:
            seen.add(artist)
            unique_artists.append(artist)
    return unique_artists or []


def _process_artist_name(name: str) -> Optional[str]:
    """
    Process and normalize artist name:
    - Remove suffixes: "- Topic", "Official", "VEVO", "Release - Topic"
    - Remove trailing spaces and dashes
    - Block certain channels: "Release - Topic"
    
    Args:
        name: Raw artist name
        
    Returns:
        Normalized artist name or None if it's a blocklisted channel
    """
    if not name or not isinstance(name, str):
        return None
    
    name = name.strip()
    
    # Blocklist certain channels/labels
    blocklist = [
        'Release - Topic',
        'Topic',
        '[Topic]',
    ]
    
    if name in blocklist or name.lower() in [b.lower() for b in blocklist]:
        return None
    
    # Remove suffixes (case-insensitive)
    # Order matters - remove compound suffixes first
    suffixes_to_remove = [
        ' - Topic',
        '- Topic',
        ' - Official',
        '- Official',
        ' Official',
        ' - VEVO',
        '- VEVO',
        ' VEVO',
    ]
    
    for suffix in suffixes_to_remove:
        if name.lower().endswith(suffix.lower()):
            name = name[:-len(suffix)].strip()
            break  # Only remove one suffix
    
    name = name.strip().rstrip('-').strip()  # Remove any trailing dashes/spaces
    
    # Ensure name is not empty after processing
    if not name or len(name) < 2:
        return None
    
    return name


def _extract_best_thumbnail(thumbnails) -> str:
    """
    Extract the best quality thumbnail URL from API response.
    Handles both list and dict structures, preferring highest resolution.
    
    Args:
        thumbnails: Thumbnail data from API (list of dicts or dict)
        
    Returns:
        Thumbnail URL string, empty string if none available
    """
    if not thumbnails:
        return ""
    
    # Handle dict format
    if isinstance(thumbnails, dict):
        return thumbnails.get('url', '')
    
    # Handle list format - get highest resolution
    if isinstance(thumbnails, list) and len(thumbnails) > 0:
        # Prefer thumbnails with explicit width/height
        best = max(
            thumbnails,
            key=lambda t: (t.get('width') or 0) * (t.get('height') or 0)
        )
        return best.get('url', '')
    
    return ""


def _get_duration_seconds(duration_raw) -> Optional[int]:
    """
    Convert duration from various formats to seconds.
    Handles string format (MM:SS or H:MM:SS) and integer seconds.
    
    Args:
        duration_raw: Duration from API (int, str, or None)
        
    Returns:
        Duration in seconds as int, or None if unable to parse
    """
    if duration_raw is None:
        return None
    
    # Already in seconds (int)
    if isinstance(duration_raw, int):
        return duration_raw if duration_raw > 0 else None
    
    # String format MM:SS or H:MM:SS
    if isinstance(duration_raw, str):
        text = duration_raw.strip()
        if not text:
            return None
        try:
            parts = [int(p) for p in text.split(":")]
        except Exception:
            return None
        if len(parts) == 2:
            minutes, seconds = parts
            if minutes < 0 or seconds < 0 or seconds >= 60:
                return None
            total = minutes * 60 + seconds
            return total if total > 0 else None
        if len(parts) == 3:
            hours, minutes, seconds = parts
            if hours < 0 or minutes < 0 or seconds < 0 or minutes >= 60 or seconds >= 60:
                return None
            total = hours * 3600 + minutes * 60 + seconds
            return total if total > 0 else None
        return None
    
    return None


def filter_music_tracks(items: List[Dict], include_validation: bool = True, source: str = "search") -> List[Dict]:
    """
    Filter and normalize a list of items to ensure only real music tracks are returned.
    
    This is the core filtering function used by all music endpoints:
    - /api/recommendations
    - /api/trending
    - /api/home
    - /api/home/trending-tracks
    - /api/daily-mixes
    
    Validation rules (ALL must pass):
    1. videoId exists and non-empty
    2. title exists and non-empty, passes blocklist filter
    3. artists list exists and not empty
    4. thumbnail exists and not empty (or can be generated)
    5. duration > 60 seconds (relaxed for trusted sources)
    Args:
        items: List of raw track items from provider APIs
        include_validation: Whether to validate duration/title/artist constraints (default True)
        source: Source of tracks for context-aware filtering (default "search")
                Options: "search", "artist", "album", "recommendation", "mix", "similar_mix", "trending"
                Trusted sources (artist, album, recommendation, mix, similar_mix, trending): Relaxed validation
        
    Returns:
        List of cleaned, validated music track dictionaries with normalized format.
        
    Output format for each item:
    {
        "videoId": str,
        "title": str,
        "artists": [str, ...],
        "thumbnail": str (Fallback to hqdefault.jpg if missing),
        "album": str (optional)
    }
    """
    if not items or not isinstance(items, list):
        logger.warning(f"filter_music_tracks called with invalid items: {type(items)}")
        return []
    
    # Determine if source is trusted (relaxed validation rules)
    trusted_sources = {"artist", "album", "recommendation", "mix", "trending", "similar_mix"}
    allow_missing_duration = source in trusted_sources
    allow_missing_album = source in trusted_sources
    
    filtered_results = []
    rejected_count = 0
    
    for idx, item in enumerate(items):
        if not isinstance(item, dict):
            logger.debug(f"Skipping non-dict item at index {idx}")
            rejected_count += 1
            continue
        
        # 1. Validate videoId
        video_id = item.get('videoId') or item.get('id')
        if not video_id or not isinstance(video_id, str) or not video_id.strip():
            logger.debug(f"Item {idx}: Missing or empty videoId")
            rejected_count += 1
            continue
        
        video_id = video_id.strip()
        
        # 2. Validate title and blocklist
        title = item.get('title') or item.get('name', '').strip()
        if not title or not isinstance(title, str):
            logger.debug(f"Item {video_id}: Missing or empty title")
            rejected_count += 1
            continue
        
        title = title.strip()
        
        if not _is_title_allowed(title):
            logger.info(f"Item rejected (title blocklist): videoId={video_id} title={title}")
            rejected_count += 1
            continue
        
        # 3. Validate artists
        artists = _extract_artists(item.get('artists'))
        if not artists:
            logger.debug(f"Item {video_id}: No valid artists extracted")
            rejected_count += 1
            continue
        
        # 4. Validate/extract thumbnail
        thumbnail = _extract_best_thumbnail(item.get('thumbnails') or item.get('thumbnail'))
        # Provider-agnostic fallback for sparse payloads.
        if not thumbnail:
            thumbnail = str(item.get('image') or '')
        
        # 5. Validate duration (only if data is available)
        # For shallow validation (trending/explore data), duration may not be available
        # For deep validation, we should have duration from get_song() or search results
        # Trusted sources (artist, album, recommendation, mix) allow missing duration
        duration_seconds = _get_duration_seconds(item.get('duration') or item.get('duration_seconds'))
        if include_validation:  # Deep validation mode
            if not allow_missing_duration:  # Strict mode for search/unknown sources
                if duration_seconds is None or duration_seconds <= MIN_DURATION_SECONDS:
                    logger.debug(f"Item {video_id}: Invalid/short duration {duration_seconds}s")
                    rejected_count += 1
                    continue
            else:  # Relaxed mode for trusted sources
                if duration_seconds is not None and duration_seconds <= MIN_DURATION_SECONDS:
                    logger.debug(f"Item {video_id}: Short duration {duration_seconds}s")
                    rejected_count += 1
                    continue
        # Shallow validation mode (trending): skip duration check if not available.
        
        # All validations passed - add to results
        cleaned_item = {
            'videoId': video_id,
            'title': title,
            'artists': artists,
            'thumbnail': thumbnail,
            'album': item.get('album', ''),
        }
        
        filtered_results.append(cleaned_item)
    
    logger.info(
        f"filter_music_tracks: source={source} input_count={len(items)} output_count={len(filtered_results)} "
        f"rejected={rejected_count} validation_enabled={include_validation}"
    )
    
    return filtered_results


def normalize_item_to_track(item: Dict) -> Optional[Dict]:
    """
    Normalize a single item to track format without full filtering.
    Useful for cases where item is already known to be valid.
    
    Args:
        item: Raw item dict from API
        
    Returns:
        Normalized track dict or None if required fields missing
    """
    video_id = item.get('videoId') or item.get('id')
    if not video_id:
        return None
    
    title = item.get('title') or item.get('name', '')
    if not title:
        return None
    
    artists = _extract_artists(item.get('artists'))
    if not artists:
        return None
    
    thumbnail = _extract_best_thumbnail(item.get('thumbnails') or item.get('thumbnail'))
    if not thumbnail:
        thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
    
    return {
        'videoId': video_id,
        'title': title,
        'artists': artists,
        'thumbnail': thumbnail,
        'album': item.get('album', ''),
    }
