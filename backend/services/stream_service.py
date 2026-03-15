"""
Stream Service - Resolves direct playable song streams from JioSaavn API.
"""

from __future__ import annotations

import logging
from typing import Any, Dict, Optional

from services import jiosaavn_service

logger = logging.getLogger(__name__)


class StreamService:
    """Service responsible for resolving stream URLs from song IDs."""

    def __init__(self):
        logger.info("Initialized StreamService using integrated jiosaavn_service")

    def get_stream_url(self, song_id: str) -> Optional[Dict[str, Any]]:
        """
        Resolve direct stream metadata for a song ID from JioSaavn API.

        Args:
            song_id: JioSaavn song ID

        Returns:
            Normalized stream payload if resolved, otherwise None.
        """
        normalized_song_id = (song_id or "").strip()
        if not normalized_song_id:
            logger.warning("Stream resolver called with empty song_id")
            return None

        logger.info("Resolving stream URL for song ID: %s", normalized_song_id)

        try:
            raw_song = self._fetch_song(normalized_song_id)
            if not raw_song:
                logger.warning("Song not found in JioSaavn API: %s", normalized_song_id)
                return None

            normalized = self._normalize_stream_payload(normalized_song_id, raw_song)
            
            logger.info(
                "Stream URL resolved successfully for song: %s | title='%s' | artist='%s'",
                normalized_song_id,
                normalized.get("title"),
                normalized.get("artist"),
            )
            return normalized

        except ValueError as exc:
            # Missing stream URL or validation failed
            logger.error(
                "Stream validation failed for song %s: %s",
                normalized_song_id,
                str(exc),
            )
            return None
        except Exception as exc:
            logger.error(
                "Unexpected stream resolution failure for song %s: %s",
                normalized_song_id,
                str(exc),
                exc_info=True,
            )
            return None

    def _fetch_song(self, song_id: str) -> Optional[Dict[str, Any]]:
        """Fetch song metadata from JioSaavn API."""
        logger.debug("Fetching song directly from JioSaavn: %s", song_id)
        # Use integrated jiosaavn_service
        song = jiosaavn_service.get_song_details(song_id, include_lyrics=False)
        
        if not song:
            logger.warning("Failed to get song data from JioSaavn for ID: %s", song_id)
            return None
            
        # Validate that we have essential stream URL
        if not song.get("media_url"):
            logger.error("Stream URL (media_url) missing for song ID: %s", song_id)
            raise ValueError(f"Stream URL missing for song ID: {song_id}")
            
        return song

    def _normalize_stream_payload(self, song_id: str, song: Dict[str, Any]) -> Dict[str, Any]:
        """Normalize JioSaavn song data to AURA internal schema."""
        # Extract primary fields
        title = self._safe_str(song.get("song") or song.get("title"))
        artist = self._safe_str(song.get("primary_artists") or song.get("artist"))
        image = self._safe_str(song.get("image"))
        duration_int = self._safe_int(song.get("duration"), default=0)
        stream_url = self._safe_str(song.get("media_url") or song.get("stream_url"))
        
        # Validate essential fields
        if not stream_url:
            raise ValueError(f"Stream URL missing for song ID: {song_id}")
        if not title:
            title = "Unknown"

        # Parse artists into array
        artists = [part.strip() for part in artist.split(",") if part.strip()]

        return {
            # Backward-compatible fields for existing clients (Android/Web)
            "videoId": self._safe_str(song.get("id") or song_id),
            "title": title,
            "artist": artist,
            "artists": artists,
            "thumbnail": image,
            "duration": str(duration_int),
            "url": stream_url,
            "album": self._safe_str(song.get("album")),
            
            # Normalized JioSaavn fields
            "id": self._safe_str(song.get("id") or song_id),
            "image": image,
            "stream_url": stream_url,
            "singers": self._safe_str(song.get("singers")),
            "starring": self._safe_str(song.get("starring")),
            "language": self._safe_str(song.get("language")).lower(),
            "play_count": self._safe_int(song.get("play_count"), default=0),
            "year": self._safe_str(song.get("year")),
        }

    @staticmethod
    def _safe_str(value: Any) -> str:
        if value is None:
            return ""
        return str(value).strip()

    @staticmethod
    def _safe_int(value: Any, default: int = 0) -> int:
        try:
            if value is None:
                return default
            if isinstance(value, bool):
                return default
            return int(float(str(value).strip()))
        except (TypeError, ValueError):
            return default


_stream_service = StreamService()


def get_stream_url(song_id: str) -> Optional[Dict[str, Any]]:
    """Convenience function for resolving stream metadata."""
    return _stream_service.get_stream_url(song_id)
