# 微信小程序版 · 粤语歌词

原生小程序（WXML / WXSS / JS）+ **sql.js**（WASM SQLite 本地库）。

## 导入运行

1. 打开 **微信开发者工具**
2. 「导入项目」→ 目录选择本目录（`wx/`）
3. AppID 选「测试号」即可（project.config.json 已配置 `touristappid`）
4. **工具 → 构建 npm**（生成 `miniprogram_npm/sql.js`，必须）
5. 编译运行

> 开发工具 → 详情 → 本地设置 → 勾选 **「不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书」**，
> 否则无法请求 `http://` 的歌词服务。

## 歌词获取管道

本地搜不到时，点在线结果「导入」，按阶段执行（失败可重试，不写半截数据）：

```
获取原词（apiserver ← 网易云/QQ音乐）→ 生成粤拼（apiserver g2p）→ 生成谐音（GLM-5.3-Flash）→ 存入 SQLite
```

- 原词与粤拼来自 **apiserver**（仓库 `apiserver/`，需先启动，见其 README）
- 谐音由 **GLM-5.3-Flash**（智谱）客户端直连标注，key 内置于 `utils/glm.js`
  （后续迁移到 apiserver 代理）

## 目录结构

```
wx/
├── app.js / app.json / app.wxss / sitemap.json / project.config.json
├── package.json                 # sql.js 依赖（需「构建 npm」）
├── data/seed_lyrics.json        # 内置示范歌曲
├── utils/
│   ├── db.js                    # SQLite 封装（sql.js，载入/防抖落盘/迁移/建表）
│   ├── sql-wasm.wasm            # sql.js WASM（随包，约 658KB）
│   ├── store.js                 # 歌词库 API + 主题/歌词服务设置
│   ├── lyrics-api.js            # apiserver 客户端（搜索/取词/g2p/健康检查）
│   ├── glm.js                   # GLM-5.3-Flash 谐音标注（客户端直连）
│   ├── pipeline.js              # 导入管道（去重→原词→粤拼→谐音→入库）
│   └── themes.js                # 6 套主题预设
└── pages/
    ├── index/                   # 主页（列表/搜索顶栏，主题/切歌/设置底栏，最近弹层）
    ├── search/                  # 搜索页（本地 + 在线结果 + 导入管道进度）
    └── settings/                # 设置页（歌词服务地址 + 粤拼声调开关）
```

## 存储

| 位置 | 内容 |
|------|------|
| `USER_DATA_PATH/lyrics.db` | SQLite 库（songs + lines 表），启动载入内存，防抖 1s 落盘 + onHide 落盘 |
| `cl_current` | 当前歌曲 id |
| `cl_theme_index` | 主题索引 |
| `cl_settings` | `{apiserverUrl, showTone}` |
| `cl_db_migrated` | 旧数据迁移标记 |

旧版 `cl_songs`（JSON 数组）在首次启动时自动导入 SQLite 后删除。

## 访问本机歌词服务

- **开发者工具**：勾选「不校验合法域名」后，地址填 `http://127.0.0.1:8000` 即可
- **真机预览/调试**：手机与电脑同一局域网，地址填电脑局域网 IP
  （如 `http://192.168.1.10:8000`），且服务需 `--host 0.0.0.0` 启动

## 页面说明

- 主页为**自定义导航栏**，顶栏左 📋 列表（最近 20 首，点击直接切换）、右 🔍 搜索；
  底栏 🎨 主题 ｜ ‹ 上一首 ｜ 下一首 › ｜ ⚙️ 设置（切歌为循环切换）
- 歌词区为 `scroll-view`，每句三行；粤拼按设置显示带/不带声调数字
- 主题面板、最近列表均为底部弹层
