"""
User Service - Handles user-related operations
Supports both local storage and Firebase Firestore backend
"""
from typing import Dict, Optional, List
import json
import os
from datetime import datetime, timedelta
import hashlib
import logging
from threading import Lock

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
    """
    Initialize Firebase Admin SDK.
    
    Tries in order:
    1. FIREBASE_CREDENTIALS environment variable (JSON string) - for production
    2. Service account JSON file in known locations - for local development
    """
    if not FIRESTORE_AVAILABLE:
        logger.warning("Firebase Admin SDK unavailable")
        return False

    try:
        if firebase_admin._apps:
            logger.info("✅ Firebase already initialized")
            return True

        # METHOD 1: Try environment variable first (production/Render deployment)
        firebase_json_str = os.environ.get("FIREBASE_CREDENTIALS")
        if firebase_json_str:
            logger.info("🔍 Loading Firebase credentials from FIREBASE_CREDENTIALS environment variable")
            try:
                firebase_json = json.loads(firebase_json_str)
                cred = credentials.Certificate(firebase_json)
                firebase_admin.initialize_app(cred)
                logger.info("✅ Firebase initialized successfully using environment variable")
                return True
            except json.JSONDecodeError as e:
                logger.error(f"❌ Invalid FIREBASE_CREDENTIALS JSON: {str(e)}")
                # Continue to try file-based method
            except Exception as e:
                logger.error(f"❌ Failed to initialize with FIREBASE_CREDENTIALS: {str(e)}")
                # Continue to try file-based method

        # METHOD 2: Try loading from service account JSON file (local development)
        logger.info("🔍 Looking for Firebase service account JSON file...")
        
        # Search in known locations
        # __file__ is backend/services/user_service.py
        # Go up 2 levels to get to project root
        backend_dir = os.path.dirname(os.path.abspath(__file__))  # backend/services
        backend_parent = os.path.dirname(backend_dir)  # backend
        project_root = os.path.dirname(backend_parent)  # project root
        
        candidate_paths = [
            # Project root (where the file is typically placed)
            os.path.join(project_root, 'aura-music-65802-f1d41fed789f.json'),
            # Also check with the older filename pattern
            os.path.join(project_root, 'aura-music-65802-firebase-adminsdk-fbsvc-86f9c08a71.json'),
            # Backend directory
            os.path.join(backend_parent, 'aura-music-65802-f1d41fed789f.json'),
            # Current working directory
            os.path.join(os.getcwd(), 'aura-music-65802-f1d41fed789f.json'),
        ]
        
        for file_path in candidate_paths:
            if os.path.exists(file_path):
                logger.info(f"✅ Found service account file: {file_path}")
                try:
                    cred = credentials.Certificate(file_path)
                    firebase_admin.initialize_app(cred)
                    logger.info("✅ Firebase initialized successfully using service account file")
                    return True
                except Exception as e:
                    logger.error(f"❌ Failed to initialize with file {file_path}: {str(e)}")
        
        # No credentials found
        logger.error("❌ Firebase credentials NOT FOUND")
        logger.error("   Neither FIREBASE_CREDENTIALS environment variable nor service account JSON file found")
        logger.error("   Searched locations:")
        for path in candidate_paths:
            logger.error(f"     - {path}")
        return False

    except Exception as e:
        logger.error(f"❌ Firebase initialization failed: {str(e)}", exc_info=True)
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
    
    # Supported languages for the application
    SUPPORTED_LANGUAGES = [
        'hindi', 'telugu', 'tamil', 'english', 'punjabi',
        'malayalam', 'kannada', 'marathi', 'gujarati', 'bengali'
    ]
    
    # Language cache configuration (5 minutes)
    LANGUAGE_CACHE_TTL = timedelta(minutes=5)
    
    def __init__(self, storage_path: str = 'data/users.json'):
        self.storage_path = storage_path
        self._ensure_storage_dir()
        self.users = self._load_users()
        self.sessions = {}  # In production, use JWT or Redis
        
        # Initialize Firestore if available
        self.db = get_firestore_client()
        
        # Language preference cache
        self._language_cache = {}
        self._cache_lock = Lock()
    
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
                    logger.error(f"Error fetching liked songs from Firestore: {e}")
        except Exception as e:
            logger.error(f"Unexpected error in get_user_liked_songs: {e}")
        
        return []
    
    def _validate_languages(self, languages: List[str]) -> tuple[bool, Optional[str]]:
        """
        Validate language preferences.
        
        Returns:
            (is_valid, error_message)
        """
        if not languages:
            return False, "At least one language must be selected"
        
        if len(languages) > 5:
            return False, "Maximum 5 languages allowed"
        
        # Check for duplicates
        if len(languages) != len(set(languages)):
            return False, "Duplicate languages not allowed"
        
        # Normalize and validate against supported languages
        normalized = [lang.lower().strip() for lang in languages]
        for lang in normalized:
            if lang not in self.SUPPORTED_LANGUAGES:
                return False, f"Unsupported language: {lang}"
        
        return True, None
    
    def _invalidate_language_cache(self, uid: str):
        """Invalidate language cache for a user."""
        with self._cache_lock:
            if uid in self._language_cache:
                del self._language_cache[uid]
                logger.debug(f"Invalidated language cache for user {uid}")
    
    def get_user_languages(self, uid: str) -> List[str]:
        """
        Get user's language preferences with caching.
        
        Args:
            uid: User ID
            
        Returns:
            List of language preferences (lowercase strings)
        """
        # Check cache first
        with self._cache_lock:
            if uid in self._language_cache:
                cached_data, cached_time = self._language_cache[uid]
                if datetime.now() - cached_time < self.LANGUAGE_CACHE_TTL:
                    logger.debug(f"Language cache hit for user {uid}")
                    return cached_data
                else:
                    # Cache expired
                    del self._language_cache[uid]
        
        try:
            # Try Firestore first
            if self.db:
                try:
                    user_ref = self.db.collection('users').document(uid)
                    user_doc = user_ref.get()
                    
                    if user_doc.exists:
                        data = user_doc.to_dict()
                        languages = data.get('languagePreferences', [])
                        
                        # Cache the result
                        with self._cache_lock:
                            self._language_cache[uid] = (languages, datetime.now())
                        
                        logger.debug(f"Fetched {len(languages)} languages from Firestore for user {uid}")
                        return languages
                except Exception as e:
                    logger.warning(f"Error fetching languages from Firestore: {str(e)}")
            
            # Fallback to local storage
            user = self.users.get(uid)
            if user:
                languages = user.get('languagePreferences', [])
                
                # Cache the result
                with self._cache_lock:
                    self._language_cache[uid] = (languages, datetime.now())
                
                logger.debug(f"Fetched {len(languages)} languages from local storage for user {uid}")
                return languages
            
            return []
            
        except Exception as e:
            logger.error(f"Error getting user languages for {uid}: {str(e)}")
            return []
    
    def update_user_languages(self, uid: str, languages: List[str]) -> tuple[bool, Optional[str]]:
        """
        Update user's language preferences.
        
        Args:
            uid: User ID
            languages: List of language preferences
            
        Returns:
            (success, error_message)
        """
        # Validate languages
        is_valid, error = self._validate_languages(languages)
        if not is_valid:
            return False, error
        
        # Normalize languages (lowercase, strip whitespace)
        normalized_languages = [lang.lower().strip() for lang in languages]
        
        try:
            # Check if languages are unchanged to avoid unnecessary writes
            current_languages = self.get_user_languages(uid)
            if set(current_languages) == set(normalized_languages):
                logger.info(f"Languages unchanged for user {uid}, skipping update")
                return True, None
            
            # Update Firestore if available
            if self.db:
                try:
                    user_ref = self.db.collection('users').document(uid)
                    user_ref.set({
                        'languagePreferences': normalized_languages,
                        'languagePreferencesUpdatedAt': firestore.SERVER_TIMESTAMP
                    }, merge=True)
                    
                    logger.info(f"Updated languages in Firestore for user {uid}: {normalized_languages}")
                    
                    # Invalidate cache
                    self._invalidate_language_cache(uid)
                    
                    return True, None
                except Exception as e:
                    logger.error(f"Error updating languages in Firestore: {str(e)}")
                    # Continue to update local storage as fallback
            
            # Update local storage
            if uid not in self.users:
                self.users[uid] = {}
            
            self.users[uid]['languagePreferences'] = normalized_languages
            self.users[uid]['languagePreferencesUpdatedAt'] = datetime.now().isoformat()
            self._save_users()
            
            logger.info(f"Updated languages in local storage for user {uid}: {normalized_languages}")
            
            # Invalidate cache
            self._invalidate_language_cache(uid)
            
            return True, None
            
        except Exception as e:
            logger.error(f"Error updating user languages for {uid}: {str(e)}")
            return False, "Failed to update language preferences"
            
            # Fallback to local storage
            user = self.users.get(uid)
            if user and user.get('likedSongs'):
                logger.debug(f"Fetched {len(user['likedSongs'])} liked songs from local storage for user {uid}")
                return user['likedSongs']
            
            return []
            
        except Exception as e:
            logger.error(f"Error getting user liked songs for {uid}: {str(e)}")
            return []

