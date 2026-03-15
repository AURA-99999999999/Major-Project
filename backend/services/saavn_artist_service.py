"""Saavn Artist Service - artist metadata via Saavn Sumit search."""

from typing import Dict, List, Optional
import logging

from services.jiosaavn_service import JioSaavnService

logger = logging.getLogger(__name__)

PLACEHOLDER_IMAGE = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='300' height='300'%3E%3Crect width='100%25' height='100%25' fill='%23111'/%3E%3Ctext x='50%25' y='50%25' dominant-baseline='middle' text-anchor='middle' fill='%23888' font-family='Arial' font-size='20'%3ENo%20Image%3C/text%3E%3C/svg%3E"
_SERVICE = JioSaavnService()


def fetch_artist_metadata(artist_name: str) -> Optional[Dict]:
    cleaned = (artist_name or "").strip()
    if not cleaned:
        return None

    logger.info("Fetching artist metadata: %s", cleaned)
    artists = _SERVICE.search_artists(cleaned, limit=1)
    if not artists:
        return None

    artist = artists[0]
    return {
        "id": artist.get("id") or cleaned.lower().replace(" ", "-"),
        "name": artist.get("name") or cleaned,
        "image": artist.get("image") or PLACEHOLDER_IMAGE,
        "link": artist.get("url") or "",
    }


def batch_fetch_artists(artist_names: List[str], max_results: int = 10) -> List[Dict]:
    results: List[Dict] = []
    for artist_name in artist_names:
        if len(results) >= max_results:
            break
        artist = fetch_artist_metadata(artist_name)
        if artist:
            results.append(artist)
    return results
