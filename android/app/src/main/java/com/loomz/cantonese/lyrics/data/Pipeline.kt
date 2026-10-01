package com.loomz.cantonese.lyrics.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 在线歌曲同步（客户端只同步，不生成）：
 *   去重 → 下载歌曲文档(apiserver，服务端缓存命中直接返回/未命中生成) → 存文件 + 写元数据
 *
 * 任一步失败不写半截数据，可重试。
 * onStage(stage) 回调进度：'sync' | 'done'
 * 返回歌曲 id（去重命中时返回已存在的 id）。
 */
object Pipeline {

    suspend fun syncSong(
        provider: String,
        trackId: String,
        title: String,
        artist: String,
        onStage: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val t0 = title.trim()
        val a0 = artist.trim()

        // 1. 去重：已有同 trackId（或同歌名+歌手）直接打开
        val dup = LyricsRepository.findDuplicate(trackId, t0, a0)
        if (dup != null) {
            onStage("done")
            return@withContext dup
        }

        // 2. 下载歌曲文档（服务端未命中缓存时会生成，首次通常约 20 秒，个别情况更久）
        onStage("sync")
        val (doc, raw) = LyricsApiClient.getSong(provider, trackId)

        // 3. 存文件 + 写元数据（all-or-nothing：走到这里才落盘）
        val id = "${provider}_$trackId"
        val now = System.currentTimeMillis()
        LyricsRepository.addSyncedSong(
            SongEntity(
                id = id,
                title = doc.title.ifBlank { t0 }.ifBlank { "未命名" },
                artist = doc.artist.ifBlank { a0 },
                source = provider,
                trackId = trackId,
                serverVersion = doc.version,
                hasLocalEdit = false,
                preferLocal = false,
                createdAt = now,
                lastOpenedAt = now
            ),
            raw
        )
        onStage("done")
        id
    }
}
