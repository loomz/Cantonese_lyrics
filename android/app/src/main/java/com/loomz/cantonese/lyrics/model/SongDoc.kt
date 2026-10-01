package com.loomz.cantonese.lyrics.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 歌曲文档：API 响应 = 客户端 songs 表 serverDoc/localDoc 列，同一结构（JSON 原文存储）。
 * 一行四字段（粤拼带调 / 粤拼不带调 / 普通话原词 / 中文谐音）。
 *
 * serverDoc 存 API 响应原文（字节保真）；localDoc 存客户端序列化（provider/trackId 可空）。
 */
data class SongDoc(
    val id: String,
    val provider: String = "",
    val trackId: String = "",
    val version: Int = 0,
    val generatedAt: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val lines: List<LyricLine> = emptyList()
) {
    companion object {
        /** 容错解析（全 opt*）：缺字段给默认值。 */
        fun parse(text: String): SongDoc {
            val root = JSONObject(text)
            val lines = root.optJSONArray("lines")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    LyricLine(
                        jyutping = o.optString("jyutping", ""),
                        jyutpingToneless = o.optString("jyutpingToneless", ""),
                        mandarin = o.optString("mandarin", ""),
                        homophone = o.optString("homophone", "")
                    )
                }
            } ?: emptyList()
            return SongDoc(
                id = root.optString("id", ""),
                provider = root.optString("provider", ""),
                trackId = root.optString("trackId", ""),
                version = root.optInt("version", 0),
                generatedAt = root.optLong("generatedAt", 0L),
                title = root.optString("title", ""),
                artist = root.optString("artist", ""),
                lines = lines
            )
        }

        /** 序列化（客户端写本地编辑文件用）。 */
        fun toJson(doc: SongDoc): String {
            val root = JSONObject().apply {
                put("id", doc.id)
                put("provider", doc.provider)
                put("trackId", doc.trackId)
                put("version", doc.version)
                put("generatedAt", doc.generatedAt)
                put("title", doc.title)
                put("artist", doc.artist)
                put("lines", JSONArray().apply {
                    doc.lines.forEach { l ->
                        put(
                            JSONObject().apply {
                                put("jyutping", l.jyutping)
                                put("jyutpingToneless", l.jyutpingToneless)
                                put("mandarin", l.mandarin)
                                put("homophone", l.homophone)
                            }
                        )
                    }
                })
            }
            return root.toString(2)
        }
    }
}
