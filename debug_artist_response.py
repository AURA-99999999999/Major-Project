"""
Debug Artist Endpoint Response
Print full JSON response to diagnose empty data issue
"""

import requests
import json

BASE_URL = "http://127.0.0.1:5000"

def debug_artist():
    artist_id = "UCIaFw5VBEK8qaW6nRpx_qnw"
    print(f"Testing artist: {artist_id}")
    
    try:
        response = requests.get(
            f"{BASE_URL}/api/artist/{artist_id}",
            timeout=60
        )
        
        print(f"\nStatus Code: {response.status_code}")
        print(f"\nFull JSON Response:")
        print(json.dumps(response.json(), indent=2))
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    debug_artist()
