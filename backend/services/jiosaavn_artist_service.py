"""Artist detail service backed by JioSaavn search endpoint."""

from __future__ import annotations

import logging
from typing import Any, Dict, List

from services.cache_manager import get_cache
from services import jiosaavn_service

logger = logging.getLogger(__name__)

# Maximum requests per user action
MAX_REQUESTS_PER_ACTION = 3


class JioSaavnArtistService:
    """Build artist songs and albums using JioSaavn search results."""

    def __init__(self):
        self.cache = get_cache()

    def get_artist_details(self, artist_name: str) -> Dict[str, Any]:
        """Return artist details in the app-facing format.

        LAZY-LOADED: Only called when user clicks an artist.
        
        Output shape:
        {
            "artist": "Arijit Singh",
            "songs": [ ... up to 10 ... ],
            "albums": [ ... up to 5 ... ]
        }
        """
        artist_name = (artist_name or "").strip()
        if not artist_name:
            return {"artist": "Unknown", "songs": [], "albums": []}
        
        # Check cache first (10 minute TTL)
        cache_key = f"artist_details:{artist_name.lower()}"
        cached = self.cache.get(cache_key)
        if cached:
            logger.info(f"Artist detail cache hit: {artist_name}")
            return cached

        logger.info("Artist detail request (LAZY-LOADED): %s", artist_name)

        try:
            raw_songs = self._fetch_songs_for_query(artist_name)
            artist_songs = self._filter_artist_songs(raw_songs, artist_name)
            popular_songs = self._get_popular_songs(artist_songs)
            albums = self._extract_albums_from_songs(artist_songs)

            logger.info("Songs found: %d", len(popular_songs))
            logger.info("Albums generated: %d", len(albums))

            result = {
                "artist": artist_name,
                "songs": popular_songs,
                "albums": albums,
            }
            
            # Cache for 10 minutes
            self.cache.set(cache_key, result, ttl=600)
            return result
            
        except Exception as e:
            logger.error("Artist detail build failed for '%s': %s", artist_name, str(e), exc_info=True)
            error_result = {"artist": artist_name or "Unknown", "songs": [], "albums": []}
            # Cache error result for shorter time (1 minute) to prevent repeated failures
            self.cache.set(cache_key, error_result, ttl=60)
            return error_result

    def _fetch_songs_for_query(self, artist_name: str) -> List[Dict[str, Any]]:
        # Use integrated jiosaavn_service instead of HTTP calls
        songs = jiosaavn_service.search_songs(artist_name, limit=50, include_full_data=False)
        return songs if isinstance(songs, list) else []

    def _filter_artist_songs(self, songs: List[Dict[str, Any]], artist_name: str) -> List[Dict[str, Any]]:
        artist_name_lower = artist_name.lower()
        filtered: List[Dict[str, Any]] = []

        for song in songs:
            if not isinstance(song, dict):
                continue

            primary_artists = str(song.get("primary_artists") or "")
            if artist_name_lower in primary_artists.lower():
                filtered.append(song)

        return filtered

    def _get_popular_songs(self, songs: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        def play_count(song: Dict[str, Any]) -> int:
            try:
                return int(song.get("play_count", 0) or 0)
            except (TypeError, ValueError):
                return 0

        sorted_songs = sorted(songs, key=play_count, reverse=True)
        normalized = [self._normalize_song(song) for song in sorted_songs]

        # Keep playable, non-empty song items only.
        normalized = [song for song in normalized if song.get("id") and song.get("media_url")]
        return normalized[:10]

    def _extract_albums_from_songs(self, songs: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        seen_titles: set[str] = set()
        albums: List[Dict[str, Any]] = []

        for song in songs:
            if not isinstance(song, dict):
                continue

            album_title = str(song.get("album") or "").strip()
            if not album_title:
                continue

            normalized_key = album_title.lower()
            if normalized_key in seen_titles:
                continue

            seen_titles.add(normalized_key)
            albums.append(
                {
                    "title": album_title,
                    "artist": str(song.get("primary_artists") or "Unknown Artist"),
                    "image": str(song.get("image") or ""),
                    "query": str(song.get("album_url") or ""),
                }
            )

            if len(albums) >= 5:
                break

        return albums

    def _normalize_song(self, song: Dict[str, Any]) -> Dict[str, Any]:
        return {
            "id": str(song.get("id") or ""),
            "title": str(song.get("song") or song.get("title") or "Unknown"),
            "artist": str(song.get("primary_artists") or "Unknown Artist"),
            "album": str(song.get("album") or ""),
            "image": str(song.get("image") or ""),
            "duration": str(song.get("duration") or "0"),
            "media_url": str(song.get("media_url") or ""),
            "play_count": str(song.get("play_count") or "0"),
        }
