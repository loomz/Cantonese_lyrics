"""QQ Music — fallback provider.

u.y.qq.com musicu.fcg is region-restricted (returns 500003 from some
networks), so this source is only used when the primary (Netease) fails or
returns nothing. Track ids are song mids ("0025XXXX...").
"""
import json

import httpx

from .base import LyricsProvider, Track
from .lrc import clean_lrc

_UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)


class QQProvider(LyricsProvider):
    name = "qq"

    def __init__(self, client: httpx.Client | None = None):
        self._client = client or httpx.Client(
            headers={"User-Agent": _UA}, timeout=httpx.Timeout(15.0)
        )

    def search(self, query: str, limit: int = 20) -> list[Track]:
        payload = {
            "module": "music.search.SearchServer",
            "method": "DoSearchForQQMusicDesktop",
            "param": {
                "query": query,
                "num_per_page": limit,
                "page_num": 1,
                "search_type": 0,
            },
        }
        r = self._client.get(
            "https://u.y.qq.com/cgi-bin/musicu.fcg",
            params={
                "format": "json",
                "incharset": "utf8",
                "outcharset": "utf8",
                "data": json.dumps(payload, ensure_ascii=False),
            },
        )
        r.raise_for_status()
        data = r.json()
        # Region restriction (e.g. 500003) comes back as HTTP 200 with the
        # error nested in the body — surface it so callers can report it.
        param = data.get("param") or {}
        if param.get("code") not in (0, None):
            raise RuntimeError(
                f"QQ 音乐接口返回 {param.get('code')}（可能地域限制）"
            )
        body = (data.get("data") or {}).get("body") or {}
        songs = (body.get("song") or {}).get("list") or []
        out: list[Track] = []
        for s in songs:
            mid = s.get("mid")
            if not mid:
                continue
            artist = ",".join(x.get("name", "") for x in (s.get("singer") or []))
            out.append(Track(mid, s.get("name", ""), artist))
        return out

    def fetch(self, track_id: str) -> tuple[str, str, list[str]]:
        # track_id is the song mid
        r = self._client.get(
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            params={
                "songmid": track_id,
                "format": "json",
                "nobase64": 1,
                "g_tk": 10001,
                "loginUin": 0,
            },
        )
        r.raise_for_status()
        data = r.json()
        lyric = data.get("lyric") or ""
        lines = clean_lrc(lyric)
        return data.get("songName", ""), data.get("singer", ""), lines
