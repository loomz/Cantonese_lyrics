package com.loomz.cantonese.lyrics.model

/**
 * 一句歌词的三行展示：
 * jyutping         - 第一行：注音（粤拼带调 / 韩日语罗马音，由 doc.language 决定语义）
 * jyutpingToneless - 第一行：注音不带调（粤拼去数字；ko/ja 与 jyutping 同值）
 * mandarin         - 第二行：原文（粤/韩/日语原歌词，字段名沿用历史）
 * homophone        - 第三行：中文谐音
 */
data class LyricLine(
    val jyutping: String = "",
    val jyutpingToneless: String = "",
    val mandarin: String = "",
    val homophone: String = ""
)

data class Song(
    val id: String,
    val title: String,
    val artist: String = "",
    val source: String = "local", // builtin / local / netease / qq
    val trackId: String? = null, // 平台曲目 id，用于去重
    val createdAt: Long = 0L,
    val lastOpenedAt: Long = 0L, // 「最近列表」排序依据
    val serverVersion: Int = 0, // 0 = 内置/未同步
    val hasLocalEdit: Boolean = false, // 有用户本地编辑（songs.localDoc 非空）
    val preferLocal: Boolean = false, // 当前查看本地编辑版
    val language: String = "yue", // doc 顶层 language：yue | ko | ja
    val lines: List<LyricLine> = emptyList()
)

/** 语言的 UI 显示名；未知语言返回 null（不显示标签）。 */
fun languageLabel(language: String): String? = when (language) {
    "yue" -> "粤语"
    "ko" -> "韩语"
    "ja" -> "日语"
    else -> null
}
