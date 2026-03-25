#!/usr/bin/env python
"""Simple test of Fresh Picks DTOs."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Suppress most logging
import logging
logging.disable(logging.CRITICAL)

try:
    from services import jiosaavn_service as jio_api
    from app import _fetch_playlist_songs_by_id
    
    # Test the function
    playlist_id = "1265183353"  # Known good playlist
    print(f"Testing _fetch_playlist_songs_by_id with playlist ID: {playlist_id}")
    
    song_dtos = _fetch_playlist_songs_by_id(playlist_id, limit=15)
    print(f"Result: {len(song_dtos)} DTOs returned")
    
    if song_dtos and len(song_dtos) > 0:
        print(f"Success! First DTO title: {song_dtos[0].get('title')}")
        print(f"Languages in results: {set(s.get('language') for s in song_dtos[:5])}")
    else:
        print("ERROR: No DTOs returned")
                
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
