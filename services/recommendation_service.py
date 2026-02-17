"""
Recommendation Service - Industry-level personalized recommendations
Uses YTMusic APIs for content discovery without exposing streaming infrastructure
"""
from ytmusicapi import YTMusic
from typing import List, Dict, Optional, Set, Tuple
import logging
import os
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import Counter

try:
    import firebase_admin
    from firebase_admin import credentials, firestore
    FIRESTORE_AVAILABLE = True
except ImportError:
    FIRESTORE_AVAILABLE = False

logger = logging.getLogger(__name__)

DEFAULT_SERVICE_ACCOUNT_FILENAME = (
    'aura-music-65802-firebase-adminsdk-fbsvc-86f9c08a71.json'
)


class RecommendationService:
    """
    Generates personalized recommendations using:
    - User play history from Firestore
    - Artist similarity and related songs
    - Trending charts as cold-start fallback
    - Intelligent ranking with scoring weights
    """
    
    # Scoring weights for recommendation sources
    SCORE_WEIGHTS = {
        'song_related': 5,      # From song-based recommendations
        'artist_songs': 3,      # From top artists' songs
        'related_artists': 2,   # From related artists
        'search': 2,            # From search queries
        'trending': 1,          # Trending fallback
    }
    
    # Cache TTL in seconds
    CACHE_TTL_ARTIST = 3600    # 1 hour
    CACHE_TTL_RELATED = 1800   # 30 minutes

    # Validation limits and filters
    VALIDATION_SCAN_LIMIT = 25
    ALLOWED_MUSIC_VIDEO_TYPES = {
        'MUSIC_VIDEO_TYPE_ATV',
        'MUSIC_VIDEO_TYPE_OMV',
        'MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK',
    }
    TITLE_BLOCKLIST = [
        'interview',
        'trailer',
        'review',
        'press',
        'reaction',
        'behind the scenes',
        'making',
        'podcast',
        'episode',
        'full movie',
        'teaser',
    ]
    
    def __init__(self, ytmusic: YTMusic, user_service):
        self.ytmusic = ytmusic
        self.user_service = user_service
        
        # Cache: {key: (data, timestamp)}
        self.cache_artist: Dict[str, Tuple[Dict, float]] = {}
        self.cache_related: Dict[str, Tuple[List, float]] = {}
        
        self.max_workers = 3  # Limit concurrent ytmusic calls
        self._firestore_db = None
        self._ensure_firestore_client()

    def _ensure_firestore_client(self) -> None:
        """Initialize Firebase Admin SDK and Firestore client if possible."""
        if not FIRESTORE_AVAILABLE:
            return

        if self._firestore_db:
            return

        try:
            if not firebase_admin._apps:
                try:
                    service_account_path = os.environ.get('FIREBASE_SERVICE_ACCOUNT_PATH')
                    if not service_account_path:
                        workspace_path = os.path.abspath(os.getcwd())
                        candidate = os.path.join(workspace_path, DEFAULT_SERVICE_ACCOUNT_FILENAME)
                        service_account_path = candidate if os.path.exists(candidate) else None

                    if service_account_path and os.path.exists(service_account_path):
                        firebase_admin.initialize_app(
                            credentials.Certificate(service_account_path)
                        )
                    else:
                        firebase_admin.initialize_app(credentials.ApplicationDefault())
                except Exception as e:
                    logger.warning(f"Firestore init failed: {str(e)}")
                    return

            self._firestore_db = firestore.client()
        except Exception as e:
            logger.warning(f"Firestore client unavailable: {str(e)}")
    
    def get_recommendations(self, uid: str, limit: int = 20) -> Dict:
        """
        Generate personalized recommendations for a user.
        
        Args:
            uid: User ID
            limit: Number of recommendations to return
            
        Returns:
            Dict with recommended songs
        """
        try:
            logger.info(f"Generating recommendations for user: {uid}")
            
            # 1. Fetch user data from Firestore
            user_plays = self._fetch_user_plays(uid)
            user_liked = self._fetch_user_liked_songs(uid)

            logger.debug(f"User {uid}: {len(user_plays)} plays, {len(user_liked)} liked songs")

            # Required temporary debug logs
            print(f"[RECOMMENDER] Plays found: {len(user_plays)}")
            print(f"[RECOMMENDER] Sample play: {user_plays[0] if user_plays else 'None'}")

            # Create sets for faster lookup and filtering
            played_ids: Set[str] = {s.get('videoId') for s in user_plays if s.get('videoId')}
            liked_ids: Set[str] = {s.get('videoId') for s in user_liked if s.get('videoId')}
            all_consumed: Set[str] = played_ids | liked_ids

            # 2. Extract top artists and albums from play history
            top_artists, top_albums = self._extract_top_artists_and_albums(user_plays)
            print(f"[RECOMMENDER] Top artists: {top_artists}")
            print(f"[RECOMMENDER] Top albums: {top_albums}")

            # 3. Gather recommendations using search queries
            recommendations: Dict[str, Dict] = {}  # {videoId: song_data}

            if user_plays:
                search_queries = self._build_search_queries(top_artists, top_albums)
                for query in search_queries:
                    search_results = self._search_songs(query, limit=10)
                    self._merge_recommendations(recommendations, search_results, 'search')
                logger.info(f"Added {len(recommendations)} from search queries")

            # Trending fallback layer (cold-start)
            if len(user_plays) < 1:
                trending_recommendations = self._get_trending_fallback()
                self._merge_recommendations(recommendations, trending_recommendations, 'trending')
                logger.info(f"Added {len(trending_recommendations)} from trending (cold-start)")
            
            # 4. Deduplicate, filter, and rank
            final_recommendations = self._deduplicate_and_filter(
                recommendations,
                all_consumed,
                limit
            )
            
            logger.info(f"Generated {len(final_recommendations)} final recommendations for {uid}")
            
            return {
                'count': len(final_recommendations),
                'source': 'recommendation_engine',
                'results': final_recommendations
            }
            
        except Exception as e:
            logger.error(f"Error generating recommendations for {uid}: {str(e)}", exc_info=True)
            # Graceful failure: return empty recommendations
            return {
                'count': 0,
                'source': 'recommendation_engine',
                'results': []
            }
    
    def _fetch_user_plays(self, uid: str) -> List[Dict]:
        """Fetch user's plays from Firestore without filtering out valid documents."""
        try:
            self._ensure_firestore_client()
            db = self._firestore_db or getattr(self.user_service, 'db', None)

            if db:
                plays_ref = db.collection('users').document(uid).collection('plays')
                plays = [doc.to_dict() for doc in plays_ref.stream()]
                logger.debug(f"Fetched {len(plays)} plays for user {uid} from Firestore")
                return plays or []

            # Fallback to user service if Firestore is unavailable
            plays = self.user_service.get_user_plays(uid, limit=50)
            logger.debug(f"Fetched {len(plays)} plays for user {uid} from fallback")
            return plays or []
        except Exception as e:
            logger.error(f"Failed to fetch plays for {uid}: {str(e)}")
            return []
    
    def _fetch_user_liked_songs(self, uid: str) -> List[Dict]:
        """Fetch user's liked songs from Firestore."""
        try:
            liked = self.user_service.get_user_liked_songs(uid)
            logger.debug(f"Fetched {len(liked)} liked songs for user {uid}")
            return liked or []
        except Exception as e:
            logger.error(f"Failed to fetch liked songs for {uid}: {str(e)}")
            return []
    
    def _extract_top_artists_and_albums(self, plays: List[Dict]) -> Tuple[List[str], List[str]]:
        """
        Extract top artists and albums using playCount-weighted counts.
        Returns two lists: [top_artists], [top_albums]
        """
        try:
            artist_counter: Counter = Counter()
            album_counter: Counter = Counter()

            for play in plays:
                play_count = play.get('playCount', 1)
                try:
                    play_count = int(play_count)
                except (TypeError, ValueError):
                    play_count = 1

                artists = play.get('artists', [])
                if isinstance(artists, list):
                    for artist in artists:
                        if isinstance(artist, dict):
                            artist_name = artist.get('name')
                        else:
                            artist_name = str(artist)
                        if artist_name:
                            artist_counter[artist_name] += play_count
                elif isinstance(artists, str) and artists:
                    artist_counter[artists] += play_count

                album = play.get('album')
                if album:
                    album_counter[str(album)] += play_count

            top_artists = [name for name, _ in artist_counter.most_common(3)]
            top_albums = [name for name, _ in album_counter.most_common(2)]

            logger.debug(
                f"Extracted {len(top_artists)} artists and {len(top_albums)} albums"
            )
            return top_artists, top_albums
        except Exception as e:
            logger.error(f"Error extracting top artists/albums: {str(e)}")
            return [], []

    def _build_search_queries(self, top_artists: List[str], top_albums: List[str]) -> List[str]:
        """Build search queries from top artists and albums."""
        queries: List[str] = []
        for artist in top_artists:
            if artist:
                queries.append(artist)
        for album in top_albums:
            if album:
                queries.append(album)
        return queries

    def _search_songs(self, query: str, limit: int = 10) -> Dict[str, Dict]:
        """Search songs using YTMusic with the songs filter and normalize results."""
        try:
            if not query:
                return {}

            results: Dict[str, Dict] = {}
            search_results = self.ytmusic.search(query, filter='songs', limit=limit)

            for item in search_results or []:
                song_dict = self._extract_song_data(item)
                if song_dict and song_dict.get('videoId'):
                    results[song_dict['videoId']] = {
                        **song_dict,
                        '_source': 'search',
                        '_score': self.SCORE_WEIGHTS['search']
                    }

            return results
        except Exception as e:
            logger.error(f"Search error for query '{query}': {str(e)}")
            return {}
    
    def _expand_from_artists(self, artists: List[Dict]) -> Dict[str, Dict]:
        """
        Expand recommendations from top artists' song catalogs.
        Uses concurrent API calls for efficiency.
        """
        recommendations: Dict[str, Dict] = {}
        
        try:
            with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                futures = {
                    executor.submit(self._fetch_artist_songs, artist['channelId']): artist
                    for artist in artists
                }
                
                for future in as_completed(futures):
                    try:
                        songs = future.result()
                        for song in songs:
                            if song.get('videoId'):
                                recommendations[song['videoId']] = {
                                    **song,
                                    '_source': 'artist_songs',
                                    '_score': self.SCORE_WEIGHTS['artist_songs']
                                }
                    except Exception as e:
                        logger.warning(f"Error processing artist songs: {str(e)}")
            
            logger.debug(f"Expanded to {len(recommendations)} from artists")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error expanding from artists: {str(e)}")
            return recommendations
    
    def _expand_from_recent_songs(self, recent_plays: List[Dict]) -> Dict[str, Dict]:
        """
        Expand recommendations from recent song plays.
        Gets related/similar songs for each recent play.
        Uses concurrent API calls for efficiency.
        """
        recommendations: Dict[str, Dict] = {}
        
        try:
            with ThreadPoolExecutor(max_workers=self.max_workers) as executor:
                futures = {
                    executor.submit(self._fetch_song_related, play.get('videoId')): play
                    for play in recent_plays
                    if play.get('videoId')
                }
                
                for future in as_completed(futures):
                    try:
                        songs = future.result()
                        for song in songs:
                            if song.get('videoId'):
                                recommendations[song['videoId']] = {
                                    **song,
                                    '_source': 'song_related',
                                    '_score': self.SCORE_WEIGHTS['song_related']
                                }
                    except Exception as e:
                        logger.warning(f"Error processing related songs: {str(e)}")
            
            logger.debug(f"Expanded to {len(recommendations)} from recent songs")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error expanding from recent songs: {str(e)}")
            return recommendations
    
    def _fetch_artist_songs(self, channel_id: str) -> List[Dict]:
        """
        Fetch top songs from an artist.
        Includes main songs and related artists.
        Uses caching.
        """
        try:
            # Check cache
            if channel_id in self.cache_artist:
                cached_data, timestamp = self.cache_artist[channel_id]
                if time.time() - timestamp < self.CACHE_TTL_ARTIST:
                    logger.debug(f"Using cached artist data for {channel_id}")
                    return cached_data
            
            logger.debug(f"Fetching artist data for {channel_id}")
            artist_data = self.ytmusic.get_artist(channel_id)
            
            songs = []
            
            # Extract main artist songs
            if artist_data.get('songs', {}).get('results'):
                for song in artist_data['songs']['results'][:10]:  # Top 10 songs
                    song_dict = self._extract_song_data(song)
                    if song_dict:
                        songs.append(song_dict)
            
            # Extract related artists' top songs for diversity
            if artist_data.get('related', {}).get('results'):
                for related_artist in artist_data['related']['results'][:2]:  # Top 2 related
                    if related_artist.get('id'):
                        try:
                            related_data = self._fetch_artist_songs_cached(related_artist['id'])
                            songs.extend(related_data[:5])  # 5 songs from each related artist
                        except Exception as e:
                            logger.warning(f"Error fetching related artist: {str(e)}")
            
            # Cache the result
            self.cache_artist[channel_id] = (songs, time.time())
            
            logger.debug(f"Fetched {len(songs)} songs for artist {channel_id}")
            return songs
            
        except Exception as e:
            logger.error(f"Error fetching artist songs for {channel_id}: {str(e)}")
            return []
    
    def _fetch_artist_songs_cached(self, channel_id: str) -> List[Dict]:
        """Fetch artist songs with cache bypass for related artists."""
        try:
            if channel_id in self.cache_artist:
                cached_data, timestamp = self.cache_artist[channel_id]
                if time.time() - timestamp < self.CACHE_TTL_ARTIST:
                    return cached_data
            
            artist_data = self.ytmusic.get_artist(channel_id)
            songs = []
            
            if artist_data.get('songs', {}).get('results'):
                for song in artist_data['songs']['results'][:10]:
                    song_dict = self._extract_song_data(song)
                    if song_dict:
                        songs.append(song_dict)
            
            self.cache_artist[channel_id] = (songs, time.time())
            return songs
            
        except Exception as e:
            logger.warning(f"Error fetching cached artist {channel_id}: {str(e)}")
            return []
    
    def _fetch_song_related(self, video_id: str) -> List[Dict]:
        """
        Fetch songs related to a specific song.
        Uses caching.
        """
        try:
            # Check cache
            if video_id in self.cache_related:
                cached_data, timestamp = self.cache_related[video_id]
                if time.time() - timestamp < self.CACHE_TTL_RELATED:
                    logger.debug(f"Using cached related songs for {video_id}")
                    return cached_data
            
            logger.debug(f"Fetching related songs for {video_id}")
            related_results = self.ytmusic.get_song_related(video_id)
            
            songs = []
            
            # Extract songs from "You might also like" and similar sections
            if isinstance(related_results, list):
                for section in related_results:
                    if isinstance(section, dict):
                        # Extract from various section structures
                        contents = section.get('contents', [])
                        if isinstance(contents, list):
                            for item in contents:
                                song_dict = self._extract_song_data(item)
                                if song_dict:
                                    songs.append(song_dict)
                        
                        # Also check top-level items
                        if section.get('results'):
                            for item in section['results']:
                                song_dict = self._extract_song_data(item)
                                if song_dict:
                                    songs.append(song_dict)
            
            # Limit to prevent bloat
            songs = songs[:15]
            
            # Cache the result
            self.cache_related[video_id] = (songs, time.time())
            
            logger.debug(f"Fetched {len(songs)} related songs for {video_id}")
            return songs
            
        except Exception as e:
            logger.error(f"Error fetching related songs for {video_id}: {str(e)}")
            return []
    
    def _get_trending_fallback(self, country: str = 'IN') -> Dict[str, Dict]:
        """
        Get trending songs as a fallback for cold-start users.
        Used when user has less than 5 plays.
        """
        try:
            logger.debug(f"Fetching trending songs for cold-start (country: {country})")
            charts = self.ytmusic.get_charts(country=country)
            
            recommendations: Dict[str, Dict] = {}
            
            # Extract songs from chart playlists
            if charts.get('charts'):
                for chart in charts['charts'][:3]:  # Top 3 charts
                    if chart.get('items'):
                        for item in chart['items'][:5]:  # Top 5 from each chart
                            song_dict = self._extract_song_data(item)
                            if song_dict and song_dict.get('videoId'):
                                recommendations[song_dict['videoId']] = {
                                    **song_dict,
                                    '_source': 'trending',
                                    '_score': self.SCORE_WEIGHTS['trending']
                                }
            
            logger.debug(f"Got {len(recommendations)} trending songs")
            return recommendations
            
        except Exception as e:
            logger.error(f"Error fetching trending fallback: {str(e)}")
            return {}
    
    def _extract_song_data(self, item: Dict) -> Optional[Dict]:
        """
        Extract song data from YTMusic API response.
        Validates required fields and ignores non-song items.
        """
        try:
            # Skip if not a song (could be playlist, artist, etc.)
            if item.get('resultType') and item['resultType'] != 'song':
                return None
            
            # Check for required videoId
            video_id = item.get('videoId') or item.get('id')
            if not video_id:
                return None
            
            # Extract artists
            artists = []
            if item.get('artists'):
                if isinstance(item['artists'], list):
                    for artist in item['artists']:
                        if isinstance(artist, dict):
                            artists.append(artist.get('name', 'Unknown'))
                        else:
                            artists.append(str(artist))
            
            # Extract thumbnail with strict fallback order
            thumbnail = ''
            if item.get('thumbnails'):
                thumbnails = item['thumbnails']
                if isinstance(thumbnails, list) and thumbnails:
                    thumbnail = thumbnails[0].get('url', '')
                elif isinstance(thumbnails, dict):
                    thumbnail = thumbnails.get('url', '')

            if not thumbnail:
                thumbnail = item.get('thumbnail', '')

            if not thumbnail and video_id:
                thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
            
            return {
                'videoId': video_id,
                'title': item.get('title', item.get('name', 'Unknown')),
                'artists': artists or ['Unknown'],
                'thumbnail': thumbnail,
                'album': item.get('album', ''),
            }
            
        except Exception as e:
            logger.debug(f"Error extracting song data: {str(e)}")
            return None

    def _is_title_allowed(self, title: str) -> bool:
        """Lightweight title filter to avoid obvious non-music content."""
        if not title:
            return True
        lowered = title.lower()
        return not any(term in lowered for term in self.TITLE_BLOCKLIST)

    def _is_valid_music_video(self, video_id: str, title: str) -> bool:
        """Validate the video is a real music track using YTMusic get_song."""
        if not video_id:
            return False

        try:
            song_details = self.ytmusic.get_song(video_id)
            video_details = song_details.get('videoDetails', {}) if isinstance(song_details, dict) else {}
            music_video_type = video_details.get('musicVideoType')

            if music_video_type in self.ALLOWED_MUSIC_VIDEO_TYPES:
                return True

            logger.info(
                "Recommendation skipped: reason=music_video_type videoId=%s title=%s musicVideoType=%s",
                video_id,
                title,
                music_video_type
            )
            return False
        except Exception as e:
            logger.warning(
                "Recommendation validation failed: videoId=%s title=%s error=%s",
                video_id,
                title,
                str(e)
            )
            return False
    
    def _merge_recommendations(
        self,
        main_dict: Dict[str, Dict],
        additions: Dict[str, Dict],
        source: str
    ) -> None:
        """
        Merge new recommendations into main dict.
        Updates scores if song already exists from different source.
        """
        for video_id, song_data in additions.items():
            if video_id in main_dict:
                # Accumulate scores from multiple sources
                main_dict[video_id]['_score'] += song_data.get('_score', 0)
                # Update source list
                sources = main_dict[video_id].get('_sources', [])
                if source not in sources:
                    sources.append(source)
                main_dict[video_id]['_sources'] = sources
            else:
                # New song
                song_data['_sources'] = [source]
                main_dict[video_id] = song_data
    
    def _deduplicate_and_filter(
        self,
        recommendations: Dict[str, Dict],
        consumed_ids: Set[str],
        limit: int
    ) -> List[Dict]:
        """
        Deduplicate, filter out consumed songs, and return top N by score.
        """
        # Filter out consumed songs
        filtered = {
            vid: data for vid, data in recommendations.items()
            if vid not in consumed_ids
        }
        
        # Sort by score (descending)
        sorted_recs = sorted(
            filtered.items(),
            key=lambda x: x[1].get('_score', 0),
            reverse=True
        )
        
        # Clean up and return top N (validated as real music tracks)
        result = []
        validation_calls = 0
        for video_id, song_data in sorted_recs:
            if len(result) >= limit:
                break

            title = song_data.get('title', '')
            if not self._is_title_allowed(title):
                logger.info(
                    "Recommendation skipped: reason=title_filter videoId=%s title=%s",
                    video_id,
                    title
                )
                continue

            if validation_calls >= self.VALIDATION_SCAN_LIMIT:
                logger.info(
                    "Recommendation validation limit reached: limit=%s collected=%s",
                    self.VALIDATION_SCAN_LIMIT,
                    len(result)
                )
                break

            validation_calls += 1
            if not self._is_valid_music_video(video_id, title):
                continue

            thumbnail = song_data.get('thumbnail', '')
            if not thumbnail and video_id:
                thumbnail = f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"

            # Remove internal scoring fields
            cleaned = {
                'videoId': song_data.get('videoId'),
                'title': song_data.get('title'),
                'artists': song_data.get('artists', []),
                'thumbnail': thumbnail,
                'album': song_data.get('album', ''),
            }
            result.append(cleaned)
        logger.info("Final clean recommendations count=%s", len(result))
        return result
