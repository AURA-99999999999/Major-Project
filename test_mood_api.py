"""
Test script for Mood API endpoints
Tests the /api/home/moods and /api/home/mood-playlists endpoints
"""
import requests
import json

BASE_URL = "http://127.0.0.1:5000"

def test_mood_categories():
    """Test GET /api/home/moods"""
    print("\n" + "="*60)
    print("Testing: GET /api/home/moods")
    print("="*60)
    
    try:
        response = requests.get(f"{BASE_URL}/api/home/moods")
        print(f"Status Code: {response.status_code}")
        
        data = response.json()
        print(f"Success: {data.get('success')}")
        print(f"Count: {data.get('count')}")
        
        categories = data.get('categories', [])
        if categories:
            print(f"\nFirst 5 categories:")
            for i, category in enumerate(categories[:5], 1):
                print(f"  {i}. {category['title']}")
                print(f"     Params: {category['params'][:50]}...")
                print(f"     Color: {category['color']}")
            
            # Return params from first category for next test
            return categories[0]['params']
        else:
            print("No categories returned")
            return None
            
    except Exception as e:
        print(f"ERROR: {str(e)}")
        return None

def test_mood_playlists(params):
    """Test GET /api/home/mood-playlists"""
    print("\n" + "="*60)
    print("Testing: GET /api/home/mood-playlists")
    print("="*60)
    
    if not params:
        print("ERROR: No params provided (test skipped)")
        return
    
    try:
        response = requests.get(
            f"{BASE_URL}/api/home/mood-playlists",
            params={'params': params, 'limit': 5}
        )
        print(f"Status Code: {response.status_code}")
        
        data = response.json()
        print(f"Success: {data.get('success')}")
        print(f"Count: {data.get('count')}")
        
        playlists = data.get('playlists', [])
        if playlists:
            print(f"\nPlaylists returned:")
            for i, playlist in enumerate(playlists, 1):
                print(f"  {i}. {playlist['title']}")
                print(f"     ID: {playlist['playlistId']}")
                if playlist.get('description'):
                    print(f"     Desc: {playlist['description'][:50]}...")
        else:
            print("No playlists returned")
            
    except Exception as e:
        print(f"ERROR: {str(e)}")

def test_invalid_mood_playlists():
    """Test GET /api/home/mood-playlists with missing params"""
    print("\n" + "="*60)
    print("Testing: GET /api/home/mood-playlists (invalid - no params)")
    print("="*60)
    
    try:
        response = requests.get(f"{BASE_URL}/api/home/mood-playlists")
        print(f"Status Code: {response.status_code}")
        
        data = response.json()
        print(f"Success: {data.get('success')}")
        print(f"Error: {data.get('error', 'N/A')}")
            
    except Exception as e:
        print(f"ERROR: {str(e)}")

if __name__ == "__main__":
    print("\n" + "="*60)
    print("MOOD API ENDPOINT TESTS")
    print("="*60)
    print("\nMake sure the Flask server is running on http://127.0.0.1:5000")
    print("Run with: python app.py")
    
    # Test 1: Get mood categories
    params = test_mood_categories()
    
    # Test 2: Get mood playlists for first category
    if params:
        test_mood_playlists(params)
    
    # Test 3: Error handling (missing params)
    test_invalid_mood_playlists()
    
    print("\n" + "="*60)
    print("TESTS COMPLETE")
    print("="*60)
