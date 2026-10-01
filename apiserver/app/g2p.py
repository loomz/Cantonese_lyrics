"""Jyutping conversion via canto-hk-g2p.

Pipeline is process-wide singleton: it is thread-safe and loading the model
is expensive, so we never build more than one.
"""
import re
import threading

from canto_hk_g2p import Pipeline

_lock = threading.Lock()
_pipeline: Pipeline | None = None


def get_pipeline() -> Pipeline:
    global _pipeline
    if _pipeline is None:
        with _lock:
            if _pipeline is None:
                _pipeline = Pipeline()
    return _pipeline


# A jyutping syllable ends in a tone digit 1-6 (e.g. "nei5"). Strip the
# trailing digit from every syllable to get the toneless variant. The digit
# is always preceded by a letter (the syllable final) and followed by a
# space or end-of-string, so this won't touch other text.
_TONE_RE = re.compile(r"(?<=\w)([1-6])(?=\s|$)")


def strip_tones(jyutping: str) -> str:
    return _TONE_RE.sub("", jyutping)
