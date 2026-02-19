"""
Test script to demonstrate production-grade search improvements

Tests:
1. Short query rejection (< 2 chars)
2. Valid query with timing
3. Cache hit on second request
4. Different query (cache miss)
"""
import requests
import time

BASE_URL = "http://127.0.0.1:5000"

def test_search(query, test_name):
    print(f"\n{'='*60}")
    print(f"TEST: {test_name}")
    print(f"Query: '{query}'")
    print('='*60)
    
    start = time.time()
    try:
        response = requests.get(f"{BASE_URL}/api/search", params={'query': query, 'limit': 5})
        elapsed = time.time() - start
        
        print(f"Status: {response.status_code}")
        print(f"Time: {elapsed*1000:.0f}ms")
        
        data = response.json()
        print(f"Success: {data.get('success')}")
        print(f"Count: {data.get('count')}")
        print(f"Cached: {data.get('cached', False)}")
        
        if data.get('error'):
            print(f"Error: {data['error']}")
        elif data.get('count', 0) > 0:
            print(f"First result: {data['results'][0]['title']}")
            
        return elapsed
        
    except Exception as e:
        print(f"ERROR: {e}")
        return None

if __name__ == "__main__":
    print("\n" + "="*60)
    print("PRODUCTION-GRADE SEARCH SYSTEM TEST")
    print("="*60)
    
    # Test 1: Too short query
    test_search("a", "Short query (1 char) - should return empty")
    
    # Test 2: Valid query (first time - cache miss)
    time1 = test_search("coldplay", "Valid query - FIRST request (cache MISS)")
    
    # Test 3: Same query (should be cached)
    time.sleep(0.5)
    time2 = test_search("coldplay", "Same query - SECOND request (cache HIT)")
    
    if time1 and time2:
        speedup = time1 / time2
        print(f"\n{'='*60}")
        print(f"CACHE PERFORMANCE")
        print(f"{'='*60}")
        print(f"First request:  {time1*1000:.0f}ms")
        print(f"Cached request: {time2*1000:.0f}ms")
        print(f"Speedup: {speedup:.1f}x faster!")
    
    # Test 4: Different query (cache miss again)
    test_search("beatles", "Different query (cache MISS)")
    
    print(f"\n{'='*60}")
    print("TESTS COMPLETE")
    print(f"{'='*60}\n")
