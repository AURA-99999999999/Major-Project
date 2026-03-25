#!/usr/bin/env python
"""Quick test of Fresh Picks API."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Suppress logging
import logging
logging.disable(logging.CRITICAL)

try:
    from app import app
    client = app.test_client()
    
    # Test Fresh Picks with empty language (default)
    response = client.get('/api/fresh-picks?limit=15&uid=test')
    
    print("=" * 60)
    print("FRESH PICKS TEST RESULTS")
    print("=" * 60)
    print(f"HTTP Status: {response.status_code}")
    
    if response.status_code == 200:
        data = response.get_json()
        songs = data.get('songs', [])
        print(f"Songs Returned: {len(songs)}")
        
        if songs and len(songs) > 0:
            print(f"\nFirst 3 Songs:")
            for i, song in enumerate(songs[:3], 1):
                print(f"  {i}. {song.get('title', 'Unknown')} - {song.get('language', 'unknown')}")
                print(f"     Artist: {song.get('artist', 'Unknown')}")
                print(f"     Has Image: {'image' in song and bool(song['image'])}")
                print(f"     Has URL: {'media_url' in song and bool(song['media_url'])}")
        else:
            print("No songs returned")
    else:
        print(f"Error: {response.get_json()}")
        
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
