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

## 快速启动

### 1. 服务端 (apiserver)

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

**环境变量**（可选，放在 `apiserver/.env`）：
- `LYRICS_DATA_DIR`：歌词数据目录（默认 `<apiserver>/data`）
- `GLM_API_KEY`：GLM API 密钥

### 2. Android 客户端

```bash
cd android
./gradlew assembleDebug
```

### 3. 管理后台 (admin)

#### admin-element-plus (Element Plus 版本，端口 3001)

```bash
cd admin-element-plus
npm install
npm run dev
```

#### admin-ant-design (Ant Design Vue 版本，端口 3002)

```bash
cd admin-ant-design
npm install
npm run dev
```

## 编译构建

### 服务端

```bash
cd apiserver
pip install -r requirements.txt
```

### Android

```bash
cd android
./gradlew assembleDebug
```

### 管理后台

```bash
# 编译 admin-element-plus
cd admin-element-plus
npm run build

# 编译 admin-ant-design
cd admin-ant-design
npm run build
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 服务端 | Python 3.11+ / FastAPI / canto-hk-g2p / GLM API |
| Android | Kotlin / Jetpack Compose / Room (SQLite) / Material 3 |
| 管理后台 | Vue 3 + Vite + TypeScript + Vue Router 4 + Pinia + Axios |
| UI 框架 | Element Plus / Ant Design Vue |

## 功能清单

### 服务端 (apiserver)

- 歌词搜索（网易云主 / QQ 备）
- 歌词生成管道：取词 → 语言检测 → 粤拼/罗马音 → GLM 谐音
- 文件缓存：`data/lyrics/{provider}_{trackId}.json`
- 版本管理：`/api/song/{provider}/{id}/version`
- APK 分发：`/api/apk/latest` / `/api/apk/download`
- 管理后台 API：歌词管理、模型管理

### Android 客户端

- 本地数据库（Room）：歌曲元数据 + 歌词内容
- 版本管理：serverVersion / hasLocalEdit / preferLocal
- 在线搜索 + 导入管道
- 版本切换（我的版本 / 服务端版本）
- 应用内升级（APK 下载）

### 管理后台 (admin)

- **歌词管理**：列表、详情、重新生成、删除缓存
- **模型管理**：配置查看、手动切换、故障转移、状态监控

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

## 开发状态

### ✅ 进行中：Android + apiserver + admin

**优先级最高**，先完成 Android、apiserver 和 admin 的所有功能，再跟进 iOS 和 wx。

### ⏸️ 暂停：iOS / wx

- wx（微信小程序）和 iOS 客户端已暂停开发
- 保留代码但不再迭代，功能以 Android 为准

## 版本历史

- v1.4：韩日语歌词「谐音/罗马音」主行一键切换
- v1.3：支持韩语/日语歌曲，App 更名「谐音歌词」
- v1.2：应用内升级 + 歌词逐行编辑 + 设置页版本号
- v1.1：基础功能（粤拼/谐音生成）
- v1.0：初始版本
