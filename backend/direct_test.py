#!/usr/bin/env python
"""Test Fresh Picks with explicit language."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Suppress logging
import logging
logging.disable(logging.CRITICAL)

try:
    from app import _compute_fresh_picks, _normalize_user_languages
    
    print("=" * 60)
    print("DIRECT FRESH PICKS COMPUTATION TEST")
    print("=" * 60)
    
    # Test with explicit English language
    languages = ["english"]
    print(f"\nTesting with languages: {languages}")
    
    songs = _compute_fresh_picks(languages, limit=15)
    print(f"Songs Returned: {len(songs)}")
    
    if songs and len(songs) > 0:
        print(f"\nFirst 3 Songs:")
        for i, song in enumerate(songs[:3], 1):
            print(f"  {i}. {song.get('title', 'Unknown')} - {song.get('language', 'unknown')}")
            print(f"     ID: {song.get('id')}")
            print(f"     Artist: {song.get('artist', 'Unknown')}")
    else:
        print("No songs returned - checking why...")
        # Test search function directly
        from services.jiosaavn_service import search_playlists_jiosaavn
        print("\nTesting playlist search for 'international hits'...")
        playlists = search_playlists_jiosaavn('international hits', limit=3)
        print(f"Playlists found: {len(playlists)}")
        for p in playlists[:1]:
            print(f"  {p.get('title')} (ID: {p.get('id')})")
            
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
