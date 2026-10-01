"""GLM-5.3-Flash 谐音标注（智谱 API，服务端调用）。

key 不再内置在客户端：这里从环境变量 GLM_API_KEY 读取，未设置时用内置
默认值兜底（本地零配置）。生产部署用 `docker run -e GLM_API_KEY=...` 注入，
公网部署前建议把代码里的默认 key 移除。

定位是纯「标注」任务：输入原词行数组，逐行输出中文谐音，
不生成/不修改原词与粤拼。
"""
import json
import os

import httpx

_API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
# 本地零配置兜底；生产请用环境变量 GLM_API_KEY 覆盖。
_DEFAULT_KEY = "783275b708024d14bb4be9e4246004e0.UiDz1BIIZEotVX6W"
_MODEL = "glm-5.3-flash"

_SYSTEM_PROMPT = (
    "你是一个粤语谐音标注助手。用户给你若干行粤语歌词（普通话汉字写法），"
    "你为每一行逐字标注「中文谐音」：\n"
    "1. 该行每个字对应一个谐音汉字，用普通话读出来尽量接近该字在粤语中的发音；\n"
    "2. 谐音字与空格分隔，字数与顺序和原行一致；\n"
    "3. 只输出一个 JSON 对象：{\"lines\": [\"谐 音 1\", \"谐 音 2\", ...]}，"
    "lines 行数与输入一致、顺序一致；\n"
    "4. 不要输出任何解释、markdown 代码块或额外文字。"
)


class GlmError(Exception):
    """GLM 调用失败（网络 / HTTP / 解析）。"""


def _api_key() -> str:
    return os.environ.get("GLM_API_KEY") or _DEFAULT_KEY


def _chat(user_prompt: str) -> str:
    body = {
        "model": _MODEL,
        "temperature": 0.3,
        "stream": False,
        # 该模型始终带思考（reasoning），不支持关闭。用 OpenAI 风格 reasoning_effort
        # 控制思考量：谐音标注是机械任务，用 low 档整首（几十行）约 20s 返回；
        # 不传（默认高思考）会陷入超长思考，实测 >10 分钟仍不返回。
        "reasoning_effort": "low",
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
    }
    # 每次调用新建 client：httpx.Client 非线程安全，避免跨请求复用连接池状态。
    # low 档下整首标注通常 <30s；读超时 300s 作兜底，连接 15s。
    with httpx.Client(timeout=httpx.Timeout(300.0, connect=15.0)) as client:
        r = client.post(
            _API_URL,
            headers={"Authorization": f"Bearer {_api_key()}"},
            json=body,
        )
    if r.status_code // 100 != 2:
        raise GlmError(f"GLM HTTP {r.status_code}：{r.text[:200]}")
    try:
        return r.json()["choices"][0]["message"]["content"]
    except Exception as e:
        raise GlmError(f"无法解析模型响应：{r.text[:200]}") from e


def annotate(lines: list[str]) -> list[str]:
    """为原词行数组逐行标注谐音，返回与输入等长、同序的谐音数组。"""
    numbered = "\n".join(f"{i + 1}. {l}" for i, l in enumerate(lines))
    user_prompt = (
        f"以下是粤语歌词（共 {len(lines)} 行），请逐行逐字标注中文谐音：\n"
        f"{numbered}\n\n只输出 JSON：{{\"lines\": [...]}}"
    )
    return parse_homophones(_chat(user_prompt))


def parse_homophones(content: str) -> list[str]:
    """解析模型返回（容忍代码块包裹、前后多余文字）→ 谐音字符串数组。"""
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
        raise GlmError("模型返回中未找到 JSON，请重试")
    try:
        root = json.loads(text[start : end + 1])
    except Exception as e:
        raise GlmError("模型返回的 JSON 解析失败，请重试") from e
    arr = root.get("lines") or []
    out = [str(x).strip() for x in arr]
    if not out:
        raise GlmError("模型返回的谐音为空，请重试")
    return out
