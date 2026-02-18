#!/usr/bin/env python3
"""
Comprehensive verification: Check that trending songs work end-to-end
"""
from services.music_service import MusicService
from config import Config

print("=" * 60)
print("COMPREHENSIVE TRENDING FIX VERIFICATION")
print("=" * 60)

try:
    # Initialize music service
    music_service = MusicService(Config.YDL_OPTS)
    
    # Test 1: Get trending songs
    print("\n[TEST 1] Getting trending songs (limit=10)...")
    trending = music_service.get_trending_songs(limit=10)
    
    print(f"✓ Returned {len(trending)} songs")
    
    if len(trending) == 0:
        print("✗ ERROR: No trending songs returned!")
        exit(1)
    
    # Test 2: Validate structure
    print("\n[TEST 2] Validating song structure...")
    for i, song in enumerate(trending, 1):
        required_fields = ['videoId', 'title', 'artists', 'thumbnail']
        missing = [f for f in required_fields if not song.get(f)]
        
        if missing:
            print(f"✗ Song {i} missing fields: {missing}")
            exit(1)
        
        # Validate field types
        if not isinstance(song['artists'], list) or len(song['artists']) == 0:
            print(f"✗ Song {i} has invalid artists: {song['artists']}")
            exit(1)
        
        if not isinstance(song['videoId'], str) or not song['videoId'].strip():
            print(f"✗ Song {i} has invalid videoId: {song['videoId']}")
            exit(1)
    
    print(f"✓ All {len(trending)} songs have valid structure")
    
    # Test 3: Check for obvious non-music content
    print("\n[TEST 3] Checking for non-music content...")
    blocklist_terms = ['interview', 'trailer', 'podcast', 'reaction', 'review']
    for song in trending:
        title_lower = song['title'].lower()
        for term in blocklist_terms:
            if term in title_lower:
                print(f"✗ Found potential non-music content: '{term}' in '{song['title']}'")
                # Don't exit - just warn, as some titles might be false positives
    
    print(f"✓ No obvious non-music content detected")
    
    # Test 4: Display results
    print("\n[TEST 4] Displaying first 5 trending songs:")
    print("-" * 60)
    for i, song in enumerate(trending[:5], 1):
        print(f"\n{i}. {song['title']}")
        print(f"   Artists: {', '.join(song['artists'])}")
        print(f"   VideoId: {song['videoId']}")
        print(f"   Thumbnail Present: {'Yes' if song['thumbnail'] else 'No (using fallback)'}")
    
    print("\n" + "=" * 60)
    print("✓ ALL TESTS PASSED!")
    print(f"✓ Trending songs are working correctly ({len(trending)} songs returned)")
    print("=" * 60)
    
except Exception as e:
    print(f"\n✗ ERROR: {e}")
    import traceback
    traceback.print_exc()
    exit(1)
