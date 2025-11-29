"""
Playlist Service - Handles playlist operations
In a production app, this would use a database. For now, using in-memory storage.
"""
from typing import List, Dict, Optional
import json
import os
from datetime import datetime

class PlaylistService:
    """Service for managing playlists"""
    
    def __init__(self, storage_path: str = 'data/playlists.json'):
        self.storage_path = storage_path
        self._ensure_storage_dir()
        self.playlists = self._load_playlists()
    
    def _ensure_storage_dir(self):
        """Ensure the data directory exists"""
        os.makedirs(os.path.dirname(self.storage_path) if os.path.dirname(self.storage_path) else '.', exist_ok=True)
    
    def _load_playlists(self) -> Dict:
        """Load playlists from storage"""
        if os.path.exists(self.storage_path):
            try:
                with open(self.storage_path, 'r') as f:
                    return json.load(f)
            except:
                return {}
        return {}
    
    def _save_playlists(self):
        """Save playlists to storage"""
        try:
            with open(self.storage_path, 'w') as f:
                json.dump(self.playlists, f, indent=2)
        except Exception as e:
            print(f"Error saving playlists: {e}")
    
    def create_playlist(self, name: str, description: str = '', user_id: str = 'default') -> Dict:
        """Create a new playlist"""
        playlist_id = f"{user_id}_{int(datetime.now().timestamp())}"
        playlist = {
            'id': playlist_id,
            'name': name,
            'description': description,
            'userId': user_id,
            'songs': [],
            'coverImage': '',
            'createdAt': datetime.now().isoformat(),
            'updatedAt': datetime.now().isoformat(),
        }
        
        if user_id not in self.playlists:
            self.playlists[user_id] = []
        
        self.playlists[user_id].append(playlist)
        self._save_playlists()
        return playlist
    
    def get_playlists(self, user_id: str = 'default') -> List[Dict]:
        """Get all playlists for a user"""
        return self.playlists.get(user_id, [])
    
    def get_playlist(self, playlist_id: str, user_id: str = 'default') -> Optional[Dict]:
        """Get a specific playlist"""
        user_playlists = self.playlists.get(user_id, [])
        for playlist in user_playlists:
            if playlist['id'] == playlist_id:
                return playlist
        return None
    
    def add_song_to_playlist(self, playlist_id: str, song_data: Dict, user_id: str = 'default') -> bool:
        """Add a song to a playlist"""
        playlist = self.get_playlist(playlist_id, user_id)
        if not playlist:
            return False
        
        # Check if song already exists
        if not any(s.get('videoId') == song_data.get('videoId') for s in playlist['songs']):
            playlist['songs'].append(song_data)
            playlist['updatedAt'] = datetime.now().isoformat()
            self._save_playlists()
            return True
        return False
    
    def remove_song_from_playlist(self, playlist_id: str, video_id: str, user_id: str = 'default') -> bool:
        """Remove a song from a playlist"""
        playlist = self.get_playlist(playlist_id, user_id)
        if not playlist:
            return False
        
        playlist['songs'] = [s for s in playlist['songs'] if s.get('videoId') != video_id]
        playlist['updatedAt'] = datetime.now().isoformat()
        self._save_playlists()
        return True
    
    def update_playlist(self, playlist_id: str, updates: Dict, user_id: str = 'default') -> bool:
        """Update playlist metadata"""
        playlist = self.get_playlist(playlist_id, user_id)
        if not playlist:
            return False
        
        if 'name' in updates:
            playlist['name'] = updates['name']
        if 'description' in updates:
            playlist['description'] = updates['description']
        if 'coverImage' in updates:
            playlist['coverImage'] = updates['coverImage']
        
        playlist['updatedAt'] = datetime.now().isoformat()
        self._save_playlists()
        return True
    
    def delete_playlist(self, playlist_id: str, user_id: str = 'default') -> bool:
        """Delete a playlist"""
        user_playlists = self.playlists.get(user_id, [])
        self.playlists[user_id] = [p for p in user_playlists if p['id'] != playlist_id]
        self._save_playlists()
        return True

