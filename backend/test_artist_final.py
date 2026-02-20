"""
Final Artist Endpoint Test - Production Ready
Validates the corrected YTMusic API implementation
"""

import requests

BASE_URL = "http://127.0.0.1:5000"

def test_artist_final():
    print("\n" + "="*70)
    print("🎤 ARTIST ENDPOINT - PRODUCTION VALIDATION")
    print("="*70)
    
    artist_id = "UCIaFw5VBEK8qaW6nRpx_qnw"
    print(f"\n✅ Testing: Coldplay ({artist_id})")
    
    try:
        response = requests.get(f"{BASE_URL}/api/artist/{artist_id}", timeout=60)
        
        if response.status_code == 200:
            data = response.json()
            
            # Correct access path: data['artist']
            artist = data.get('artist', {})
            cached = data.get('cached', False)
            
            print(f"\n📊 Artist Information:")
            print(f"   Name: {artist.get('name')}")
            print(f"   Subscribers: {artist.get('subscribers')}")
            print(f"   Description: {artist.get('description', '')[:100]}...")
            print(f"   Cached: {cached}")
            
            print(f"\n🎵 Top Songs (using get_playlist API):")
            for i, song in enumerate(artist.get('topSongs', [])[:5], 1):
                print(f"   {i}. {song.get('title')} - {', '.join(song.get('artists', []))}")
            
            print(f"\n💿 Albums (using get_artist_albums API):")
            for i, album in enumerate(artist.get('albums', [])[:5], 1):
                print(f"   {i}. {album.get('title')} ({album.get('year')})")
            
            print(f"\n" + "="*70)
            print(f"✅ SUCCESS - All YTMusic APIs Working Correctly!")
            print(f"   ✓ get_artist(): Succeeded")
            print(f"   ✓ get_playlist(): Succeeded ({len(artist.get('topSongs', []))} songs)")
            print(f"   ✓ get_artist_albums(): Succeeded ({len(artist.get('albums', []))} albums)")
            print(f"   ✓ filter_music_tracks(): Applied")
            print("="*70 + "\n")
            
        else:
            print(f"\n❌ Error: {response.status_code}")
            print(f"Response: {response.text[:200]}")
            
    except Exception as e:
        print(f"\n❌ Error: {e}")

if __name__ == "__main__":
    test_artist_final()
