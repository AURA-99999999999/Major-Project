"""
Test suite for music-only filtering.
Validates that non-music content is filtered out and artists are parsed correctly.
"""
from __future__ import annotations

import io
import logging
import sys
from typing import List, Dict

from services.music_filter import (
    filter_music_tracks,
    _is_title_allowed,
    _extract_artists,
    _get_duration_seconds,
    MIN_DURATION_SECONDS,
)

# Fix Windows terminal encoding issues
if sys.stdout.encoding != "utf-8":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")
logger = logging.getLogger(__name__)


class TestMusicFilter:
    """Test suite for music filtering functions."""

    @staticmethod
    def test_title_blocklist() -> None:
        blocked_titles = [
            "Interview with John Doe",
            "Official Movie Trailer 2024",
            "Behind the Scenes Production",
            "Podcast Episode 15",
            "Music Video (Lyric Video)",
            "Live Interview - Full Show",
            "Press Meet Coverage",
            "Reaction Video",
            "making of documentary",
            "official trailer reaction",
            "Release - Topic",
        ]

        allowed_titles = [
            "Song Title - Artist Name (Official Video)",
            "New Album Release 2024",
            "Greatest Hits Collection",
            "Live Performance Concert",
            "Studio Session Recording",
        ]

        for title in blocked_titles:
            result = _is_title_allowed(title)
            assert not result, f"Blocked title was incorrectly allowed: {title}"
        for title in allowed_titles:
            result = _is_title_allowed(title)
            assert result, f"Allowed title was incorrectly blocked: {title}"

    @staticmethod
    def test_duration_parsing() -> None:
        test_cases = [
            (280, 280),
            ("4:40", 280),
            ("1:30:45", 5445),
            (45, 45),
            ("0:45", 45),
            (60, 60),
            ("1:00", 60),
            (None, None),
            ("", None),
            ("invalid", None),
        ]

        for raw_duration, expected in test_cases:
            result = _get_duration_seconds(raw_duration)
            assert result == expected, f"Duration parse mismatch for {raw_duration!r}: expected {expected}, got {result}"

    @staticmethod
    def test_artist_extraction() -> None:
        test_cases = [
            ([{"name": "Artist One"}, {"name": "Artist Two"}], ["Artist One", "Artist Two"]),
            (["Artist One", "Artist Two"], ["Artist One", "Artist Two"]),
            ("Single Artist", ["Single Artist"]),
            ("Arivu, Santhosh Narayanan", ["Arivu", "Santhosh Narayanan"]),
            ("Harris Jayaraj - Topic", ["Harris Jayaraj"]),
            ("D. Imman - Official", ["D. Imman"]),
            ("Yuvan Shankar Raja VEVO", ["Yuvan Shankar Raja"]),
            ("Release - Topic", []),
            ([], []),
            (None, []),
        ]

        for artists_input, expected in test_cases:
            result = _extract_artists(artists_input)
            assert result == expected, f"Artist extraction mismatch for {artists_input!r}: expected {expected}, got {result}"

    @staticmethod
    def test_full_filtering_pipeline() -> None:
        mock_items: List[Dict] = [
            {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "artists": [{"name": "Rick Astley"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg", "width": 168, "height": 94}],
                "duration": "3:32",
                "album": "Whenever You Need Somebody",
            },
            {
                "title": "Song Without ID",
                "artists": [{"name": "Artist"}],
                "thumbnails": [{"url": "https://example.com/thumb.jpg"}],
                "duration": "3:00",
            },
            {
                "videoId": "interview123",
                "title": "Interview with Taylor Swift",
                "artists": [{"name": "Taylor Swift"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/interview123/default.jpg"}],
                "duration": "15:30",
            },
            {
                "videoId": "noartist456",
                "title": "Mystery Song",
                "artists": [],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/noartist456/default.jpg"}],
                "duration": "3:45",
            },
            {
                "videoId": "short789",
                "title": "Intro Music",
                "artists": [{"name": "Composer"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/short789/default.jpg"}],
                "duration": "0:45",
            },
            {
                "videoId": "nothumbnail",
                "title": "Great Song",
                "artists": [{"name": "Great Artist"}],
                "duration": "4:12",
            },
            {
                "videoId": "podcast999",
                "title": "Podcast Episode 42 - Music Discussion",
                "artists": [{"name": "Host"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/podcast999/default.jpg"}],
                "duration": "45:00",
            },
        ]

        result = filter_music_tracks(mock_items, include_validation=False)

        # With include_validation=False, lightweight filtering keeps three items.
        assert len(result) == 3


def run_all_tests() -> bool:
    tests = [
        ("Title Blocklist", TestMusicFilter.test_title_blocklist),
        ("Duration Parsing", TestMusicFilter.test_duration_parsing),
        ("Artist Extraction", TestMusicFilter.test_artist_extraction),
        ("Full Filtering Pipeline", TestMusicFilter.test_full_filtering_pipeline),
    ]

    results = {}
    for test_name, test_func in tests:
        try:
            results[test_name] = test_func()
        except Exception as exc:
            logger.error(f"Test '{test_name}' error: {exc}")
            results[test_name] = False

    for test_name, result in results.items():
        status = "[PASS]" if result else "[FAIL]"
        print(f"  {status}: {test_name}")

    return all(results.values())


if __name__ == "__main__":
    sys.exit(0 if run_all_tests() else 1)
