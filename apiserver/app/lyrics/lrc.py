"""LRC parsing / cleaning.

Strips [mm:ss.xx] timestamps, [ti:]/[ar:]/... tags, and metadata lines
(作词/作曲/编曲/制作人/唱片公司 ...), keeping only the sung lines.
"""
import re

# One timestamp marker at the start of a line: [mm:ss], [mm:ss.xx], [mm:ss:xx]
_TS_RE = re.compile(r"^\[\d{1,2}:\d{1,2}(?:[.:]\d{1,3})?\]")
# LRC metadata tags: [ti:...] [ar:...] [al:...] [by:...] [offset:...] ...
_TAG_RE = re.compile(
    r"^\[(?:ti|ar|al|by|offset|length|total|key|hash|sign)\s*:.*?\]\s*$", re.I
)
# Credit lines that sometimes appear in the lyric body itself.
_META_RE = re.compile(
    r"^\s*(?:作词|作曲|编曲|制作人|监制|统筹|出品|发行|唱片公司|混音|录音|和声|翻译|修音|"
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


def clean_lrc(raw: str) -> list[str]:
    if not raw:
        return []
    out: list[str] = []
    for raw_line in raw.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = _strip_prefix(raw_line.strip())
        if not line:
            continue
        if _META_RE.match(line):
            continue
        out.append(line)
    return out
