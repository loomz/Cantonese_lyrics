import SwiftUI

/// 主界面：几乎全屏歌词 + 顶部 上一首/搜索/下一首 + 底部 主题/设置
struct ContentView: View {
    @EnvironmentObject var store: LyricsStore
    @EnvironmentObject var settings: AppSettings
    @State private var showSearch = false
    @State private var showSettings = false
    @State private var showThemePicker = false

    var theme: LyricsTheme { allThemes[settings.themeIndex % allThemes.count] }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: 0) {
                topBar
                if let song = store.currentSong {
                    Text("\(song.title)  ·  \(song.artist)")
                        .font(.system(size: 16))
                        .foregroundStyle(theme.jyutping)
                        .lineLimit(1)
                        .padding(.horizontal, 48)
                        .padding(.bottom, 4)
                }
                lyricsList
                bottomBar
            }
        }
        .sheet(isPresented: $showSearch) { SearchView() }
        .sheet(isPresented: $showSettings) { SettingsView() }
        .sheet(isPresented: $showThemePicker) { ThemePickerView() }
    }

    private var topBar: some View {
        HStack {
            Button { store.prev() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 24, weight: .semibold))
            }
            .accessibilityLabel("上一首")
            Spacer()
            Button { showSearch = true } label: {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 20, weight: .semibold))
            }
            .accessibilityLabel("搜索歌词")
            Spacer()
            Button { store.next() } label: {
                Image(systemName: "chevron.right")
                    .font(.system(size: 24, weight: .semibold))
            }
            .accessibilityLabel("下一首")
        }
        .foregroundStyle(theme.mandarin.opacity(0.92))
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    private var lyricsList: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 22) {
                    if let song = store.currentSong {
                        ForEach(Array(song.lines.enumerated()), id: \.offset) { idx, line in
                            LyricLineBlock(line: line, theme: theme)
                                .id(idx)
                        }
                    } else {
                        Text("暂无歌词，点上方 🔍 搜索")
                            .foregroundStyle(theme.jyutping)
                            .padding(.top, 60)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 24)
                .padding(.vertical, 12)
            }
            .onChange(of: store.currentSongId) { _ in
                withAnimation { proxy.scrollTo(0, anchor: .top) }
            }
        }
    }

    private var bottomBar: some View {
        HStack {
            Button { showThemePicker = true } label: {
                Label("主题", systemImage: "paintpalette")
            }
            Spacer()
            Button { showSettings = true } label: {
                Label("设置", systemImage: "gearshape")
            }
        }
        .font(.system(size: 13))
        .foregroundStyle(theme.mandarin.opacity(0.85))
        .padding(.horizontal, 24)
        .padding(.vertical, 8)
        .background(theme.background.opacity(0.96))
    }
}

/// 一句歌词（方案 A）：粤拼单独一行居中；普通话 + 中文谐音两行逐字对齐。
///
/// 普通话与中文谐音都是汉字（等宽），相同字号 + 相同字距 + 居中即可逐字对齐，
/// 无需测量、无需缩放，字号保持大而清晰。粤拼是拉丁字母（变宽），单独居中、字号略小。
struct LyricLineBlock: View {
    let line: LyricLine
    let theme: LyricsTheme

    var body: some View {
        VStack(spacing: 3) {
            if !line.jyutping.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(line.jyutping)
                    .font(.system(size: 13))
                    .tracking(1)
                    .foregroundStyle(theme.jyutping)
                    .multilineTextAlignment(.center)
            }
            if !line.mandarin.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(line.mandarin)
                    .font(.system(size: 20, weight: .bold))
                    .tracking(2)
                    .foregroundStyle(theme.mandarin)
                    .multilineTextAlignment(.center)
            }
            if !line.homophone.trimmingCharacters(in: .whitespaces).isEmpty {
                Text(line.homophone)
                    .font(.system(size: 20))
                    .tracking(2)
                    .foregroundStyle(theme.homophone)
                    .multilineTextAlignment(.center)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

/// 主题选择：预设 6 套模板
struct ThemePickerView: View {
    @EnvironmentObject var settings: AppSettings
    @Environment(\.dismiss) var dismiss

    var body: some View {
        NavigationStack {
            List(allThemes) { t in
                Button {
                    settings.themeIndex = t.id
                    dismiss()
                } label: {
                    HStack(spacing: 12) {
                        RoundedRectangle(cornerRadius: 6)
                            .fill(t.background)
                            .frame(width: 36, height: 36)
                            .overlay(
                                Text("粤")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundStyle(t.mandarin)
                            )
                        VStack(alignment: .leading, spacing: 2) {
                            HStack {
                                Text(t.name).font(.system(size: 15))
                                if t.id == settings.themeIndex {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.blue)
                                }
                            }
                            Text("粤拼 / 正文 / 谐音 三行配色")
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("选择主题模板")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }
}
