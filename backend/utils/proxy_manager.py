import logging
import random
import threading
from pathlib import Path
from typing import Dict, List, Optional, Set

import requests
from requests import Response

logger = logging.getLogger(__name__)

PROXY_TIMEOUT_SECONDS = 5
PROXY_RETRY_LIMIT = 3
PROXY_CHECK_URL = "https://ipinfo.io/json"
PROXY_FILE_PATH = Path(__file__).with_name("indian_proxies.txt")


class ProxyManager:
    def __init__(self, proxy_file_path: Path = PROXY_FILE_PATH) -> None:
        self._proxy_file_path = proxy_file_path
        self._all_proxies: List[str] = []
        self._working_proxies: List[str] = []
        self._pool_ready = False
        self._lock = threading.Lock()

    def load_proxies(self) -> List[str]:
        proxies: List[str] = []
        try:
            if not self._proxy_file_path.exists():
                logger.debug("Proxy file not found: %s", self._proxy_file_path)
                return []

            for raw_line in self._proxy_file_path.read_text(encoding="utf-8").splitlines():
                line = raw_line.strip()
                if not line or line.startswith("#"):
                    continue
                if self._is_valid_proxy_format(line):
                    proxies.append(line)

        except Exception as exc:
            logger.debug("Failed to load proxies: %s", exc)
            return []

        return proxies

    def test_proxy(self, proxy: str) -> bool:
        proxy_dict = self._as_requests_proxy(proxy)
        try:
            response = requests.get(
                PROXY_CHECK_URL,
                proxies=proxy_dict,
                timeout=PROXY_TIMEOUT_SECONDS,
            )
            if response.status_code != 200:
                return False

            payload = response.json()
            country = str(payload.get("country", "")).upper()
            return country == "IN"
        except Exception:
            return False

    def build_proxy_pool(self) -> None:
        with self._lock:
            if self._pool_ready:
                return

            self._all_proxies = self.load_proxies()
            working: List[str] = []

            for proxy in self._all_proxies:
                if self.test_proxy(proxy):
                    working.append(proxy)

            self._working_proxies = working
            self._pool_ready = True
            logger.debug(
                "Proxy pool initialized: %s/%s working proxies",
                len(self._working_proxies),
                len(self._all_proxies),
            )

    def get_proxy(self, exclude: Optional[Set[str]] = None) -> Optional[Dict[str, str]]:
        if not self._pool_ready:
            self.build_proxy_pool()

        excluded = exclude or set()
        with self._lock:
            available = [p for p in self._working_proxies if p not in excluded]
            if not available:
                return None
            picked = random.choice(available)
            return self._as_requests_proxy(picked)

    def remove_proxy(self, proxy_dict: Optional[Dict[str, str]]) -> None:
        if not proxy_dict:
            return

        raw = str(proxy_dict.get("http") or "").replace("http://", "").strip()
        if not raw:
            return

        with self._lock:
            if raw in self._working_proxies:
                self._working_proxies.remove(raw)

    @staticmethod
    def _as_requests_proxy(proxy: str) -> Dict[str, str]:
        normalized = f"http://{proxy}"
        return {"http": normalized, "https": normalized}

    @staticmethod
    def _is_valid_proxy_format(proxy: str) -> bool:
        parts = proxy.split(":")
        if len(parts) != 2:
            return False
        host, port = parts[0].strip(), parts[1].strip()
        if not host or not port.isdigit():
            return False
        return True


_proxy_manager = ProxyManager()


def load_proxies() -> List[str]:
    return _proxy_manager.load_proxies()


def test_proxy(proxy: str) -> bool:
    return _proxy_manager.test_proxy(proxy)


def build_proxy_pool() -> None:
    _proxy_manager.build_proxy_pool()


def get_proxy() -> Optional[Dict[str, str]]:
    return _proxy_manager.get_proxy()


def fetch_with_proxy(url: str) -> Optional[Response]:
    if not url:
        return None

    tried: Set[str] = set()
    for _ in range(PROXY_RETRY_LIMIT):
        proxy = _proxy_manager.get_proxy(exclude=tried)
        if not proxy:
            break

        proxy_key = str(proxy.get("http") or "").replace("http://", "")
        if proxy_key:
            tried.add(proxy_key)

        try:
            logger.debug("Proxy request using: %s", proxy_key)
            response = requests.get(url, proxies=proxy, timeout=PROXY_TIMEOUT_SECONDS)
            if response.status_code >= 500:
                logger.debug("Proxy request failed with status %s via %s", response.status_code, proxy_key)
                _proxy_manager.remove_proxy(proxy)
                continue
            return response
        except Exception as exc:
            logger.debug("Proxy request exception via %s: %s", proxy_key, exc)
            _proxy_manager.remove_proxy(proxy)

    try:
        logger.debug("Proxy fallback to direct request")
        return requests.get(url, timeout=PROXY_TIMEOUT_SECONDS)
    except Exception as exc:
        logger.debug("Direct fallback request failed: %s", exc)
        return None
