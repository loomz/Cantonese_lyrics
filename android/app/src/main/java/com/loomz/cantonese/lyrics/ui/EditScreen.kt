package com.loomz.cantonese.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomz.cantonese.lyrics.data.LyricsRepository
import com.loomz.cantonese.lyrics.model.LyricLine
import com.loomz.cantonese.lyrics.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 歌词编辑器：逐行编辑四字段（粤拼带调 / 粤拼不带调 / 普通话原文 / 中文谐音）。
 * 保存为本地编辑版（songs.localDoc），保存后当前使用「我的版本」。
 * v1 只改现有行，不增删行（行集由服务端文档定义）。界面用固定配色，不随主题变。
 */
@Composable
fun EditScreen(onBack: () -> Unit) {
    val song = LyricsRepository.currentSong
    if (song == null) {
        // 理论上不会发生：主屏编辑按钮在 song==null 时禁用
        Box(Modifier.fillMaxSize().background(Chrome.background)) {
            Text("无歌曲可编辑", color = Chrome.text, modifier = Modifier.align(Alignment.Center))
        }
        return
    }
    EditorBody(song = song, onBack = onBack)
}

@Composable
private fun EditorBody(song: Song, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // 活动版本的可编辑副本（按 song.id 重置）
    var lines by remember(song.id) { mutableStateOf(song.lines) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    fun save() {
        if (saving) return
        saving = true
        scope.launch {
            LyricsRepository.saveLocalEdit(song.id, lines)
            saving = false
            saved = true
            delay(600) // 短暂显示「已保存 ✓」再返回
            onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Chrome.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── 顶栏：返回 + 标题 + 保存 ──
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = Chrome.text)
            }
            Text(
                "编辑歌词",
                color = Chrome.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { save() }, enabled = !saving) {
                Text(if (saving) "保存中…" else if (saved) "已保存 ✓" else "保存", color = Chrome.text)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                LineEditorCard(
                    index = index,
                    line = line,
                    onChange = { updated ->
                        lines = lines.toMutableList().also { it[index] = updated }
                    }
                )
            }
        }
    }
}

/** 一行歌词的编辑卡片：4 个字段（固定配色） */
@Composable
private fun LineEditorCard(
    index: Int,
    line: LyricLine,
    onChange: (LyricLine) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Chrome.surface)
            .padding(12.dp)
    ) {
        Text("第 ${index + 1} 行", color = Chrome.muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        FieldRow("粤拼（带调）", line.jyutping) { onChange(line.copy(jyutping = it)) }
        Spacer(Modifier.height(6.dp))
        FieldRow("粤拼（不带调）", line.jyutpingToneless) { onChange(line.copy(jyutpingToneless = it)) }
        Spacer(Modifier.height(6.dp))
        FieldRow("普通话原文", line.mandarin) { onChange(line.copy(mandarin = it)) }
        Spacer(Modifier.height(6.dp))
        FieldRow("中文谐音", line.homophone) { onChange(line.copy(homophone = it)) }
    }
}

@Composable
private fun FieldRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, color = Chrome.muted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
