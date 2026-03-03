"""
User Service - Handles user-related operations
Supports both local storage and Firebase Firestore backend
"""
from typing import Dict, Optional, List
import json
import os
from datetime import datetime
import hashlib
import logging

logger = logging.getLogger(__name__)

# DEFAULT_SERVICE_ACCOUNT_FILENAME = 'aura-music-65802-firebase-adminsdk-fbsvc-86f9c08a71.json'

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIRESTORE_AVAILABLE = True
except ImportError:
    FIRESTORE_AVAILABLE = False


# def _resolve_service_account_path() -> Optional[str]:
#     """Resolve Firebase service account JSON path from env and known locations."""
#     env_path = os.environ.get('FIREBASE_SERVICE_ACCOUNT_PATH')
#     if env_path and os.path.exists(env_path):
#         return env_path

#     backend_dir = os.path.dirname(os.path.dirname(__file__))
#     candidates = [
#         os.path.join(backend_dir, DEFAULT_SERVICE_ACCOUNT_FILENAME),
#         os.path.join(os.getcwd(), DEFAULT_SERVICE_ACCOUNT_FILENAME),
#         os.path.join(os.getcwd(), 'backend', DEFAULT_SERVICE_ACCOUNT_FILENAME),
#     ]
#     for candidate in candidates:
#         if os.path.exists(candidate):
#             return candidate
#     return None


def initialize_firebase_admin() -> bool:
    """Initialize Firebase Admin SDK using environment variable (Render-safe)."""
    if not FIRESTORE_AVAILABLE:
        logger.warning("Firebase Admin SDK unavailable")
        return False

    try:
        if firebase_admin._apps:
            logger.info("Firebase already initialized")
            return True

        # 🔍 DEBUG: Check if env variable exists
        logger.info(f"ENV KEYS: {list(os.environ.keys())}")

        firebase_json_str = os.environ.get("FIREBASE_CREDENTIALS")

        if not firebase_json_str:
            logger.error("FIREBASE_CREDENTIALS environment variable NOT FOUND")
            return False

        logger.info(f"FIREBASE_CREDENTIALS length: {len(firebase_json_str)}")

        # 🔍 DEBUG: Catch JSON formatting errors
        try:
            firebase_json = json.loads(firebase_json_str)
        except json.JSONDecodeError as e:
            logger.error(f"Invalid FIREBASE_CREDENTIALS JSON: {str(e)}")
            return False

        cred = credentials.Certificate(firebase_json)
        firebase_admin.initialize_app(cred)

        logger.info("Firebase initialized successfully using environment variable")
        return True

    except Exception as e:
        logger.error(f"Firebase initialization failed: {str(e)}", exc_info=True)
        return False

def get_firestore_client():
    """Get a connected Firestore client if possible."""
    if not FIRESTORE_AVAILABLE:
        return None

    if not initialize_firebase_admin():
        return None

    try:
        db = firestore.client()
        logger.info("Firestore connected")
        return db
    except Exception as e:
        logger.error(f"Firestore client unavailable: {str(e)}")
        return None

class UserService:
    """Service for managing users"""
    
    def __init__(self, storage_path: str = 'data/users.json'):
        self.storage_path = storage_path
        self._ensure_storage_dir()
        self.users = self._load_users()
        self.sessions = {}  # In production, use JWT or Redis
        
        # Initialize Firestore if available
        self.db = get_firestore_client()
    
    def _ensure_storage_dir(self):
        """Ensure the data directory exists"""
        os.makedirs(os.path.dirname(self.storage_path) if os.path.dirname(self.storage_path) else '.', exist_ok=True)
    
    def _load_users(self) -> Dict:
        """Load users from storage"""
        if os.path.exists(self.storage_path):
            try:
                with open(self.storage_path, 'r') as f:
                    return json.load(f)
            except:
                return {}
        return {}
    
    def _save_users(self):
        """Save users to storage"""
        try:
            with open(self.storage_path, 'w') as f:
                json.dump(self.users, f, indent=2)
        except Exception as e:
            print(f"Error saving users: {e}")
    
    def _hash_password(self, password: str) -> str:
        """Hash password (in production, use bcrypt or similar)"""
        return hashlib.sha256(password.encode()).hexdigest()
    
    def create_user(self, username: str, email: str, password: str) -> Optional[Dict]:
        """Create a new user"""
        # Check if user exists
        for user_id, user_data in self.users.items():
            if user_data.get('email') == email or user_data.get('username') == username:
                return None
        
        user_id = f"user_{int(datetime.now().timestamp())}"
        user = {
            'id': user_id,
            'username': username,
            'email': email,
            'passwordHash': self._hash_password(password),
            'createdAt': datetime.now().isoformat(),
            'likedSongs': [],
            'recentlyPlayed': [],
            'playlists': [],
        }
        
        self.users[user_id] = user
        self._save_users()
        
        # Return user without password
        user_copy = user.copy()
        del user_copy['passwordHash']
        return user_copy
    
    def authenticate_user(self, email: str, password: str) -> Optional[Dict]:
        """Authenticate a user"""
        password_hash = self._hash_password(password)
        
        for user_id, user_data in self.users.items():
            if user_data.get('email') == email and user_data.get('passwordHash') == password_hash:
                user_copy = user_data.copy()
                del user_copy['passwordHash']
                return user_copy
        
        return None
    
    def get_user(self, user_id: str) -> Optional[Dict]:
        """Get user by ID"""
        user = self.users.get(user_id)
        if user:
            user_copy = user.copy()
            if 'passwordHash' in user_copy:
                del user_copy['passwordHash']
            return user_copy
        return None
    
    def add_liked_song(self, user_id: str, song_data: Dict) -> bool:
        """Add a song to user's liked songs"""
        user = self.users.get(user_id)
        if not user:
            return False
        
        if 'likedSongs' not in user:
            user['likedSongs'] = []
        
        # Check if already liked
        if not any(s.get('videoId') == song_data.get('videoId') for s in user['likedSongs']):
            user['likedSongs'].append(song_data)
            self._save_users()
            return True
        return False
    
    def remove_liked_song(self, user_id: str, video_id: str) -> bool:
        """Remove a song from user's liked songs"""
        user = self.users.get(user_id)
        if not user:
            return False
        
        if 'likedSongs' not in user:
            user['likedSongs'] = []
        
        user['likedSongs'] = [s for s in user['likedSongs'] if s.get('videoId') != video_id]
        self._save_users()
        return True
    
    def add_recently_played(self, user_id: str, song_data: Dict) -> bool:
        """Add to recently played (keep last 50)"""
        user = self.users.get(user_id)
        if not user:
            return False
        
        if 'recentlyPlayed' not in user:
            user['recentlyPlayed'] = []
        
        # Remove if already exists
        user['recentlyPlayed'] = [s for s in user['recentlyPlayed'] if s.get('videoId') != song_data.get('videoId')]
        
        # Add to front
        user['recentlyPlayed'].insert(0, song_data)
        
        # Keep only last 50
        user['recentlyPlayed'] = user['recentlyPlayed'][:50]
        
        self._save_users()
        return True
    
    def get_user_plays(self, uid: str, limit: int = 50) -> List[Dict]:
        """
        Get user's recent plays from Firestore or local storage.
        Returns list of play records ordered by recency.
        """
        try:
            # Try Firestore first
            if self.db:
                try:
                    plays_ref = self.db.collection('users').document(uid).collection('plays')
                    docs = plays_ref.order_by('lastPlayedAt', direction=firestore.Query.DESCENDING).limit(limit).stream()
                    
                    plays = []
                    for doc in docs:
                        data = doc.to_dict()
                        if data:
                            plays.append(data)
                    
                    logger.debug(f"Fetched {len(plays)} plays from Firestore for user {uid}")
                    return plays
                except Exception as e:
                    logger.warning(f"Error fetching plays from Firestore: {str(e)}")
            
            # Fallback to local storage
            user = self.users.get(uid)
            if user and user.get('recentlyPlayed'):
                logger.debug(f"Fetched {len(user['recentlyPlayed'])} plays from local storage for user {uid}")
                return user['recentlyPlayed'][:limit]
            
            return []
            
        except Exception as e:
            logger.error(f"Error getting user plays for {uid}: {str(e)}")
            return []
    
    def get_user_liked_songs(self, uid: str) -> List[Dict]:
        """
        Get user's liked songs from Firestore or local storage.
        """
        try:
            # Try Firestore first
            if self.db:
                try:
                    liked_ref = self.db.collection('users').document(uid).collection('likedSongs')
                    docs = liked_ref.stream()
                    
                    liked_songs = []
                    for doc in docs:
                        data = doc.to_dict()
                        if data:
                            liked_songs.append(data)
                    
                    logger.debug(f"Fetched {len(liked_songs)} liked songs from Firestore for user {uid}")
                    return liked_songs
                except Exception as e:
                    logger.warning(f"Error fetching liked songs from Firestore: {str(e)}")
            
            # Fallback to local storage
            user = self.users.get(uid)
            if user and user.get('likedSongs'):
                logger.debug(f"Fetched {len(user['likedSongs'])} liked songs from local storage for user {uid}")
                return user['likedSongs']
            
            return []
            
        except Exception as e:
            logger.error(f"Error getting user liked songs for {uid}: {str(e)}")
            return []

