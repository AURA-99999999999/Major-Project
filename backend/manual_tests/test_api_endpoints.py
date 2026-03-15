"""Test backend API endpoints end-to-end"""
import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from app import app

print("=" * 60)
print("TESTING BACKEND API ENDPOINTS")
print("=" * 60)

client = app.test_client()

# Test search endpoint
print("\n1. Testing /api/search endpoint")
print("-" * 60)

search_resp = client.get('/api/search?query=arijit&limit=3')
search_data = search_resp.get_json()

print(f"   Status Code: {search_resp.status_code}")
print(f"   Success: {search_data.get('success')}")
print(f"   Songs Count: {len(search_data.get('songs', []))}")

if search_data.get('songs'):
    first_song = search_data['songs'][0]
    print(f"\n   First Song:")
    print(f"     - videoId: {first_song.get('videoId')}")
    print(f"     - Title: {first_song.get('title')}")
    print(f"     - Artist: {first_song.get('artist')}")
    
    # Test streaming endpoint
    print(f"\n2. Testing /api/song endpoint")
    print("-" * 60)
    
    song_id = first_song.get('videoId')
    stream_resp = client.get(f'/api/song/{song_id}')
    stream_data = stream_resp.get_json()
    
    print(f"   Status Code: {stream_resp.status_code}")
    print(f"   Success: {stream_data.get('success')}")
    
    if stream_data.get('data'):
        song_data = stream_data['data']
        print(f"\n   Stream Data:")
        print(f"     - videoId: {song_data.get('videoId')}")
        print(f"     - id: {song_data.get('id')}")
        print(f"     - Title: {song_data.get('title')}")
        print(f"     - Artist: {song_data.get('artist')}")
        print(f"     - Stream URL exists: {bool(song_data.get('stream_url'))}")
        print(f"     - Stream URL: {song_data.get('stream_url', '')[:70]}...")
        print(f"     - Duration: {song_data.get('duration')}")
        print(f"     - Album: {song_data.get('album')}")
    else:
        print(f"   ✗ Error: {stream_data.get('error')}")
else:
    print("   ✗ No songs found in search results")

print("\n" + "=" * 60)
print("✓ ALL TESTS COMPLETED")
print("=" * 60)
