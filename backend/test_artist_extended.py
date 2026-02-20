"""
Test Artist Endpoint with Extended Timeout
Tests the corrected artist API implementation with 60 second timeout
"""

import requests
import json

BASE_URL = "http://127.0.0.1:5000"

def test_artist_extended():
    """Test artist endpoint with extended timeout"""
    print("\n" + "="*60)
    print("Testing Artist Endpoint (Corrected Implementation)")
    print("="*60)
    
    # Test with Coldplay
    artist_id = "UCIaFw5VBEK8qaW6nRpx_qnw"
    print(f"\n🎤 Fetching Artist: {artist_id} (Coldplay)")
    print(f"⏱️  Timeout: 60 seconds")
    print(f"🔄 API Calls: get_artist() + get_playlist() + get_artist_albums()")
    
    try:
        response = requests.get(
            f"{BASE_URL}/api/artist/{artist_id}",
            timeout=60  # Extended timeout for multiple API calls
        )
        
        if response.status_code == 200:
            data = response.json()
            
            print("\n✅ SUCCESS!")
            print(f"\n📊 Artist Info:")
            print(f"  Name: {data.get('name', 'N/A')}")
            print(f"  Subscribers: {data.get('subscribers', 'N/A')}")
            print(f"  Description: {data.get('description', 'N/A')[:100]}...")
            
            # Top Songs (from playlist API)
            print(f"\n🎵 Top Songs ({len(data.get('topSongs', []))}):")
            for i, song in enumerate(data.get('topSongs', [])[:3], 1):
                print(f"  {i}. {song.get('title', 'N/A')} - {song.get('artist', 'N/A')}")
                print(f"     Duration: {song.get('duration', 'N/A')}")
            
            # Albums (from get_artist_albums API)
            print(f"\n💿 Albums ({len(data.get('albums', []))}):")
            for i, album in enumerate(data.get('albums', [])[:3], 1):
                print(f"  {i}. {album.get('title', 'N/A')} ({album.get('year', 'N/A')})")
                print(f"     Type: {album.get('type', 'N/A')}")
            
            print("\n" + "="*60)
            print(f"✅ All data retrieved successfully!")
            print(f"   Top Songs: {len(data.get('topSongs', []))} songs")
            print(f"   Albums: {len(data.get('albums', []))} albums")
            print("="*60)
            
        else:
            print(f"\n❌ Error: {response.status_code}")
            print(f"Response: {response.text[:200]}")
            
    except requests.exceptions.Timeout:
        print("\n❌ Request timed out after 60 seconds")
        print("   This indicates the API calls are taking very long")
        print("   Consider: concurrent calls or caching")
    except Exception as e:
        print(f"\n❌ Error: {e}")

if __name__ == "__main__":
    test_artist_extended()
