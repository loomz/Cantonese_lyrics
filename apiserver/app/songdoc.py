"""歌曲文档的生成与文件缓存。

每首歌一个 JSON 文档，缓存在 data/lyrics/{provider}_{trackId}.json。
任一用户请求某歌：命中缓存直接返回；未命中才生成（取词→检测语言→
粤拼或罗马音→GLM 谐音）并原子落盘，之后所有用户秒取。

语言路由：yue → canto-hk-g2p 粤拼；ko/ja → netease romalrc 官方罗马音
（缺行 GLM 兜底）；谐音全部 GLM 按 language 分派 prompt。

线程安全：per-track 锁跨 取词+g2p+GLM 全程持有——同一未缓存歌的并发请求
去重（第二个等文件，不重复计费 GLM）；不同歌并行。
"""
import json
import os
import tempfile
import threading
import time
from pathlib import Path

from .g2p import get_pipeline, strip_tones
from .glm import annotate, romanize
from .lang import detect_language
from .lyrics import get_provider

# 数据目录默认 <apiserver>/data（CWD 无关）；可用 LYRICS_DATA_DIR 覆盖。
# 对外公开（main.py 的 APK 下载目录 data/apk/ 也基于它）。
DATA_DIR = Path(
    os.environ.get("LYRICS_DATA_DIR")
    or Path(__file__).resolve().parent.parent / "data"
)
_LYRICS_DIR = DATA_DIR / "lyrics"

_track_locks: dict[str, threading.Lock] = {}
_track_locks_guard = threading.Lock()


def _lock_for(key: str) -> threading.Lock:
    with _track_locks_guard:
        lock = _track_locks.get(key)
        if lock is None:
            lock = _track_locks[key] = threading.Lock()
        return lock


def doc_path(provider: str, track_id: str) -> Path:
    return _LYRICS_DIR / f"{provider}_{track_id}.json"


def _atomic_write(path: Path, text: str) -> None:
    """tmp + fsync + rename，避免读到写了一半的文件。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(dir=str(path.parent), prefix=path.name + ".", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(text)
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    except Exception:
        try:
            os.unlink(tmp)
        except OSError:
            pass
        raise


def load_doc(provider: str, track_id: str) -> dict | None:
    """读缓存文档；不存在或损坏返回 None。"""
    p = doc_path(provider, track_id)
    if not p.exists():
        return None
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception:
        return None


class UnknownProviderError(Exception):
    pass


class NoLyricsError(Exception):
    pass


class UpstreamError(Exception):
    pass


def _backfill_language(doc: dict) -> dict:
    """旧缓存无 language 字段 → 现场检测并补写落盘（version 不变）。

    仅安全网：现有缓存均为粤语。客户端也默认 "yue" 兜底。
    """
    if "language" in doc:
        return doc
    try:
        doc["language"] = detect_language(
            [l.get("mandarin", "") for l in doc.get("lines") or []]
        )
        _atomic_write(
            doc_path(doc.get("provider", ""), doc.get("trackId", "")),
            json.dumps(doc, ensure_ascii=False, indent=2),
        )
    except Exception:
        pass  # 回填失败不影响返回
    return doc


def generate_song(provider: str, track_id: str, force: bool = False) -> tuple[dict, bool]:
    """返回 (doc, from_cache)。命中缓存直接返回；否则生成并落盘。

    force=True 用于 /refresh：跳过缓存，重新生成，version = 旧 + 1（无旧文件则 1）。
    """
    p = get_provider(provider)
    if p is None:
        raise UnknownProviderError(f"未知歌词源: {provider}")

    if not force:
        cached = load_doc(provider, track_id)
        if cached is not None:
            return _backfill_language(cached), True

    with _lock_for(f"{provider}:{track_id}"):
        # 双重检查：并发请求中可能已有别的线程生成并落盘。
        if not force:
            cached = load_doc(provider, track_id)
            if cached is not None:
                return _backfill_language(cached), True

        old = load_doc(provider, track_id) if force else None
        try:
            title, artist, ts_lines, roma = p.fetch_rich(track_id)
            # fetch_rich 返回 [(ts, text), ...]，songdoc 只需要 text
            lines = [text for _, text in ts_lines]
        except Exception as e:
            raise UpstreamError(f"获取歌词失败: {e}") from e
        if not lines:
            raise NoLyricsError("该歌曲没有歌词")

        language = detect_language(lines)
        try:
            if language == "yue":
                # 粤语：canto-hk-g2p 粤拼（romalrc 是粤语罗马音非粤拼，不用）。
                with_tone = get_pipeline().convert_batch(lines)
                toneless = [strip_tones(t) for t in with_tone]
            else:
                # 韩语/日语：官方罗马音（romalrc 按时间戳对齐），
                # 缺行 → GLM 罗马音兜底（非致命，失败该行留空）。
                roma_lines = [roma.get(i, "") for i in range(len(lines))]
                missing = [i for i, r in enumerate(roma_lines) if not r.strip()]
                if missing:
                    try:
                        glm_roma = romanize([lines[i] for i in missing], language)
                        for j, i in enumerate(missing):
                            roma_lines[i] = glm_roma[j]
                    except Exception:
                        pass
                with_tone = roma_lines
                toneless = roma_lines  # ko/ja 无声调数字，两字段同值

            homophones = annotate(lines, language)
        except Exception as e:
            raise UpstreamError(f"粤拼/谐音生成失败: {e}") from e

        version = (old.get("version", 0) + 1) if (force and old) else 1
        doc = {
            "id": f"{provider}_{track_id}",
            "provider": provider,
            "trackId": track_id,
            "version": version,
            "generatedAt": int(time.time() * 1000),
            "language": language,
            "title": title,
            "artist": artist,
            "lines": [
                {
                    "jyutping": with_tone[i] if i < len(with_tone) else "",
                    "jyutpingToneless": toneless[i] if i < len(toneless) else "",
                    "mandarin": lines[i],
                    "homophone": homophones[i] if i < len(homophones) else "",
                }
                for i in range(len(lines))
            ],
        }
        _atomic_write(
            doc_path(provider, track_id),
            json.dumps(doc, ensure_ascii=False, indent=2),
        )
        return doc, False
