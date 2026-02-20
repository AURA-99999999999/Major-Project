"""
Final Test - Artist Songs Display Fix
Tests multiple artists to ensure songs are consistently returned
"""

import requests

BASE_URL = "http://127.0.0.1:5000"

def test_multiple_artists():
    """Test that artist songs are displayed for various artists"""
    
    test_cases = [
        ("UCIaFw5VBEK8qaW6nRpx_qnw", "Coldplay"),
        ("UC0C-w0YjGpqDXGB8IHb662A", "Ed Sheeran"),
        ("UCEOhyFWWfKUuOqNRG8VBIgg", "Billie Eilish"),
    ]
    
    print("\n" + "="*70)
    print("🎸 ARTIST SONGS DISPLAY FIX - VALIDATION TEST")
    print("="*70)
    
    all_passed = True
    
    for artist_id, artist_name in test_cases:
        print(f"\n{'─'*70}")
        print(f"Testing: {artist_name}")
        print(f"{'─'*70}")
        
        try:
            response = requests.get(f"{BASE_URL}/api/artist/{artist_id}", timeout=60)
            
            if response.status_code == 200:
                data = response.json()
                artist = data.get('artist', {})
                songs = artist.get('topSongs', [])
                albums = artist.get('albums', [])
                
                if len(songs) > 0:
                    print(f"✅ PASS - {len(songs)} songs returned")
                    print(f"   Top Songs:")
                    for i, song in enumerate(songs[:3], 1):
                        print(f"     {i}. {song.get('title')} - {', '.join(song.get('artists', []))}")
                    print(f"   Albums: {len(albums)}")
                else:
                    print(f"❌ FAIL - No songs returned!")
                    print(f"   Artist data: name={artist.get('name')}, subscribers={artist.get('subscribers')}")
                    all_passed = False
            else:
                print(f"❌ FAIL - HTTP {response.status_code}")
                all_passed = False
                
        except Exception as e:
            print(f"❌ FAIL - Exception: {e}")
            all_passed = False
    
    print(f"\n{'='*70}")
    if all_passed:
        print("✅ ALL TESTS PASSED - Artist songs display fix verified!")
        print("   Android app should now show artist songs correctly")
    else:
        print("❌ SOME TESTS FAILED - Review errors above")
    print("="*70 + "\n")

if __name__ == "__main__":
    test_multiple_artists()
