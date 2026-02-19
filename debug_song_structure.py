"""
Debug YTMusic API Song Response Structure
Check what fields are available in song objects, especially album.id/browseId
"""

from ytmusicapi import YTMusic
import json

def debug_song_search():
    """Check song structure from search results"""
    print("=" * 80)
    print("DEBUGGING YTMUSIC API SONG RESPONSE STRUCTURE")
    print("=" * 80)
    
    ytmusic = YTMusic()
    
    # Test 1: Search for songs
    print("\n1. Testing song search results...")
    print("-" * 80)
    results = ytmusic.search("Shape of You Ed Sheeran", filter="songs", limit=3)
    
    if results:
        print(f"\nFound {len(results)} songs. Inspecting first song:")
        song = results[0]
        
        print("\n📋 Full song object structure:")
        print(json.dumps(song, indent=2))
        
        print("\n🔍 Album field inspection:")
        if 'album' in song:
            album = song['album']
            print(f"  Type: {type(album)}")
            print(f"  Value: {album}")
            
            if isinstance(album, dict):
                print(f"\n  Available keys in album object:")
                for key in album.keys():
                    print(f"    - {key}: {album[key]}")
        else:
            print("  ❌ No 'album' field found")
    
    # Test 2: Get artist songs
    print("\n\n2. Testing get_artist songs...")
    print("-" * 80)
    try:
        artist_id = "UCIaFw5VBEK8qaW6nRpx_qnw"  # Example artist
        artist_data = ytmusic.get_artist(artist_id)
        
        if 'songs' in artist_data and artist_data['songs']:
            songs = artist_data['songs'].get('results', [])
            if songs:
                print(f"\nFound {len(songs)} artist songs. Inspecting first song:")
                song = songs[0]
                
                print("\n📋 Full artist song object structure:")
                print(json.dumps(song, indent=2))
                
                print("\n🔍 Album field inspection:")
                if 'album' in song:
                    album = song['album']
                    print(f"  Type: {type(album)}")
                    print(f"  Value: {album}")
                    
                    if isinstance(album, dict):
                        print(f"\n  Available keys in album object:")
                        for key in album.keys():
                            print(f"    - {key}: {album[key]}")
                else:
                    print("  ❌ No 'album' field found")
    except Exception as e:
        print(f"  Error getting artist songs: {e}")
    
    # Test 3: Get album tracks
    print("\n\n3. Testing get_album tracks...")
    print("-" * 80)
    try:
        # Search for an album first
        album_results = ytmusic.search("Divide Ed Sheeran", filter="albums", limit=1)
        if album_results:
            album_browse_id = album_results[0].get('browseId')
            print(f"  Album browseId: {album_browse_id}")
            
            album_data = ytmusic.get_album(album_browse_id)
            tracks = album_data.get('tracks', [])
            
            if tracks:
                print(f"\nFound {len(tracks)} tracks in album. Inspecting first track:")
                track = tracks[0]
                
                print("\n📋 Full album track object structure:")
                print(json.dumps(track, indent=2))
                
                print("\n🔍 Album field inspection:")
                if 'album' in track:
                    album = track['album']
                    print(f"  Type: {type(album)}")
                    print(f"  Value: {album}")
                    
                    if isinstance(album, dict):
                        print(f"\n  Available keys in album object:")
                        for key in album.keys():
                            print(f"    - {key}: {album[key]}")
                else:
                    print("  ❌ No 'album' field found in track")
                    print(f"  ✓ But we already know the album ID: {album_browse_id}")
    except Exception as e:
        print(f"  Error getting album tracks: {e}")
    
    # Test 4: Get song details
    print("\n\n4. Testing get_song details...")
    print("-" * 80)
    try:
        results = ytmusic.search("Shape of You", filter="songs", limit=1)
        if results:
            video_id = results[0].get('videoId')
            print(f"  VideoId: {video_id}")
            
            song_details = ytmusic.get_song(video_id)
            video_details = song_details.get('videoDetails', {})
            
            print("\n📋 Video details structure:")
            print(json.dumps(video_details, indent=2))
            
            print("\n🔍 Looking for album information in videoDetails...")
            album_keys = [k for k in video_details.keys() if 'album' in k.lower()]
            if album_keys:
                print(f"  Found album-related keys: {album_keys}")
                for key in album_keys:
                    print(f"    {key}: {video_details[key]}")
            else:
                print("  ❌ No album information in videoDetails")
    except Exception as e:
        print(f"  Error getting song details: {e}")
    
    print("\n" + "=" * 80)
    print("SUMMARY")
    print("=" * 80)
    print("\n💡 Key findings will be displayed above.")
    print("   Look for 'browseId' or 'id' fields within the album object.")

if __name__ == "__main__":
    debug_song_search()
