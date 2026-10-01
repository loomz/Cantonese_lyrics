package com.loomz.cantonese.lyrics.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 本地数据库（Room/SQLite）：歌曲元数据 + 歌词内容，单一数据源。
 *
 * songs 表：
 *   元数据列：id/title/artist/source/trackId/serverVersion/hasLocalEdit/preferLocal/
 *             createdAt/lastOpenedAt
 *   歌词内容列（JSON 原文，字节保真）：
 *     serverDoc  服务端版（API 响应原文 / 内置种子 version 0）
 *     localDoc   用户本地编辑版（客户端序列化）
 *
 * 活动文档 = preferLocal && hasLocalEdit 且 localDoc 非空 → localDoc，否则 serverDoc。
 *
 * 列表/搜索/最近/去重 只取元数据列（SongMeta 投影），不读歌词内容（懒加载，
 * 仅打开单首时 byId 读全列）。搜索用 LIKE 而非 FTS：个人歌词库量级（≤1 万首）
 * 下 LIKE 扫描为亚毫秒级，且 FTS 默认分词器不按字切中文，子串匹配反而失效。
 */

@Entity(
    tableName = "songs",
    indices = [Index("title"), Index("artist"), Index("lastOpenedAt"), Index("trackId")]
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String = "",
    val source: String = "local",
    val trackId: String? = null,
    val serverVersion: Int = 0,
    val hasLocalEdit: Boolean = false,
    val preferLocal: Boolean = false,
    val createdAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val serverDoc: String? = null, // 服务端版歌词（JSON 原文）
    val localDoc: String? = null // 用户本地编辑版歌词（JSON 原文）
)

/** 元数据投影：列表/搜索/最近 用，不含歌词内容列（懒加载）。 */
data class SongMeta(
    val id: String,
    val title: String,
    val artist: String,
    val source: String,
    val trackId: String?,
    val serverVersion: Int,
    val hasLocalEdit: Boolean,
    val preferLocal: Boolean,
    val createdAt: Long,
    val lastOpenedAt: Long
)

@Dao
interface LyricsDao {
    @Query(
        "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, " +
            "createdAt, lastOpenedAt FROM songs ORDER BY createdAt, title"
    )
    fun allSongs(): List<SongMeta>

    @Query(
        "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, " +
            "createdAt, lastOpenedAt FROM songs WHERE lower(title) LIKE :q OR lower(artist) LIKE :q " +
            "ORDER BY lastOpenedAt DESC"
    )
    fun search(q: String): List<SongMeta>

    @Query(
        "SELECT id, title, artist, source, trackId, serverVersion, hasLocalEdit, preferLocal, " +
            "createdAt, lastOpenedAt FROM songs ORDER BY lastOpenedAt DESC LIMIT :n"
    )
    fun recent(n: Int): List<SongMeta>

    @Query("SELECT * FROM songs WHERE id = :id")
    fun byId(id: String): SongEntity?

    @Query("SELECT id FROM songs WHERE trackId = :trackId LIMIT 1")
    fun byTrackId(trackId: String): String?

    @Query("SELECT id FROM songs WHERE title = :title AND artist = :artist LIMIT 1")
    fun byTitleArtist(title: String, artist: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSong(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :id")
    fun deleteSong(id: String)

    @Query("UPDATE songs SET lastOpenedAt = :ts WHERE id = :id")
    fun touch(id: String, ts: Long)

    @Query("UPDATE songs SET hasLocalEdit = :hasLocalEdit, preferLocal = :preferLocal WHERE id = :id")
    fun setEditFlags(id: String, hasLocalEdit: Boolean, preferLocal: Boolean)

    @Query("UPDATE songs SET serverDoc = :doc WHERE id = :id")
    fun setServerDoc(id: String, doc: String?)

    @Query("UPDATE songs SET localDoc = :doc WHERE id = :id")
    fun setLocalDoc(id: String, doc: String?)

    @Query("UPDATE songs SET serverVersion = :v WHERE id = :id")
    fun setServerVersion(id: String, v: Int)
}

@Database(entities = [SongEntity::class], version = 2, exportSchema = false)
abstract class LyricsDatabase : RoomDatabase() {
    abstract fun dao(): LyricsDao

    companion object {
        const val NAME = "lyrics.db"

        /** v1（歌词存 filesDir/lyrics 文件）→ v2（歌词并入 songs 表 serverDoc/localDoc 列）。 */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN serverDoc TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN localDoc TEXT")
            }
        }
    }
}
