package com.loomz.cantonese.lyrics.model

/**
 * 一句歌词的三行展示：
 * jyutping         - 第一行：粤拼（带声调数字）
 * jyutpingToneless - 第一行：粤拼（不带数字，默认显示）
 * mandarin         - 第二行：普通话（原歌词汉字）
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
    val lines: List<LyricLine> = emptyList()
)
