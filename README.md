# 粤语歌词 · 三行对照（Android 为主；iOS / 微信小程序暂停）

一个粤语歌词查看器：几乎全屏展示歌词，**一句歌词三行**——

| 行 | 内容 | 来源 | 示例 |
|----|------|------|------|
| 第一行 | 粤拼（Jyutping） | `canto-hk-g2p` 精确转换 | `nei5 si6 nei5 go3 go3 syut3 nei5 zoeng2 bat1 daai6` |
| 第二行 | 普通话原文 | 网易云 / QQ 音乐**真实歌词** | `你 是 你 个 个 说 你 长 不 大` |
| 第三行 | 中文谐音 | GLM-5.3-Flash 逐行标注 | `内 系 内 锅 锅 虚 内 章 八 带` |

粤拼元数据同时保存**带声调数字**与**不带数字**两版，默认显示不带数字，
设置中可切换（`showTone`）。

## 架构：服务端生成 + 文件缓存 + 客户端同步

歌词文档由 **apiserver** 生成并缓存为文件，**客户端只同步与本地编辑，不做任何生成**：

```
Android 客户端 ──> apiserver (Python/FastAPI)
                    ├── GET  /api/song/{p}/{id}         命中缓存→秒回；未命中→生成+落盘
                    ├── POST /api/song/{p}/{id}/refresh 强制重新生成（version+1）
                    ├── GET  /api/song/{p}/{id}/version 只查版本号（不触发生成）
                    ├── GET  /api/lyrics/search          搜歌（发现用）
                    └── 管道：取词 → canto-hk-g2p → GLM-5.3-Flash（key 在服务端）
                           落盘 data/lyrics/{p}_{id}.json（原子写，per-track 锁去重）
```

- 任一用户请求某歌：服务端命中缓存直接返回；未命中才生成（取真实原词→粤拼→GLM 谐音）
  并落盘，之后所有用户秒取。**首次生成通常约 20 秒**（个别情况更久）。
- **GLM key 在服务端**（环境变量 `GLM_API_KEY`，代码内置默认值兜底本地零配置），
  客户端不再内置 key。

## 歌曲文档（两端共同的数据契约）

服务端文件 = API 响应 = 客户端本地 `{id}.json`，同一 schema：

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

- `id = "{provider}_{trackId}"`（沿用客户端 id 规则）；`version` 整数（首次生成=1，
  服务端刷新 +1）；`generatedAt` 毫秒。

## 客户端版本管理（Android）

每首歌三个状态：`serverVersion`（0=内置/未同步）、`hasLocalEdit`、`preferLocal`。
**活动文件** = `preferLocal && hasLocalEdit` 且本地文件存在 → 用户编辑版，否则服务端版。

| 操作 | 效果 |
|---|---|
| 同步最新 | 下载服务端最新文档覆盖本地服务端版（**不动**用户编辑） |
| 用我的版本 / 用服务端版本 | 切换查看本地编辑 / 服务端版 |
| 放弃我的修改 | 删本地编辑文件 |
| 保存本地编辑 | 逐行编辑四字段后写入本地文件 |
| 服务端重新生成 | 服务端 version+1 后同步到本地 |

打开歌曲时**异步**查 `/version`（不阻塞切歌）；服务端版本更新 → 主屏出
「服务端有新版本 [同步最新][暂不]」横幅。内置歌（无 trackId）：同步/重新生成禁用，
本地编辑与版本切换仍可用。

## 目录结构

```
Cantonese_lyrics/
├── apiserver/  # 歌词服务（Python/FastAPI）：生成+缓存歌曲文档 + 搜歌 + legacy 端点
├── android/    # Android 版（Kotlin + Jetpack Compose + Room）—— 新架构（同步+本地编辑）
├── ios/        # iOS 版（SwiftUI）—— 暂停，仍用旧架构（依赖 legacy 端点）
├── wx/         # 微信小程序版（原生 + sql.js）—— 暂停，仍用旧架构（依赖 legacy 端点）
└── shared/
    └── seed_lyrics.json   # 内置示范歌曲（android 亦有 assets 副本）
```

> **iOS / 微信小程序暂停**：仍停在旧架构（客户端直连 GLM、按行存 DB），继续依赖
> apiserver 保留的 legacy 端点（`POST /api/g2p`、`GET /api/lyrics/{p}/{id}`）。
> 本次只重构 **apiserver + Android**。

## 客户端存储（Android，新架构）

- **元数据**：Room `songs` 表（歌名/歌手/来源/trackId/服务端版本/本地编辑标志）
- **歌词内容**：`filesDir/lyrics/{id}.json`（服务端版，**原样存 API 响应文本**）+
  `{id}.local.json`（用户编辑版，客户端序列化，同一 schema）
- 首次启动自动把旧格式文件转新格式并导入内置 seed（幂等）

> 暂停的 iOS/wx 仍用旧的 `songs` + `lines` 两表 schema（按行存 DB），与 Android 新架构不同。

## 功能清单（Android）

- **全屏歌词**：每句三行（粤拼 / 普通话 / 中文谐音），纵向滚动浏览
- **顶栏**：左 📋 最近列表（最近打开的 20 首）/ 右 🔍 搜索 ｜ ✏️ 编辑 ｜ 🕘 版本管理
- **底栏**：🎨 主题 ｜ ‹ 上一首 ｜ 下一首 › ｜ ⚙️ 设置（切歌循环切换，更新 lastOpenedAt）
- **更新横幅**：服务端版本更新时提示「同步最新 / 暂不」
- **歌词编辑**：逐行编辑四字段（粤拼带调/不带调/普通话/谐音），保存为「我的版本」
- **版本管理**：同步最新 / 用我的 / 用服务端 / 放弃修改 / 服务端重新生成
- **搜索页**：本地 + 在线歌曲（网易云/QQ）+ 同步进度 + 本地歌曲库（可删除）
- **主题模板**（7 套，自动记忆）：经典黑 / 深夜蓝 / 日落橘 / 森林绿 / 樱花粉 / 少女粉 / 复古米
  - 主题**只改歌词显示区**（背景色 + 三行文字颜色）；顶/底栏、弹窗、搜索 / 设置 / 编辑等
    所有非歌词界面用一套固定深色配色（`Chrome`），不随主题变化
  - 主题选择弹窗里每套模板直接渲染「我吻你吻上太空」三行样例，直观看出效果
- **设置**：歌词服务地址（可测试连接）+ 粤拼声调数字开关

## 各端构建说明

- apiserver：见 [apiserver/README.md](apiserver/README.md)（本地运行 + 云部署）
- Android：见 [android/README.md](android/README.md)
- 微信小程序：见 [wx/README.md](wx/README.md)（暂停，旧架构）
- iOS：见 [ios/README.md](ios/README.md)（暂停，旧架构）

## 已知限制

- **首次同步某歌通常约 20 秒**：服务端未命中缓存时现场生成（取词→粤拼→GLM），之后秒取
- 在线歌词依赖网易云/QQ 音乐公开接口，接口变更时需更新 apiserver
- QQ 音乐接口在部分网络（地域限制）不可用，此时自动仅用网易云
- 谐音由大模型标注，个别字可能不准，请以粤拼为准
- 歌词库为本地存储，客户端之间不自动同步（服务端文档缓存跨用户共享）
