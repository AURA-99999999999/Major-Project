"""Quick test of top artists extraction after fix"""
import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from services.user_profile_service import UserProfileService

# Test with a specific user UID that has play history
test_uid = "OXWbIWmuxQVpc01NCp3VV1VI0KV2"  # The user with 123 plays

print(f"Testing top artists extraction for UID: {test_uid}\n")

profile_service = UserProfileService()
top_artists = profile_service.get_top_artists(test_uid, limit=10)

print(f"Found {len(top_artists)} top artists:\n")

if top_artists:
    for i, artist in enumerate(top_artists, 1):
        print(f"{i}. {artist.get('artist')} - Score: {artist.get('score'):.2f}, Plays: {artist.get('play_count')}")
else:
    print("No artists found!")

# Also get full profile to see breakdown
profile = profile_service.get_user_profile(test_uid)
print(f"\nProfile Summary:")
print(f"  - Total plays: {profile['total_plays']}")
print(f"  - Total likes: {profile['total_likes']}")
print(f"  - Top artists count: {len(profile['top_artists'])}")
print(f"  - Top albums count: {len(profile['top_albums'])}")
