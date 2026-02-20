#!/usr/bin/env python3
"""Test the corrected artist endpoint with proper YTMusic API usage"""

import requests

BASE_URL = "http://localhost:5000"

# Test with Coldplay artist (from previous search)
artist_id = "UCIaFw5VBEK8qaW6nRpx_qnw"

print("="*60)
print("Testing Artist Detail Endpoint (Corrected API Usage)")
print("="*60)

try:
    print(f"\nFetching artist: {artist_id}")
    response = requests.get(f"{BASE_URL}/api/artist/{artist_id}", timeout=30)
    
    if response.status_code == 200:
        data = response.json()
        artist = data.get('artist', {})
        
        print(f"\n✓ Status: 200 OK")
        print(f"✓ Name: {artist.get('name')}")
        print(f"✓ Subscribers: {artist.get('subscribers')}")
        print(f"✓ Description: {artist.get('description', '')[:100]}...")
        print(f"\n✓ Top Songs: {len(artist.get('topSongs', []))} tracks")
        
        # Show first 3 top songs
        if artist.get('topSongs'):
            print("\nTop Songs:")
            for i, song in enumerate(artist.get('topSongs', [])[:3], 1):
                print(f"  {i}. {song.get('title')} - {song.get('artists')}")
        
        print(f"\n✓ Albums: {len(artist.get('albums', []))} albums")
        
        # Show first 3 albums
        if artist.get('albums'):
            print("\nAlbums:")
            for i, album in enumerate(artist.get('albums', [])[:3], 1):
                print(f"  {i}. {album.get('title')} ({album.get('year', 'N/A')})")
                
    else:
        print(f"\n✗ Failed: {response.status_code}")
        print(f"Response: {response.text}")
        
except Exception as e:
    print(f"\n✗ Error: {e}")

print("\n" + "="*60)
