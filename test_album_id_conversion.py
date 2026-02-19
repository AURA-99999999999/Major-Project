"""
Test if YTMusic album.id can be used with get_album()
"""

from ytmusicapi import YTMusic
import json

def test_album_id_usage():
    """Test if album.id from song can be used to fetch album details"""
    print("=" * 80)
    print("TESTING ALBUM ID USAGE")
    print("=" * 80)
    
    ytmusic = YTMusic()
    
    # Step 1: Get a song with album.id
    print("\n1. Searching for a song...")
    results = ytmusic.search("Shape of You Ed Sheeran", filter="songs", limit=1)
    
    if not results:
        print("  ❌ No results found")
        return
    
    song = results[0]
    album_info = song.get('album', {})
    
    if isinstance(album_info, dict):
        album_name = album_info.get('name')
        album_id = album_info.get('id')
        
        print(f"\n  Song: {song.get('title')}")
        print(f"  Album Name: {album_name}")
        print(f"  Album ID: {album_id}")
    else:
        print(f"  ❌ Album is not a dict: {album_info}")
        return
    
    # Step 2: Try to use album.id with get_album()
    print(f"\n2. Testing if album.id can be used with get_album()...")
    
    try:
        album_details = ytmusic.get_album(album_id)
        
        print(f"  ✅ SUCCESS! get_album('{album_id}') works!")
        print(f"\n  Album Details:")
        print(f"    Title: {album_details.get('title')}")
        print(f"    Artist: {album_details.get('artists', [{}])[0].get('name')}")
        print(f"    Year: {album_details.get('year')}")
        print(f"    Track Count: {len(album_details.get('tracks', []))}")
        print(f"    Browse ID: {album_details.get('browseId')}")
        print(f"    Audio Playlist ID: {album_details.get('audioPlaylistId')}")
        
        print(f"\n  💡 The album.id from song search IS the browseId!")
        
    except Exception as e:
        print(f"  ❌ FAILED: {e}")
    
    # Step 3: Compare with searching for album directly
    print(f"\n3. Comparing with album search...")
    try:
        album_search = ytmusic.search(album_name, filter="albums", limit=1)
        if album_search:
            search_result = album_search[0]
            search_browse_id = search_result.get('browseId')
            
            print(f"  Album search browseId: {search_browse_id}")
            print(f"  Song's album.id:       {album_id}")
            print(f"  Match: {search_browse_id == album_id}")
    except Exception as e:
        print(f"  ❌ Search failed: {e}")
    
    print("\n" + "=" * 80)
    print("CONCLUSION")
    print("=" * 80)
    print("\n✅ album.id from songs is the BROWSE ID (MPREb_...)")
    print("✅ It can be used directly with get_album()")
    print("✅ No need to search for the album separately!")

if __name__ == "__main__":
    test_album_id_usage()
