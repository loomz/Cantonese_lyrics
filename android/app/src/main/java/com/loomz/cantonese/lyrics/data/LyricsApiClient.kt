package com.loomz.cantonese.lyrics.data

import com.loomz.cantonese.lyrics.model.SongDoc
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * apiserver 客户端（仓库内 Python 服务，见 apiserver/README.md）：
 *  - GET  /health                     连接测试
 *  - GET  /api/lyrics/search          在线搜歌（网易云主 / QQ 备）
 *  - GET  /api/song/{p}/{trackId}     取歌曲文档（服务端缓存命中直接返回，未命中生成，首次约 20 秒）
 *  - POST /api/song/{p}/{trackId}/refresh  强制重新生成（version +1）
 *  - GET  /api/song/{p}/{trackId}/version  查缓存版本号（不触发生成）
 */
object LyricsApiClient {

    class ApiException(message: String) : Exception(message)

    data class TrackResult(val trackId: String, val title: String, val artist: String)
    data class SearchResult(val provider: String, val results: List<TrackResult>)

    /** 歌词服务基地址（供 [AppUpdater] 等复用同一套地址逻辑）。 */
    internal fun base(): String =
        SettingsStore.settings.apiserverUrl.trim().trimEnd('/').ifEmpty { "http://192.168.3.89:8000" }

    /** 连接测试（用已保存的地址） */
    fun health(): String = healthWith(base())

    /** 连接测试（用指定地址，供设置页测试未保存的输入） */
    fun healthWith(url: String): String {
        val u = url.trim().trimEnd('/')
        if (u.isEmpty()) throw ApiException("歌词服务地址未填写")
        val body = get("$u/health", 5_000, 5_000)
        if (!JSONObject(body).optBoolean("ok", false)) throw ApiException("服务响应异常")
        return "连接成功"
    }

    fun searchSongs(q: String, limit: Int = 20): SearchResult {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val body = get("${base()}/api/lyrics/search?q=$encoded&limit=$limit", 5_000, 15_000)
        val root = JSONObject(body)
        val results = root.getJSONArray("results").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                TrackResult(
                    trackId = o.getString("trackId"),
                    title = o.optString("title", ""),
                    artist = o.optString("artist", "")
                )
            }
        }
        return SearchResult(root.optString("provider", "netease"), results)
    }

    /**
     * 取歌曲文档。服务端未命中缓存时会生成（取词→粤拼→GLM），首次通常约 20 秒（个别情况更久），
     * 故读超时 360s（须大于服务端 GLM 读超时 300s）。返回 (解析后的文档, 原始 JSON 文本)。
     */
    fun getSong(provider: String, trackId: String): Pair<SongDoc, String> {
        val raw = get("${base()}/api/song/$provider/$trackId", 15_000, 360_000)
        return SongDoc.parse(raw) to raw
    }

    /** 强制服务端重新生成（version +1），返回 (新文档, 原始 JSON 文本)。 */
    fun refreshSong(provider: String, trackId: String): Pair<SongDoc, String> {
        val raw = post("${base()}/api/song/$provider/$trackId/refresh", "{}", 15_000, 360_000)
        return SongDoc.parse(raw) to raw
    }

    /** 查缓存版本号（不触发生成）。未缓存返回 null。 */
    fun songVersion(provider: String, trackId: String): Int? {
        return try {
            val body = get("${base()}/api/song/$provider/$trackId/version", 5_000, 10_000)
            JSONObject(body).optInt("version", 0)
        } catch (e: ApiException) {
            if (e.message?.contains("HTTP 404") == true) null else throw e
        }
    }

    // ── 底层 HTTP ──

    internal fun get(url: String, connectMs: Int, readMs: Int): String =
        read(open(url, "GET", connectMs, readMs))

    private fun post(url: String, payload: String, connectMs: Int, readMs: Int): String {
        val conn = open(url, "POST", connectMs, readMs)
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(payload.toByteArray()) }
        return read(conn)
    }

    private fun open(url: String, method: String, connectMs: Int, readMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = connectMs
        conn.readTimeout = readMs
        return conn
    }

    private fun read(conn: HttpURLConnection): String {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val resp = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            // apiserver 错误响应是 JSON {detail: "..."}
            val detail = try {
                JSONObject(resp).optString("detail", "").ifBlank { resp.take(200) }
            } catch (_: Exception) {
                resp.take(200)
            }
            throw ApiException("歌词服务 HTTP $code：$detail")
        }
        return resp
    }
}
