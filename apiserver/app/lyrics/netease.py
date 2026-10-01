"""NetEase Music (music.163.com) — primary provider.

No auth required, but a browser-like User-Agent is mandatory.
Verified endpoints (2026-09):
  search : /api/search/get/web?s={q}&type=1&limit={n}&offset=0
  lyric  : /api/song/lyric?id={id}&tv=1&lv=1&kv=1   (all-zero params return empty lrc!)
  detail : /api/song/detail?ids=[{id}]              (v3 variant returns 400)
"""
import httpx

from .base import LyricsProvider, Track
from .lrc import clean_lrc

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

    def fetch(self, track_id: str) -> tuple[str, str, list[str]]:
        r = self._client.get(
            "https://music.163.com/api/song/lyric",
            params={"id": track_id, "tv": 1, "lv": 1, "kv": 1},
        )
        r.raise_for_status()
        lrc = ((r.json().get("lrc") or {}).get("lyric")) or ""
        lines = clean_lrc(lrc)

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
