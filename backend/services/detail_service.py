"""
Detail Service - Fetch album, artist, and playlist details
Implements filtered content retrieval for detail screens
"""
from ytmusicapi import YTMusic
from typing import Dict, List, Optional
import logging
from services.music_filter import filter_music_tracks

logger = logging.getLogger(__name__)

class DetailService:
    """
    Handles detail fetching for albums, artists, and playlists
    All results are filtered to music-only content
    """
    
    def __init__(self, ytmusic: YTMusic):
        self.ytmusic = ytmusic
    
    def get_album_details(self, browse_id: str) -> Optional[Dict]:
        """
        Get album details with filtered songs
        
        Args:
            browse_id: Album browse ID from search
            
        Returns:
            {
                'albumId': str,
                'title': str,
                'artist': str,
                'artists': List[str],
                'thumbnail': str,
                'year': str,
                'trackCount': int,
                'duration': str,
                'songs': List[Dict]
            }
        """
        try:
            logger.info(f"📀 Fetching album: {browse_id}")
            album_data = self.ytmusic.get_album(browse_id)
            
            if not album_data:
                return None
            
            # Extract basic metadata
            album_id = album_data.get('audioPlaylistId', browse_id)
            title = album_data.get('title', 'Unknown Album')
            
            # Extract artist info
            artists_data = album_data.get('artists', [])
            artist_names = []
            if artists_data:
                artist_names = [
                    artist.get('name', 'Unknown')
                    for artist in artists_data
                    if isinstance(artist, dict)
                ]
            
            primary_artist = artist_names[0] if artist_names else 'Unknown Artist'
            
            # Get thumbnail
            thumbnails = album_data.get('thumbnails', [])
            thumbnail = self._get_best_thumbnail(thumbnails)
            
            # Extract tracks
            raw_tracks = album_data.get('tracks', [])
            songs = self._map_album_songs(raw_tracks)
            
            # Filter songs to music-only
            songs = filter_music_tracks(songs, ytmusic=self.ytmusic)
            
            result = {
                'albumId': album_id,
                'browseId': browse_id,
                'title': title,
                'artist': primary_artist,
                'artists': artist_names,
                'thumbnail': thumbnail,
                'year': album_data.get('year'),
                'trackCount': len(songs),
                'duration': album_data.get('duration'),
                'songs': songs
            }
            
            logger.info(f"✓ Album loaded: {title} - {len(songs)} songs")
            return result
            
        except Exception as e:
            logger.error(f"❌ Error fetching album {browse_id}: {str(e)}")
            return None
    
    def get_artist_details(self, browse_id: str) -> Optional[Dict]:
        """
        Get artist details with top songs and albums
        
        Args:
            browse_id: Artist browse ID from search
            
        Returns:
            {
                'artistId': str,
                'name': str,
                'thumbnail': str,
                'subscribers': str,
                'description': str,
                'topSongs': List[Dict],
                'albums': List[Dict]
            }
        """
        try:
            logger.info(f"🎤 Fetching artist: {browse_id}")
            artist_data = self.ytmusic.get_artist(browse_id)
            
            if not artist_data:
                return None
            
            # Extract basic metadata
            name = artist_data.get('name', 'Unknown Artist')
            description = artist_data.get('description', '')
            subscribers = artist_data.get('subscribers', '')
            
            # Get thumbnail
            thumbnails = artist_data.get('thumbnails', [])
            thumbnail = self._get_best_thumbnail(thumbnails)
            
            # Extract top songs using the CORRECT YTMusic API pattern
            # artist_data["songs"] returns a browseId for a playlist
            top_songs = []
            songs_section = artist_data.get('songs', {})
            if songs_section and isinstance(songs_section, dict):
                songs_browse_id = songs_section.get('browseId')
                if songs_browse_id:
                    try:
                        # Fetch the full playlist of top songs
                        songs_playlist = self.ytmusic.get_playlist(songs_browse_id)
                        if songs_playlist and 'tracks' in songs_playlist:
                            raw_songs = songs_playlist['tracks']
                            top_songs = self._map_playlist_songs(raw_songs)
                            # Filter with shallow validation (artist playlists are pre-curated)
                            top_songs = filter_music_tracks(top_songs, ytmusic=None, include_validation=False)[:10]
                    except Exception as e:
                        logger.warning(f"Failed to fetch artist top songs playlist: {e}")
                else:
                    # Fallback: use results if available (older API format)
                    raw_songs = songs_section.get('results', [])
                    if raw_songs:
                        top_songs = self._map_artist_songs(raw_songs)
                        top_songs = filter_music_tracks(top_songs, ytmusic=None, include_validation=False)[:10]
            
            # Extract albums using get_artist_albums() for full album list
            albums = []
            albums_section = artist_data.get('albums', {})
            if albums_section and isinstance(albums_section, dict):
                albums_browse_id = albums_section.get('browseId')
                albums_params = albums_section.get('params')
                
                if albums_browse_id and albums_params:
                    try:
                        # Use get_artist_albums() for full album list
                        full_albums = self.ytmusic.get_artist_albums(albums_browse_id, albums_params)
                        if full_albums:
                            albums = self._map_artist_albums(full_albums)[:10]
                    except Exception as e:
                        logger.warning(f"Failed to fetch full artist albums: {e}")
                        # Fallback: use results if available
                        raw_albums = albums_section.get('results', [])
                        if raw_albums:
                            albums = self._map_artist_albums(raw_albums)[:10]
                else:
                    # Fallback: use results if browseId/params not available
                    raw_albums = albums_section.get('results', [])
                    if raw_albums:
                        albums = self._map_artist_albums(raw_albums)[:10]
            
            result = {
                'artistId': browse_id,
                'name': name,
                'thumbnail': thumbnail,
                'subscribers': subscribers,
                'description': description,
                'topSongs': top_songs,
                'albums': albums
            }
            
            logger.info(f"✓ Artist loaded: {name} - {len(top_songs)} songs, {len(albums)} albums")
            return result
            
        except Exception as e:
            logger.error(f"❌ Error fetching artist {browse_id}: {str(e)}")
            return None
    
    def get_playlist_details(self, browse_id: str) -> Optional[Dict]:
        """
        Get playlist details with filtered songs
        
        Args:
            browse_id: Playlist browse ID from search
            
        Returns:
            {
                'playlistId': str,
                'title': str,
                'author': str,
                'thumbnail': str,
                'trackCount': int,
                'duration': str,
                'songs': List[Dict]
            }
        """
        try:
            logger.info(f"📋 Fetching playlist: {browse_id}")
            playlist_data = self.ytmusic.get_playlist(browse_id)
            
            if not playlist_data:
                return None
            
            # Extract metadata
            playlist_id = playlist_data.get('id', browse_id)
            title = playlist_data.get('title', 'Unknown Playlist')
            
            # Get author
            author = playlist_data.get('author', {})
            author_name = 'YouTube Music'
            if isinstance(author, dict):
                author_name = author.get('name', 'YouTube Music')
            elif isinstance(author, str):
                author_name = author
            
            # Get thumbnail
            thumbnails = playlist_data.get('thumbnails', [])
            thumbnail = self._get_best_thumbnail(thumbnails)
            
            # Extract tracks
            raw_tracks = playlist_data.get('tracks', [])
            songs = self._map_playlist_songs(raw_tracks)
            
            # Filter songs to music-only
            songs = filter_music_tracks(songs, ytmusic=self.ytmusic)
            
            result = {
                'playlistId': playlist_id,
                'browseId': browse_id,
                'title': title,
                'author': author_name,
                'thumbnail': thumbnail,
                'trackCount': len(songs),
                'duration': playlist_data.get('duration'),
                'description': playlist_data.get('description', ''),
                'songs': songs
            }
            
            logger.info(f"✓ Playlist loaded: {title} - {len(songs)} songs")
            return result
            
        except Exception as e:
            logger.error(f"❌ Error fetching playlist {browse_id}: {str(e)}")
            return None
    
    # Helper methods for mapping
    
    def _map_album_songs(self, raw_tracks: List[Dict]) -> List[Dict]:
        """Map album tracks to standardized song format"""
        songs = []
        for idx, track in enumerate(raw_tracks):
            try:
                video_id = track.get('videoId')
                title = track.get('title')
                
                if not video_id or not title:
                    continue
                
                # Extract artists
                artists_data = track.get('artists', [])
                artist_names = []
                if artists_data:
                    artist_names = [
                        artist.get('name', 'Unknown')
                        for artist in artists_data
                        if isinstance(artist, dict)
                    ]
                
                if not artist_names:
                    artist_names = ['Unknown Artist']
                
                songs.append({
                    'videoId': video_id,
                    'title': title,
                    'artists': artist_names,
                    'thumbnail': self._get_best_thumbnail(track.get('thumbnails', [])),
                    'duration': track.get('duration'),
                    'duration_seconds': track.get('duration_seconds'),
                    'trackNumber': idx + 1,
                    'type': 'song'
                })
            except Exception as e:
                logger.debug(f"Error mapping album track: {str(e)}")
                continue
        
        return songs
    
    def _map_artist_songs(self, raw_songs: List[Dict]) -> List[Dict]:
        """Map artist songs to standardized format"""
        songs = []
        for song in raw_songs:
            try:
                video_id = song.get('videoId')
                title = song.get('title')
                
                if not video_id or not title:
                    continue
                
                # Extract artists
                artists_data = song.get('artists', [])
                artist_names = []
                if artists_data:
                    artist_names = [
                        artist.get('name', 'Unknown')
                        for artist in artists_data
                        if isinstance(artist, dict)
                    ]
                
                if not artist_names:
                    artist_names = ['Unknown Artist']
                
                songs.append({
                    'videoId': video_id,
                    'title': title,
                    'artists': artist_names,
                    'thumbnail': self._get_best_thumbnail(song.get('thumbnails', [])),
                    'album': song.get('album', {}).get('name') if song.get('album') else None,
                    'duration': song.get('duration'),
                    'duration_seconds': song.get('duration_seconds'),
                    'year': song.get('year'),
                    'type': 'song'
                })
            except Exception as e:
                logger.debug(f"Error mapping artist song: {str(e)}")
                continue
        
        return songs
    
    def _map_artist_albums(self, raw_albums: List[Dict]) -> List[Dict]:
        """Map artist albums to standardized format"""
        albums = []
        for album in raw_albums:
            try:
                browse_id = album.get('browseId')
                title = album.get('title')
                
                if not browse_id or not title:
                    continue
                
                albums.append({
                    'browseId': browse_id,
                    'title': title,
                    'thumbnail': self._get_best_thumbnail(album.get('thumbnails', [])),
                    'year': album.get('year'),
                    'type': album.get('type', 'Album')
                })
            except Exception as e:
                logger.debug(f"Error mapping artist album: {str(e)}")
                continue
        
        return albums
    
    def _map_playlist_songs(self, raw_tracks: List[Dict]) -> List[Dict]:
        """Map playlist tracks to standardized song format"""
        songs = []
        for track in raw_tracks:
            try:
                video_id = track.get('videoId')
                title = track.get('title')
                
                if not video_id or not title:
                    continue
                
                # Extract artists
                artists_data = track.get('artists', [])
                artist_names = []
                if artists_data:
                    artist_names = [
                        artist.get('name', 'Unknown')
                        for artist in artists_data
                        if isinstance(artist, dict)
                    ]
                
                if not artist_names:
                    artist_names = ['Unknown Artist']
                
                songs.append({
                    'videoId': video_id,
                    'title': title,
                    'artists': artist_names,
                    'thumbnail': self._get_best_thumbnail(track.get('thumbnails', [])),
                    'album': track.get('album', {}).get('name') if track.get('album') else None,
                    'duration': track.get('duration'),
                    'duration_seconds': track.get('duration_seconds'),
                    'type': 'song'
                })
            except Exception as e:
                logger.debug(f"Error mapping playlist track: {str(e)}")
                continue
        
        return songs
    
    def _get_best_thumbnail(self, thumbnails: List[Dict]) -> str:
        """Extract highest quality thumbnail URL"""
        if not thumbnails:
            return ''
        # Get the highest resolution thumbnail (last in list)
        return thumbnails[-1].get('url', '') if thumbnails else ''
