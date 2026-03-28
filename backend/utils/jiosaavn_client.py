import json
import logging
from typing import Any, Dict, Optional

import requests

logger = logging.getLogger(__name__)

JIOSAAVN_API_URL = "https://www.jiosaavn.com/api.php"

HEADERS = {
    "User-Agent": "Mozilla/5.0",
    "Accept": "application/json",
    "Referer": "https://www.jiosaavn.com/",
}

session = requests.Session()
session.headers.update(HEADERS)


def jiosaavn_request(url: str, params: Optional[Dict[str, Any]] = None) -> Optional[Any]:
    try:
        response = session.get(url, params=params, timeout=5)
        response.raise_for_status()
        try:
            return response.json()
        except Exception:
            return json.loads(response.text)
    except Exception as exc:
        logger.debug("JioSaavn web-client request failed: %s", exc)
        return None
