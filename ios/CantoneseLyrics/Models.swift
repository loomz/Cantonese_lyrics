import Foundation

/// 一句歌词的三行展示：
/// jyutping  - 第一行：粤拼
/// mandarin  - 第二行：普通话（原歌词汉字）
/// homophone - 第三行：中文谐音
struct LyricLine: Codable, Equatable {
    var jyutping: String
    var mandarin: String
    var homophone: String
}

struct Song: Codable, Equatable, Identifiable {
    var id: String
    var title: String
    var artist: String
    var source: String   // builtin / local / llm
    var createdAt: Date
    var lines: [LyricLine]

    init(id: String, title: String, artist: String, source: String, createdAt: Date, lines: [LyricLine]) {
        self.id = id
        self.title = title
        self.artist = artist
        self.source = source
        self.createdAt = createdAt
        self.lines = lines
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, artist, source, createdAt, lines
    }

    private static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    /// createdAt 兼容两种格式：
    ///  - 数字毫秒时间戳（Android / 小程序 / 种子文件的统一格式）
    ///  - ISO8601 字符串
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? "未命名"
        artist = try c.decodeIfPresent(String.self, forKey: .artist) ?? ""
        source = try c.decodeIfPresent(String.self, forKey: .source) ?? "local"
        if let ms = try? c.decode(Double.self, forKey: .createdAt) {
            createdAt = Date(timeIntervalSince1970: ms / 1000.0)
        } else if let s = try c.decode(String.self, forKey: .createdAt) {
            createdAt = Self.iso8601.date(from: s) ?? Date()
        } else {
            throw DecodingError.keyNotFound(
                .createdAt,
                DecodingError.Context(codingPath: c.codingPath, debugDescription: "缺少 createdAt")
            )
        }
        lines = try c.decode([LyricLine].self, forKey: .lines)
    }

    /// 统一写回数字毫秒，保证三端歌词文件互通
    func encode(to encoder: Encoder) throws {
        var c = try encoder.container(keyedBy: CodingKeys.self)
        try c.encode(id, forKey: .id)
        try c.encode(title, forKey: .title)
        try c.encode(artist, forKey: .artist)
        try c.encode(source, forKey: .source)
        try c.encode(Int(createdAt.timeIntervalSince1970 * 1000), forKey: .createdAt)
        try c.encode(lines, forKey: .lines)
    }
}
