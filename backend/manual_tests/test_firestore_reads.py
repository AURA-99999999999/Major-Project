"""
Diagnostic script to test Firestore reads for recommendation service
Run this to diagnose why plays and liked songs are showing as 0
"""
import os
import sys
import logging


BACKEND_ROOT = os.path.dirname(os.path.dirname(__file__))
sys.path.insert(0, BACKEND_ROOT)

from services.user_service import get_firestore_client, initialize_firebase_admin

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

def test_firestore_connection():
    """Test if Firestore is accessible"""
    logger.info("=" * 60)
    logger.info("FIRESTORE CONNECTION TEST")
    logger.info("=" * 60)
    
    # Try to initialize Firebase with improved method that checks both env var and file
    if not initialize_firebase_admin():
        logger.error("❌ Firebase initialization FAILED")
        logger.info("   Check that service account file exists or FIREBASE_CREDENTIALS is set")
        return None
    
    # Try to get Firestore client
    db = get_firestore_client()
    if not db:
        logger.error("❌ Firestore client is None")
        return None
    
    logger.info("✅ Firestore client obtained successfully")
    
    return db

def test_user_data(db, uid="TEST_USER_ID"):
    """Test reading user data from Firestore"""
    if not db:
        logger.error("Cannot test - Firestore not connected")
        return
    
    logger.info("=" * 60)
    logger.info(f"TESTING USER DATA FOR UID: {uid}")
    logger.info("=" * 60)
    
    try:
        # Test reading plays collection
        logger.info("\n📀 Testing plays collection...")
        plays_ref = db.collection("users").document(uid).collection("plays")
        plays_docs = list(plays_ref.stream())
        logger.info(f"   Documents found: {len(plays_docs)}")
        
        if plays_docs:
            logger.info("   Sample play document:")
            sample = plays_docs[0].to_dict()
            for key, value in sample.items():
                logger.info(f"      {key}: {value}")
        else:
            logger.warning("   ⚠️  No play documents found")
            logger.info("   Make sure the frontend is writing plays to:")
            logger.info(f"      users/{uid}/plays/<playId>")
        
        # Test reading likedSongs collection
        logger.info("\n❤️  Testing likedSongs collection...")
        liked_ref = db.collection("users").document(uid).collection("likedSongs")
        liked_docs = list(liked_ref.stream())
        logger.info(f"   Documents found: {len(liked_docs)}")
        
        if liked_docs:
            logger.info("   Sample liked song document:")
            sample = liked_docs[0].to_dict()
            for key, value in sample.items():
                logger.info(f"      {key}: {value}")
        else:
            logger.warning("   ⚠️  No liked song documents found")
            logger.info("   Make sure the frontend is writing liked songs to:")
            logger.info(f"      users/{uid}/likedSongs/<songId>")
        
    except Exception as e:
        logger.error(f"❌ Error reading from Firestore: {str(e)}")
        import traceback
        traceback.print_exc()

def main():
    logger.info("\n🔍 AURA Recommender Firestore Diagnostic Tool\n")
    
    # Test connection
    db = test_firestore_connection()
    
    # If connection successful, test with a user ID
    if db:
        logger.info("\n" + "=" * 60)
        logger.info("Enter a user ID to test (or press Enter to skip):")
        uid = input("UID: ").strip()
        
        if uid:
            test_user_data(db, uid)
        else:
            logger.info("Skipping user data test")
    
    logger.info("\n" + "=" * 60)
    logger.info("DIAGNOSTIC COMPLETE")
    logger.info("=" * 60)

if __name__ == "__main__":
    main()
