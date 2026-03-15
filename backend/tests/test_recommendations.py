"""
Basic recommendation tests for DailyMixService diversity behavior.
"""
from __future__ import annotations

from collections import Counter

from services.daily_mix_service import DailyMixService


def _make_song(video_id: str, artists: list[str]) -> dict:
    return {
        "videoId": video_id,
        "title": f"Song {video_id}",
        "artists": [{"name": artist} for artist in artists],
    }


def _primary_artist(song: dict) -> str:
    artists = song.get("artists")
    if isinstance(artists, list) and artists:
        first = artists[0]
        if isinstance(first, dict):
            return str(first.get("name") or "Unknown")
        return str(first or "Unknown")
    if isinstance(artists, str) and artists.strip():
        return artists.split(",")[0].strip()

    primary_artists = str(song.get("primary_artists") or song.get("artist") or "").strip()
    if primary_artists:
        return primary_artists.split(",")[0].strip()
    return "Unknown"


def test_diversity_minimum_artists() -> None:
    service = DailyMixService(recommendation_service=None, user_service=None)

    songs = []
    # Build 30 songs across 8 artists, with some collaborations
    artist_groups = [
        ["Harris Jayaraj", "Anirudh Ravichander"],
        ["D. Imman"],
        ["Santhosh Narayanan"],
        ["Yuvan Shankar Raja", "DSP"],
        ["Vivek-Mervin"],
        ["Sam CS"],
        ["Thaman S"],
        ["A.R. Rahman"],
    ]

    video_id = 1
    for artists in artist_groups:
        for _ in range(4):
            songs.append(_make_song(f"vid{video_id}", artists))
            video_id += 1

    result = service._ensure_diversity_resilient(
        songs,
        artist_listen_count={},
        all_consumed=set(),
        exclude_played=True,
        min_threshold=15,
        max_per_artist=3,
        mix_type="similar",
    )

    assert len(result) >= 15

    primary_artists = [_primary_artist(song) for song in result]
    unique_artists = {a for a in primary_artists if a}
    assert len(unique_artists) >= 5


def test_artist_distribution_not_collapsed() -> None:
    service = DailyMixService(recommendation_service=None, user_service=None)

    songs = [
        _make_song("a1", ["Harris Jayaraj - Topic", "Anirudh Ravichander Official"]),
        _make_song("a2", ["Harris Jayaraj - Topic"]),
        _make_song("a3", ["D. Imman - Official"]),
        _make_song("a4", ["Santhosh Narayanan"]),
        _make_song("a5", ["Yuvan Shankar Raja VEVO"]),
        _make_song("a6", ["Sam CS"]),
        _make_song("a7", ["Thaman S"]),
        _make_song("a8", ["A.R. Rahman"]),
        _make_song("a9", ["Vivek-Mervin"]),
        _make_song("a10", ["DSP"]),
    ]

    result = service._ensure_diversity_resilient(
        songs,
        artist_listen_count={},
        all_consumed=set(),
        exclude_played=True,
        min_threshold=8,
        max_per_artist=2,
        mix_type="similar",
    )

    primary_artists = [_primary_artist(song) for song in result]
    artist_counts = Counter(primary_artists)
    assert len(artist_counts) >= 5
