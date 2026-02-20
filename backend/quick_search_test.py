import requests

# Test with fresh query (uncached)
response = requests.get('http://localhost:5000/api/search', params={'query': 'the beatles'})
data = response.json()

print(f"Status: {response.status_code}")
print(f"Songs: {len(data.get('songs', []))}/5")
print(f"Albums: {len(data.get('albums', []))}/5")
print(f"Artists: {len(data.get('artists', []))}/5")
print(f"Playlists: {len(data.get('playlists', []))}/5")
print(f"Cached: {data.get('cached', False)}")
