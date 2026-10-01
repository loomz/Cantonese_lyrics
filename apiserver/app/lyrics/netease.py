"""NetEase Music (music.163.com) — primary provider.

No auth required, but a browser-like User-Agent is mandatory.
Verified endpoints (2026-09):
  search : /api/search/get/web?s={q}&type=1&limit={n}&offset=0
  lyric  : /api/song/lyric?id={id}&tv=1&lv=1&kv=1&rv=1
           (all-zero params return empty lrc!; rv=1 附带 romalrc 官方罗马音，
            韩日语歌才有，时间戳与 lrc 逐行对应)
  detail : /api/song/detail?ids=[{id}]              (v3 variant returns 400)
"""
import httpx

from .base import LyricsProvider, Track
from .lrc import clean_lrc, clean_lrc_with_ts, parse_lrc_text_map

_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


class NeteaseProvider(LyricsProvider):
    name = "netease"

    def __init__(self, client: httpx.Client | None = None):
        self._client = client or httpx.Client(
            headers={"User-Agent": _UA}, timeout=httpx.Timeout(15.0)
        )

    def search(self, query: str, limit: int = 20) -> list[Track]:
        r = self._client.get(
            "https://music.163.com/api/search/get/web",
            params={"s": query, "type": 1, "limit": limit, "offset": 0},
        )
        r.raise_for_status()
        songs = (r.json().get("result") or {}).get("songs") or []
        out: list[Track] = []
        for s in songs:
            sid = s.get("id")
            if not sid:
                continue
            artist = ",".join(a.get("name", "") for a in (s.get("artists") or []))
            out.append(Track(str(sid), s.get("name", ""), artist))
        return out

    def _lyric(self, track_id: str) -> dict:
        """拉歌词接口并返回原始 JSON（lrc / romalrc / tlyric 等字段）。"""
        r = self._client.get(
            "https://music.163.com/api/song/lyric",
            params={"id": track_id, "tv": 1, "lv": 1, "kv": 1, "rv": 1},
        )
        r.raise_for_status()
        return r.json()

    def _detail_meta(self, track_id: str) -> tuple[str, str]:
        """Title/artist are best-effort: the client already has them from the
        search result, so a failure here must not break the fetch."""
        title, artist = "", ""
        try:
            d = self._client.get(
                "https://music.163.com/api/song/detail",
                params={"ids": f"[{track_id}]"},
            )
            d.raise_for_status()
            songs = d.json().get("songs") or []
            if songs:
                title = songs[0].get("name", "")
                artist = ",".join(
                    a.get("name", "") for a in (songs[0].get("artists") or [])
                )
        except Exception:
            pass
        return title, artist

    def fetch(self, track_id: str) -> tuple[str, str, list[str]]:
        lrc = ((self._lyric(track_id).get("lrc") or {}).get("lyric")) or ""
        lines = clean_lrc(lrc)
        title, artist = self._detail_meta(track_id)
        return title, artist, lines

    def fetch_rich(
        self, track_id: str
    ) -> tuple[str, str, list[str], dict[int, str]]:
        """同 fetch，另附官方罗马音 roma_by_index（romalrc 按时间戳对齐）。

        romalrc 毫秒位数可能不一（[00:29.62] vs [00:29.620]）→ 归一化匹配；
        行数可多于 lrc（卡拉OK同时间戳多行）→ map 取首条命中。
        对不上的行省略 key，由调用方（songdoc）GLM 兜底。
        """
        data = self._lyric(track_id)
        lrc = ((data.get("lrc") or {}).get("lyric")) or ""
        roma_raw = ((data.get("romalrc") or {}).get("lyric")) or ""
        ts_lines = clean_lrc_with_ts(lrc)
        lines = [text for _, text in ts_lines]
        title, artist = self._detail_meta(track_id)

        roma_by_index: dict[int, str] = {}
        if roma_raw:
            roma_map = parse_lrc_text_map(roma_raw)
            for i, (ts, _) in enumerate(ts_lines):
                if ts is None:
                    continue
                roma = roma_map.get(ts, "")
                if roma.strip():
                    roma_by_index[i] = roma
        return title, artist, lines, roma_by_index

        # Title/artist are best-effort: the client already has them from the
        # search result, so a failure here must not break the fetch.
        title, artist = "", ""
        try:
            d = self._client.get(
                "https://music.163.com/api/song/detail",
                params={"ids": f"[{track_id}]"},
            )
            d.raise_for_status()
            songs = d.json().get("songs") or []
            if songs:
                title = songs[0].get("name", "")
                artist = ",".join(
                    a.get("name", "") for a in (songs[0].get("artists") or [])
                )
        except Exception:
            pass
        return title, artist, lines
