import Foundation
import SwiftUI

/// 大模型（歌词生成）设置
struct LlmSettings: Codable {
    var provider: String = "ollama" // ollama=本地Qwen, openai=免费大模型API(OpenAI兼容)
    var baseUrl: String = "http://127.0.0.1:11434/v1"
    var apiKey: String = ""
    var model: String = "qwen3.8-27b"
}

final class AppSettings: ObservableObject {
    @Published var themeIndex: Int {
        didSet { UserDefaults.standard.set(themeIndex, forKey: "cl_theme_index") }
    }
    @Published var llm: LlmSettings {
        didSet { saveLlm() }
    }

    private static let kLlm = "cl_llm_settings"

    init() {
        let idx = UserDefaults.standard.integer(forKey: "cl_theme_index")
        self.themeIndex = idx
        if let data = UserDefaults.standard.data(forKey: Self.kLlm),
           let s = try? JSONDecoder().decode(LlmSettings.self, from: data) {
            self.llm = s
        } else {
            self.llm = LlmSettings()
        }
    }

    private func saveLlm() {
        if let data = try? JSONEncoder().encode(llm) {
            UserDefaults.standard.set(data, forKey: Self.kLlm)
        }
    }
}
