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

    def fetch_rich(
        self, track_id: str
    ) -> tuple[str, str, list[tuple[int | None, str]], dict[int, str]]:
        """Return (title, artist, lines, roma_by_index)。

        lines: [(ts_ms | None, text), ...] — 每行首个时间戳（毫秒）+ 文本。
        roma_by_index: 行下标 → 该行官方罗马音（如 netease romalrc），
        缺行/整首缺失时省略 key。默认无音译来源，返回空 dict
        （QQ 等自动走此实现）。
        """
        title, artist, lines = self.fetch(track_id)
        # 基类无时间戳信息，返回 (None, text) 格式
        return title, artist, [(None, text) for text in lines], {}
