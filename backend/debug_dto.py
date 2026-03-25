#!/usr/bin/env python
"""Debug _to_song_dto function."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Suppress logging
import logging
logging.disable(logging.CRITICAL)

try:
    from services.jiosaavn_service import (
        search_playlists_jiosaavn, 
        get_playlist_details,
    )
    from app import _to_song_dto
    
    print("=" * 60)
    print("DEBUG: _to_song_dto FUNCTION")
    print("=" * 60)
    
    # Get a playlist
    playlists = search_playlists_jiosaavn('international hits', limit=1)
    if not playlists:
        print("No playlists found")
    else:
        playlist = playlists[0]
        playlist_id = str(playlist.get('id', '')).strip()
        
        # Get playlist details
        playlist_data = get_playlist_details(playlist_id)
        if not playlist_data:
            print("Could not fetch playlist")
        else:
            songs = playlist_data.get('songs', [])
            if songs and len(songs) > 0:
                print(f"\n1. First raw song from playlist:")
                first_song = songs[0]
                print(f"   Type: {type(first_song)}")
                print(f"   Keys: {list(first_song.keys()) if isinstance(first_song, dict) else 'Not a dict'}")
                if isinstance(first_song, dict):
                    print(f"   Content sample:")
                    for key, value in list(first_song.items())[:5]:
                        print(f"     {key}: {value}")
                
                print(f"\n2. Trying to convert to DTO...")
                try:
                    dto = _to_song_dto(first_song)
                    print(f"   DTO created successfully!")
                    print(f"   DTO keys: {list(dto.keys())}")
                    print(f"   Title: {dto.get('title')}")
                    print(f"   ID: {dto.get('videoId')}")
                    print(f"   URL: {dto.get('url')}")
                except Exception as e:
                    print(f"   ERROR: {e}")
                    import traceback
                    traceback.print_exc()
            else:
                print(f"No songs in playlist")
                
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
