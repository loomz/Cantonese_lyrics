import SwiftUI

/// 搜索页：
/// 1. 优先本地搜索（歌名/歌手模糊匹配）
/// 2. 本地没有 → 调用大模型（本地 Qwen 或免费 API）生成并保存到本地
/// 3. 底部展示本地歌曲库，可删除
struct SearchView: View {
    @EnvironmentObject var store: LyricsStore
    @EnvironmentObject var settings: AppSettings
    @Environment(\.dismiss) var dismiss

    @State private var query = ""
    @State private var results: [Song]?
    @State private var generating = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                HStack {
                    TextField("歌名 或 歌手", text: $query)
                        .textFieldStyle(.roundedBorder)
                        .autocorrectionDisabled()
                        .onSubmit { doSearch() }
                    Button("搜索") { doSearch() }
                        .buttonStyle(.borderedProminent)
                        .disabled(query.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                .padding(.horizontal)

                if generating {
                    VStack(spacing: 10) {
                        ProgressView()
                        Text("正在用 AI 生成歌词，可能需要 1-3 分钟…")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.top, 24)
                }

                if let error {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .padding(.horizontal)
                }

                if let results, !generating {
                    if results.isEmpty {
                        VStack(spacing: 14) {
                            Text("本地没有找到《\(query.trimmingCharacters(in: .whitespaces))》")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                            Button {
                                generate()
                            } label: {
                                Label("用 AI 搜索并生成歌词", systemImage: "sparkles")
                            }
                            .buttonStyle(.borderedProminent)
                        }
                        .padding(.top, 20)
                    } else {
                        List(results) { song in
                            Button {
                                store.open(song.id)
                                dismiss()
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(song.title).font(.headline)
                                    Text("\(song.artist) · \(song.lines.count) 行 · \(sourceText(song.source))")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                        }
                        .listStyle(.plain)
                    }
                }

                // 本地歌曲库
                VStack(alignment: .leading, spacing: 4) {
                    Text("本地歌曲库（\(store.songs.count)）")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                    List {
                        ForEach(store.songs) { song in
                            HStack {
                                Button {
                                    store.open(song.id)
                                    dismiss()
                                } label: {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(song.title).font(.subheadline)
                                        Text("\(song.artist) · \(sourceText(song.source))")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }
                                Spacer()
                                Button(role: .destructive) {
                                    store.delete(song.id)
                                } label: {
                                    Image(systemName: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                }
                .padding(.top, 8)
            }
            .padding(.top, 8)
            .navigationTitle("搜索歌词")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }

    private func sourceText(_ s: String) -> String {
        switch s {
        case "builtin": return "内置"
        case "llm": return "AI 生成"
        default: return "本地"
        }
    }

    private func doSearch() {
        error = nil
        results = store.search(query)
    }

    private func generate() {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return }
        error = nil
        generating = true
        Task {
            do {
                let song = try await LlmService.generateLyrics(settings: settings.llm, query: q)
                store.add(song)
                generating = false
                dismiss() // 生成成功直接展示
            } catch {
                generating = false
                self.error = error.localizedDescription
            }
        }
    }
}
