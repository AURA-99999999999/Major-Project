"""Artist normalization utilities.

We intentionally maintain two concepts:
- display_name: original metadata value (for UI)
- normalized_key: canonical key for comparison/aggregation
"""

from __future__ import annotations

import re
from typing import Iterable, List

_NON_ALNUM_RE = re.compile(r"[^a-z0-9]+")


def normalized_artist_key(display_name: str) -> str:
    """Return a canonical artist key for internal aggregation.

    Rules:
    - lowercase
    - remove punctuation
    - handle initials ordering noise by dropping short initial-like tokens
    - remove duplicate tokens (preserve order)
    """
    raw = str(display_name or "").strip().lower()
    if not raw:
        return ""

    cleaned = _NON_ALNUM_RE.sub(" ", raw)
    cleaned = re.sub(r"\s+", " ", cleaned).strip()
    if not cleaned:
        return ""

    tokens = [t for t in cleaned.split(" ") if t]
    deduped: List[str] = []
    seen = set()
    for token in tokens:
        if token not in seen:
            seen.add(token)
            deduped.append(token)

    if len(deduped) <= 1:
        return deduped[0] if deduped else ""

    def _is_initialish(token: str) -> bool:
        return token.isalpha() and len(token) <= 2

    non_initial_tokens = [t for t in deduped if not _is_initialish(t)]
    if non_initial_tokens:
        deduped = non_initial_tokens

    return " ".join(deduped).strip()


def most_common_display_name(names: Iterable[str]) -> str:
    """Pick the most common display name (stable tie-break: first seen)."""
    counts = {}
    order: List[str] = []
    for name in names:
        value = str(name or "").strip()
        if not value:
            continue
        if value not in counts:
            counts[value] = 0
            order.append(value)
        counts[value] += 1
    if not counts:
        return ""
    best = max(order, key=lambda n: (counts.get(n, 0), -order.index(n)))
    return best

