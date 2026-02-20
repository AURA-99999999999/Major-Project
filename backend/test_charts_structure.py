#!/usr/bin/env python3
"""
Test to inspect the actual structure of get_charts()
"""
from ytmusicapi import YTMusic
import json

print("=" * 60)
print("Testing YTMusic.get_charts() full structure")
print("=" * 60)

try:
    ytmusic = YTMusic()
    charts = ytmusic.get_charts(country='US')
    
    print(f"\nTop-level keys in charts: {list(charts.keys())}")
    
    # Check 'charts' key
    if 'charts' in charts:
        print(f"\n'charts' key exists!")
        print(f"Number of charts: {len(charts['charts'])}")
        
        if charts['charts']:
            first_chart = charts['charts'][0]
            print(f"\nFirst chart keys: {list(first_chart.keys())}")
            print(f"First chart title: {first_chart.get('title')}")
            print(f"First chart items count: {len(first_chart.get('items', []))}")
            
            # Check first item in first chart
            if first_chart.get('items'):
                first_item = first_chart['items'][0]
                print(f"\nFirst item in first chart:")
                print(f"  Keys: {list(first_item.keys())}")
                print(f"  Title: {first_item.get('title')}")
                print(f"  VideoId: {first_item.get('videoId')}")
                print(f"  Artists: {first_item.get('artists')}")
                print(f"  Album: {first_item.get('album')}")
    
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
