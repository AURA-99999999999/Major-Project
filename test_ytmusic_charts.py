#!/usr/bin/env python
"""Debug YTMusic get_charts output"""
import sys
sys.path.insert(0, '.')

from ytmusicapi import YTMusic
import json

print("=" * 60)
print("Testing YTMusic.get_charts()")
print("=" * 60)

try:
    ytmusic = YTMusic()
    print("\nCalling get_charts(country='US')...")
    
    charts = ytmusic.get_charts(country='US')
    
    print(f"\nRaw response type: {type(charts)}")
    print(f"Response keys: {charts.keys() if isinstance(charts, dict) else 'N/A'}")
    
    songs = charts.get('songs', [])
    print(f"\nNumber of songs in response: {len(songs)}")
    
    if songs:
        print(f"\nFirst song structure:")
        print(json.dumps(songs[0], indent=2, default=str)[:500] + "...")
    else:
        print("\n[WARNING] No songs in response!")
        print(f"Full response keys: {list(charts.keys())}")
        print(f"\nFull response structure:")
        print(json.dumps({k: type(v).__name__ for k, v in charts.items()}, indent=2))
        
except Exception as e:
    print(f"\n[ERROR] {str(e)}")
    import traceback
    traceback.print_exc()

print("\n" + "=" * 60)
