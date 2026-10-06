"""模型管理器：云端模型（glm / dashscope / longcat）+ 自定义模型切换。

配置文件：apiserver/data/model_config.json（持久化，改 active 字段即切换默认模型）

内置云端模型：
  - glm       智谱 glm-5.3-flash
  - dashscope 通义千问 qwen3.8-max-0902（默认）
  - longcat   美团 LongCat-2.5-Preview

自定义模型：默认本地 llama-swap (http://localhost:8080) + qwen3.8-27b，
可在管理页面修改地址 / 模型 ID / key 并保存。

每个云端模型可覆盖其 model ID（例如 dashscope → qwen-turbo），
通过配置文件的 custom_models.{name}.model 字段实现。

API key 解析优先级：环境变量 > 内置默认 key（本地零配置兜底）。
"""
import json
import os
import time
from pathlib import Path

# ── 数据目录 & 配置文件路径 ────────────────────────────────
_DATA_DIR = Path(os.environ.get(
    "LYRICS_DATA_DIR", Path(__file__).resolve().parent.parent / "data"
))
CONFIG_PATH = _DATA_DIR / "model_config.json"

# ── 内置云端模型 ──────────────────────────────────────────
CLOUD_MODELS = {
    "glm": {
        "label": "GLM (智谱)",
        "api_url": "https://open.bigmodel.cn/api/paas/v4/chat/completions",
        "model": "glm-5.3-flash",
        "key_env": "GLM_API_KEY",
        "default_key": "783275b708024d14bb4be9e4246004e0.UiDz1BIIZEotVX6W",
        # glm-5.3-flash 强制思考模式，机械标注任务用 low 档（否则 >10 分钟不返回）
        "extra": {"reasoning_effort": "low"},
    },
    "dashscope": {
        "label": "DashScope (通义千问)",
        "api_url": "https://ws-4t86k06rkxtyytzk.cn-beijing.maas.aliyuncs.com/compatible-mode/v1/chat/completions",
        "model": "qwen3.7-flash",
        "key_env": "DASHSCOPE_API_KEY",
        "default_key": "sk-a0630fdb1ea848dabeae37ee2e87245c",
        "extra": {},
    },
    "longcat": {
        "label": "LongCat (美团)",
        "api_url": "https://api.longcat.chat/openai/v1/chat/completions",
        "model": "LongCat-2.5-Preview",
        "key_env": "LONGCAT_API_KEY",
        "default_key": "ak_2j06Q54We8Ll8qW0CL11c9G15lM3V",
        "extra": {},
    },
}

# 自定义模型默认值：本地 llama-swap + qwen3.8-27b
DEFAULT_CUSTOM = {
    "label": "自定义模型",
    "api_url": "http://localhost:8080/v1/chat/completions",
    "model": "qwen3.8-27b",
    "api_key": "local",
}

DEFAULT_CONFIG = {
    "active": "longcat",
    "custom": dict(DEFAULT_CUSTOM),
}

# ── 工具函数 ──────────────────────────────────────────────


def _mask_key(key: str) -> str:
    """API key 脱敏（只展示给前端，避免泄露完整 key）。"""
    if not key:
        return ""
    if len(key) <= 10:
        return key[:3] + "****"
    return key[:6] + "****" + key[-4:]


# ── 配置文件辅助读写（custom_models 子段） ──────────────────

_MODEL_OVERRIDES_PATH = CONFIG_PATH.with_suffix(".overrides.json")


def _read_model_overrides() -> dict:
    """读取 custom_models 子段（形如 {"dashscope": {"model": "xxx"}}）。"""
    if not _MODEL_OVERRIDES_PATH.exists():
        return {}
    try:
        return json.loads(_MODEL_OVERRIDES_PATH.read_text(encoding="utf-8"))
    except Exception:
        return {}


def _write_model_overrides(overrides: dict) -> bool:
    """原子写入 custom_models 子段。"""
    try:
        tmp = _MODEL_OVERRIDES_PATH.with_suffix(".tmp")
        tmp.write_text(json.dumps(overrides, indent=2, ensure_ascii=False), encoding="utf-8")
        tmp.replace(_MODEL_OVERRIDES_PATH)
        return True
    except Exception:
        return False


# ══════════════════════════════════════════════════════════
#              ModelManager 主类
# ══════════════════════════════════════════════════════════

class ModelManager:
    """模型管理器：读写配置、切换模型、自定义模型配置、云端模型 ID 覆盖。"""

    def __init__(self):
        self.config_path = CONFIG_PATH

    # ── 配置读写 ──────────────────────────────────────────

    def _load(self) -> dict:
        """从文件加载配置（不存在时用默认值）。"""
        if not self.config_path.exists():
            return json.loads(json.dumps(DEFAULT_CONFIG))
        try:
            cfg = json.loads(self.config_path.read_text(encoding="utf-8"))
            merged = json.loads(json.dumps(DEFAULT_CONFIG))
            if isinstance(cfg.get("custom"), dict):
                merged["custom"].update(cfg["custom"])
            if cfg.get("active") in self.model_names():
                merged["active"] = cfg["active"]
            return merged
        except Exception:
            return json.loads(json.dumps(DEFAULT_CONFIG))

    def _save(self, cfg: dict) -> bool:
        """原子写入配置文件。"""
        try:
            self.config_path.parent.mkdir(parents=True, exist_ok=True)
            tmp = self.config_path.with_suffix(".tmp")
            tmp.write_text(
                json.dumps(cfg, indent=2, ensure_ascii=False), encoding="utf-8"
            )
            tmp.replace(self.config_path)
            return True
        except Exception:
            return False

    def model_names(self) -> list[str]:
        """所有可选模型名（云端 + 自定义）。"""
        return list(CLOUD_MODELS.keys()) + ["custom"]

    # ── 当前活动模型（供 glm.py 调用）────────────────────

    def get_active_config(self) -> dict:
        """解析当前活动模型的完整调用配置。

        Returns:
            {name, label, api_url, model, api_key, extra}
            api_key 为明文（仅服务端内部使用，不下发前端）。
            model 优先使用 custom_models 中的覆盖值。
        """
        cfg = self._load()
        name = cfg["active"]
        if name == "custom":
            c = dict(cfg["custom"])
            return {
                "name": "custom",
                "label": c.get("label") or DEFAULT_CUSTOM["label"],
                "api_url": c.get("api_url") or DEFAULT_CUSTOM["api_url"],
                "model": c.get("model") or DEFAULT_CUSTOM["model"],
                "api_key": c.get("api_key") or "",
                "extra": {},
            }
        m = CLOUD_MODELS.get(name) or CLOUD_MODELS["dashscope"]
        # 检查是否有覆盖的 model ID
        overrides = _read_model_overrides()
        override = overrides.get(name, {})
        effective_model = override.get("model") or m["model"]
        return {
            "name": name,
            "label": m["label"],
            "api_url": m["api_url"],
            "model": effective_model,
            "api_key": os.environ.get(m["key_env"]) or m["default_key"],
            "extra": dict(m["extra"]),
        }

    def set_active(self, name: str) -> bool:
        """切换活动模型（glm / dashscope / longcat / custom）。"""
        if name not in self.model_names():
            return False
        cfg = self._load()
        cfg["active"] = name
        return self._save(cfg)

    # ── 云端模型 ID 覆盖 ────────────────────────────────

    def get_model_override(self, name: str) -> dict | None:
        """获取指定云端模型的覆盖配置（model ID 等）。"""
        if name not in CLOUD_MODELS:
            return None
        overrides = _read_model_overrides()
        ovr = overrides.get(name, {})
        return {
            "model": ovr.get("model"),
            "api_key": ovr.get("api_key"),
            "label": ovr.get("label"),
        }

    def set_model_override(self, name: str, model: str,
                           api_key: str | None = None,
                           label: str | None = None) -> bool:
        """保存云端模型的覆盖配置（model ID / key / label）。

        传空字符串或 None 表示保持原值不变。
        """
        if name not in CLOUD_MODELS:
            return False
        overrides = _read_model_overrides()
        entry = overrides.setdefault(name, {})
        if model is not None and model.strip():
            entry["model"] = model.strip()
        else:
            entry.pop("model", None)
        if api_key is not None and api_key.strip() and "****" not in api_key:
            entry["api_key"] = api_key.strip()
        else:
            if "api_key" in entry:
                entry.pop("api_key", None)
        if label is not None:
            entry["label"] = label.strip()
        elif "label" in entry and not label:
            entry.pop("label", None)
        return _write_model_overrides(overrides)

    # ── 自定义模型配置 ────────────────────────────────────

    def get_custom(self) -> dict:
        """获取自定义模型配置（key 脱敏）。"""
        cfg = self._load()
        c = dict(cfg["custom"])
        c["api_key_masked"] = _mask_key(c.get("api_key", ""))
        return c

    def set_custom(self, api_url: str, model: str,
                   api_key: str | None = None, label: str | None = None) -> bool:
        """保存自定义模型配置。

        api_key 传 None 或空表示保留原值（前端脱敏后不回传完整 key）。
        """
        if not api_url or not model:
            return False
        cfg = self._load()
        cfg["custom"]["api_url"] = api_url.strip()
        cfg["custom"]["model"] = model.strip()
        if api_key is not None and api_key.strip() and "****" not in api_key:
            cfg["custom"]["api_key"] = api_key.strip()
        if label is not None:
            cfg["custom"]["label"] = label.strip() or DEFAULT_CUSTOM["label"]
        return self._save(cfg)

    # ── 页面展示 ──────────────────────────────────────────

    def list_models(self) -> list[dict]:
        """列出所有可选模型（供管理页面下拉框，key 脱敏）。

        model 字段：优先显示已保存的覆盖值，否则显示预设值。
        """
        cfg = self._load()
        overrides = _read_model_overrides()
        result = []
        for name, m in CLOUD_MODELS.items():
            key = os.environ.get(m["key_env"]) or m["default_key"]
            ovr = overrides.get(name, {})
            effective_model = ovr.get("model") or m["model"]
            effective_label = ovr.get("label") or m["label"]
            effective_key = ovr.get("api_key") or key
            result.append({
                "name": name,
                "label": effective_label,
                "api_url": m["api_url"],
                "model": effective_model,
                "api_key_masked": _mask_key(effective_key),
                "preset_model": m["model"],  # 原始预设值
                "active": cfg["active"] == name,
            })
        c = cfg["custom"]
        result.append({
            "name": "custom",
            "label": c.get("label") or DEFAULT_CUSTOM["label"],
            "api_url": c.get("api_url") or DEFAULT_CUSTOM["api_url"],
            "model": c.get("model") or DEFAULT_CUSTOM["model"],
            "api_key_masked": _mask_key(c.get("api_key", "")),
            "active": cfg["active"] == "custom",
        })
        return result

    def check_health(self, name: str | None = None) -> dict:
        """健康检查：向目标模型发一个最小 chat 请求。"""
        import httpx

        if name is None:
            active = self.get_active_config()
        elif name == "custom":
            cfg = self._load()
            c = cfg["custom"]
            active = {
                "name": "custom",
                "api_url": c.get("api_url"),
                "model": c.get("model"),
                "api_key": c.get("api_key", ""),
                "extra": {},
            }
        elif name not in CLOUD_MODELS:
            return {"healthy": False, "error": "模型不存在"}
        else:
            m = CLOUD_MODELS[name]
            # 优先使用覆盖的 key/model
            overrides = _read_model_overrides()
            ovr = overrides.get(name, {})
            effective_model = ovr.get("model") or m["model"]
            effective_key = ovr.get("api_key") or os.environ.get(m["key_env"]) or m["default_key"]
            active = {
                "name": name,
                "api_url": m["api_url"],
                "model": effective_model,
                "api_key": effective_key,
                "extra": dict(m["extra"]),
            }

        try:
            body = {
                "model": active["model"],
                "temperature": 0,
                "stream": False,
                "max_tokens": 8,
                "messages": [{"role": "user", "content": "hi"}],
            }
            body.update(active.get("extra") or {})
            headers = {}
            if active.get("api_key"):
                headers["Authorization"] = f"Bearer {active['api_key']}"
            with httpx.Client(timeout=httpx.Timeout(30.0, connect=10.0)) as client:
                r = client.post(active["api_url"], headers=headers, json=body)
            ok = r.status_code // 100 == 2
            return {
                "healthy": ok,
                "name": active["name"],
                "model": active["model"],
                "status_code": r.status_code,
                "error": None if ok else r.text[:200],
                "last_check": int(time.time()),
            }
        except Exception as e:
            return {
                "healthy": False,
                "name": active["name"],
                "model": active.get("model"),
                "error": str(e)[:200],
                "last_check": int(time.time()),
            }


# 全局实例
model_manager = ModelManager()
