package com.loomz.cantonese.lyrics.data

import android.content.Context
import com.loomz.cantonese.lyrics.model.LyricLine
import com.loomz.cantonese.lyrics.model.SongDoc
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 启动迁移（幂等，每次启动跑）：
 * 1. 把 filesDir/lyrics/ 下残留的歌词文件读进 songs 表（serverDoc/localDoc 列），然后删文件
 * 2. 把 assets 里的示范歌曲（旧格式）导入 songs 表
 *
 * 覆盖三种文件来源（幂等）：
 *   {id}.json        新格式服务端版（有 version 键）→ serverDoc（存原文，字节保真）
 *   {id}.local.json  用户本地编辑版（有 version 键）→ localDoc
 *   旧格式（无 version 键）→ 解析后 upsert 行 + serverDoc
 *
 * 全新安装无残留文件，第 1 步直接跳过；种子已存在则跳过（幂等）。
 * 旧数据没有「不带声调」的粤拼，由 jyutping 剥离每个音节尾部 [1-6] 派生；seed 本身无声调，两版相同。
 */
object Migration {

    /** 剥离粤拼音节尾部的声调数字（如 nei5 si6 → nei si） */
    private val TONE_RE = Regex("([a-z]+)([1-6])(?=\\s|$)")

    fun stripTones(s: String): String = TONE_RE.replace(s) { it.groupValues[1] }

    fun migrate(context: Context, dao: LyricsDao) {
        migrateFilesToDb(context, dao)
        importSeed(context, dao)
    }

    /** 把 filesDir/lyrics/ 下残留文件读进 DB（serverDoc/localDoc 列），然后删文件。 */
    private fun migrateFilesToDb(context: Context, dao: LyricsDao) {
        val dir = File(context.filesDir, "lyrics")
        val files = dir.listFiles { f -> f.isFile && f.extension == "json" } ?: return
        for (f in files) {
            val text = try {
                f.readText()
            } catch (_: Exception) {
                f.delete()
                continue
            }
            val name = f.name
            if (name.endsWith(".local.json")) {
                // 用户本地编辑版 → localDoc
                val id = name.removeSuffix(".local.json")
                if (dao.byId(id) != null) dao.setLocalDoc(id, text)
            } else {
                val root = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                }
                if (root != null && root.has("version")) {
                    // 新格式服务端版 → serverDoc（存原文，字节保真）
                    val id = name.removeSuffix(".json")
                    if (dao.byId(id) != null) dao.setServerDoc(id, text)
                } else if (root != null) {
                    // 旧格式（无 version 键）→ 解析入库
                    val id = root.optString("id", name.removeSuffix(".json"))
                    if (id.isNotEmpty()) {
                        val doc = toSongDoc(root)
                        if (dao.byId(id) == null) {
                            dao.upsertSong(toEntity(root, doc).copy(serverDoc = SongDoc.toJson(doc)))
                        } else {
                            dao.setServerDoc(id, SongDoc.toJson(doc))
                        }
                    }
                }
            }
            f.delete()
        }
    }

    /** 种子是 assets/lyrics/seed_lyrics.json 里的歌曲数组（旧格式）；已存在的跳过（幂等） */
    private fun importSeed(context: Context, dao: LyricsDao) {
        try {
            val text = context.assets.open("lyrics/seed_lyrics.json").use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            val arr = JSONArray(text)
            for (i in 0 until arr.length()) {
                val root = arr.getJSONObject(i)
                val id = root.optString("id", "")
                if (id.isEmpty()) continue
                if (dao.byId(id) != null) continue
                val doc = toSongDoc(root)
                dao.upsertSong(toEntity(root, doc).copy(serverDoc = SongDoc.toJson(doc)))
            }
        } catch (_: Exception) {
        }
    }

    /** 旧格式 JSON → 新格式 SongDoc（version 0，provider/trackId 从 id/source 推）。 */
    private fun toSongDoc(root: JSONObject): SongDoc {
        val id = root.optString("id", "")
        val source = root.optString("source", "local")
        val (provider, trackId) = deriveProviderTrackId(id, source)
        val lines = root.optJSONArray("lines")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val jp = o.optString("jyutping", "")
                LyricLine(
                    jyutping = jp,
                    jyutpingToneless = o.optString("jyutpingToneless", "").ifBlank { stripTones(jp) },
                    mandarin = o.optString("mandarin", ""),
                    homophone = o.optString("homophone", "")
                )
            }.filter { it.mandarin.isNotBlank() || it.jyutping.isNotBlank() }
        } ?: emptyList()
        return SongDoc(
            id = id,
            provider = provider,
            trackId = trackId,
            version = 0,
            generatedAt = root.optLong("createdAt", 0L),
            title = root.optString("title", "未命名"),
            artist = root.optString("artist", ""),
            lines = lines
        )
    }

    private fun toEntity(root: JSONObject, doc: SongDoc): SongEntity {
        val now = System.currentTimeMillis()
        return SongEntity(
            id = doc.id,
            title = doc.title,
            artist = doc.artist,
            source = root.optString("source", "local"),
            trackId = doc.trackId.ifBlank { null },
            serverVersion = 0,
            hasLocalEdit = false,
            preferLocal = false,
            createdAt = root.optLong("createdAt", 0L).takeIf { it > 0 } ?: now,
            lastOpenedAt = root.optLong("lastOpenedAt", root.optLong("createdAt", 0L))
                .takeIf { it > 0 } ?: now
        )
    }

    /** netease/qq 的 id 形如 {source}_{trackId}；其余（builtin_* 等）无 trackId。 */
    private fun deriveProviderTrackId(id: String, source: String): Pair<String, String> {
        if (source == "netease" || source == "qq") {
            val prefix = "${source}_"
            if (id.startsWith(prefix)) return source to id.removePrefix(prefix)
        }
        return "" to ""
    }
}
