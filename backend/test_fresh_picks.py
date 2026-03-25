#!/usr/bin/env python
"""Test Fresh Picks endpoint with new implementation."""

import sys
sys.path.insert(0, '/Users/Bharat Sri Vastava/Downloads/Telegram Desktop/mjrr/Major-Project/backend')

from app import app

# Test Fresh Picks
c = app.test_client()
r = c.get('/api/fresh-picks?limit=15&uid=test_user_123')
j = r.get_json(silent=True) or {}
songs = j.get('songs') or []

print(f"fresh_status={r.status_code}")
print(f"fresh_count={len(songs)}")
if songs:
    samples = [(s.get('title'), s.get('language')) for s in songs[:3]]
    print(f"fresh_sample={samples}")
    
    # Check response structure
    first_song = songs[0]
    print(f"\nFirst song structure:")
    print(f"  id: {first_song.get('id')}")
    print(f"  title: {first_song.get('title')}")
    print(f"  artist: {first_song.get('artist')}")
    print(f"  language: {first_song.get('language')}")
    print(f"  image: {first_song.get('image')}")
    print(f"  media_url: {first_song.get('media_url')}")
else:
    print("No songs returned")

print(f"\nTotal response keys: {list(j.keys())}")
