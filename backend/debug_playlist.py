#!/usr/bin/env python
"""Debug playlist fetch."""
import sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Suppress logging except for our test
import logging
logging.disable(logging.CRITICAL)

try:
    from services.jiosaavn_service import (
        search_playlists_jiosaavn, 
        get_playlist_details,
    )
    from app import _to_song_dto, _fetch_playlist_songs_by_id
    
    print("=" * 60)
    print("DEBUG: PLAYLIST FETCH")
    print("=" * 60)
    
    # Search for playlists
    print("\n1. Searching for playlists...")
    playlists = search_playlists_jiosaavn('international hits', limit=3)
    print(f"   Found {len(playlists)} playlists")
    
    if not playlists:
        print("   No playlists found - stopping debug.")
    else:
        playlist = playlists[0]
        print(f"   Top playlist: {playlist.get('title')} (ID: {playlist.get('id')})")
        
        # Try to fetch playlist details
        playlist_id = str(playlist.get('id', '')).strip()
        print(f"\n2. Fetching playlist details for ID: {playlist_id}")
        
        playlist_data = get_playlist_details(playlist_id)
        if playlist_data:
            print(f"   Playlist fetched successfully")
            print(f"   Keys in response: {list(playlist_data.keys())}")
            
            songs = playlist_data.get('songs', [])
            print(f"   Number of songs: {len(songs) if isinstance(songs, list) else 'Not a list'}")
            
            if songs and len(songs) > 0:
                print(f"\n3. First 3 songs in playlist:")
                for i, song in enumerate(songs[:3], 1):
                    song_id = song.get('id') or 'unknown'
                    title = song.get('song') or song.get('title') or 'Unknown'
                    language = song.get('language') or 'unknown'
                    print(f"   {i}. {title} (ID: {song_id}, Language: {language})")
            else:
                print("   No songs found in playlist")
        else:
            print(f"   ERROR: Could not fetch playlist details")
            
        # Now test the _fetch_playlist_songs_by_id function (from app.py)
        print(f"\n4. Testing _fetch_playlist_songs_by_id function...")
        try:
            song_dtos = _fetch_playlist_songs_by_id(playlist_id, limit=15)
            print(f"   DTOs returned: {len(song_dtos)}")
            if song_dtos:
                print(f"   First DTO: {song_dtos[0].get('title')}")
        except Exception as e:
            print(f"   ERROR: {e}")
            
except Exception as e:
    print(f"ERROR: {e}")
    import traceback
    traceback.print_exc()
