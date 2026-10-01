package com.loomz.cantonese.lyrics.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.room.Room
import com.loomz.cantonese.lyrics.model.LyricLine
import com.loomz.cantonese.lyrics.model.Song
import com.loomz.cantonese.lyrics.model.SongDoc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地歌词仓库（单一数据源 = Room songs 表）：
 * - 元数据 + 歌词内容（serverDoc/localDoc JSON 原文）都在 songs 表
 * - 首次启动把旧文件（filesDir/lyrics）迁移进 DB，并导入种子歌曲
 * - 提供 上一首/下一首、搜索、最近列表、去重、增删、版本管理 等能力
 *
 * 版本管理状态（每首歌）：serverVersion(0=内置) / hasLocalEdit / preferLocal。
 * 活动文档 = preferLocal && hasLocalEdit 且 localDoc 非空 → localDoc，否则 serverDoc。
 *
 * lines 懒加载：列表/搜索/最近 只查元数据列（SongMeta），仅 currentSong 经 byId 读全列解析歌词。
 * open/next/prev 非 suspend（小查询主线程读，沿用 allowMainThreadQueries）。
 */
object LyricsRepository {

    private lateinit var context: Context
    private lateinit var dao: LyricsDao

    var songs by mutableStateOf(emptyList<Song>())
        private set
    var currentSongId by mutableStateOf<String?>(null)
        private set
    var currentSong by mutableStateOf<Song?>(null)
        private set
    /** 服务端新版本号；-1 = 无更新。> currentSong.serverVersion 时主屏出横幅。 */
    var serverUpdateVersion by mutableIntStateOf(-1)
        private set

    fun init(context: Context) {
        this.context = context
        val db = Room.databaseBuilder(context, LyricsDatabase::class.java, LyricsDatabase.NAME)
            .allowMainThreadQueries()
            .addMigrations(LyricsDatabase.MIGRATION_1_2)
            .build()
        dao = db.dao()
        Migration.migrate(context, dao)
        reload()
    }

    /** 重新加载元数据 + 当前歌曲（懒加载 lines），并清更新横幅。 */
    fun reload() {
        songs = dao.allSongs().map { it.toSong() }
        if (currentSongId == null || songs.none { it.id == currentSongId }) {
            currentSongId = songs.firstOrNull()?.id
        }
        currentSong = currentSongId?.let { loadSong(it) }
        serverUpdateVersion = -1
    }

    /** 元数据投影 → Song（lines 空，懒加载）。 */
    private fun SongMeta.toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        source = source,
        trackId = trackId,
        createdAt = createdAt,
        lastOpenedAt = lastOpenedAt,
        serverVersion = serverVersion,
        hasLocalEdit = hasLocalEdit,
        preferLocal = preferLocal,
        lines = emptyList()
    )

    /** 完整实体 → Song（lines 空，供 loadSong 填充）。 */
    private fun SongEntity.toSong() = Song(
        id = id,
        title = title,
        artist = artist,
        source = source,
        trackId = trackId,
        createdAt = createdAt,
        lastOpenedAt = lastOpenedAt,
        serverVersion = serverVersion,
        hasLocalEdit = hasLocalEdit,
        preferLocal = preferLocal,
        lines = emptyList()
    )

    /** 读活动文档（localDoc 或 serverDoc），填 lines/language，返回完整 Song。 */
    private fun loadSong(id: String): Song? {
        val e = dao.byId(id) ?: return null
        val docText = if (e.preferLocal && e.hasLocalEdit) (e.localDoc ?: e.serverDoc) else e.serverDoc
        val doc = docText?.let { parseDoc(it) } ?: return null
        return e.toSong().copy(lines = doc.lines, language = doc.language)
    }

    /** 容错解析歌词文档 JSON；损坏返回 null。 */
    private fun parseDoc(text: String): SongDoc? =
        try {
            SongDoc.parse(text)
        } catch (_: Exception) {
            null
        }

    private fun currentIndex(): Int = songs.indexOfFirst { it.id == currentSongId }

    fun next() = move(1)
    fun prev() = move(-1)

    private fun move(delta: Int) {
        val list = songs
        if (list.isEmpty()) return
        val i = currentIndex()
        val ni = if (i < 0) 0 else ((i + delta) % list.size + list.size) % list.size
        open(list[ni].id)
    }

    /** 打开歌曲并更新 lastOpenedAt（最近列表排序依据）。 */
    fun open(songId: String) {
        if (songs.none { it.id == songId }) return
        currentSongId = songId
        dao.touch(songId, System.currentTimeMillis())
        currentSong = loadSong(songId)
        serverUpdateVersion = -1
    }

    /** 本地搜索：按歌名/歌手模糊匹配（返回元数据）。 */
    fun search(query: String): List<Song> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return songs
        return dao.search("%$q%").map { it.toSong() }
    }

    /** 最近列表（顶栏「列表」按钮）。 */
    fun getRecent(limit: Int = 20): List<Song> = dao.recent(limit).map { it.toSong() }

    /** 去重：同 trackId，或同歌名+歌手。 */
    fun findDuplicate(trackId: String?, title: String, artist: String): String? {
        if (!trackId.isNullOrBlank()) {
            dao.byTrackId(trackId)?.let { return it }
        }
        if (title.isNotBlank()) {
            dao.byTitleArtist(title, artist.ifBlank { "" })?.let { return it }
        }
        return null
    }

    /** 同步入库：写 serverDoc + upsert 元数据，并打开新歌。 */
    fun addSyncedSong(entity: SongEntity, rawJson: String) {
        dao.upsertSong(entity.copy(serverDoc = rawJson))
        reload()
        currentSongId = entity.id
        currentSong = loadSong(entity.id)
    }

    fun deleteSong(songId: String) {
        dao.deleteSong(songId)
        if (currentSongId == songId) {
            currentSongId = null
            currentSong = null
        }
        reload()
    }

    // ── 版本管理 ──

    /**
     * 异步查服务端版本号（不触发生成）。服务端版本 > 本地 → 置 serverUpdateVersion（主屏横幅）；
     * 否则置 -1（清横幅）。内置歌（无 trackId）/ 非平台源 / 网络失败 → 静默置 -1。
     */
    suspend fun checkServerVersion() {
        val song = currentSong ?: return
        val trackId = song.trackId
        val provider = song.source
        if (trackId == null || (provider != "netease" && provider != "qq")) {
            serverUpdateVersion = -1
            return
        }
        val v = withContext(Dispatchers.IO) {
            try {
                LyricsApiClient.songVersion(provider, trackId)
            } catch (_: Exception) {
                null
            }
        }
        serverUpdateVersion = if (v != null && v > song.serverVersion) v else -1
    }

    /** 同步最新：下载服务端最新文档覆盖 serverDoc（不动 localDoc），更新 serverVersion。 */
    suspend fun syncLatest() {
        val song = currentSong ?: return
        val trackId = song.trackId ?: return
        val provider = song.source
        if (provider != "netease" && provider != "qq") return
        val (doc, raw) = withContext(Dispatchers.IO) {
            LyricsApiClient.getSong(provider, trackId)
        }
        dao.setServerDoc(song.id, raw)
        dao.setServerVersion(song.id, doc.version)
        reload()
        checkServerVersion()
    }

    /** 服务端重新生成（version+1）后同步到本地。 */
    suspend fun refreshAndSync() {
        val song = currentSong ?: return
        val trackId = song.trackId ?: return
        val provider = song.source
        if (provider != "netease" && provider != "qq") return
        val (doc, raw) = withContext(Dispatchers.IO) {
            LyricsApiClient.refreshSong(provider, trackId)
        }
        dao.setServerDoc(song.id, raw)
        dao.setServerVersion(song.id, doc.version)
        reload()
        checkServerVersion()
    }

    /** 切到「我的版本」（需有本地编辑）。 */
    fun useMyVersion() {
        val song = currentSong ?: return
        if (!song.hasLocalEdit) return
        dao.setEditFlags(song.id, hasLocalEdit = true, preferLocal = true)
        reload()
    }

    /** 切到「服务端版本」。 */
    fun useServerVersion() {
        val song = currentSong ?: return
        dao.setEditFlags(song.id, hasLocalEdit = song.hasLocalEdit, preferLocal = false)
        reload()
    }

    /** 放弃我的修改：清 localDoc，清标志。 */
    fun discardMyEdits() {
        val song = currentSong ?: return
        if (!song.hasLocalEdit) return
        dao.setLocalDoc(song.id, null)
        dao.setEditFlags(song.id, hasLocalEdit = false, preferLocal = false)
        reload()
    }

    /** 保存本地编辑：写 localDoc，置 hasLocalEdit=true、preferLocal=true（保存即查看自己的版本）。 */
    suspend fun saveLocalEdit(songId: String, lines: List<LyricLine>) {
        val song = songs.find { it.id == songId } ?: return
        // language 取自当前活动文档原文（元数据投影没有该字段）。
        val language = withContext(Dispatchers.IO) {
            val e = dao.byId(songId)
            val docText = if (e != null && e.preferLocal && e.hasLocalEdit) (e.localDoc ?: e.serverDoc) else e?.serverDoc
            docText?.let { parseDoc(it) }?.language ?: "yue"
        }
        val doc = SongDoc(
            id = song.id,
            provider = if (song.source == "netease" || song.source == "qq") song.source else "",
            trackId = song.trackId ?: "",
            version = song.serverVersion,
            generatedAt = 0L,
            title = song.title,
            artist = song.artist,
            language = language,
            lines = lines
        )
        withContext(Dispatchers.IO) {
            dao.setLocalDoc(songId, SongDoc.toJson(doc))
        }
        dao.setEditFlags(songId, hasLocalEdit = true, preferLocal = true)
        reload()
    }
}
