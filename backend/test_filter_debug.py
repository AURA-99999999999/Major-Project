#!/usr/bin/env python3
"""
Debug: Test get_explore and see raw items before filtering
"""
from ytmusicapi import YTMusic
from services.music_filter import filter_music_tracks

print("=" * 60)
print("Testing get_explore and filtering")
print("=" * 60)

try:
    ytmusic = YTMusic()
    explore = ytmusic.get_explore()
    raw_items = explore.get('trending', {}).get('items', [])
    
    print(f"\nTotal raw items from get_explore: {len(raw_items)}")
    
    if raw_items:
        print(f"\nFirst 3 raw items:")
        for i, item in enumerate(raw_items[:3]):
            print(f"\n  Item {i+1}:")
            print(f"    Title: {item.get('title')}")
            print(f"    VideoId: {item.get('videoId')}")
            print(f"    Keys: {list(item.keys())}")
        
        # Now try filtering
        print(f"\n\nApplying filter_music_tracks...")
        filtered = filter_music_tracks(raw_items, ytmusic=None, include_validation=False)
        print(f"Filtered items: {len(filtered)}")
        
        if filtered:
            print(f"\nFirst filtered item:")
            print(f"  Title: {filtered[0].get('title')}")
            print(f"  VideoId: {filtered[0].get('videoId')}")
        
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
