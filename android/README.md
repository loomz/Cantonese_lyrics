# Android 版 · 粤语歌词

Kotlin + Jetpack Compose 单模块工程。运行时依赖仅 AndroidX（Compose / Material3 /
Room / coroutines），网络用系统 `HttpURLConnection`，JSON 用系统 `org.json`。

**客户端只负责同步与本地编辑，不做任何生成**：歌词文档（取词→粤拼→GLM 谐音）由
[apiserver](../apiserver/README.md) 生成并缓存，客户端下载存本地文件，可再逐行编辑。

## 环境要求

- Android Studio（Hedgehog 或更新）
- JDK 17（Android Studio 自带；命令行构建需 `JAVA_HOME` 指向 JDK 17）
- Android SDK 34（首次同步自动下载）
- minSdk 24（Android 7.0+）

## 构建运行

```bash
# 方式一：Android Studio
# 直接打开本目录（android/），等待 Gradle 同步完成后 Run

# 方式二：命令行（勿用默认 Java 8，AGP 8.4 会失败）
cd android
JAVA_HOME=/home/loomz/jdk/jdk-17.0.12 ./gradlew assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

首次构建会下载 Gradle 8.7 与依赖（已内置 gradle wrapper，使用阿里云镜像）。

## 目录结构

```
android/
├── app/src/main/
│   ├── assets/lyrics/seed_lyrics.json   # 内置示范歌曲（数组）
│   ├── java/com/loomz/cantonese/lyrics/
│   │   ├── MainActivity.kt          # 入口，页面切换（主/搜索/设置/编辑）
│   │   ├── model/
│   │   │   ├── Song.kt              # Song（含版本管理字段）/ LyricLine 数据模型
│   │   │   └── SongDoc.kt           # 歌曲文档（= 服务端文件 = API 响应）解析/序列化
│   │   ├── data/
│   │   │   ├── LyricsDatabase.kt    # Room：songs 元数据表与 DAO（歌词内容存文件）
│   │   │   ├── SongFiles.kt         # filesDir/lyrics/{id}.json 与 {id}.local.json 读写（原子写）
│   │   │   ├── Migration.kt         # 旧格式文件 → 新格式 + assets seed 导入（幂等）
│   │   │   ├── LyricsRepository.kt  # 歌词库 + 版本管理（切歌/搜索/同步/本地编辑）
│   │   │   ├── LyricsApiClient.kt   # apiserver 客户端（health/搜歌/取文档/查版本/刷新）
│   │   │   ├── Pipeline.kt          # 同步管道：去重 → 下载文档 → 存文件 + 写元数据
│   │   │   └── SettingsStore.kt     # 设置 + 主题（SharedPreferences）
│   │   └── ui/
│   │       ├── Themes.kt            # 7 套歌词主题预设 + 固定 Chrome 配色（非歌词区）
│   │       ├── MainScreen.kt        # 全屏歌词 + 顶栏(列表/搜索/编辑/版本) + 更新横幅 + 底栏
│   │       ├── SearchScreen.kt      # 本地 + 在线搜索 + 同步管道 + 歌曲库（可删除）
│   │       ├── EditScreen.kt        # 逐行编辑四字段（粤拼带调/不带调/普通话/谐音）
│   │       └── SettingsScreen.kt    # 服务地址 / 声调开关 / 测试连接
│   └── AndroidManifest.xml          # INTERNET 权限 + usesCleartextTraffic
└── build.gradle.kts / settings.gradle.kts / gradle wrapper
```

## 存储位置

- 元数据：Room SQLite（`<app databases>/lyrics.db`，仅 `songs` 表：歌名/歌手/来源/
  trackId/服务端版本/本地编辑标志等）
- 歌词内容：`filesDir/lyrics/` 下每首歌两个文件——
  - `{id}.json`：服务端版（**原样存 API 响应文本**，字节保真）
  - `{id}.local.json`：用户编辑版（客户端序列化，同一 schema）
- 设置：SharedPreferences（主题索引、apiserver 地址、声调显示开关）
- 首次启动自动把旧格式 `filesDir/lyrics/*.json`（无 `version` 键）转新格式，并把
  assets seed 导入（幂等）

## 版本管理（每首歌）

状态：`serverVersion`（0=内置/未同步）、`hasLocalEdit`、`preferLocal`。
**活动文件** = `preferLocal && hasLocalEdit` 且 `{id}.local.json` 存在 → 本地文件，
否则服务端文件。主屏右上角「历史」图标打开版本管理弹窗：

| 操作 | 效果 | 可用条件 |
|---|---|---|
| 同步最新 | 下载服务端最新文档覆盖 `{id}.json`（不动 local），更新 serverVersion | 有 trackId |
| 用我的版本 | `preferLocal=true`，查看本地编辑 | 有本地编辑 |
| 用服务端版本 | `preferLocal=false`，查看服务端版 | 有本地编辑 |
| 放弃我的修改 | 删 `{id}.local.json`，清标志（确认弹窗） | 有本地编辑 |
| 服务端重新生成 | 服务端 version+1 后同步到本地 | 有 trackId |

打开歌曲时**异步**查 `/version`（不阻塞切歌）；服务端版本 > 本地 → 主屏出横幅
「服务端有新版本(vN) [同步最新][暂不]」。内置歌（无 trackId）：同步/重新生成禁用，
本地编辑与版本切换仍可用。

## 歌词编辑

主屏右上角「编辑」图标进入编辑器：逐行编辑四字段（粤拼带调 / 粤拼不带调 /
普通话原文 / 中文谐音）。保存写入 `{id}.local.json` 并切到「我的版本」。
v1 只改现有行，不增删行（行集由服务端文档定义）。

## 在线同步

在线搜歌 / 同步歌曲文档都走 [apiserver](../apiserver/README.md)：

```bash
# 电脑上
cd apiserver
uvicorn main:app --host 0.0.0.0 --port 8000
```

App 设置页默认 `http://192.168.3.89:8000`（本机调试可改 `http://127.0.0.1:8000`；
手机真机填电脑局域网 IP，如 `http://192.168.1.10:8000`），点「测试歌词服务连接」验证。

歌词由服务端生成（取词→粤拼→GLM 谐音，key 在服务端），客户端只下载与本地编辑。
首次同步某歌时服务端未命中缓存会现场生成，通常约 20 秒（个别情况更久）；之后所有请求秒取。
