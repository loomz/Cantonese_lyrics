"""LRC parsing / cleaning.

Strips [mm:ss.xx] timestamps, [ti:]/[ar:]/... tags, and metadata lines
(作词/作曲/编曲/制作人/唱片公司/作詞/작사 ...), keeping only the sung lines.
"""
import re

# One timestamp marker at the start of a line: [mm:ss], [mm:ss.xx], [mm:ss:xx]
_TS_RE = re.compile(r"^\[\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?\]")
# LRC metadata tags: [ti:...] [ar:...] [al:...] [by:...] [offset:...] ...
_TAG_RE = re.compile(
    r"^\[(?:ti|ar|al|by|offset|length|total|key|hash|sign)\s*:.*?\]\s*$", re.I
)
# Credit lines that sometimes appear in the lyric body itself.
# 含日文（作詞/編曲…）与韩文（작사/작곡/편곡）credit 词。
_META_RE = re.compile(
    r"^\s*(?:作词|作曲|编曲|制作人|监制|统筹|出品|发行|唱片公司|混音|录音|和声|翻译|修音|"
    r"作詞|作曲|編曲|訳詞|译詞|振付|"
    r"작사|작곡|편곡|"
    r"lyricist|composer|arranger|producer|label|published)\s*[:：]",
    re.I,
)


def _strip_prefix(line: str) -> str:
    """Remove leading timestamp / tag markers (there may be several)."""
    changed = True
    while changed:
        changed = False
        m = _TS_RE.match(line)
        if m:
            line = line[m.end():].strip()
            changed = True
            continue
        m = _TAG_RE.match(line)
        if m:
            line = line[m.end():].strip()
            changed = True
    return line


def _ts_to_ms(ts: str) -> int:
    """"[mm:ss(.|:)ff]" → 毫秒；小数位 2-3 位（[00:29.62] 与 [00:29.620] 等价）。"""
    m = re.match(r"\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]", ts)
    if not m:
        return -1
    mm, ss, frac = int(m.group(1)), int(m.group(2)), m.group(3) or ""
    ms = (mm * 60 + ss) * 1000
    if frac:
        # 2 位当百分秒、3 位当毫秒（LRC 惯例）
        ms += int(frac) * (10 if len(frac) == 2 else 1)
    return ms


def _first_ts(raw_line: str) -> tuple[int | None, str]:
    """返回 (首个时间戳的毫秒数或 None, 剥去所有前缀后的文本)。"""
    m = _TS_RE.match(raw_line)
    ts = _ts_to_ms(m.group(0)) if m else None
    return ts, _strip_prefix(raw_line)


def clean_lrc_with_ts(raw: str) -> list[tuple[int | None, str]]:
    """清洁歌词，保留每行首个时间戳：[(ts_ms | None, text), ...]。

    原始行序保持；供 romalrc 按时间戳对齐使用。
    """
    if not raw:
        return []
    out: list[tuple[int | None, str]] = []
    for raw_line in raw.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        ts, line = _first_ts(raw_line.strip())
        if not line:
            continue
        if _META_RE.match(line):
            continue
        out.append((ts, line))
    return out


def parse_lrc_text_map(raw: str) -> dict[int, str]:
    """LRC → {ts_ms: text}。同时间戳多条取第一条非空（卡拉OK式重复行），空文本跳过。"""
    out: dict[int, str] = {}
    for ts, text in clean_lrc_with_ts(raw):
        if ts is None or not text:
            continue
        if ts not in out:  # 首条命中
            out[ts] = text
    return out


def clean_lrc(raw: str) -> list[str]:
    return [text for _, text in clean_lrc_with_ts(raw)]
