"""Cantonese lyrics API server.

Endpoints:
  GET  /health                      -> {"ok": true}
  GET  /api/lyrics/search           -> online song search (netease primary, qq fallback)
  GET  /api/song/{provider}/{id}    -> song doc; cache hit returns file, miss generates
                                       (fetch -> g2p -> GLM homophones) and persists it
  POST /api/song/{provider}/{id}/refresh -> force regenerate (version + 1)
  GET  /api/song/{provider}/{id}/version -> cached version number only (no generation)
  GET  /api/apk/latest              -> latest APK version info (in-app upgrade check)
  GET  /api/apk/download            -> download the APK (phone browser / in-app install)
  GET  /apk                         -> HTML download page for phone browsers

Legacy (kept for the paused iOS / mini-program clients and debugging):
  POST /api/g2p                     -> jyutping conversion (canto-hk-g2p)
  GET  /api/lyrics/{provider}/{id}  -> cleaned lyric lines for a track
"""
import json

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from pydantic import BaseModel, Field

from app.g2p import get_pipeline, strip_tones
from app.lyrics import get_provider, search_all
from app.songdoc import (
    DATA_DIR,
    NoLyricsError,
    UpstreamError,
    UnknownProviderError,
    generate_song,
    load_doc,
)

app = FastAPI(title="Homophone Lyrics API Server")

# Clients are phones / mini-programs on the LAN or the public internet:
# allow everything for now (no auth on this service yet).
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class G2pRequest(BaseModel):
    lines: list[str] = Field(default_factory=list)


@app.get("/health")
def health():
    return {"ok": True}


@app.post("/api/g2p")
def g2p(req: G2pRequest):
    if not req.lines:
        return {"lines": []}
    pipeline = get_pipeline()
    with_tone = pipeline.convert_batch(req.lines)
    return {
        "lines": [
            {"with_tone": t, "without_tone": strip_tones(t)}
            for t in with_tone
        ]
    }


@app.get("/api/lyrics/search")
def lyrics_search(q: str = Query(..., min_length=1), limit: int = Query(20, ge=1, le=50)):
    try:
        provider, results = search_all(q, limit)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"所有歌词源均失败: {e}")
    return {
        "provider": provider.name,
        "results": [
            {"trackId": t.track_id, "title": t.title, "artist": t.artist}
            for t in results
        ],
    }


@app.get("/api/lyrics/{provider}/{track_id}")
def lyrics_fetch(provider: str, track_id: str):
    p = get_provider(provider)
    if p is None:
        raise HTTPException(status_code=404, detail=f"未知歌词源: {provider}")
    try:
        title, artist, lines = p.fetch(track_id)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"获取歌词失败: {e}")
    if not lines:
        raise HTTPException(status_code=404, detail="该歌曲没有歌词")
    return {"title": title, "artist": artist, "lines": lines}


def _song_error(e: Exception) -> HTTPException:
    if isinstance(e, (UnknownProviderError, NoLyricsError)):
        return HTTPException(status_code=404, detail=str(e))
    if isinstance(e, UpstreamError):
        return HTTPException(status_code=502, detail=str(e))
    return HTTPException(status_code=500, detail=f"内部错误: {e}")


@app.get("/api/song/{provider}/{track_id}")
def song_get(provider: str, track_id: str):
    """命中缓存直接返回文件；未命中则生成（取词→粤拼→GLM）并落盘后返回。"""
    try:
        doc, from_cache = generate_song(provider, track_id, force=False)
    except Exception as e:
        raise _song_error(e)
    return JSONResponse(content=doc, headers={"X-From-Cache": "1" if from_cache else "0"})


@app.post("/api/song/{provider}/{track_id}/refresh")
def song_refresh(provider: str, track_id: str):
    """强制重新生成（服务端刷新），version = 旧 + 1（无旧文件则 1）。"""
    try:
        doc, _ = generate_song(provider, track_id, force=True)
    except Exception as e:
        raise _song_error(e)
    # 刷新必为现场生成，与 song_get 一致带 X-From-Cache:0
    return JSONResponse(content=doc, headers={"X-From-Cache": "0"})


@app.get("/api/song/{provider}/{track_id}/version")
def song_version(provider: str, track_id: str):
    """只查缓存版本号，不触发生成（供客户端轻量查更新）。未缓存返回 404。"""
    doc = load_doc(provider, track_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="该歌曲尚未缓存")
    return {"version": doc.get("version", 0)}


# ── APK 下载（手机浏览器 / App 内升级）──────────────────────────────
# 与歌词同一数据目录（LYRICS_DATA_DIR 可覆盖），由 deploy_apk.sh 部署：
#   data/apk/cantonese-lyrics.apk  待下载的 APK
#   data/apk/latest.json           版本信息（versionCode/versionName/size/sha256/...）
_APK_DIR = DATA_DIR / "apk"
_APK_FILE = _APK_DIR / "cantonese-lyrics.apk"
_APK_META = _APK_DIR / "latest.json"

_APK_PAGE_STYLE = """
  body { margin:0; background:#0f1115; color:#e8eaed;
         font-family:system-ui,-apple-system,"PingFang SC","Microsoft YaHei",sans-serif;
         display:flex; min-height:100vh; align-items:center; justify-content:center; }
  .card { width:88%; max-width:420px; background:#1a1d24; border:1px solid #2a2e37;
          border-radius:16px; padding:32px 28px; text-align:center; }
  h1 { font-size:20px; margin:0 0 6px; }
  .ver { color:#8b93a1; font-size:14px; margin-bottom:24px; }
  a.btn { display:block; background:#4c8dff; color:#fff; text-decoration:none;
          font-size:16px; font-weight:600; padding:14px 0; border-radius:12px; }
  .log { color:#c3c9d4; font-size:13px; margin-top:18px; line-height:1.7; white-space:pre-line; }
  .note { color:#8b93a1; font-size:12px; margin-top:18px; line-height:1.6; }
"""


def _apk_meta() -> dict:
    """读 latest.json；不存在或损坏返回 {}。"""
    if not _APK_META.exists():
        return {}
    try:
        return json.loads(_APK_META.read_text(encoding="utf-8"))
    except Exception:
        return {}


@app.get("/api/apk/latest")
def apk_latest():
    """App 内升级检查：最新版本信息（versionCode/versionName/size/sha256/url）。未部署 404。"""
    if not _APK_FILE.exists():
        raise HTTPException(status_code=404, detail="暂无可下载版本")
    meta = _apk_meta()
    meta.setdefault("versionCode", 0)
    meta.setdefault("versionName", "0")
    meta["url"] = "/api/apk/download"
    return meta


@app.get("/api/apk/download")
def apk_download():
    """下载 APK：Content-Type 为 apk，手机浏览器访问直接触发下载。

    文件名带版本号（cantonese-lyrics-v1.2.apk）+ 禁缓存头：
    避免手机把下载目录里旧版本同名文件（cantonese-lyrics.apk）当成新包安装，
    也避免浏览器/下载管理器命中缓存下发旧包。
    """
    if not _APK_FILE.exists():
        raise HTTPException(status_code=404, detail="APK 未部署")
    meta = _apk_meta()
    ver = meta.get("versionName", "0")
    filename = f"cantonese-lyrics-v{ver}.apk"
    return FileResponse(
        path=_APK_FILE,
        media_type="application/vnd.android.package-archive",
        filename=filename,
        headers={
            "Cache-Control": "no-cache, no-store, must-revalidate",
            "Pragma": "no-cache",
            "Expires": "0",
        },
    )


@app.get("/apk", response_class=HTMLResponse)
def apk_page():
    """手机浏览器下载页：版本信息 + 下载按钮。"""
    if not _APK_FILE.exists():
        raise HTTPException(status_code=404, detail="APK 尚未部署")
    meta = _apk_meta()
    version = meta.get("versionName", "?")
    code = meta.get("versionCode", 0)
    size_mb = f"{meta['size'] / 1048576:.1f}" if meta.get("size") else "?"
    changelog = meta.get("changelog", "")
    changelog_html = f'<div class="log">{changelog}</div>' if changelog else ""
    return f"""<!doctype html>
<html lang="zh">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>谐音歌词 · 下载</title>
<style>{_APK_PAGE_STYLE}</style>
</head>
<body>
<div class="card">
  <h1>谐音歌词</h1>
  <div class="ver">版本 {version} · {size_mb} MB</div>
  <a class="btn" href="/api/apk/download?v={code}">下载并安装</a>
  {changelog_html}
  <div class="note">下载后在文件列表点击 APK 安装；<br>如提示「未知来源应用」，请在设置中允许。</div>
</div>
</body>
</html>
"""


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000)
