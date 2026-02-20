#!/usr/bin/env python
"""Quick test to debug trending list"""
import sys
sys.path.insert(0, '.')

from services.music_service import MusicService
from config import Config

print("=" * 60)
print("Testing Trending Endpoint")
print("=" * 60)

ms = MusicService(Config.YDL_OPTS)
print("\nGetting trending songs from YTMusic...")

try:
    trending = ms.get_trending_songs(limit=20)
    print(f"\nResult: {len(trending)} songs returned")
    
    for i, item in enumerate(trending[:5], 1):
        print(f"\n{i}. {item.get('title')}")
        print(f"   Artists: {', '.join(item.get('artists', []))}")
        print(f"   VideoID: {item.get('videoId')}")
        print(f"   Has Thumbnail: {'Yes' if item.get('thumbnail') else 'No'}")
    
    if len(trending) == 0:
        print("\n[ERROR] No trending songs returned!")
        print("This could mean:")
        print("  1. All items were filtered out (too strict filtering)")
        print("  2. YTMusic API returned no data")
        print("  3. Error occurred during fetch")
                
except Exception as e:
    print(f"\n[ERROR] {str(e)}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
