"""
Music Content Filtering Service
Provides production-grade filtering to ensure only real music tracks are returned.
This centralizes the filtering logic used across recommendation, trending, and home endpoints.
"""

from typing import List, Dict, Optional
import logging

logger = logging.getLogger(__name__)

# Validation constants
ALLOWED_MUSIC_VIDEO_TYPES = {
    'MUSIC_VIDEO_TYPE_ATV',          # Official music video (Audio Track Video)
    'MUSIC_VIDEO_TYPE_OMV',          # Official Music Video
    'MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK',  # Privately owned track
}

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
    Extract artist names from various API response formats.
    Handles both list and dict structures from YTMusic API.
    
    Args:
        artists_raw: Raw artists data from API (list of dicts or strings)
        
    Returns:
        List of artist name strings, empty list if extraction fails
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
            artists.append(name)
    
    return artists or []


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
        try:
            duration_raw = duration_raw.strip()
            parts = duration_raw.split(':')
            
            if len(parts) == 2:  # MM:SS
                minutes, seconds = map(int, parts)
                total_seconds = minutes * 60 + seconds
            elif len(parts) == 3:  # H:MM:SS
                hours, minutes, seconds = map(int, parts)
                total_seconds = hours * 3600 + minutes * 60 + seconds
            else:
                return None
            
            return total_seconds if total_seconds > 0 else None
        except (ValueError, AttributeError):
            return None
    
    return None


def filter_music_tracks(items: List[Dict], ytmusic=None, include_validation: bool = True) -> List[Dict]:
    """
    Filter and normalize a list of items to ensure only real music tracks are returned.
    
    This is the core filtering function used by all music endpoints:
    - /api/recommendations
    - /api/trending
    - /api/home
    - /api/home/trending-tracks
    
    Validation rules (ALL must pass):
    1. videoId exists and non-empty
    2. title exists and non-empty, passes blocklist filter
    3. artists list exists and not empty
    4. thumbnail exists and not empty (or can be generated)
    5. duration > 60 seconds
    6. (Optional) musicVideoType in ALLOWED_MUSIC_VIDEO_TYPES
    
    Args:
        items: List of raw items from YTMusic API
        ytmusic: YTMusic instance for validation calls (optional)
        include_validation: Whether to validate musicVideoType if ytmusic available (default True)
        
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
        # Fallback: generate thumbnail URL if missing
        if not thumbnail:
            thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
            logger.debug(f"Item {video_id}: Using fallback thumbnail URL")
        
        # 5. Validate duration (only if data is available)
        # For shallow validation (trending/explore data), duration may not be available
        # For deep validation, we should have duration from get_song() or search results
        duration_seconds = _get_duration_seconds(item.get('duration') or item.get('duration_seconds'))
        if include_validation:  # Deep validation mode - duration is required
            if duration_seconds is None or duration_seconds <= MIN_DURATION_SECONDS:
                logger.debug(f"Item {video_id}: Invalid/short duration {duration_seconds}s")
                rejected_count += 1
                continue
        # Shallow validation mode (trending): skip duration check if not available
        # These items are pre-curated by YTMusic so duration validation is less critical
        
        # 6. (Optional) Deep validation: Check musicVideoType if ytmusic available
        if include_validation and ytmusic:
            if not _validate_music_video_type(video_id, title, ytmusic):
                logger.info(f"Item rejected (music video type validation): videoId={video_id} title={title}")
                rejected_count += 1
                continue
        
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
        f"filter_music_tracks: input_count={len(items)} output_count={len(filtered_results)} "
        f"rejected={rejected_count} validation_enabled={include_validation}"
    )
    
    return filtered_results


def _validate_music_video_type(video_id: str, title: str, ytmusic) -> bool:
    """
    Deep validation: Call YTMusic.get_song() to verify musicVideoType.
    This is expensive (1 API call per item) so use sparingly and only when needed.
    
    Args:
        video_id: The YouTube video ID
        title: The track title (for logging)
        ytmusic: YTMusic instance
        
    Returns:
        True if music video type is allowed, False otherwise
    """
    try:
        song_details = ytmusic.get_song(video_id)
        video_details = song_details.get('videoDetails', {}) if isinstance(song_details, dict) else {}
        music_video_type = video_details.get('musicVideoType')
        
        if music_video_type in ALLOWED_MUSIC_VIDEO_TYPES:
            logger.debug(f"Video type validation passed: videoId={video_id} type={music_video_type}")
            return True
        
        logger.info(
            f"Video type validation failed: videoId={video_id} title={title} type={music_video_type}"
        )
        return False
    except Exception as e:
        logger.warning(
            f"Video type validation error: videoId={video_id} title={title} error={str(e)}"
        )
        # On validation error, reject to be safe (prefer false negatives over false positives)
        return False


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
