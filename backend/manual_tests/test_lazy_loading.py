"""
Test Script for Lazy-Loading Architecture
Tests new endpoints and verifies zero API calls on Firestore-only operations
"""

import requests
import json
import time
import os

BASE_URL = os.getenv("BASE_URL", "http://localhost:5000")
TEST_USER_ID = "test_user_123"  # Replace with a real user ID from your Firestore

def print_section(title):
    """Print a formatted section header"""
    print("\n" + "=" * 70)
    print(f"  {title}")
    print("=" * 70 + "\n")

def test_endpoint(name, url, expected_keys=None):
    """Test an endpoint and print results"""
    print(f"📡 Testing: {name}")
    print(f"   URL: {url}")
    
    try:
        start_time = time.time()
        response = requests.get(url, timeout=10)
        elapsed = time.time() - start_time
        
        print(f"   ✅ Status: {response.status_code}")
        print(f"   ⏱️  Response time: {elapsed:.3f}s")
        
        if response.status_code == 200:
            data = response.json()
            print(f"   📦 Response keys: {list(data.keys())}")
            
            # Verify expected keys
            if expected_keys:
                missing_keys = [k for k in expected_keys if k not in data]
                if missing_keys:
                    print(f"   ⚠️  Missing keys: {missing_keys}")
                else:
                    print(f"   ✅ All expected keys present")
            
            # Print sample data
            if isinstance(data, dict):
                if 'count' in data:
                    print(f"   📊 Count: {data['count']}")
                if 'results' in data and isinstance(data['results'], list):
                    print(f"   📊 Results: {len(data['results'])} items")
                if 'top_artists' in data and isinstance(data['top_artists'], list):
                    print(f"   📊 Top artists: {len(data['top_artists'])} artists")
            
            return True, data
        else:
            print(f"   ❌ Error: {response.text}")
            return False, None
            
    except Exception as e:
        print(f"   ❌ Exception: {str(e)}")
        return False, None

def main():
    """Run all tests"""
    
    print_section("🧪 Testing Lazy-Loading Architecture")
    print(f"Base URL: {BASE_URL}")
    print(f"Test User ID: {TEST_USER_ID}")
    print("\n⚠️  Make sure the Flask server is running on port 5000!")
    input("Press Enter to continue...")
    
    # Test 1: User Profile (Firestore-only)
    print_section("1️⃣  User Profile (Firestore-only, Zero API calls)")
    test_endpoint(
        "GET /api/user/profile",
        f"{BASE_URL}/api/user/profile?uid={TEST_USER_ID}",
        expected_keys=['userId', 'top_artists', 'stats']
    )
    
    # Test 2: Recommendations V2 (Firestore-only)
    print_section("2️⃣  Recommendations V2 (Firestore-only, Zero API calls)")
    test_endpoint(
        "GET /api/recommendations/v2",
        f"{BASE_URL}/api/recommendations/v2?uid={TEST_USER_ID}&limit=20",
        expected_keys=['userId', 'count', 'source', 'results']
    )
    
    # Test 3: Mood Categories (Static list, no API calls)
    print_section("3️⃣  Mood Categories (Static list, Zero API calls)")
    test_endpoint(
        "GET /api/home/moods",
        f"{BASE_URL}/api/home/moods",
        expected_keys=['success', 'categories', 'count']
    )
    
    # Test 4: Mood Playlists (Lazy-loaded, API call on first request)
    print_section("4️⃣  Mood Playlists (Lazy-loaded, First Request = API call)")
    print("🔹 First request (should trigger API call):")
    success1, data1 = test_endpoint(
        "GET /api/home/mood-playlists",
        f"{BASE_URL}/api/home/mood-playlists?mood=Chill&limit=10",
        expected_keys=['success', 'playlists', 'count']
    )
    
    if success1:
        print("\n🔹 Second request (should use cache):")
        time.sleep(0.5)
        success2, data2 = test_endpoint(
            "GET /api/home/mood-playlists (CACHED)",
            f"{BASE_URL}/api/home/mood-playlists?mood=Chill&limit=10",
            expected_keys=['success', 'playlists', 'count']
        )
    
    # Test 5: Artist Details (Lazy-loaded, API call on first request)
    print_section("5️⃣  Artist Details (Lazy-loaded, First Request = API call)")
    test_artist = "Arijit Singh"
    print(f"🔹 Testing artist: {test_artist}")
    print("🔹 First request (should trigger API call):")
    success1, data1 = test_endpoint(
        "GET /api/artist/<name>",
        f"{BASE_URL}/api/artist/{test_artist}",
        expected_keys=['artist', 'songs', 'albums']
    )
    
    if success1:
        print("\n🔹 Second request (should use cache):")
        time.sleep(0.5)
        success2, data2 = test_endpoint(
            "GET /api/artist/<name> (CACHED)",
            f"{BASE_URL}/api/artist/{test_artist}",
            expected_keys=['artist', 'songs', 'albums']
        )
    
    # Test 6: Original Recommendations endpoint (for comparison)
    print_section("6️⃣  Original Recommendations (Legacy Baseline)")
    test_endpoint(
        "GET /api/recommendations",
        f"{BASE_URL}/api/recommendations?uid={TEST_USER_ID}&limit=20",
        expected_keys=['count', 'source', 'results']
    )
    
    # Summary
    print_section("✅ Testing Complete!")
    print("""
Key Observations:
1. User Profile & Recommendations V2: Should show ZERO JioSaavn API calls in logs
2. Mood Categories: Always returns static list, no API calls
3. Mood Playlists: First request triggers API, second uses cache
4. Artist Details: First request triggers API, second uses cache

Verify in Backend Logs:
- Look for "[API/REC-V2] Firestore-only recommendations"
- Look for "[API/USER-PROFILE] Fetching taste profile"
- Look for "cache hit" messages on repeat requests
- Should see NO repeated API calls for cached content
    """)

if __name__ == "__main__":
    main()
