"""
Quick test to verify recommendation service can connect to Firestore
"""
import sys
from pathlib import Path


BACKEND_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND_ROOT))

from services.recommendation_service import RecommendationService
from services.user_service import UserService
from services.jiosaavn_service import JioSaavnService
import logging

logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

def test_recommendation_service_firestore():
    """Test that recommendation service can connect to Firestore"""
    logger.info("=" * 60)
    logger.info("Testing Recommendation Service Firestore Integration")
    logger.info("=" * 60)
    
    try:
        # Initialize services
        user_service = UserService()
        jiosaavn_service = JioSaavnService()
        recommendation_service = RecommendationService(
            user_service=user_service,
            jiosaavn_service=jiosaavn_service
        )
        
        # Test with a dummy user
        test_uid = "test_user_12345"
        logger.info(f"\nTesting with user: {test_uid}")
        
        # Try to get recommendations
        result = recommendation_service.get_recommendations(test_uid, limit=5)
        
        logger.info(f"\n✅ Recommendation service working!")
        logger.info(f"   Results count: {result.get('count', 0)}")
        logger.info(f"   Source: {result.get('source', 'unknown')}")
        
        # Check the logs for Firestore connection status
        if result.get('count', 0) > 0:
            logger.info(f"\n✅ Successfully generated {result['count']} recommendations")
        else:
            logger.warning(f"\n⚠️  No recommendations generated (cold-start expected for new user)")
        
        return True
        
    except Exception as e:
        logger.error(f"\n❌ Test failed: {str(e)}", exc_info=True)
        return False

if __name__ == "__main__":
    success = test_recommendation_service_firestore()
    sys.exit(0 if success else 1)
