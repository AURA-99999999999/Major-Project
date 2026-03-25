#!/usr/bin/env python
"""Debug _fetch_playlist_songs_by_id with logging."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Enable DEBUG logging
import logging
logging.basicConfig(level=logging.DEBUG, format='%(levelname)s: %(message)s')

try:
    from services.jiosaavn_service import search_playlists_jiosaavn
    from app import _fetch_playlist_songs_by_id
    
    print("=" * 60)
    print("DEBUG: _fetch_playlist_songs_by_id WITH LOGGING")
    print("=" * 60)
    
    # Get a playlist
    playlists = search_playlists_jiosaavn('international hits', limit=1)
    if not playlists:
        print("No playlists found")
    else:
        playlist = playlists[0]
        playlist_id = str(playlist.get('id', '')).strip()
        print(f"\nFetching songs for playlist ID: {playlist_id}")
        
        # Call the function we're debugging
        song_dtos = _fetch_playlist_songs_by_id(playlist_id, limit=15)
        print(f"\nResult: {len(song_dtos)} DTOs returned")
        if song_dtos:
            print(f"First DTO title: {song_dtos[0].get('title')}")
                
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
