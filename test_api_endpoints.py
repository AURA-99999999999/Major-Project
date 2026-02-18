#!/usr/bin/env python3
"""
Test the /api/trending and /api/home endpoints
"""
import requests
import json
import sys

print("=" * 60)
print("Testing Flask API Endpoints")
print("=" * 60)

# Start Flask app in background
import subprocess
import time

print("\nStarting Flask app...")
proc = subprocess.Popen([sys.executable, 'app.py'], 
                        stdout=subprocess.PIPE, 
                        stderr=subprocess.PIPE)

# Wait for Flask to start
time.sleep(3)

try:
    # Test /api/trending endpoint
    print("\n\n>>> Testing /api/trending endpoint")
    print("-" * 60)
    response = requests.get('http://localhost:5000/api/trending', timeout=15)
    
    if response.status_code == 200:
        data = response.json()
        print(f"✓ Status: {response.status_code}")
        print(f"✓ Count: {data.get('count')} trending songs")
        if data.get('results'):
            print(f"\nFirst 3 results:")
            for i, song in enumerate(data['results'][:3], 1):
                print(f"\n  {i}. {song.get('title')}")
                print(f"     VideoId: {song.get('videoId')}")
                print(f"     Artists: {', '.join(song.get('artists', []))}")
    else:
        print(f"✗ Status: {response.status_code}")
        print(f"Response: {response.text}")
    
    # Test /api/home endpoint
    print("\n\n>>> Testing /api/home endpoint")
    print("-" * 60)
    response = requests.get('http://localhost:5000/api/home?limit=5', timeout=15)
    
    if response.status_code == 200:
        data = response.json()
        print(f"✓ Status: {response.status_code}")
        print(f"✓ Count: {data.get('count')} items in home")
        if data.get('trending'):
            print(f"\nFirst 2 trending items:")
            for i, song in enumerate(data['trending'][:2], 1):
                print(f"\n  {i}. {song.get('title')}")
                print(f"     VideoId: {song.get('videoId')}")
    else:
        print(f"✗ Status: {response.status_code}")
        print(f"Response: {response.text}")
        
finally:
    print("\n\nStopping Flask app...")
    proc.terminate()
    proc.wait()
