"""Quick test script for JioSaavn integration"""
import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from services.jiosaavn_service import search_songs
from services.stream_service import get_stream_url

print("=" * 50)
print("Testing JioSaavn Search")
print("=" * 50)

results = search_songs('arijit', limit=5)
print(f"\nSearch returned {len(results)} songs\n")

if results:
    for i, song in enumerate(results[:3], 1):
        print(f"{i}. ID: {song.get('id')}")
        print(f"   Title: {song.get('title')}")
        print(f"   Artist: {song.get('artist')}")
        print(f"   videoId: {song.get('videoId')}")
        print()
    
    print("=" * 50)
    print("Testing Stream Resolution")
    print("=" * 50)
    
    test_song_id = results[0].get('id')
    print(f"\nResolving stream for song ID: {test_song_id}\n")
    
    stream_data = get_stream_url(test_song_id)
    if stream_data:
        print("✓ Stream resolved successfully!")
        print(f"  Title: {stream_data.get('title')}")
        print(f"  Artist: {stream_data.get('artist')}")
        print(f"  Stream URL: {stream_data.get('stream_url')[:60]}...")
        print(f"  videoId: {stream_data.get('videoId')}")
        print(f"  id: {stream_data.get('id')}")
    else:
        print("✗ Failed to resolve stream")
else:
    print("✗ No search results")
