"""谐音标注 / 罗马音转写（服务端调用）。

模型配置统一由 app.model_manager 管理（data/model_config.json）：
  云端模型 glm / dashscope / longcat，或自定义模型（默认本地 llama-swap
  + qwen3.8-27b）。在管理页面切换，或改配置文件 active 字段即生效，
  无需重启（每次调用实时读取配置）。

定位是纯「标注」任务：输入原词行数组，逐行输出中文谐音或罗马音，
不生成/不修改原词。
"""
import json
import logging

import httpx

from .model_manager import model_manager

logger = logging.getLogger("lyrics.glm")

# ── 中文谐音 prompts ──────────────────────────────────────────────

_SYSTEM_PROMPT_YUE = (
    "你是一个粤语谐音标注助手。用户给你若干行粤语歌词（普通话汉字写法），"
    "你为每一行逐字标注「中文谐音」：\n"
    "1. 该行每个字对应一个谐音汉字，用普通话读出来尽量接近该字在粤语中的发音；\n"
    "2. 谐音字与空格分隔，字数与顺序和原行一致；\n"
    "3. 只输出一个 JSON 对象：{\"lines\": [\"谐 音 1\", \"谐 音 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "4. 不要输出任何解释、markdown 代码块或额外文字。"
)

# 韩语是黏着语、有连音/音变，「逐字对应」不成立：按空格分隔的「词」为单位。
_SYSTEM_PROMPT_KO = (
    "你是一个韩语歌词谐音标注助手。用户给你若干行韩语歌词，"
    "你为每一行标注「中文谐音」：\n"
    "1. 以空格分隔的「词」为单位，每个词给一个中文谐音词（1-3 个汉字），"
    "用普通话读出来尽量接近该韩语词的发音（连音/音变按实际读法取音）；\n"
    "2. 谐音词与空格分隔，词数与顺序和原行一致；\n"
    "3. 只输出一个 JSON 对象：{\"lines\": [\"谐 音 1\", \"谐 音 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "4. 不要输出任何解释、markdown 代码块或额外文字。"
)

# 日语无空格、有送假名，「逐字对应」不成立：按读音单位宽松对齐。
_SYSTEM_PROMPT_JA = (
    "你是一个日语歌词谐音标注助手。用户给你若干行日语歌词，"
    "你为每一行标注「中文谐音」：\n"
    "1. 按读音单位（一个词/词素/助词为一个单位）标注中文谐音（每单位 1-3 个汉字），"
    "用普通话读出来尽量接近该单位的日语发音；\n"
    "2. 单位之间用空格分隔；单位数与该行读音块大致对应即可，顺序与原行一致；\n"
    "3. 只输出一个 JSON 对象：{\"lines\": [\"谐 音 1\", \"谐 音 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "4. 不要输出任何解释、markdown 代码块或额外文字。"
)

# ── 罗马音 prompts（韩日语注音兜底）───────────────────────────────

_SYSTEM_PROMPT_ROMA_KO = (
    "你是一个韩语转写助手。把用户给的每一行韩语歌词转写成 RR 式罗马字：\n"
    "1. 小写，词间保留原空格；连音按实际读法转写；\n"
    "2. 只输出一个 JSON 对象：{\"lines\": [\"roma 1\", \"roma 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "3. 不要输出任何解释、markdown 代码块或额外文字。"
)

_SYSTEM_PROMPT_ROMA_JA = (
    "你是一个日语转写助手。把用户给的每一行日语歌词转写成黑本式罗马字：\n"
    "1. 小写，汉字按读音展开，读音单位间用空格分隔；长音用「-」（如 kōu 不用 ou）；\n"
    "2. 只输出一个 JSON 对象：{\"lines\": [\"roma 1\", \"roma 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "3. 不要输出任何解释、markdown 代码块或额外文字。"
)


class GlmError(Exception):
    """GLM 调用失败（网络 / HTTP / 解析）。"""


def _chat(user_prompt: str, system_prompt: str = _SYSTEM_PROMPT_YUE) -> str:
    # 每次调用实时读取活动模型配置（页面切换 / 改配置文件即生效）
    cfg = model_manager.get_active_config()
    body = {
        "model": cfg["model"],
        "temperature": 0.3,
        "stream": False,
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt},
        ],
    }
    # 模型特定的额外参数（如 glm-5.3-flash 的 reasoning_effort=low）
    body.update(cfg.get("extra") or {})
    headers = {}
    if cfg.get("api_key"):
        headers["Authorization"] = f"Bearer {cfg['api_key']}"
    logger.info(
        "模型调用: %s (%s) -> %s, 行数=%d",
        cfg["name"], cfg["model"], cfg["api_url"],
        len(user_prompt.splitlines()),
    )
    # 每次调用新建 client：httpx.Client 非线程安全，避免跨请求复用连接池状态。
    # 整首标注通常 <30s；读超时 300s 作兜底，连接 15s。
    with httpx.Client(timeout=httpx.Timeout(300.0, connect=15.0)) as client:
        r = client.post(cfg["api_url"], headers=headers, json=body)
    if r.status_code // 100 != 2:
        logger.error("模型 HTTP %s: %s", r.status_code, r.text[:300])
        raise GlmError(
            f"{cfg['name']} HTTP {r.status_code}：{r.text[:200]}"
        )
    data = r.json()
    msg = data["choices"][0]["message"]
    content = msg.get("content") or ""
    finish = data["choices"][0].get("finish_reason")
    # 本地模型可能把内容塞进 reasoning_content 或返回空 content，记日志便于排查
    if not content.strip():
        logger.warning(
            "模型返回空 content (finish_reason=%s, reasoning=%r)",
            finish, (msg.get("reasoning_content") or "")[:200],
        )
    logger.info("模型返回: finish_reason=%s, content_len=%d", finish, len(content))
    return content


def annotate(lines: list[str], language: str = "yue") -> list[str]:
    """为原词行数组逐行标注谐音，返回与输入等长、同序的谐音数组。"""
    numbered = "\n".join(f"{i + 1}. {l}" for i, l in enumerate(lines))
    if language == "ko":
        system_prompt = _SYSTEM_PROMPT_KO
        user_prompt = (
            f"以下是韩语歌词（共 {len(lines)} 行），请逐行标注中文谐音：\n"
            f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
        )
    elif language == "ja":
        system_prompt = _SYSTEM_PROMPT_JA
        user_prompt = (
            f"以下是日语歌词（共 {len(lines)} 行），请逐行标注中文谐音：\n"
            f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
        )
    else:
        system_prompt = _SYSTEM_PROMPT_YUE
        user_prompt = (
            f"以下是粤语歌词（共 {len(lines)} 行），请逐行逐字标注中文谐音：\n"
            f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
        )
    return parse_homophones(_chat(user_prompt, system_prompt))


def romanize(lines: list[str], language: str) -> list[str]:
    """GLM 罗马音兜底（romalrc 对不上的行），返回与输入等长、同序的罗马音数组。"""
    if language not in ("ko", "ja"):
        raise GlmError(f"romanize 只支持 ko/ja，收到 {language}")
    numbered = "\n".join(f"{i + 1}. {l}" for i, l in enumerate(lines))
    if language == "ko":
        system_prompt = _SYSTEM_PROMPT_ROMA_KO
        user_prompt = (
            f"以下是韩语歌词（共 {len(lines)} 行），请逐行转写罗马字：\n"
            f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
        )
    else:
        system_prompt = _SYSTEM_PROMPT_ROMA_JA
        user_prompt = (
            f"以下是日语歌词（共 {len(lines)} 行），请逐行转写罗马字：\n"
            f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
        )
    return parse_homophones(_chat(user_prompt, system_prompt))


def parse_homophones(content: str) -> list[str]:
    """解析模型返回（容忍代码块包裹、前后多余文字）→ 谐音/罗马音字符串数组。"""
    text = content.strip()
    if text.startswith("```"):
        text = text.split("```", 1)[1].strip()
        if text.startswith("json"):
            text = text[4:].strip()
        if text.endswith("```"):
            text = text.rsplit("```", 1)[0].strip()
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end <= start:
        logger.error("解析失败(无 JSON)，原始返回: %r", content[:500])
        raise GlmError(f"模型返回中未找到 JSON（原始返回: {content[:200]!r}），请重试")
    try:
        root = json.loads(text[start : end + 1])
    except Exception as e:
        logger.error("JSON 解析失败: %s, 片段: %r", e, text[start:end+1][:300])
        raise GlmError(f"模型返回的 JSON 解析失败（{text[start:end+1][:100]!r}），请重试") from e
    arr = root.get("lines") or []
    out = [str(x).strip() for x in arr]
    if not out:
        logger.error("谐音为空，原始返回: %r", content[:500])
        raise GlmError(f"模型返回的谐音为空（原始返回: {content[:200]!r}），请重试")
    return out
