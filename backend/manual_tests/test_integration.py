"""Test script to verify JioSaavnAPI integration."""

import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from services import jiosaavn_service

print("=" * 60)
print("AURA Backend - JioSaavnAPI Integration Test")
print("=" * 60)
print()

# Test 1: Search songs
print("TEST 1: Search for songs...")
print(f"API Base URL: {jiosaavn_service.JIOSAAVN_API_BASE}")
print(f"Calling: {jiosaavn_service.JIOSAAVN_API_BASE}/result/")
print()

try:
    songs = jiosaavn_service.search_songs("arijit singh", limit=3)
    if songs:
        print(f"✓ SUCCESS: Found {len(songs)} songs")
        for i, song in enumerate(songs, 1):
            print(f"  {i}. {song.get('title', 'N/A')} - {song.get('singers', 'N/A')}")
    else:
        print("✗ FAILED: No songs returned")
except Exception as e:
    print(f"✗ ERROR: {e}")

print()
print("-" * 60)
print()

# Test 2: Get artists (uses external API)
print("TEST 2: Search for artists...")
print(f"Saavn Sumit URL: {jiosaavn_service.SAAVN_SUMIT_BASE}")
print(f"Calling: {jiosaavn_service.SAAVN_SUMIT_BASE}/search/artists")
print()

try:
    artists = jiosaavn_service.search_artists("arijit singh", limit=2)
    if artists:
        print(f"✓ SUCCESS: Found {len(artists)} artists")
        for i, artist in enumerate(artists, 1):
            print(f"  {i}. {artist.get('name', 'N/A')}")
    else:
        print("✗ FAILED: No artists returned")
except Exception as e:
    print(f"✗ ERROR: {e}")

print()
print("-" * 60)
print()

# Test 3: Get mood playlists (uses external API)
print("TEST 3: Get mood playlists...")
print(f"Calling: {jiosaavn_service.SAAVN_SUMIT_BASE}/search/playlists")
print()

try:
    playlists = jiosaavn_service.get_mood_playlists("Chill", limit=2)
    if playlists:
        print(f"✓ SUCCESS: Found {len(playlists)} playlists")
        for i, playlist in enumerate(playlists, 1):
            print(f"  {i}. {playlist.get('title', 'N/A')}")
    else:
        print("✗ FAILED: No playlists returned")
except Exception as e:
    print(f"✗ ERROR: {e}")

print()
print("=" * 60)
print("IMPORTANT: For TEST 1 to pass, JioSaavnAPI server must be running")
print("Start it with: cd JioSaavnAPI && python app.py")
print("=" * 60)
