package com.loomz.cantonese.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomz.cantonese.lyrics.data.LyricsApiClient
import com.loomz.cantonese.lyrics.data.LyricsRepository
import com.loomz.cantonese.lyrics.data.Pipeline
import com.loomz.cantonese.lyrics.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 在线搜索结果（provider 标明实际来源：netease / qq） */
private data class OnlineItem(
    val provider: String,
    val trackId: String,
    val title: String,
    val artist: String
)

/** 同步阶段 → 进度文案（客户端只同步，生成在服务端） */
private val STAGE_TEXT = mapOf(
    "sync" to "正在同步（服务端生成，首次约 20 秒）…"
)

private fun sourceText(source: String): String = when (source) {
    "builtin" -> "内置"
    "netease" -> "网易云"
    "qq" -> "QQ 音乐"
    else -> "本地"
}

/** 搜索页：本地搜索 + 在线歌曲（网易云/QQ）+ 导入管道进度 + 本地歌曲库（可删除）（固定配色） */
@Composable
fun SearchScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf(false) }
    val results = remember { mutableStateOf(emptyList<Song>()) }
    var online by remember { mutableStateOf(emptyList<OnlineItem>()) }
    var onlineLoading by remember { mutableStateOf(false) }
    var onlineError by remember { mutableStateOf<String?>(null) }
    var onlineProviderText by remember { mutableStateOf("") }
    var pipeline by remember { mutableStateOf<OnlineItem?>(null) }
    var stageText by remember { mutableStateOf("") }
    var pipelineError by remember { mutableStateOf<String?>(null) }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        searched = true
        results.value = LyricsRepository.search(q)
        onlineLoading = true
        onlineError = null
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) {
                    LyricsApiClient.searchSongs(q, 20)
                }
                online = r.results.map {
                    OnlineItem(r.provider, it.trackId, it.title, it.artist)
                }
                onlineProviderText =
                    if (r.provider == "netease") "网易云" else "QQ 音乐"
            } catch (e: Exception) {
                onlineError = e.message ?: "在线搜索失败"
            } finally {
                onlineLoading = false
            }
        }
    }

    fun runSync(item: OnlineItem) {
        if (pipeline != null) return
        pipeline = item
        pipelineError = null
        stageText = STAGE_TEXT["sync"] ?: ""
        scope.launch {
            try {
                val id = Pipeline.syncSong(
                    provider = item.provider,
                    trackId = item.trackId,
                    title = item.title,
                    artist = item.artist
                ) { stage ->
                    if (stage != "done") stageText = STAGE_TEXT[stage] ?: stage
                }
                LyricsRepository.open(id)
                pipeline = null
                onBack()
            } catch (e: Exception) {
                pipelineError = e.message ?: "同步失败"
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Chrome.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── 顶栏：返回 + 搜索框 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = Chrome.text
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索歌名 / 歌词", color = Chrome.muted) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )
                OutlinedButton(onClick = { doSearch() }, enabled = !onlineLoading) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Chrome.text)
                    Spacer(Modifier.width(4.dp))
                    Text("搜索", color = Chrome.text)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // ── 导入管道进度 / 失败重试 ──
                pipeline?.let { item ->
                    item(key = "pipeline") {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Chrome.surface)
                                .padding(16.dp)
                        ) {
                            Text(
                                "${item.title} · ${item.artist}",
                                color = Chrome.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(10.dp))
                            if (pipelineError == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Chrome.accent
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(stageText, color = Chrome.muted, fontSize = 14.sp)
                                }
                            } else {
                                Text(pipelineError!!, color = Chrome.error, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { runSync(item) }) {
                                    Text("重试", color = Chrome.text)
                                }
                            }
                        }
                    }
                }

                if (searched) {
                    // ── 本地搜索结果 ──
                    item(key = "local-header") {
                        Text(
                            "本地结果（${results.value.size}）",
                            color = Chrome.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    if (results.value.isEmpty()) {
                        item(key = "local-empty") {
                            Text("本地没有匹配的歌曲", color = Chrome.muted, fontSize = 14.sp)
                        }
                    } else {
                        items(results.value, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    LyricsRepository.open(song.id)
                                    onBack()
                                },
                                onDelete = null
                            )
                        }
                    }

                    // ── 在线歌曲 ──
                    item(key = "online-header") {
                        Text(
                            "在线歌曲" +
                                (if (onlineProviderText.isNotEmpty()) "（$onlineProviderText）" else "") +
                                (if (onlineLoading) " 搜索中…" else ""),
                            color = Chrome.muted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    if (onlineLoading) {
                        item(key = "online-loading") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Chrome.accent
                                )
                                Spacer(Modifier.width(10.dp))
                                Text("正在搜索在线歌曲…", color = Chrome.muted, fontSize = 14.sp)
                            }
                        }
                    } else if (onlineError != null) {
                        item(key = "online-error") {
                            Text(
                                "在线搜索失败：${onlineError}\n（检查「设置」中的歌词服务地址是否可达）",
                                color = Chrome.error,
                                fontSize = 13.sp
                            )
                        }
                    } else if (online.isEmpty()) {
                        item(key = "online-empty") {
                            Text("在线没有匹配的歌曲", color = Chrome.muted, fontSize = 14.sp)
                        }
                    } else {
                        items(online, key = { "${it.provider}_${it.trackId}" }) { o ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { runSync(o) }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        o.title,
                                        color = Chrome.text,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        o.artist,
                                        color = Chrome.muted,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { runSync(o) },
                                    enabled = pipeline == null
                                ) {
                                    Text("导入", color = Chrome.text, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // ── 本地歌曲库 ──
                item(key = "library-header") {
                    Text(
                        "本地歌曲库（${LyricsRepository.songs.size}）",
                        color = Chrome.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(LyricsRepository.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        onClick = {
                            LyricsRepository.open(song.id)
                            onBack()
                        },
                        onDelete = { LyricsRepository.deleteSong(song.id) }
                    )
                }
            }
        }
    }
}

/** 歌曲行：歌名 + 歌手·行数·来源，可选删除按钮（固定配色） */
@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = Chrome.text,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist} · ${sourceText(song.source)}" +
                    (if (song.hasLocalEdit) " · 已编辑" else ""),
                color = Chrome.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = Chrome.error
                )
            }
        }
    }
}
