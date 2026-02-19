"""
Complete Artist Songs Test
Test the exact flow Android app would use
"""

import requests
import json

BASE_URL = "http://127.0.0.1:5000"

def test_artist_complete():
    """Test Ed Sheeran artist (known to work from previous test)"""
    
    # Browse ID from search result
    artist_id = "UC0C-w0YjGpqDXGB8IHb662A"
    
    print("\n" + "="*70)
    print("🎸 COMPLETE ARTIST TEST - Ed Sheeran")
    print("="*70)
    
    try:
        print(f"\n1️⃣ Fetching artist details...")
        response = requests.get(f"{BASE_URL}/api/artist/{artist_id}", timeout=60)
        
        print(f"   Status: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            
            print(f"\n2️⃣ Parsing response...")
            print(f"   Success: {data.get('success')}")
            print(f"   Cached: {data.get('cached')}")
            
            artist = data.get('artist')
            if not artist:
                print(f"\n❌ ISSUE: 'artist' key missing from response!")
                print(f"   Response keys: {list(data.keys())}")
                return
                
            print(f"\n3️⃣ Artist Data:")
            print(f"   artistId: {artist.get('artistId')}")
            print(f"   name: {artist.get('name')}")
            print(f"   subscribers: {artist.get('subscribers')}")
            print(f"   thumbnail: {artist.get('thumbnail', 'MISSING')[:50]}...")
            
            top_songs = artist.get('topSongs')
            if not top_songs:
                print(f"\n❌ ISSUE: 'topSongs' is empty or missing!")
                print(f"   topSongs value: {top_songs}")
                print(f"   Artist keys: {list(artist.keys())}")
                return
            
            print(f"\n4️⃣ Top Songs Array:")
            print(f"   Type: {type(top_songs)}")
            print(f"   Length: {len(top_songs)}")
            print(f"   Is Empty: {len(top_songs) == 0}")
            
            if len(top_songs) > 0:
                print(f"\n5️⃣ First Song Details:")
                first_song = top_songs[0]
                print(f"   Type: {type(first_song)}")
                print(f"   Keys: {list(first_song.keys())}")
                print(f"   ")
                print(f"   videoId: {first_song.get('videoId')}")
                print(f"   title: {first_song.get('title')}")
                print(f"   artists: {first_song.get('artists')}")
                print(f"   album: {first_song.get('album')}")
                print(f"   thumbnail: {first_song.get('thumbnail', 'MISSING')[:50]}...")
                print(f"   duration: {first_song.get('duration', 'null')}")
                
                print(f"\n6️⃣ All Songs List:")
                for i, song in enumerate(top_songs, 1):
                    print(f"   {i}. {song.get('title')} - {', '.join(song.get('artists', []))}")
                
                print(f"\n✅ SUCCESS - All data correct!")
                print(f"   Songs should display in Android app")
            else:
                print(f"\n❌ ISSUE: topSongs array is empty!")
                
        else:
            print(f"\n❌ HTTP Error: {response.status_code}")
            print(f"Response: {response.text}")
            
    except Exception as e:
        print(f"\n❌ Exception: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_artist_complete()
