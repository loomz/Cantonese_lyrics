"""歌词语言检测。

按全行非空白字符的 Unicode 区段计数判断：
  ko — 谚文（韩语）
  ja — 假名（日语）
  yue — 其余（汉字/中文 → 现有粤语管线；纯英文也归此，与现状一致）

只承诺 yue/ko/ja 三种；混合歌按 谚文→假名→汉字 优先级判定。
"""
import re

# 谚文音节 + 谚文字母（Compat/Jamo）
_HANGUL_RE = re.compile(r"[가-힣ᄀ-ᇿ㄰-㆏]")
# 平假名 + 片假名 + 片假名扩展
_KANA_RE = re.compile(r"[぀-ゟ゠-ヿㇰ-ㇿ]")
# 汉字（统一表意文字，中日共用）
_HAN_RE = re.compile(r"[一-鿿]")

# 少于此数量的字符视为装饰/乱入，不足以判定语言
_MIN_CHARS = 3


def detect_language(lines: list[str]) -> str:
    """返回 "ko" | "ja" | "yue"。"""
    hangul = sum(len(_HANGUL_RE.findall(l)) for l in lines)
    kana = sum(len(_KANA_RE.findall(l)) for l in lines)
    if hangul >= _MIN_CHARS and hangul > kana:
        return "ko"
    if kana >= _MIN_CHARS:
        return "ja"
    return "yue"
