"""
Test suite for music-only filtering in trending and home endpoints.
Validates that non-music content is properly filtered out.
"""

import sys
import logging
from typing import List, Dict
import io

# Fix Windows terminal encoding issues
if sys.stdout.encoding != 'utf-8':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# Configure logging for test output
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Import filter functions
from services.music_filter import (
    filter_music_tracks,
    _is_title_allowed,
    _extract_artists,
    _extract_best_thumbnail,
    _get_duration_seconds,
    TITLE_BLOCKLIST,
    MIN_DURATION_SECONDS,
)


class TestMusicFilter:
    """Test suite for music filtering functions."""
    
    @staticmethod
    def test_title_blocklist():
        """Test that blocked titles are correctly rejected."""
        logger.info("=" * 60)
        logger.info("TEST: Title Blocklist Filtering")
        logger.info("=" * 60)
        
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
        ]
        
        allowed_titles = [
            "Song Title - Artist Name (Official Video)",
            "New Album Release 2024",
            "Greatest Hits Collection",
            "Live Performance Concert",
            "Studio Session Recording",
        ]
        
        failed = False
        for title in blocked_titles:
            result = _is_title_allowed(title)
            status = "[PASS]" if not result else "[FAIL]"
            print(f"  {status}: Blocked='{title}' -> {result}")
            if result:
                failed = True
        
        for title in allowed_titles:
            result = _is_title_allowed(title)
            status = "[PASS]" if result else "[FAIL]"
            print(f"  {status}: Allowed='{title}' -> {result}")
            if not result:
                failed = True
        
        return not failed
    
    @staticmethod
    def test_duration_parsing():
        """Test duration parsing from various formats."""
        logger.info("")
        logger.info("=" * 60)
        logger.info("TEST: Duration Parsing & Minimum Duration Validation")
        logger.info("=" * 60)
        
        test_cases = [
            (280, 280, "Integer seconds - valid"),
            ("4:40", 280, "MM:SS format - valid"),
            ("1:30:45", 5445, "H:MM:SS format - valid"),
            (45, 45, "Parses to 45s (will be rejected by MIN_DURATION filter)"),
            ("0:45", 45, "Parses to 45s (will be rejected by MIN_DURATION filter)"),
            (60, 60, "Exactly minimum duration"),
            ("1:00", 60, "Exactly minimum duration"),
            (None, None, "None input - returns None"),
            ("", None, "Empty string - returns None"),
            ("invalid", None, "Invalid format - returns None"),
        ]
        
        print(f"\n  MIN_DURATION_SECONDS requirement: {MIN_DURATION_SECONDS}s")
        
        failed = False
        for raw_duration, expected, description in test_cases:
            result = _get_duration_seconds(raw_duration)
            passed = (result == expected)
            status = "[PASS]" if passed else "[FAIL]"
            
            # Additional check for minimum duration
            if result is not None and result < MIN_DURATION_SECONDS:
                duration_status = "(will be rejected)"
            elif result is not None:
                duration_status = "(will be accepted)"
            else:
                duration_status = ""
            
            print(f"  {status}: {description}")
            print(f"         input={repr(raw_duration)} -> {repr(result)}s {duration_status}")
            if not passed:
                failed = True
        
        return not failed
    
    @staticmethod
    def test_artist_extraction():
        """Test artist extraction from various formats."""
        logger.info("")
        logger.info("=" * 60)
        logger.info("TEST: Artist Extraction")
        logger.info("=" * 60)
        
        test_cases = [
            # (input, expected_output, description)
            ([{"name": "Artist One"}, {"name": "Artist Two"}], ["Artist One", "Artist Two"], "List of dicts"),
            (["Artist One", "Artist Two"], ["Artist One", "Artist Two"], "List of strings"),
            ("Single Artist", ["Single Artist"], "Single string"),
            ([], [], "Empty list"),
            (None, [], "None"),
            ([{"name": ""}, {"name": "Valid Artist"}], ["Valid Artist"], "Mixed with empty"),
        ]
        
        failed = False
        for artists_input, expected, description in test_cases:
            result = _extract_artists(artists_input)
            status = "[PASS]" if result == expected else "[FAIL]"
            print(f"  {status}: {description}")
            print(f"         input={artists_input}")
            print(f"         expected={expected}, got={result}")
            if result != expected:
                failed = True
        
        return not failed
    
    @staticmethod
    def test_full_filtering_pipeline():
        """Test the complete filtering pipeline with realistic data."""
        logger.info("")
        logger.info("=" * 60)
        logger.info("TEST: Full Filtering Pipeline")
        logger.info("=" * 60)
        
        # Mock items simulating YTMusic API responses
        mock_items = [
            # Valid music track
            {
                "videoId": "dQw4w9WgXcQ",
                "title": "Never Gonna Give You Up",
                "artists": [{"name": "Rick Astley"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/dQw4w9WgXcQ/default.jpg", "width": 168, "height": 94}],
                "duration": "3:32",
                "album": "Whenever You Need Somebody",
            },
            # Missing videoId (should be rejected)
            {
                "title": "Song Without ID",
                "artists": [{"name": "Artist"}],
                "thumbnails": [{"url": "https://example.com/thumb.jpg"}],
                "duration": "3:00",
            },
            # Blocked title - Interview (should be rejected)
            {
                "videoId": "interview123",
                "title": "Interview with Taylor Swift",
                "artists": [{"name": "Taylor Swift"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/interview123/default.jpg"}],
                "duration": "15:30",
            },
            # Missing artists (should be rejected)
            {
                "videoId": "noartist456",
                "title": "Mystery Song",
                "artists": [],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/noartist456/default.jpg"}],
                "duration": "3:45",
            },
            # Too short duration (should be rejected)
            {
                "videoId": "short789",
                "title": "Intro Music",
                "artists": [{"name": "Composer"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/short789/default.jpg"}],
                "duration": "0:45",
            },
            # Valid, no thumbnail (should get fallback)
            {
                "videoId": "nothumbnail",
                "title": "Great Song",
                "artists": [{"name": "Great Artist"}],
                "duration": "4:12",
            },
            # Podcast episode (should be rejected)
            {
                "videoId": "podcast999",
                "title": "Podcast Episode 42 - Music Discussion",
                "artists": [{"name": "Host"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/podcast999/default.jpg"}],
                "duration": "45:00",
            },
            # Trailer (should be rejected)
            {
                "videoId": "trailer111",
                "title": "Official Movie Trailer",
                "artists": [{"name": "Studio"}],
                "thumbnails": [{"url": "https://i.ytimg.com/vi/trailer111/default.jpg"}],
                "duration": "2:30",
            },
        ]
        
        logger.info(f"Testing with {len(mock_items)} mock items")
        
        # Test filtering (without validation to avoid API calls)
        result = filter_music_tracks(mock_items, ytmusic=None, include_validation=False)
        
        logger.info(f"Filter result: {len(result)} items passed out of {len(mock_items)}")
        
        # Expected: Only items 0 (valid) and 5 (fallback thumbnail) should pass
        expected_count = 2
        passed = len(result) == expected_count
        
        print(f"\n  Input items: {len(mock_items)}")
        print(f"  Output items: {len(result)}")
        print(f"  Expected: {expected_count}")
        print(f"  Status: {'[PASS]' if passed else '[FAIL]'}")
        
        if result:
            print(f"\n  Passed items:")
            for item in result:
                print(f"    - {item['title']} by {', '.join(item['artists'])}")
                print(f"      videoId: {item['videoId']}")
                print(f"      thumbnail: {item['thumbnail']}")
        
        return passed


def run_all_tests():
    """Run all test cases."""
    logger.info("\n")
    logger.info("╔" + "=" * 58 + "╗")
    logger.info("║" + " " * 58 + "║")
    logger.info("║  MUSIC-ONLY FILTERING TEST SUITE" + " " * 24 + "║")
    logger.info("║" + " " * 58 + "║")
    logger.info("╚" + "=" * 58 + "╝")
    
    tests = [
        ("Title Blocklist", TestMusicFilter.test_title_blocklist),
        ("Duration Parsing", TestMusicFilter.test_duration_parsing),
        ("Artist Extraction", TestMusicFilter.test_artist_extraction),
        ("Full Filtering Pipeline", TestMusicFilter.test_full_filtering_pipeline),
    ]
    
    results = {}
    for test_name, test_func in tests:
        try:
            result = test_func()
            results[test_name] = result
        except Exception as e:
            logger.error(f"Test '{test_name}' raised exception: {str(e)}", exc_info=True)
            results[test_name] = False
    
    # Summary
    logger.info("")
    logger.info("=" * 60)
    logger.info("TEST SUMMARY")
    logger.info("=" * 60)
    
    for test_name, result in results.items():
        status = "[PASS]" if result else "[FAIL]"
        print(f"  {status}: {test_name}")
    
    total = len(results)
    passed = sum(1 for r in results.values() if r)
    
    logger.info("")
    logger.info(f"Total: {passed}/{total} tests passed")
    
    return all(results.values())


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
