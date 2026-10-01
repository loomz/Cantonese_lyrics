"""Provider protocol for lyric sources."""
from dataclasses import dataclass


@dataclass
class Track:
    track_id: str  # provider-specific id (netease numeric id / qq song mid)
    title: str
    artist: str


class LyricsProvider:
    name: str = "base"

    def search(self, query: str, limit: int = 20) -> list[Track]:
        raise NotImplementedError

    def fetch(self, track_id: str) -> tuple[str, str, list[str]]:
        """Return (title, artist, lines). lines is the cleaned lyric text,
        one entry per line, no timestamps or metadata."""
        raise NotImplementedError
