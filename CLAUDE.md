# 谐音歌词 (Cantonese Lyrics)

多端谐音歌词应用：粤语/韩语/日语歌词的注音（粤拼/罗马音）+ 中文谐音生成与管理。

## 项目结构

```
Cantonese_lyrics/
├── android/              # Android 客户端 (Kotlin + Jetpack Compose + Room)
├── apiserver/            # 服务端 (Python FastAPI)
├── admin-element-plus/   # 管理后台 (Element Plus 版本)
├── admin-ant-design/     # 管理后台 (Ant Design Vue 版本)
├── wx/                   # 微信小程序客户端 (暂停开发)
├── ios/                  # iOS 客户端 (暂停开发)
└── shared/               # 共享资源（种子歌词等）
```

## 当前开发状态

### ✅ 进行中：Android + apiserver + admin

**优先级最高**，先完成 Android、apiserver 和 admin 的所有功能，再跟进 iOS 和 wx。

### ⏸️ 暂停：iOS / wx

- wx（微信小程序）和 iOS 客户端已暂停开发
- 保留代码但不再迭代，功能以 Android 为准

---

## 前端双工程一致性要求

管理后台有两个独立工程（`admin-element-plus` 和 `admin-ant-design`），**任何功能改动必须同时修改两个工程**，保持业务逻辑一致。

### 一致性规则

1. **业务逻辑同步**：API 封装、数据处理、事件处理函数必须完全一致
2. **UI 组件差异允许**：`el-*` vs `a-*` 组件名差异是合理的，但行为必须相同
3. **文件结构同步**：新增/删除文件时，两个工程都要同步操作
4. **类型定义同步**：TypeScript 接口定义必须完全一致

### 修改检查清单

- [ ] `src/api/` 下的 API 封装文件是否同步
- [ ] `src/views/` 下的视图文件是否同步
- [ ] `src/router/` 路由配置是否同步
- [ ] `src/components/` 组件是否同步
- [ ] 类型检查是否通过（`npx vue-tsc --noEmit`）

---

## 架构概览

### 1. 服务端 (apiserver)

**技术栈**：Python 3.11+ / FastAPI / canto-hk-g2p / GLM API

**核心功能**：
- 歌词搜索（网易云主 / QQ 备）
- 歌词生成管道：取词 → 语言检测 → 粤拼/罗马音 → GLM 谐音
- 文件缓存：`data/lyrics/{provider}_{trackId}.json`
- 版本管理：`/api/song/{provider}/{id}/version`
- APK 分发：`/api/apk/latest` / `/api/apk/download`

**目录结构**：
```
apiserver/
├── main.py              # FastAPI 入口
├── app/
│   ├── songdoc.py       # 歌词文档生成与缓存
│   ├── lyrics/          # 歌词源（netease/qq）
│   ├── g2p.py           # 粤拼转换
│   ├── glm.py           # GLM 谐音生成
│   └── lang.py          # 语言检测
└── data/lyrics/         # 歌词缓存文件
```

### 3. 管理后台 (admin)

**技术栈**：Vue 3 + Vite + TypeScript + Vue Router 4 + Pinia + Axios

**两个版本**：
- **admin-element-plus**：Element Plus UI 框架，端口 3001
- **admin-ant-design**：Ant Design Vue UI 框架，端口 3002

**核心功能**：
- 歌词管理：列表、详情、重新生成、删除缓存
- 模型管理：配置查看、手动切换、故障转移、状态监控

**目录结构**：
```
admin-element-plus/          # Element Plus 版本
├── src/
│   ├── api/                 # API 封装
│   │   ├── index.ts         # Axios 实例
│   │   ├── lyrics.ts        # 歌词 API
│   │   └── model.ts         # 模型 API
│   ├── components/          # 组件
│   │   └── Layout.vue       # 布局组件
│   ├── router/              # 路由
│   │   └── index.ts         # 路由配置
│   ├── views/               # 页面
│   │   ├── lyrics/          # 歌词管理
│   │   │   ├── List.vue     # 歌词列表
│   │   │   └── Detail.vue   # 歌词详情
│   │   └── model/           # 模型管理
│   │       └── ModelManage.vue
│   ├── App.vue
│   └── main.ts
├── package.json
├── vite.config.ts
└── tsconfig.json

admin-ant-design/            # Ant Design Vue 版本（结构同上）
```

### 2. Android 客户端

**技术栈**：Kotlin / Jetpack Compose / Room (SQLite) / Material 3

**核心功能**：
- 本地数据库（Room）：歌曲元数据 + 歌词内容（serverDoc/localDoc）
- 版本管理：
  - `serverVersion`：服务端版本号
  - `hasLocalEdit`：是否有本地编辑
  - `preferLocal`：是否优先使用本地版本
  - 活动文档 = `preferLocal && hasLocalEdit` → `localDoc`，否则 `serverDoc`
- 在线搜索 + 导入管道
- 版本切换（我的版本 / 服务端版本）
- 应用内升级（APK 下载）

**目录结构**：
```
android/app/src/main/java/com/loomz/cantonese/lyrics/
├── MainActivity.kt      # 入口
├── model/
│   ├── Song.kt          # 歌曲模型
│   └── SongDoc.kt       # 歌词文档模型
├── data/
│   ├── LyricsDatabase.kt    # Room 数据库
│   ├── LyricsRepository.kt  # 数据仓库（单一数据源）
│   ├── LyricsApiClient.kt   # 服务端 API 客户端
│   ├── Pipeline.kt          # 导入管道
│   ├── Migration.kt         # 数据迁移
│   ├── SettingsStore.kt     # 设置存储
│   └── AppUpdater.kt        # 应用内升级
└── ui/
    ├── MainScreen.kt       # 主屏（歌词展示）
    ├── SearchScreen.kt     # 搜索页
    ├── SettingsScreen.kt   # 设置页
    └── Themes.kt           # 主题
```

---

## 数据模型

### 歌词文档 (SongDoc)

```json
{
  "id": "netease_12345",
  "provider": "netease",
  "trackId": "12345",
  "version": 1,
  "generatedAt": 1696156800000,
  "title": "歌曲名",
  "artist": "歌手",
  "language": "yue",
  "lines": [
    {
      "jyutping": "nei5 si6",
      "jyutpingToneless": "nei si",
      "mandarin": "你是",
      "homophone": "你系"
    }
  ]
}
```

### 版本管理状态

| 字段 | 类型 | 说明 |
|------|------|------|
| `serverVersion` | Int | 服务端版本号（0 = 内置） |
| `hasLocalEdit` | Boolean | 是否有本地编辑 |
| `preferLocal` | Boolean | 是否优先使用本地版本 |

**活动文档逻辑**：
```
if (preferLocal && hasLocalEdit && localDoc != null) {
    显示 localDoc（我的版本）
} else {
    显示 serverDoc（服务端版本）
}
```

---

## API 端点

### 歌词相关

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/lyrics/search?q=&limit=` | 在线搜歌 |
| GET | `/api/lyrics/{provider}/{trackId}` | 取歌词（清洗后） |
| GET | `/api/song/{provider}/{trackId}` | 获取完整文档（缓存/生成） |
| POST | `/api/song/{provider}/{trackId}/refresh` | 强制重新生成（version+1） |
| GET | `/api/song/{provider}/{trackId}/version` | 获取版本号 |

### APK 升级

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/apk/latest` | 最新版本信息 |
| GET | `/api/apk/download` | 下载 APK |
| GET | `/apk` | 下载页（HTML） |

### 管理后台 (admin)

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/lyrics/list` | 分页歌词列表 |
| GET | `/api/admin/lyrics/{provider}/{trackId}` | 歌词详情 |
| DELETE | `/api/admin/lyrics/{provider}/{trackId}` | 删除缓存 |
| GET | `/api/admin/model/config` | 获取模型配置 |
| POST | `/api/admin/model/switch` | 切换模型 |
| GET | `/api/admin/model/status` | 模型状态 |
| POST | `/api/admin/model/failover` | 配置故障转移 |

---

## 开发指南

### 服务端

**方式一：使用脚本（推荐）**
```bash
./service.sh start      # 启动（首次自动建 .venv 并安装依赖）
./service.sh stop       # 停止
./service.sh restart    # 重启
./service.sh status     # 查看运行状态
./service.sh logs       # 实时跟踪日志
```

**方式二：手动启动**
```bash
cd apiserver
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

### Android

**编译 APK**（需要 JDK 17）：
```bash
export JAVA_HOME=/home/loomz/jdk/jdk-17.0.12
cd android
./gradlew assembleDebug
```

**部署 APK 到 apiserver 下载目录**：
```bash
cd apiserver
./deploy_apk.sh [更新说明]
```

APK 输出路径：`android/app/build/outputs/apk/debug/app-debug.apk`

### 管理后台 (admin)

```bash
# admin-element-plus (端口 3001)
cd admin-element-plus
npm install
npm run dev

# admin-ant-design (端口 3002)
cd admin-ant-design
npm install
npm run dev
```

**编译构建**：
```bash
# 编译 admin-element-plus
cd admin-element-plus
npm run build

# 编译 admin-ant-design
cd admin-ant-design
npm run build
```

### 环境变量

| 变量 | 说明 |
|------|------|
| `LYRICS_DATA_DIR` | 歌词数据目录（默认 `<apiserver>/data`） |
| `GLM_API_KEY` | GLM API 密钥 |

---

## 待实现功能（Android + apiserver）

### 高优先级

- [ ] 服务端 SQLite 索引（支持歌词内容搜索）
- [ ] 客户端版本历史记录
- [ ] 批量导入/导出

### 中优先级

- [ ] 歌词编辑历史
- [ ] 收藏/标签功能
- [ ] 离线模式优化

### 低优先级

- [ ] 多语言界面
- [ ] 云同步

---

## 模型配置

- 配置文件：`apiserver/data/model_config.json`（改 `active` 字段即可切换默认模型）
- 可选模型：`glm`（智谱）/ `dashscope`（通义千问，默认）/ `longcat`（美团）/ `custom`（自定义，默认本地 llama-swap `http://localhost:8080` + `qwen3.8-27b`）
- 每次模型调用实时读取配置，切换无需重启
- 管理页面（admin `/model`）可切换模型、编辑自定义模型（地址 / 模型 ID / key）

### ⚠️ 本地模型（custom）注意事项

**不要对本地模型（llama-swap）做健康检查 / 生成测试**。本地开发环境本身就在用
llama-swap 跑 qwen3.8-27b 做编码，测试请求会触发 llama-swap 模型切换 / 进程切换，
打断当前开发会话。排查本地模型问题只看日志（`apiserver/logs/uvicorn.log` 及
llama-swap 日志），不主动发请求。

---

## 版本历史

- v1.4：韩日语歌词「谐音/罗马音」主行一键切换
- v1.3：支持韩语/日语歌曲，App 更名「谐音歌词」
- v1.2：应用内升级 + 歌词逐行编辑 + 设置页版本号
- v1.1：基础功能（粤拼/谐音生成）
- v1.0：初始版本
