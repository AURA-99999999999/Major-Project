#!/usr/bin/env python3
"""Test the new album/artist/playlist detail endpoints"""

import requests
import time
import sys

BASE_URL = "http://localhost:5000"

def test_search_limits():
    """Test search returns max 5 items per category"""
    print("\n" + "="*60)
    print("Testing Search Result Limits (max 5 per category)")
    print("="*60)
    
    try:
        response = requests.get(f"{BASE_URL}/api/search?query=coldplay", timeout=10)
        if response.status_code == 200:
            data = response.json()
            print(f"✓ Status: {response.status_code}")
            print(f"✓ Songs: {len(data.get('songs', []))} (max 5)")
            print(f"✓ Albums: {len(data.get('albums', []))} (max 5)")
            print(f"✓ Artists: {len(data.get('artists', []))} (max 5)")
            print(f"✓ Playlists: {len(data.get('playlists', []))} (max 5)")
            
            # Get sample IDs for testing detail endpoints
            album_id = data.get('albums', [{}])[0].get('browseId') if data.get('albums') else None
            artist_id = data.get('artists', [{}])[0].get('browseId') if data.get('artists') else None
            playlist_id = data.get('playlists', [{}])[0].get('browseId') if data.get('playlists') else None
            
            return album_id, artist_id, playlist_id
        else:
            print(f"✗ Failed: {response.status_code}")
            return None, None, None
    except Exception as e:
        print(f"✗ Error: {e}")
        return None, None, None


def test_album_detail(album_id):
    """Test album detail endpoint"""
    print("\n" + "="*60)
    print(f"Testing Album Detail Endpoint")
    print("="*60)
    
    if not album_id:
        print("✗ No album ID available")
        return
    
    try:
        print(f"Album ID: {album_id}")
        response = requests.get(f"{BASE_URL}/api/album/{album_id}", timeout=10)
        if response.status_code == 200:
            data = response.json()
            album = data.get('album', {})
            print(f"✓ Status: {response.status_code}")
            print(f"✓ Title: {album.get('title', 'N/A')}")
            print(f"✓ Artists: {album.get('artists', 'N/A')}")
            print(f"✓ Year: {album.get('year', 'N/A')}")
            print(f"✓ Track Count: {album.get('trackCount', 0)}")
            print(f"✓ Songs: {len(album.get('songs', []))} tracks")
            
            if album.get('songs'):
                print(f"\nFirst 2 tracks:")
                for i, song in enumerate(album.get('songs', [])[:2], 1):
                    print(f"  {i}. {song.get('title', 'N/A')}")
                    print(f"     Duration: {song.get('duration', 'N/A')}")
        else:
            print(f"✗ Failed: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"✗ Error: {e}")


def test_artist_detail(artist_id):
    """Test artist detail endpoint"""
    print("\n" + "="*60)
    print(f"Testing Artist Detail Endpoint")
    print("="*60)
    
    if not artist_id:
        print("✗ No artist ID available")
        return
    
    try:
        print(f"Artist ID: {artist_id}")
        response = requests.get(f"{BASE_URL}/api/artist/{artist_id}", timeout=10)
        if response.status_code == 200:
            data = response.json()
            artist = data.get('artist', {})
            print(f"✓ Status: {response.status_code}")
            print(f"✓ Name: {artist.get('name', 'N/A')}")
            print(f"✓ Subscribers: {artist.get('subscribers', 'N/A')}")
            print(f"✓ Top Songs: {len(artist.get('topSongs', []))} tracks")
            print(f"✓ Albums: {len(artist.get('albums', []))} albums")
            
            if artist.get('topSongs'):
                print(f"\nTop 2 songs:")
                for i, song in enumerate(artist.get('topSongs', [])[:2], 1):
                    print(f"  {i}. {song.get('title', 'N/A')}")
            
            if artist.get('albums'):
                print(f"\nFirst 2 albums:")
                for i, album in enumerate(artist.get('albums', [])[:2], 1):
                    print(f"  {i}. {album.get('title', 'N/A')} ({album.get('year', 'N/A')})")
        else:
            print(f"✗ Failed: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"✗ Error: {e}")


def test_playlist_detail(playlist_id):
    """Test playlist detail endpoint"""
    print("\n" + "="*60)
    print(f"Testing Playlist Detail Endpoint")
    print("="*60)
    
    if not playlist_id:
        print("✗ No playlist ID available")
        return
    
    try:
        print(f"Playlist ID: {playlist_id}")
        response = requests.get(f"{BASE_URL}/api/playlist/{playlist_id}", timeout=10)
        if response.status_code == 200:
            data = response.json()
            playlist = data.get('playlist', {})
            print(f"✓ Status: {response.status_code}")
            print(f"✓ Title: {playlist.get('title', 'N/A')}")
            print(f"✓ Author: {playlist.get('author', 'N/A')}")
            print(f"✓ Track Count: {playlist.get('trackCount', 0)}")
            print(f"✓ Songs: {len(playlist.get('songs', []))} tracks")
            
            if playlist.get('songs'):
                print(f"\nFirst 2 tracks:")
                for i, song in enumerate(playlist.get('songs', [])[:2], 1):
                    print(f"  {i}. {song.get('title', 'N/A')}")
                    print(f"     Artists: {song.get('artists', 'N/A')}")
        else:
            print(f"✗ Failed: {response.status_code} - {response.text}")
    except Exception as e:
        print(f"✗ Error: {e}")


if __name__ == "__main__":
    print("\n" + "="*60)
    print("Search Enhancement - Detail Endpoints Test")
    print("="*60)
    print("Make sure Flask app is running on http://localhost:5000")
    print("="*60)
    
    # Test search limits and get sample IDs
    album_id, artist_id, playlist_id = test_search_limits()
    
    time.sleep(1)
    
    # Test detail endpoints
    if album_id:
        test_album_detail(album_id)
    
    time.sleep(1)
    
    if artist_id:
        test_artist_detail(artist_id)
    
    time.sleep(1)
    
    if playlist_id:
        test_playlist_detail(playlist_id)
    
    print("\n" + "="*60)
    print("Testing complete!")
    print("="*60)
