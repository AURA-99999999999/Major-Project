"""
Test Artist Songs - Diagnostic
Check if songs are being returned and if there's a filtering issue
"""

import requests
import json

BASE_URL = "http://127.0.0.1:5000"

def test_artist_songs():
    """Test multiple artists to see if songs are returned"""
    
    test_artists = [
        ("UCIaFw5VBEK8qaW6nRpx_qnw", "Coldplay"),
        ("UCiMhD4jzUqG-IgPzqmmYFLA", "Drake"),
        ("UC0C-w0YjGpqDXGB8IHb662A", "Ed Sheeran")
    ]
    
    for artist_id, artist_name in test_artists:
        print(f"\n{'='*60}")
        print(f"Testing: {artist_name} ({artist_id})")
        print('='*60)
        
        try:
            response = requests.get(
                f"{BASE_URL}/api/artist/{artist_id}",
                timeout=60
            )
            
            if response.status_code == 200:
                data = response.json()
                artist = data.get('artist', {})
                
                print(f"\n✓ Response Status: {response.status_code}")
                print(f"✓ Artist Name: {artist.get('name', 'N/A')}")
                print(f"✓ Top Songs Count: {len(artist.get('topSongs', []))}")
                print(f"✓ Albums Count: {len(artist.get('albums', []))}")
                
                # Show first 3 songs with all fields
                if artist.get('topSongs'):
                    print(f"\n📋 Top Songs Details:")
                    for i, song in enumerate(artist.get('topSongs', [])[:3], 1):
                        print(f"\n  Song {i}:")
                        print(f"    title: {song.get('title', 'N/A')}")
                        print(f"    videoId: {song.get('videoId', 'N/A')}")
                        print(f"    artists: {song.get('artists', [])}")
                        print(f"    album: {song.get('album', 'N/A')}")
                        print(f"    thumbnail: {song.get('thumbnail', 'N/A')[:50]}...")
                        print(f"    duration: {song.get('duration', 'N/A')}")
                else:
                    print(f"\n❌ NO SONGS RETURNED!")
                    print(f"   Full artist object keys: {list(artist.keys())}")
                    
            else:
                print(f"\n❌ Error: {response.status_code}")
                print(f"Response: {response.text[:200]}")
                
        except Exception as e:
            print(f"\n❌ Exception: {e}")
            import traceback
            traceback.print_exc()

if __name__ == "__main__":
    test_artist_songs()
