import Foundation

enum LlmError: LocalizedError {
    case badUrl
    case http(Int, String)
    case noJson
    case empty

    var errorDescription: String? {
        switch self {
        case .badUrl: return "Base URL 无效"
        case .http(let code, let body): return "HTTP \(code)：\(body)"
        case .noJson: return "模型返回中未找到 JSON，请重试或更换模型"
        case .empty: return "模型返回的歌词为空，请重试"
        }
    }
}

/// 大模型歌词生成服务（OpenAI 兼容协议）。
/// 同时支持：
///  - 本地 Qwen（Ollama: http://127.0.0.1:11434/v1，vLLM: http://127.0.0.1:8000/v1）
///  - 任意免费/付费的 OpenAI 兼容 API（填 Base URL + API Key + 模型名即可）
enum LlmService {

    static let systemPrompt = """
    你是一个粤语歌词整理助手，精通粤拼（Jyutping）注音和普通话谐音转写。
    你只输出一个合法的 JSON 对象，不输出任何解释、前后缀或 markdown 代码块。
    """

    static func userPrompt(query: String) -> String {
        """
        请整理歌曲《\(query)》的完整粤语歌词（如果《\(query)》中包含歌手信息，请一并参考）。

        只输出如下结构的 JSON 对象：
        {
          "title": "歌曲名",
          "artist": "歌手名（不确定就填 未知）",
          "lines": [
            {
              "jyutping": "这一句每个字对应的粤拼，空格分隔，如：nei5 si6 nei5",
              "mandarin": "这一句歌词原本的汉字，每个字用空格分隔",
              "homophone": "这一句每个字对应的普通话谐音汉字，空格分隔；实在找不到合适汉字时可用拼音或英文字母，如 wing、fai"
            }
          ]
        }

        规则：
        1. lines 按原歌词顺序排列，一行歌词一个对象；只输出歌词正文，不要前奏、间奏、结尾等标记。
        2. jyutping 使用标准粤拼，声调是 1-6 的数字，每个字一个音节，与 mandarin 字数一一对应。
        3. mandarin 使用歌词原本的汉字写法。
        4. homophone 中每个字的普通话读音要尽量接近该字在粤语中的读音。
        """
    }

    /// 调用模型生成歌词，返回可直接保存的 Song
    static func generateLyrics(settings: LlmSettings, query: String) async throws -> Song {
        let content = try await chat(settings: settings, prompt: userPrompt(query: query))
        return try parseSongJson(content: content)
    }

    /// 测试连接
    static func testConnection(settings: LlmSettings) async throws -> String {
        try await chat(settings: settings, prompt: "请用不超过十个字回答：1+1 等于几？")
    }

    private static func chat(settings: LlmSettings, prompt: String) async throws -> String {
        let base = settings.baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !base.isEmpty, let url = URL(string: base + "/chat/completions") else {
            throw LlmError.badUrl
        }
        var req = URLRequest(url: url, timeoutInterval: 300)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if !settings.apiKey.isEmpty {
            req.setValue("Bearer \(settings.apiKey)", forHTTPHeaderField: "Authorization")
        }
        let body: [String: Any] = [
            "model": settings.model.isEmpty ? "qwen3.8-27b" : settings.model,
            "temperature": 0.3,
            "stream": false,
            "messages": [
                ["role": "system", "content": systemPrompt],
                ["role": "user", "content": prompt]
            ]
        ]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw LlmError.http(-1, "无响应")
        }
        guard (200..<300).contains(http.statusCode) else {
            let text = String(data: data, encoding: .utf8)?.prefix(200).description ?? ""
            throw LlmError.http(http.statusCode, text)
        }
        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let choices = json["choices"] as? [[String: Any]],
            let message = choices.first?["message"] as? [String: Any],
            let content = message["content"] as? String
        else {
            throw LlmError.noJson
        }
        return content
    }

    /// 解析模型返回的 JSON（容忍代码块包裹、前后多余文字）
    static func parseSongJson(content: String) throws -> Song {
        var text = content.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.hasPrefix("```") {
            text = text.dropFirst(3).trimmingCharacters(in: .whitespacesAndNewlines)
            if text.hasPrefix("json") {
                text = text.dropFirst(4).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if text.hasSuffix("```") {
                text = String(text.dropLast(3)).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        guard let start = text.firstIndex(of: "{"), let end = text.lastIndex(of: "}") else {
            throw LlmError.noJson
        }
        guard start < end else { throw LlmError.noJson }
        let jsonText = String(text[start...end])
        guard
            let data = jsonText.data(using: .utf8),
            let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let arr = root["lines"] as? [[String: Any]]
        else {
            throw LlmError.noJson
        }
        let lines: [LyricLine] = arr.compactMap { o in
            let jp = (o["jyutping"] as? String) ?? ""
            let md = (o["mandarin"] as? String) ?? ""
            let hp = (o["homophone"] as? String) ?? ""
            guard !md.trimmingCharacters(in: .whitespaces).isEmpty
                || !jp.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            return LyricLine(jyutping: jp, mandarin: md, homophone: hp)
        }
        guard !lines.isEmpty else { throw LlmError.empty }
        let title = ((root["title"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
        let artist = ((root["artist"] as? String) ?? "").trimmingCharacters(in: .whitespaces)
        return Song(
            id: "llm_\(Int(Date().timeIntervalSince1970 * 1000))",
            title: title.isEmpty ? "未命名" : title,
            artist: artist.isEmpty ? "未知" : artist,
            source: "llm",
            createdAt: Date(),
            lines: lines
        )
    }
}
