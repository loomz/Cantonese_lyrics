# apiserver（Python / FastAPI）

歌词服务的后端。核心职责：把一首歌生成成**一个 JSON 文档**（粤拼 + 普通话原词 +
中文谐音），**缓存为文件**，之后所有客户端秒取。GLM 谐音标注也在这里完成
（key 不再内置在客户端）。

> 当前只服务 **Android** 端。**iOS / 微信小程序已暂停**，仍走下面的 legacy 端点，
> 故 legacy 端点保留不删。

## 端点

| 端点 | 说明 |
|------|------|
| `GET /health` | 健康检查，设置页「测试连接」用 |
| `GET /api/lyrics/search?q=红豆&limit=20` | 在线搜歌，网易云为主、QQ 音乐回退（发现用） |
| `GET /api/song/{provider}/{trackId}` | **取歌曲文档**。命中缓存→直接返回文件（响应头 `X-From-Cache:1`）；未命中→生成（取词→粤拼→GLM 谐音）并落盘后返回（`X-From-Cache:0`）。首次通常约 20 秒（个别情况更久） |
| `POST /api/song/{provider}/{trackId}/refresh` | 强制重新生成（服务端刷新），`version = 旧 + 1`（无旧文件则 1） |
| `GET /api/song/{provider}/{trackId}/version` | 只查缓存版本号 `{"version":N}`，**不触发生成**（供客户端轻量查更新）。未缓存返回 404 |
| `POST /api/g2p`（legacy） | 粤拼转换（canto-hk-g2p），返回带/不带声调数字两版 |
| `GET /api/lyrics/{provider}/{trackId}`（legacy） | 取歌词并清洗（去时间戳/元信息行） |

错误：未知歌词源 / 该歌无歌词 → `404`；上游取词或 GLM 失败 → `502 {"detail":...}`。

## 歌曲文档 schema

服务端文件 = API 响应 = 客户端本地 `{id}.json`，三者同一结构：

```json
{
  "id": "netease_299936", "provider": "netease", "trackId": "299936",
  "version": 1, "generatedAt": 1727450000000,
  "title": "红豆", "artist": "王菲",
  "lines": [
    {"jyutping":"wan4 min4 so1 ji6","jyutpingToneless":"wan min so ji",
     "mandarin":"晚 风 似 记","homophone":"满 风 四 记"}
  ]
}
```

- `id = "{provider}_{trackId}"`；`version` 整数（首次生成=1，`/refresh` 后 +1）；
  `generatedAt` 毫秒时间戳。
- 缓存文件：`data/lyrics/{provider}_{trackId}.json`。

## 文件缓存与并发

- 命中即返回，未命中才生成并**原子落盘**（tmp + fsync + rename，不会读到半截文件）。
- **per-track 锁**跨 取词+g2p+GLM 全程持有：同一未缓存歌的并发请求去重
  （第二个等文件，不重复计费 GLM）；不同歌并行生成。

## 环境变量

| 变量 | 说明 |
|------|------|
| `GLM_API_KEY` | 智谱 GLM key。不设置则用代码内置默认值（仅本地零配置兜底）；生产部署务必用 `docker run -e` 注入，公网部署前建议从源码移除默认值 |
| `LYRICS_DATA_DIR` | 文档缓存目录，默认 `<apiserver>/data`（CWD 无关） |

本服务不自动读 `.env`，需在 shell 或 docker 里注入（见 `.env.example`）。

## 本地运行

```bash
cd apiserver
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
```

验证：

```bash
curl http://127.0.0.1:8000/health
curl "http://127.0.0.1:8000/api/lyrics/search?q=红豆"

# 首次生成（约 20 秒），X-From-Cache:0，落盘 data/lyrics/netease_299936.json
curl -si "http://127.0.0.1:8000/api/song/netease/299936" | grep -iE "HTTP|x-from-cache"
# 二次秒回，X-From-Cache:1
curl -si "http://127.0.0.1:8000/api/song/netease/299936" | grep -i x-from-cache
# 查版本号（不触发生成）
curl "http://127.0.0.1:8000/api/song/netease/299936/version"
# 强制重新生成（version +1）
curl -X POST "http://127.0.0.1:8000/api/song/netease/299936/refresh"
# 未知源 → 404
curl -si "http://127.0.0.1:8000/api/song/badprovider/1" | head -3
```

## 手机访问

客户端「设置 → 歌词服务地址」默认 `http://192.168.3.89:8000`。
手机不在电脑上，要填**电脑的局域网 IP**，如 `http://192.168.1.10:8000`，
且服务需监听 `0.0.0.0`（上面的启动命令已经如此）。

## 云部署（Docker）

```bash
docker build -t cantonese-apiserver apiserver/
docker run -d --name cantonese-apiserver -p 8000:8000 \
  -v cantonese-lyrics-data:/app/data \
  -e GLM_API_KEY=你的key \
  cantonese-apiserver
```

客户端把「歌词服务地址」填成 `https://<你的域名或IP>` 即可。
（当前服务无鉴权，公网部署前请先加一层反向代理鉴权。）

## 歌词源说明

- **网易云**（主）：`music.163.com` 公开接口，无需鉴权，只需浏览器 UA。
  注意：歌词接口必须带 `tv=1&lv=1&kv=1`，全 0 参数会返回空歌词。
- **QQ 音乐**（备）：`u.y.qq.com/musicu.fcg` 在部分网络（地域限制）返回 500003，
  因此只在网易云失败或无结果时回退使用。
- 新增数据源：实现 `app/lyrics/base.py` 的 `LyricsProvider`，
  在 `app/lyrics/__init__.py` 的 `_PROVIDERS` 里按优先级追加即可。
