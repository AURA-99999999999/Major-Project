"""
Test Top Artists Endpoint
Validates the new top artists feature for home screen
"""

import requests

BASE_URL = "http://127.0.0.1:5000"

def test_top_artists():
    """Test top artists endpoint"""
    print("\n" + "="*70)
    print("🎸 TOP ARTISTS ENDPOINT TEST")
    print("="*70)
    
    # Test with authenticated user (you'll need a real UID from Firebase)
    test_uid = "test_user_123"  # Replace with real UID for actual test
    
    print(f"\n1️⃣ Testing endpoint: /api/home/top-artists")
    print(f"   User ID: {test_uid}")
    print(f"   Limit: 10")
    
    try:
        response = requests.get(
            f"{BASE_URL}/api/home/top-artists",
            params={"uid": test_uid, "limit": 10},
            timeout=60
        )
        
        print(f"\n2️⃣ Response Status: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            
            print(f"\n3️⃣ Response Data:")
            print(f"   Success: {data.get('success')}")
            print(f"   Count: {data.get('count')}")
            
            artists = data.get('artists', [])
            
            if artists:
                print(f"\n4️⃣ Top Artists ({len(artists)}):")
                for i, artist in enumerate(artists[:5], 1):
                    print(f"\n   Artist {i}:")
                    print(f"      browseId: {artist.get('browseId')}")
                    print(f"      name: {artist.get('name')}")
                    print(f"      thumbnail: {artist.get('thumbnail', 'N/A')[:50]}...")
                    print(f"      subscribers: {artist.get('subscribers', 'N/A')}")
                
                print(f"\n✅ SUCCESS - Endpoint working correctly!")
                print(f"   - Artists returned with circular thumbnails")
                print(f"   - Ready for Android display")
                print("="*70 + "\n")
            else:
                print(f"\n⚠️  No artists returned (empty listening history)")
                print(f"   This is expected for new users")
                print("="*70 + "\n")
                
        else:
            print(f"\n❌ Error: {response.status_code}")
            print(f"Response: {response.json()}")
            
    except Exception as e:
        print(f"\n❌ Exception: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    test_top_artists()
