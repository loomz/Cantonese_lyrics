# iOS 版 · 粤语歌词

SwiftUI 单 Target 工程，iOS 16.0+，**零第三方依赖**
（网络用 `URLSession`，JSON 用 `Codable`）。

## 环境要求

- macOS + Xcode 15+
- iOS 16.0 或更新的 iPhone / iPad / 模拟器

## 构建运行

```bash
cd ios
open CantoneseLyrics.xcodeproj
# Xcode 中选择目标设备/模拟器 → Run (⌘R)
```

Bundle ID：`com.loomz.cantonese.lyrics`（如需真机运行请在
Signing & Capabilities 中改为自己的 Team / Bundle ID）。

## 目录结构

```
ios/CantoneseLyrics/
├── CantoneseLyricsApp.swift   # @main 入口，注入 Store / Settings
├── Models.swift               # Song / LyricLine（Codable）
├── Themes.swift               # 6 套主题预设 + Color(hex:)
├── AppSettings.swift          # 主题 + 大模型设置（UserDefaults）
├── LyricsStore.swift          # 歌词库（Application Support/lyrics，种子初始化）
├── LlmService.swift           # OpenAI 兼容 API 客户端（async/await）+ 解析
├── Views/
│   ├── ContentView.swift      # 全屏歌词 + 切歌 + 底部主题/设置
│   ├── SearchView.swift       # 本地搜索 + AI 生成 + 歌词库管理
│   └── SettingsView.swift     # 大模型提供方 / Base URL / Key / 模型
├── Info.plist                 # 含 ATS 例外（允许 http 访问本机服务）
└── Resources/seed_lyrics.json # 内置示范歌曲
```

## 存储位置

- 歌词库：`Application Support/CantoneseLyrics/lyrics/*.json`
  （首次启动从 Bundle 复制种子）
- 设置：UserDefaults

## 访问本机大模型

Info.plist 已开启 `NSAllowsArbitraryLoads`，可直接 `http://` 访问局域网：

- 模拟器：Base URL 直接填 `http://127.0.0.1:11434/v1`
- 真机：填电脑局域网 IP，如 `http://192.168.x.x:11434/v1`，
  且 Ollama 需 `OLLAMA_HOST=0.0.0.0 ollama serve`
