"""
Last.fm Service - Handles Last.fm API requests
"""
from typing import Dict, List, Optional, Tuple
import logging
import requests

logger = logging.getLogger(__name__)


class LastFmService:
    """Service class for Last.fm operations"""

    def __init__(self, api_key: str):
        self.api_key = (api_key or "").strip()
        self.base_url = "https://ws.audioscrobbler.com/2.0/"

    def get_geo_top_tracks(self, country: str, limit: int = 20) -> List[Dict]:
        """Get geo.getTopTracks for a country with a compact response shape"""
        if not self.api_key:
            raise ValueError("Last.fm API key is not configured")

        params = {
            "method": "geo.gettoptracks",
            "country": country,
            "api_key": self.api_key,
            "format": "json",
            "limit": str(limit),
        }

        response = requests.get(self.base_url, params=params, timeout=10)
        if response.status_code in {401, 403}:
            raise ValueError("Invalid API key or access forbidden")
        if response.status_code == 429:
            raise ValueError("Rate limit exceeded. Try again later")
        response.raise_for_status()

        data = response.json()
        if "error" in data:
            code = int(data.get("error") or 0)
            message = data.get("message", "Last.fm API error")
            error_map = {
                10: "Invalid API key",
                26: "API key suspended",
                29: "Rate limit exceeded",
                11: "Last.fm service offline",
                16: "Temporary error. Please try again",
                6: "Invalid parameters",
            }
            raise ValueError(error_map.get(code, message))

        tracks_raw = data.get("tracks", {}).get("track", [])
        if isinstance(tracks_raw, dict):
            tracks_raw = [tracks_raw]

        normalized = []
        for index, track in enumerate(tracks_raw):
            attr = track.get("@attr", {}) or {}
            artist = track.get("artist", {}) or {}
            images = track.get("image", []) or []

            image_url = None
            for image in reversed(images):
                candidate = image.get("#text")
                if candidate:
                    image_url = candidate
                    break

            rank_raw = attr.get("rank", 0) or 0
            normalized.append(
                {
                    "rank": int(rank_raw) + 1 if str(rank_raw).isdigit() else index + 1,
                    "song_name": track.get("name", ""),
                    "track_name": track.get("name", ""),
                    "artist_name": artist.get("name", ""),
                    "listeners": int(track.get("listeners") or 0),
                    "playcount": int(track.get("listeners") or 0),
                    "duration_seconds": int(track.get("duration") or 0),
                    "image": image_url,
                    "url": track.get("url", ""),
                }
            )

        return normalized

    def get_top_tracks(
        self,
        country: str,
        location: Optional[str] = None,
        limit: int = 50,
        page: int = 1,
    ) -> Tuple[List[Dict], Dict]:
        """Get Last.fm top tracks for a country and optional metro location"""
        if not self.api_key:
            raise ValueError("Last.fm API key is not configured")

        params = {
            "method": "geo.gettoptracks",
            "country": country,
            "api_key": self.api_key,
            "format": "json",
            "limit": str(limit),
            "page": str(page),
        }
        if location:
            params["location"] = location

        response = requests.get(self.base_url, params=params, timeout=10)
        response.raise_for_status()

        data = response.json()
        if "error" in data:
            message = data.get("message", "Last.fm API error")
            raise ValueError(message)

        toptracks = data.get("toptracks", {})
        tracks = toptracks.get("track", [])

        normalized = []
        for track in tracks:
            artist = track.get("artist", {}) or {}
            images = track.get("image", []) or []
            image_url = ""
            if images:
                image_url = images[-1].get("#text", "")

            attr = track.get("@attr", {}) or {}
            normalized.append(
                {
                    "rank": int(attr.get("rank", 0) or 0),
                    "name": track.get("name", ""),
                    "playcount": int(track.get("playcount", 0) or 0),
                    "url": track.get("url", ""),
                    "streamable": track.get("streamable", {}).get("#text", ""),
                    "artist": {
                        "name": artist.get("name", ""),
                        "url": artist.get("url", ""),
                        "mbid": artist.get("mbid", ""),
                    },
                    "image": image_url,
                }
            )

        attr = toptracks.get("@attr", {}) or {}
        meta = {
            "country": toptracks.get("@attr", {}).get("country", ""),
            "page": int(attr.get("page", 1) or 1),
            "perPage": int(attr.get("perPage", limit) or limit),
            "totalPages": int(attr.get("totalPages", 1) or 1),
            "total": int(attr.get("total", len(normalized)) or len(normalized)),
        }

        return normalized, meta
