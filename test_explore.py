#!/usr/bin/env python3
"""
Test to inspect get_explore() which is what app.py uses
"""
from ytmusicapi import YTMusic
import json

print("=" * 60)
print("Testing YTMusic.get_explore() structure")
print("=" * 60)

try:
    ytmusic = YTMusic()
    explore = ytmusic.get_explore()
    
    print(f"\nTop-level keys in explore: {list(explore.keys())}")
    
    # Check 'trending' key
    if 'trending' in explore:
        trending_section = explore['trending']
        print(f"\n'trending' key exists!")
        print(f"Trending section keys: {list(trending_section.keys())}")
        
        if 'items' in trending_section:
            items = trending_section['items']
            print(f"Number of trending items: {len(items)}")
            
            if items:
                first_item = items[0]
                print(f"\nFirst trending item:")
                print(f"  Keys: {list(first_item.keys())}")
                print(f"  Title: {first_item.get('title')}")
                print(f"  VideoId: {first_item.get('videoId')}")
                print(f"  Artists: {first_item.get('artists')}")
                
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
