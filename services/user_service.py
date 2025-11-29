"""
User Service - Handles user-related operations
For production, this would use a database. Using in-memory storage for now.
"""
from typing import Dict, Optional
import json
import os
from datetime import datetime
import hashlib

class UserService:
    """Service for managing users"""
    
    def __init__(self, storage_path: str = 'data/users.json'):
        self.storage_path = storage_path
        self._ensure_storage_dir()
        self.users = self._load_users()
        self.sessions = {}  # In production, use JWT or Redis
    
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

