#!/usr/bin/env python
"""Debug what's in get_charts videos"""
import sys
sys.path.insert(0, '.')

from ytmusicapi import YTMusic
import json

print("=" * 60)
print("Testing YTMusic.get_charts() videos structure")
print("=" * 60)

try:
    ytmusic = YTMusic()
    charts = ytmusic.get_charts(country='US')
    
    videos = charts.get('videos', [])
    print(f"\nNumber of videos: {len(videos)}")
    
    if videos:
        print(f"\nFirst video structure (first 1000 chars):")
        first_video_str = json.dumps(videos[0], indent=2, default=str)
        print(first_video_str[:1000])
        if len(first_video_str) > 1000:
            print("...")
            
        print(f"\nVideo keys: {videos[0].keys()}")
        print(f"Video title: {videos[0].get('title')}")
        print(f"Video videoId: {videos[0].get('videoId')}")
        print(f"Video artists: {videos[0].get('artists')}")
    else:
        print("\n[ERROR] videos list is empty!")
        
except Exception as e:
    print(f"\n[ERROR] {str(e)}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
