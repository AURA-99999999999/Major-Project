"""
Test script to verify Collaborative Filtering is working and appearing on home screen
"""
import requests
import json
import sys

BASE_URL = "http://localhost:5000"  # Adjust if your Flask app runs on a different port

def print_section(title):
    """Print a section header"""
    print("\n" + "=" * 60)
    print(title)
    print("=" * 60)

def test_cf_debug(uid):
    """Test the CF debug endpoint"""
    print_section("1. Testing CF Debug Endpoint")
    
    url = f"{BASE_URL}/api/debug/cf-status?uid={uid}"
    print(f"\nGET {url}")
    
    try:
        response = requests.get(url, timeout=10)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print("\n✓ CF Debug Response:")
            print(f"  Firestore Available: {data.get('firestore_available')}")
            print(f"  User Has Profile: {data['user_profile']['has_profile']}")
            print(f"  Artist Count: {data['user_profile']['artist_count']}")
            print(f"  Similar Users: {data['similar_users']['count']}")
            print(f"  CF Recommendations: {data['recommendations']['count']}")
            print(f"  Can Show on Home: {data['diagnostics']['can_show_on_home']}")
            
            if data['user_profile']['top_artists']:
                print(f"\n  Top Artists: {', '.join(data['user_profile']['top_artists'][:5])}")
            
            if data['similar_users']['similarity_scores']:
                print(f"  Similarity Scores: {data['similar_users']['similarity_scores'][:3]}")
            
            return data
        else:
            print(f"✗ Error: {response.text}")
            return None
            
    except requests.exceptions.ConnectionError:
        print("\n✗ Error: Could not connect to Flask server!")
        print("  Make sure the Flask app is running: python app.py")
        return None
    except Exception as e:
        print(f"\n✗ Error: {str(e)}")
        return None

def test_cf_recommendations(uid):
    """Test the CF recommendations endpoint"""
    print_section("2. Testing CF Recommendations Endpoint")
    
    url = f"{BASE_URL}/api/recommendations/collaborative?uid={uid}&limit=10&debug=true"
    print(f"\nGET {url}")
    
    try:
        response = requests.get(url, timeout=10)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"\n✓ CF Recommendations Response:")
            print(f"  Title: {data.get('title')}")
            print(f"  Count: {data.get('count')}")
            
            if data.get('stats'):
                print(f"  Has Profile: {data['stats']['has_profile']}")
                print(f"  Similar Users: {data['stats'].get('similar_users_count', 0)}")
            
            if data.get('results'):
                print(f"\n  Sample Recommendations:")
                for i, track in enumerate(data['results'][:3], 1):
                    print(f"    {i}. {track['title']} by {', '.join(track['artists'][:2])}")
            else:
                print("\n  ⚠ No recommendations returned")
            
            return data
        else:
            print(f"✗ Error: {response.text}")
            return None
            
    except Exception as e:
        print(f"\n✗ Error: {str(e)}")
        return None

def test_home_endpoint(uid):
    """Test the home endpoint with CF section"""
    print_section("3. Testing Home Endpoint (with CF)")
    
    url = f"{BASE_URL}/api/home?uid={uid}"
    print(f"\nGET {url}")
    
    try:
        response = requests.get(url, timeout=10)
        print(f"Status Code: {response.status_code}")
        
        if response.status_code == 200:
            data = response.json()
            print(f"\n✓ Home Response:")
            print(f"  Trending Count: {data.get('count')}")
            
            # Check if CF section exists
            if 'collaborative_recommendations' in data:
                cf = data['collaborative_recommendations']
                print(f"\n  ✓ COLLABORATIVE SECTION FOUND!")
                print(f"    Title: {cf['title']}")
                print(f"    Count: {cf['count']}")
                print(f"    Sample Tracks:")
                for i, track in enumerate(cf['tracks'][:3], 1):
                    print(f"      {i}. {track['title']} by {', '.join(track['artists'][:2])}")
            else:
                print(f"\n  ✗ COLLABORATIVE SECTION NOT FOUND")
                
                # Check if debug info exists
                if 'cf_debug' in data:
                    print(f"    Debug Info: {data['cf_debug']}")
            
            return data
        else:
            print(f"✗ Error: {response.text}")
            return None
            
    except Exception as e:
        print(f"\n✗ Error: {str(e)}")
        return None

def print_summary(debug_data, cf_data, home_data):
    """Print summary of test results"""
    print_section("4. Summary & Recommendations")
    
    if not debug_data:
        print("\n✗ CF debug check failed")
        print("  → Start Flask server: python app.py")
        return
    
    firestore_ok = debug_data.get('firestore_available', False)
    profile_ok = debug_data['diagnostics']['meets_minimum_plays']
    similar_users_ok = debug_data['diagnostics']['meets_minimum_similar_users']
    recs_ok = debug_data['diagnostics']['meets_minimum_recommendations']
    can_show = debug_data['diagnostics']['can_show_on_home']
    
    print("\n✓ Diagnostic Results:")
    print(f"  [{'✓' if firestore_ok else '✗'}] Firestore Available")
    print(f"  [{'✓' if profile_ok else '✗'}] User Has Profile (min {5} plays)")
    print(f"  [{'✓' if similar_users_ok else '✗'}] Similar Users Found")
    print(f"  [{'✓' if recs_ok else '✗'}] Sufficient Recommendations (min 4)")
    print(f"  [{'✓' if can_show else '✗'}] Can Show on Home Screen")
    
    if home_data and 'collaborative_recommendations' in home_data:
        print("\n✓✓✓ SUCCESS! CF section is appearing on home screen ✓✓✓")
    else:
        print("\n✗ CF section NOT appearing on home screen")
        
        if not firestore_ok:
            print("\n→ Issue: Firestore not configured")
            print("  Solution: Check Firebase credentials and initialization")
        elif not profile_ok:
            print("\n→ Issue: User has insufficient play history")
            print(f"  Solution: User needs at least 5 plays to build taste profile")
        elif not similar_users_ok:
            print("\n→ Issue: No similar users found")
            print("  Solution: Need more users with overlapping artist preferences")
        elif not recs_ok:
            print("\n→ Issue: Insufficient CF recommendations generated")
            print("  Solution: Increase user base or lower similarity threshold")
    
    print("\n" + "=" * 60)

def main():
    """Main test function"""
    print("=" * 60)
    print("COLLABORATIVE FILTERING - HOME SCREEN TEST")
    print("=" * 60)
    
    # Get user ID from command line or use default
    if len(sys.argv) > 1:
        uid = sys.argv[1]
    else:
        uid = "test_user_123"
        print(f"\nUsing default UID: {uid}")
        print("To test with another user: python test_cf_home.py <user_id>")
    
    # Run tests
    debug_data = test_cf_debug(uid)
    cf_data = test_cf_recommendations(uid)
    home_data = test_home_endpoint(uid)
    
    # Print summary
    print_summary(debug_data, cf_data, home_data)

if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n\nTest interrupted by user")
        sys.exit(0)
