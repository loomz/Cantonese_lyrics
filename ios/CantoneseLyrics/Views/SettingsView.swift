import SwiftUI

/// 设置页：切换歌词生成来源（本地 Qwen / 免费大模型 API），并可测试连接
struct SettingsView: View {
    @EnvironmentObject var settings: AppSettings
    @Environment(\.dismiss) var dismiss

    @State private var testing = false
    @State private var testResult: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("歌词生成来源（本地搜不到时调用）") {
                    Picker("提供方", selection: $settings.llm.provider) {
                        Text("本地 Qwen（Ollama / vLLM）").tag("ollama")
                        Text("免费大模型 API（OpenAI 兼容）").tag("openai")
                    }
                    TextField("Base URL", text: $settings.llm.baseUrl)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .keyboardType(.URL)
                    TextField("API Key（本地模型可留空）", text: $settings.llm.apiKey)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    TextField("模型名", text: $settings.llm.model)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                }

                Section("快速填入") {
                    Button("Ollama 本地默认（127.0.0.1:11434）") {
                        settings.llm.baseUrl = "http://127.0.0.1:11434/v1"
                        settings.llm.model = "qwen3.8-27b"
                        settings.llm.apiKey = ""
                    }
                    Button("vLLM 本地默认（127.0.0.1:8000）") {
                        settings.llm.baseUrl = "http://127.0.0.1:8000/v1"
                        settings.llm.model = "qwen3.8-27b"
                    }
                }

                Section {
                    Button(testing ? "测试中…" : "测试连接") { runTest() }
                        .disabled(testing)
                    if let testResult {
                        Text(testResult)
                            .font(.footnote)
                            .foregroundStyle(testResult.hasPrefix("✅") ? .green : .red)
                    }
                }

                Section("说明") {
                    Text("""
                    · 手机需与运行模型的电脑在同一 Wi-Fi；Base URL 填电脑的局域网 IP，如 http://192.168.1.10:11434/v1。
                    · Ollama 启动：ollama serve；拉模型：ollama pull qwen3.8-27b。
                    · 免费 API 需填写服务商的 Base URL、API Key 与模型名（OpenAI 兼容格式）。
                    """)
                    .font(.footnote)
                }
            }
            .navigationTitle("设置")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("完成") { dismiss() }
                }
            }
        }
    }

    private func runTest() {
        testing = true
        testResult = nil
        let s = settings.llm
        Task {
            do {
                let reply = try await LlmService.testConnection(settings: s)
                await MainActor.run {
                    testResult = "✅ 连接成功：\(String(reply.prefix(60)))"
                    testing = false
                }
            } catch {
                await MainActor.run {
                    testResult = "❌ \(error.localizedDescription)"
                    testing = false
                }
            }
        }
    }
}
