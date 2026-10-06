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
import logging
from typing import Optional

from fastapi import FastAPI, HTTPException, Query, Request

# 业务日志（lyrics.* 命名空间），输出到 uvicorn 日志便于排查模型调用异常
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, HTMLResponse, JSONResponse
from pydantic import BaseModel, Field

from app.g2p import get_pipeline, strip_tones
from app.lyrics import get_provider, search_all
from app.model_manager import model_manager
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
    """取歌词（清洗后纯文本行）。"""
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


@app.get("/api/lyrics/{provider}/{track_id}/rich")
def lyrics_fetch_rich(provider: str, track_id: str):
    """取歌词（富文本，含时间戳、官方罗马音）。

    不触发 songdoc 生成管线，仅从歌词源拉原始数据。
    返回结构：
      { title, artist, lines: [{ ts: 毫秒 | null, text }], roma_by_index: { index: 官方罗马音 } }
    """
    p = get_provider(provider)
    if p is None:
        raise HTTPException(status_code=404, detail=f"未知歌词源: {provider}")
    try:
        title, artist, lines, roma_by_index = p.fetch_rich(track_id)
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"获取歌词失败: {e}")
    if not lines:
        raise HTTPException(status_code=404, detail="该歌曲没有歌词")
    # 转换 ts 为毫秒数字，方便前端直接使用
    return {
        "title": title,
        "artist": artist,
        "lines": [{"ts": ts, "text": text} for ts, text in lines],
        "roma_by_index": {str(k): v for k, v in roma_by_index.items()},
    }


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


# ── Admin API ──────────────────────────────────────────────────

@app.get("/api/admin/lyrics/list")
def admin_lyrics_list(
    page: int = Query(1, ge=1),
    page_size: int = Query(20, ge=1, le=100),
    provider: str = Query(None),
    search: str = Query(None),
):
    """分页获取缓存歌词列表。"""
    import json
    from pathlib import Path

    lyrics_dir = DATA_DIR / "lyrics"
    if not lyrics_dir.exists():
        return {"items": [], "total": 0, "page": page, "page_size": page_size}

    # 收集所有歌词文件
    all_songs = []
    for f in lyrics_dir.glob("*.json"):
        try:
            doc = json.loads(f.read_text(encoding="utf-8"))
            # 筛选 provider
            if provider and doc.get("provider") != provider:
                continue
            # 搜索过滤
            if search:
                search_lower = search.lower()
                title = doc.get("title", "").lower()
                artist = doc.get("artist", "").lower()
                if search_lower not in title and search_lower not in artist:
                    continue
            all_songs.append(doc)
        except Exception:
            continue

    # 按生成时间倒序排序
    all_songs.sort(key=lambda x: x.get("generatedAt", 0), reverse=True)

    # 分页
    total = len(all_songs)
    start = (page - 1) * page_size
    end = start + page_size
    items = all_songs[start:end]

    return {
        "items": items,
        "total": total,
        "page": page,
        "page_size": page_size
    }


@app.get("/api/admin/lyrics/{provider}/{track_id}")
def admin_lyrics_detail(provider: str, track_id: str):
    """获取单个歌词详情。"""
    doc = load_doc(provider, track_id)
    if doc is None:
        raise HTTPException(status_code=404, detail="歌词不存在")
    return doc


@app.delete("/api/admin/lyrics/{provider}/{track_id}")
def admin_lyrics_delete(provider: str, track_id: str):
    """删除缓存的歌词。"""
    from pathlib import Path

    lyrics_dir = DATA_DIR / "lyrics"
    file_path = lyrics_dir / f"{provider}_{track_id}.json"

    if not file_path.exists():
        raise HTTPException(status_code=404, detail="歌词不存在")

    try:
        file_path.unlink()
        return {"success": True, "message": "删除成功"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"删除失败: {e}")


@app.get("/api/admin/model/config")
def admin_model_config():
    """获取当前模型配置（所有可选模型 + 活动模型 + 自定义模型配置）。"""
    return {
        "models": model_manager.list_models(),
        "active": model_manager.get_active_config()["name"],
        "custom": model_manager.get_custom()
    }


class ModelSwitchRequest(BaseModel):
    name: str  # glm / dashscope / longcat / custom


@app.post("/api/admin/model/switch")
def admin_model_switch(req: ModelSwitchRequest):
    """切换活动模型。"""
    if not model_manager.set_active(req.name):
        raise HTTPException(status_code=400, detail=f"未知模型: {req.name}")
    return {"success": True, "message": f"已切换到 {req.name}"}


class ModelOverrideRequest(BaseModel):
    name: str      # glm / dashscope / longcat（必须是云端模型）
    model: str     # 覆盖的模型 ID（留空或无此字段则恢复预设值）
    api_key: Optional[str] = None
    label: Optional[str] = None


@app.post("/api/admin/model/override")
def admin_model_override(req: ModelOverrideRequest):
    """保存云端模型的覆盖配置（model ID / key / label）。

    用于在管理页面直接修改 dashscope 等云端模型的模型 ID。
    不传 model 或传空字符串表示恢复为预设值。
    """
    if req.name not in ("glm", "dashscope", "longcat"):
        raise HTTPException(status_code=400, detail="仅支持 glm / dashscope / longcat")
    ok = model_manager.set_model_override(
        req.name, req.model or None, req.api_key or None, req.label or None,
    )
    if not ok:
        raise HTTPException(status_code=500, detail="保存失败")
    return {"success": True, "message": f"已保存 {req.name} 的配置"}


class CustomModelRequest(BaseModel):
    api_url: str
    model: str
    api_key: Optional[str] = None
    label: Optional[str] = None


@app.post("/api/admin/model/custom")
def admin_model_custom(req: CustomModelRequest):
    """保存自定义模型配置（api_key 为空则保留原值）。"""
    if not model_manager.set_custom(
        req.api_url, req.model, req.api_key, req.label
    ):
        raise HTTPException(status_code=400, detail="api_url 和 model 必填")
    return {"success": True, "message": "自定义模型配置已保存"}


@app.get("/api/admin/model/status")
def admin_model_status(request: Request, name: Optional[str] = Query(None)):
    """健康检查（不传 name 检查活动模型，传 name 检查指定模型）。"""
    return model_manager.check_health(name)


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
