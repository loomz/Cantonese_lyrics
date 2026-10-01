"""Pluggable lyric providers, ordered primary-first.

Netease is the primary source (verified working from this network); QQ Music
is a fallback (region-restricted in some networks, returns 500003 there).
Adding a new source = implement LyricsProvider and append it below.
"""
from .base import LyricsProvider, Track
from .netease import NeteaseProvider
from .qq import QQProvider

_PROVIDERS: list[LyricsProvider] = [NeteaseProvider(), QQProvider()]


def get_provider(name: str) -> LyricsProvider | None:
    for p in _PROVIDERS:
        if p.name == name:
            return p
    return None


def search_all(query: str, limit: int = 20) -> tuple[LyricsProvider, list[Track]]:
    """Try providers in order; return (provider, results) from the first one
    that yields results. Raises the last error if every provider fails."""
    last_err: Exception | None = None
    for p in _PROVIDERS:
        try:
            results = p.search(query, limit)
            if results:
                return p, results
        except Exception as e:
            last_err = e
    if last_err is not None:
        raise last_err
    return _PROVIDERS[0], []
