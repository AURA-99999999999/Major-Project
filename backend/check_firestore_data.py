"""
Script to check Firestore data for user play history and liked songs
"""
import sys
import logging
from services.user_service import get_firestore_client
from services.user_profile_service import UserProfileService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def list_all_users():
    """List all users in Firestore."""
    db = get_firestore_client()
    if not db:
        logger.error("Failed to connect to Firestore")
        return []
    
    users_ref = db.collection('users')
    users = []
    
    try:
        for user_doc in users_ref.stream():
            uid = user_doc.id
            user_data = user_doc.to_dict()
            users.append({
                'uid': uid,
                'data': user_data
            })
        return users
    except Exception as e:
        logger.error(f"Error listing users: {e}")
        return []

def check_user_data(uid):
    """Check play history and liked songs for a specific user."""
    db = get_firestore_client()
    if not db:
        logger.error("Failed to connect to Firestore")
        return
    
    print(f"\n{'='*60}")
    print(f"Checking data for user: {uid}")
    print(f"{'='*60}")
    
    # Check plays
    try:
        plays_ref = db.collection('users').document(uid).collection('plays')
        plays = list(plays_ref.stream())
        print(f"\n✓ Plays collection: {len(plays)} documents")
        
        if plays:
            print("\nSample plays (first 3):")
            for i, play_doc in enumerate(plays[:3]):
                play_data = play_doc.to_dict()
                print(f"\n  Play {i+1} (ID: {play_doc.id}):")
                print(f"    - Song: {play_data.get('title', 'N/A')}")
                print(f"    - Artist: {play_data.get('artist', 'N/A') or play_data.get('artists', 'N/A')}")
                print(f"    - Album: {play_data.get('album', 'N/A')}")
                print(f"    - Played at: {play_data.get('playedAt', play_data.get('timestamp', 'N/A'))}")
                print(f"    - All keys: {list(play_data.keys())}")
        else:
            print("  ⚠ No plays found!")
            
    except Exception as e:
        print(f"  ✗ Error checking plays: {e}")
    
    # Check liked songs
    try:
        liked_ref = db.collection('users').document(uid).collection('likedSongs')
        liked = list(liked_ref.stream())
        print(f"\n✓ Liked songs collection: {len(liked)} documents")
        
        if liked:
            print("\nSample liked songs (first 3):")
            for i, liked_doc in enumerate(liked[:3]):
                liked_data = liked_doc.to_dict()
                print(f"\n  Liked Song {i+1} (ID: {liked_doc.id}):")
                print(f"    - Song: {liked_data.get('title', 'N/A')}")
                print(f"    - Artist: {liked_data.get('artist', 'N/A') or liked_data.get('artists', 'N/A')}")
                print(f"    - Album: {liked_data.get('album', 'N/A')}")
                print(f"    - Liked at: {liked_data.get('likedAt', liked_data.get('timestamp', 'N/A'))}")
                print(f"    - All keys: {list(liked_data.keys())}")
        else:
            print("  ⚠ No liked songs found!")
            
    except Exception as e:
        print(f"  ✗ Error checking liked songs: {e}")
    
    # Test UserProfileService
    print(f"\n{'='*60}")
    print("Testing UserProfileService.get_top_artists()")
    print(f"{'='*60}")
    
    try:
        profile_service = UserProfileService()
        top_artists = profile_service.get_top_artists(uid, limit=10)
        
        print(f"\n✓ Top Artists: {len(top_artists)} found")
        
        if top_artists:
            print("\nTop Artists List:")
            for i, artist in enumerate(top_artists, 1):
                print(f"  {i}. {artist.get('artist', 'N/A')} - Score: {artist.get('score', 0):.2f}, Plays: {artist.get('play_count', 0)}")
        else:
            print("  ⚠ No top artists calculated!")
            
    except Exception as e:
        print(f"  ✗ Error getting top artists: {e}")
        import traceback
        traceback.print_exc()

def main():
    print("\n" + "="*60)
    print("Firestore Data Verification Tool")
    print("="*60)
    
    # List all users
    print("\nFetching all users from Firestore...")
    users = list_all_users()
    
    if not users:
        print("\n⚠ No users found in Firestore!")
        print("\nPossible reasons:")
        print("  1. Firebase credentials not configured")
        print("  2. No users have signed up yet")
        print("  3. Connection issue with Firestore")
        return
    
    print(f"\n✓ Found {len(users)} user(s) in Firestore:\n")
    for i, user in enumerate(users, 1):
        uid = user['uid']
        user_data = user['data']
        email = user_data.get('email', 'N/A')
        display_name = user_data.get('displayName', 'N/A')
        print(f"  {i}. UID: {uid}")
        print(f"     Email: {email}")
        print(f"     Name: {display_name}")
        print()
    
    # Check data for each user
    for user in users:
        uid = user['uid']
        check_user_data(uid)
    
    print("\n" + "="*60)
    print("Verification Complete")
    print("="*60 + "\n")

if __name__ == "__main__":
    main()
