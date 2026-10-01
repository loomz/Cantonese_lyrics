package com.loomz.cantonese.lyrics.ui

import androidx.compose.ui.graphics.Color

/** 歌词展示模板：背景色 + 三行文字颜色（只作用于歌词显示区，不影响其它界面） */
data class LyricsTheme(
    val name: String,
    val background: Color,
    val jyutping: Color,
    val mandarin: Color,
    val homophone: Color
)

val LyricsThemes = listOf(
    LyricsTheme("经典黑", Color(0xFF000000), Color(0xFF8A8A8A), Color(0xFFFFFFFF), Color(0xFFB0B0B0)),
    LyricsTheme("深夜蓝", Color(0xFF0A1929), Color(0xFF5B7A99), Color(0xFFA8D8FF), Color(0xFF7FA8CC)),
    LyricsTheme("日落橘", Color(0xFF1A0E05), Color(0xFF9C6B3F), Color(0xFFFFB25E), Color(0xFFC98A4B)),
    LyricsTheme("森林绿", Color(0xFF07130B), Color(0xFF4F7A5A), Color(0xFF8FE3A1), Color(0xFF6FA97C)),
    LyricsTheme("樱花粉", Color(0xFFFFF0F3), Color(0xFFB98A94), Color(0xFFD6336C), Color(0xFFA05A6E)),
    LyricsTheme("少女粉", Color(0xFFFFD6E5), Color(0xFFA0617A), Color(0xFFD6336C), Color(0xFF9C5A72)),
    LyricsTheme("复古米", Color(0xFFF5EFE0), Color(0xFF9C8F76), Color(0xFF5D4037), Color(0xFF8D6E63))
)

/**
 * 固定「非歌词区」配色：顶/底栏、弹窗、搜索 / 设置 / 编辑等所有不显示歌词的界面
 * 一律用这套固定色，**不随歌词主题（[LyricsThemes]）变化**。
 * 深色底 + 浅色字，保证在任意歌词主题背景上都可读。
 */
object Chrome {
    val background = Color(0xFF121212)   // 页面底（搜索 / 设置 / 编辑）
    val surface = Color(0xFF1E1E1E)      // 卡片 / 次级底
    val bar = Color(0xFF1A1A1A)          // 主屏顶 / 底栏 / 标题（不透明深底，不随歌词主题变）
    val text = Color(0xFFEDEDED)         // 主文字
    val muted = Color(0xFF9E9E9E)        // 次级文字
    val outline = Color(0xFF4A4A4A)      // 描边
    val accent = Color(0xFF4FC3F7)       // 强调（进度 / 开关选中 / 横幅）
    val error = Color(0xFFEF5350)        // 错误
}
