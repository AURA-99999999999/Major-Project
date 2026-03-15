"""
Cache Manager Service - Central in-memory caching with TTL support
Implements LRU-style cache for API responses to minimize external calls
"""
from typing import Any, Optional, Dict
import time
import logging
from threading import Lock

logger = logging.getLogger(__name__)


class CacheManager:
    """Thread-safe in-memory cache with TTL support."""
    
    def __init__(self, default_ttl: int = 600):
        """
        Initialize cache manager.
        
        Args:
            default_ttl: Default time-to-live in seconds (default: 10 minutes)
        """
        self.default_ttl = default_ttl
        self._cache: Dict[str, Dict[str, Any]] = {}
        self._lock = Lock()
        logger.info(f"CacheManager initialized with default TTL: {default_ttl}s")
    
    def get(self, key: str) -> Optional[Any]:
        """
        Retrieve cached value if not expired.
        
        Args:
            key: Cache key
            
        Returns:
            Cached value or None if expired/missing
        """
        with self._lock:
            entry = self._cache.get(key)
            if not entry:
                return None
            
            # Check expiration
            if time.time() > entry['expires_at']:
                self._cache.pop(key, None)
                logger.debug(f"Cache miss (expired): {key}")
                return None
            
            logger.debug(f"Cache hit: {key}")
            return entry['value']
    
    def set(self, key: str, value: Any, ttl: Optional[int] = None) -> None:
        """
        Store value in cache with TTL.
        
        Args:
            key: Cache key
            value: Value to cache
            ttl: Time-to-live in seconds (uses default if None)
        """
        ttl = ttl if ttl is not None else self.default_ttl
        expires_at = time.time() + ttl
        
        with self._lock:
            self._cache[key] = {
                'value': value,
                'expires_at': expires_at,
                'created_at': time.time()
            }
            logger.debug(f"Cache set: {key} (TTL: {ttl}s)")
    
    def delete(self, key: str) -> bool:
        """
        Delete a cache entry.
        
        Args:
            key: Cache key
            
        Returns:
            True if entry existed, False otherwise
        """
        with self._lock:
            existed = key in self._cache
            self._cache.pop(key, None)
            if existed:
                logger.debug(f"Cache delete: {key}")
            return existed
    
    def clear(self) -> int:
        """
        Clear all cache entries.
        
        Returns:
            Number of entries cleared
        """
        with self._lock:
            count = len(self._cache)
            self._cache.clear()
            logger.info(f"Cache cleared: {count} entries removed")
            return count
    
    def cleanup_expired(self) -> int:
        """
        Remove all expired entries.
        
        Returns:
            Number of entries removed
        """
        current_time = time.time()
        with self._lock:
            expired_keys = [
                key for key, entry in self._cache.items()
                if current_time > entry['expires_at']
            ]
            for key in expired_keys:
                self._cache.pop(key, None)
            
            if expired_keys:
                logger.info(f"Cleaned up {len(expired_keys)} expired cache entries")
            return len(expired_keys)
    
    def stats(self) -> Dict[str, Any]:
        """
        Get cache statistics.
        
        Returns:
            Dictionary with cache stats
        """
        with self._lock:
            return {
                'total_entries': len(self._cache),
                'keys': list(self._cache.keys())
            }


# Global cache instance
_global_cache = CacheManager(default_ttl=600)


def get_cache() -> CacheManager:
    """Get the global cache manager instance."""
    return _global_cache
