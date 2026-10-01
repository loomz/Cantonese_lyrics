import SwiftUI

/// 歌词展示模板：背景色 + 三行文字颜色
struct LyricsTheme: Identifiable {
    let id: Int
    let name: String
    let background: Color
    let jyutping: Color
    let mandarin: Color
    let homophone: Color

    init(id: Int, name: String, bg: String, jp: String, md: String, hp: String) {
        self.id = id
        self.name = name
        self.background = Color(hex: bg)
        self.jyutping = Color(hex: jp)
        self.mandarin = Color(hex: md)
        self.homophone = Color(hex: hp)
    }
}

extension Color {
    /// 支持 #RRGGBB
    init(hex: String) {
        var s = hex
        if s.hasPrefix("#") { s.removeFirst() }
        var value: UInt64 = 0
        Scanner(string: s).scanHexInt64(&value)
        self.init(
            .sRGB,
            red: Double((value & 0xFF0000) >> 16) / 255.0,
            green: Double((value & 0x00FF00) >> 8) / 255.0,
            blue: Double(value & 0x0000FF) / 255.0,
            opacity: 1.0
        )
    }
}

let allThemes: [LyricsTheme] = [
    LyricsTheme(id: 0, name: "经典黑", bg: "#000000", jp: "#8A8A8A", md: "#FFFFFF", hp: "#B0B0B0"),
    LyricsTheme(id: 1, name: "深夜蓝", bg: "#0A1929", jp: "#5B7A99", md: "#A8D8FF", hp: "#7FA8CC"),
    LyricsTheme(id: 2, name: "日落橘", bg: "#1A0E05", jp: "#9C6B3F", md: "#FFB25E", hp: "#C98A4B"),
    LyricsTheme(id: 3, name: "森林绿", bg: "#07130B", jp: "#4F7A5A", md: "#8FE3A1", hp: "#6FA97C"),
    LyricsTheme(id: 4, name: "樱花粉", bg: "#FFF0F3", jp: "#B98A94", md: "#C2185B", hp: "#A05A6E"),
    LyricsTheme(id: 5, name: "复古米", bg: "#F5EFE0", jp: "#9C8F76", md: "#5D4037", hp: "#8D6E63")
]
