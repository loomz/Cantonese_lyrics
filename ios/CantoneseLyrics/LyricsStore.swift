import Foundation
import SwiftUI

/// 本地歌词仓库：
/// - 每首歌一个 JSON 文件，存放在 Application Support/CantoneseLyrics/lyrics/
/// - 首次启动把 Bundle 里的示范歌曲拷贝进来
/// - 提供 上一首/下一首、搜索、增删 等能力
final class LyricsStore: ObservableObject {
    @Published var songs: [Song] = []
    @Published var currentSongId: String?

    var currentSong: Song? { songs.first { $0.id == currentSongId } }

    private var dir: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("CantoneseLyrics/lyrics", isDirectory: true)
    }

    // 日期格式由 Song 的自定义 Codable 处理（数字毫秒，三端互通）
    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = .prettyPrinted
        return e
    }()
    private let decoder = JSONDecoder()

    init() {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        copySeed()
        reload()
    }

    /// 种子是 Bundle 里 seed_lyrics.json 的歌曲数组；逐首写成 <id>.json，已存在的跳过（幂等，可随版本新增内置歌）
    private func copySeed() {
        guard let url = Bundle.main.url(forResource: "seed_lyrics", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let seeds = try? decoder.decode([Song].self, from: data) else { return }
        for song in seeds {
            let target = dir.appendingPathComponent("\(song.id).json")
            if !FileManager.default.fileExists(atPath: target.path),
               let out = try? encoder.encode(song) {
                try? out.write(to: target)
            }
        }
    }

    func reload() {
        let files = (try? FileManager.default.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? []
        var list: [Song] = []
        for f in files where f.pathExtension == "json" {
            if let data = try? Data(contentsOf: f),
               let song = try? decoder.decode(Song.self, from: data) {
                list.append(song)
            }
        }
        list.sort {
            if $0.createdAt != $1.createdAt { return $0.createdAt < $1.createdAt }
            return $0.title < $1.title
        }
        songs = list
        if currentSongId == nil || !list.contains(where: { $0.id == currentSongId }) {
            currentSongId = list.first?.id
        }
    }

    private func currentIndex() -> Int {
        songs.firstIndex { $0.id == currentSongId } ?? -1
    }

    func next() { move(1) }
    func prev() { move(-1) }

    private func move(_ delta: Int) {
        guard !songs.isEmpty else { return }
        let i = currentIndex()
        let ni = i < 0 ? 0 : ((i + delta) % songs.count + songs.count) % songs.count
        currentSongId = songs[ni].id
    }

    func open(_ id: String) { currentSongId = id }

    /// 本地搜索：按歌名/歌手模糊匹配
    func search(_ query: String) -> [Song] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return songs }
        return songs.filter {
            $0.title.lowercased().contains(q) || $0.artist.lowercased().contains(q)
        }
    }

    func add(_ song: Song) {
        if let data = try? encoder.encode(song) {
            try? data.write(to: dir.appendingPathComponent("\(song.id).json"))
        }
        reload()
        currentSongId = song.id
    }

    func delete(_ id: String) {
        try? FileManager.default.removeItem(at: dir.appendingPathComponent("\(id).json"))
        reload()
    }
}
