package com.loomz.cantonese.lyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomz.cantonese.lyrics.data.LyricsRepository
import com.loomz.cantonese.lyrics.data.SettingsStore
import com.loomz.cantonese.lyrics.model.LyricLine
import com.loomz.cantonese.lyrics.model.languageLabel
import kotlinx.coroutines.launch

/** 应用入口界面：几乎全屏歌词 + 顶栏 列表/搜索 + 底栏 主题/上一首/下一首/设置 */
@Composable
fun MainScreen(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val themeIndex = SettingsStore.themeIndex
    val theme = LyricsThemes[themeIndex.coerceIn(0, LyricsThemes.size - 1)]
    val song = LyricsRepository.currentSong
    val scope = rememberCoroutineScope()
    var showThemePicker by remember { mutableStateOf(false) }
    var showRecent by remember { mutableStateOf(false) }
    var showVersionDialog by remember { mutableStateOf(false) }
    var bannerDismissed by remember(song?.id) { mutableStateOf(false) }
    var bannerSyncing by remember(song?.id) { mutableStateOf(false) }
    // 编辑态：点「编辑」进入，歌词行可点；点某行弹 4 字段编辑框，保存后自动退出（切歌重置）
    var editMode by remember(song?.id) { mutableStateOf(false) }
    var editingLineIndex by remember { mutableStateOf<Int?>(null) }

    Box(Modifier.fillMaxSize().background(theme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── 顶栏：左上 列表 / 右上 搜索 / 编辑 / 版本（固定配色，不随主题变） ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Chrome.bar)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showRecent = true }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "最近列表", tint = Chrome.text)
                }
                Spacer(Modifier.weight(1f))
                // 粤语歌：显示模式切换
                if (song != null && song.language == "yue") {
                    val mode = SettingsStore.settings.yueDisplayMode
                    TextButton(
                        onClick = {
                            val ctx = context
                            val next = (mode + 1) % 4
                            SettingsStore.saveSettings(ctx, SettingsStore.settings.copy(yueDisplayMode = next))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when (mode) {
                                0 -> "粤拼+中文"
                                1 -> "谐音+中文"
                                2 -> "粤拼带调+中文+谐音"
                                else -> "粤拼不带调+中文+谐音"
                            },
                            color = Chrome.accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                // 韩日语歌：显示模式切换
                if (song != null && (song.language == "ko" || song.language == "ja")) {
                    val mode = SettingsStore.settings.kojaDisplayMode
                    TextButton(
                        onClick = {
                            val ctx = context
                            val next = (mode + 1) % 4
                            SettingsStore.saveSettings(ctx, SettingsStore.settings.copy(kojaDisplayMode = next))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            when (mode) {
                                0 -> "谐音+原文"
                                1 -> "罗马音+原文"
                                2 -> "谐音+罗马音+原文"
                                else -> "罗马音+谐音+原文"
                            },
                            color = Chrome.accent,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Default.Search, contentDescription = "搜索歌词", tint = Chrome.text)
                }
                IconButton(onClick = { if (song != null) editMode = !editMode }, enabled = song != null) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = if (editMode) "退出编辑" else "编辑歌词",
                        tint = if (editMode) Chrome.accent else Chrome.text
                    )
                }
                IconButton(onClick = { showVersionDialog = true }, enabled = song != null) {
                    Icon(Icons.Default.History, contentDescription = "版本管理", tint = Chrome.text)
                }
            }

            // ── 歌曲标题 + 语言标签（固定配色 + 半透明底，保证任意主题背景上可读） ──
            // 标题 weight(1f) 占满剩余宽度（超长省略号），语言标签固定右缘不换行——
            // 否则歌名/歌手一长会把标签挤到「粤语」两字折行
            if (song != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Chrome.bar)
                        .padding(horizontal = 48.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${song.title}  ·  ${song.artist}",
                        color = Chrome.muted,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    languageLabel(song.language)?.let { label ->
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = label,
                            color = Chrome.accent,
                            fontSize = 10.sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .border(0.5.dp, Chrome.accent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // ── 服务端更新横幅（固定配色） ──
            val serverUpdateVersion = LyricsRepository.serverUpdateVersion
            if (song != null && !bannerDismissed && serverUpdateVersion > song.serverVersion) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Chrome.bar)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "服务端有新版本 (v$serverUpdateVersion)",
                        color = Chrome.text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (bannerSyncing) return@TextButton
                            bannerSyncing = true
                            scope.launch {
                                try {
                                    LyricsRepository.syncLatest()
                                } finally {
                                    bannerSyncing = false
                                }
                            }
                        }
                    ) {
                        Text(if (bannerSyncing) "同步中…" else "同步最新", color = Chrome.accent, fontSize = 13.sp)
                    }
                    TextButton(onClick = { bannerDismissed = true }) {
                        Text("暂不", color = Chrome.muted, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // ── 编辑态提示横幅（固定配色） ──
            if (song != null && editMode) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Chrome.bar)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "编辑模式：点击任意一行歌词进行编辑",
                        color = Chrome.text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { editMode = false }) {
                        Text("完成", color = Chrome.accent, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // ── 歌词主体（几乎全屏，唯一随主题变化的区域） ──
            val yueMode = SettingsStore.settings.yueDisplayMode
            val kojaMode = SettingsStore.settings.kojaDisplayMode
            val listState = rememberLazyListState()
            LaunchedEffect(song?.id) { listState.scrollToItem(0) }
            LaunchedEffect(song?.id) {
                if (song != null) LyricsRepository.checkServerVersion()
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                song?.lines?.let { lines ->
                    // 按索引做 key：歌词常有重复行（副歌），不能直接用内容做 key
                    itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                        // 编辑态：整行可点（高亮提示），点中弹 4 字段编辑框
                        val rowModifier = if (editMode) {
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Chrome.accent.copy(alpha = 0.14f))
                                .border(1.dp, Chrome.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable { editingLineIndex = index }
                                .padding(vertical = 6.dp, horizontal = 8.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                        Box(modifier = rowModifier) {
                            LyricLineBlock(
                                line = line,
                                jyutping = line.jyutping,
                                jyutpingToneless = line.jyutpingToneless,
                                charAligned = song.language == "yue",
                                displayMode = if (song.language == "yue") yueMode else kojaMode,
                                isYue = song.language == "yue",
                                theme = theme
                            )
                        }
                    }
                }
            }

            // ── 底栏：主题 / 上一首 / 下一首 / 设置（固定配色；每钮等宽 + 单行防换行） ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Chrome.bar)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                TextButton(
                    onClick = { showThemePicker = true },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = Chrome.text)
                    Spacer(Modifier.width(2.dp))
                    Text("主题", color = Chrome.text, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                TextButton(
                    onClick = { LyricsRepository.prev() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = null,
                        tint = Chrome.text
                    )
                    Spacer(Modifier.width(2.dp))
                    Text("上一首", color = Chrome.text, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
                TextButton(
                    onClick = { LyricsRepository.next() },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Text("下一首", color = Chrome.text, fontSize = 12.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Chrome.text
                    )
                }
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = Chrome.text)
                    Spacer(Modifier.width(2.dp))
                    Text("设置", color = Chrome.text, fontSize = 12.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(onDismiss = { showThemePicker = false })
    }
    if (showRecent) {
        RecentSongsDialog(onDismiss = { showRecent = false })
    }
    if (showVersionDialog) {
        VersionDialog(onDismiss = { showVersionDialog = false })
    }

    // ── 单行编辑弹窗：保存后替换本地歌词并自动退出编辑态 ──
    if (editingLineIndex != null && song != null) {
        val idx = editingLineIndex!!
        if (idx in song.lines.indices) {
            LineEditDialog(
                index = idx,
                line = song.lines[idx],
                language = song.language,
                onDismiss = { editingLineIndex = null },
                onSave = { updated ->
                    val s = LyricsRepository.currentSong
                    if (s != null) {
                        val newLines = s.lines.toMutableList().also { it[idx] = updated }
                        scope.launch {
                            LyricsRepository.saveLocalEdit(s.id, newLines)
                            editingLineIndex = null
                            editMode = false
                        }
                    }
                }
            )
        }
    }
}

/** 最近列表弹窗：最近打开的 20 首，点击直接切换（固定配色） */
@Composable
fun RecentSongsDialog(onDismiss: () -> Unit) {
    val recent = remember { LyricsRepository.getRecent(20) }
    val currentId = LyricsRepository.currentSongId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("最近歌词", color = Chrome.text) },
        text = {
            if (recent.isEmpty()) {
                Text("暂无最近歌词", color = Chrome.muted, fontSize = 14.sp)
            } else {
                Column {
                    recent.forEach { song ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    LyricsRepository.open(song.id)
                                    onDismiss()
                                }
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
                                    song.artist,
                                    color = Chrome.muted,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (song.id == currentId) {
                                Text("当前", color = Chrome.accent, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Chrome.text)
            }
        }
    )
}

// 中文行字号固定 20sp（字号不变）；字距按字数自适应：字少松、字多紧，尽量不换行
private const val LYRIC_FONT_SIZE = 20
private const val LYRIC_MAX_LETTER_SPACING = 8f  // 字少时最大间隔（松，但不太大）
private const val LYRIC_MIN_LETTER_SPACING = 1f  // 字多时最小间隔（有点点间隔，不紧）

/** 是否中文行：非空白字符里汉字占比过半；英文/混合行返回 false（保留原样显示） */
private fun isCjkLine(s: String): Boolean {
    val nonSpace = s.filter { !it.isWhitespace() }
    if (nonSpace.isEmpty()) return false
    val han = nonSpace.count { it in '一'..'鿿' }
    return han * 2 > nonSpace.length
}

/** 按字数 n 与可用宽度 availableW(dp) 算中文字距(sp)：字少→松、字多→紧，限制在 [min,max] */
private fun calcLyricLetterSpacing(n: Int, availableW: Int): Float {
    if (n <= 1) return 0f
    val charW = LYRIC_FONT_SIZE.toFloat()  // 中文字宽≈字号（sp≈dp，默认字体缩放）
    val raw = (availableW - n * charW) / (n - 1)
    return raw.coerceIn(LYRIC_MIN_LETTER_SPACING, LYRIC_MAX_LETTER_SPACING)
}

/**
 * 一句歌词。支持多种显示模式：
 *
 * 粤语（isYue=true）：
 * - Mode 0: 粤拼+中文（2行）
 * - Mode 1: 谐音+中文（2行）
 * - Mode 2: 粤拼带调+中文+谐音（3行）
 * - Mode 3: 粤拼不带调+中文+谐音（3行）
 *
 * 韩日语（isYue=false）：
 * - Mode 0: 谐音+原文（2行）
 * - Mode 1: 罗马音+原文（2行）
 * - Mode 2: 谐音+罗马音+原文（3行）
 * - Mode 3: 罗马音+谐音+原文（3行）
 *
 * 粤语逐字对齐：中文是等宽字，把原文与谐音都去掉字间空格变成连续汉字后，
 * 两行字数相同时用「相同字号 + 相同字距 + 居中」即可逐字对齐。
 * 韩日语不去空格、不逐字对齐——谐音按词/读音单位分隔。
 * 颜色随当前歌词主题（[theme]）变化。
 */
@Composable
fun LyricLineBlock(
    line: LyricLine,
    jyutping: String = "",
    jyutpingToneless: String = "",
    charAligned: Boolean = true,
    displayMode: Int = 0,
    isYue: Boolean = true,
    theme: LyricsTheme
) {
    val hasJ = jyutping.isNotBlank()
    val hasJT = jyutpingToneless.isNotBlank()
    val hasM = line.mandarin.isNotBlank()
    val hasH = line.homophone.isNotBlank()
    if (!hasJ && !hasJT && !hasM && !hasH) return

    if (isYue) {
        // ── 粤语显示模式 ──
        val cjk = isCjkLine(line.mandarin) || isCjkLine(line.homophone)
        val mandarin = if (cjk) line.mandarin.replace(" ", "") else line.mandarin
        val homophone = if (cjk) line.homophone.replace(" ", "") else line.homophone
        val letterSpacing: Float = if (cjk) {
            val config = LocalConfiguration.current
            val availableW = (config.screenWidthDp - 60).coerceAtLeast(160)
            calcLyricLetterSpacing(maxOf(mandarin.length, homophone.length), availableW)
        } else 0f

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (displayMode) {
                0 -> { // 粤拼+中文
                    if (hasJ) Text(text = jyutping, color = theme.jyutping, fontSize = 13.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = mandarin, color = theme.mandarin, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                }
                1 -> { // 谐音+中文
                    if (hasH) Text(text = homophone, color = theme.homophone, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = mandarin, color = theme.mandarin, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                }
                2 -> { // 粤拼带调+中文+谐音
                    if (hasJ) Text(text = jyutping, color = theme.jyutping, fontSize = 13.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = mandarin, color = theme.mandarin, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                    if (hasH) Text(text = homophone, color = theme.homophone, fontSize = 20.sp, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                }
                else -> { // 粤拼不带调+中文+谐音
                    if (hasJT) Text(text = jyutpingToneless, color = theme.jyutping, fontSize = 13.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = mandarin, color = theme.mandarin, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                    if (hasH) Text(text = homophone, color = theme.homophone, fontSize = 20.sp, letterSpacing = letterSpacing.sp, textAlign = TextAlign.Center)
                }
            }
        }
    } else {
        // ── 韩日语显示模式 ──
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (displayMode) {
                0 -> { // 谐音+原文
                    if (hasH) Text(text = line.homophone, color = theme.homophone, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (hasM) Text(text = line.mandarin, color = theme.mandarin, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                1 -> { // 罗马音+原文
                    if (hasJ) Text(text = jyutping, color = theme.jyutping, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (hasM) Text(text = line.mandarin, color = theme.mandarin, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                2 -> { // 谐音+罗马音+原文
                    if (hasH) Text(text = line.homophone, color = theme.homophone, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (hasJ) Text(text = jyutping, color = theme.jyutping, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = line.mandarin, color = theme.mandarin, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
                else -> { // 罗马音+谐音+原文
                    if (hasJ) Text(text = jyutping, color = theme.jyutping, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (hasH) Text(text = line.homophone, color = theme.homophone, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (hasM) Text(text = line.mandarin, color = theme.mandarin, fontSize = 13.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** 主题选择弹窗：每套模板渲染「我吻你吻上太空」三行样例，直观看出配色效果（弹窗框架固定配色） */
@Composable
fun ThemePickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentIndex = SettingsStore.themeIndex
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择主题模板", color = Chrome.text) },
        text = {
            // 7 套模板整列高约 830dp，超过一屏；AlertDialog 的 text 槽不自带滚动，
            // 不包 verticalScroll 时底部（少女粉/复古米）会被裁掉
            Column(Modifier.verticalScroll(rememberScrollState())) {
                LyricsThemes.forEachIndexed { index, t ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .border(1.dp, Chrome.outline, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .background(t.background)
                            .clickable {
                                SettingsStore.saveTheme(context, index)
                                onDismiss()
                            }
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, color = t.mandarin, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            if (index == currentIndex) {
                                Text("当前", color = t.jyutping, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // 三行样例：粤拼 / 普通话 / 中文谐音（取「我吻你吻上太空」）
                        Text(
                            "ngo5 man2 nei5 man2 soeng6 taai3 hung1",
                            color = t.jyutping,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "我 吻 你 吻 上 太 空",
                            color = t.mandarin,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "我 门 内 门 上 太 轰",
                            color = t.homophone,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = Chrome.text)
            }
        }
    )
}

/** 版本管理弹窗：状态块 + 5 个操作（按状态机禁用）+ 放弃修改确认（固定配色） */
@Composable
private fun VersionDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val song = LyricsRepository.currentSong ?: return
    var busy by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val usingLocal = song.preferLocal && song.hasLocalEdit
    val canSync = song.trackId != null

    // 放弃修改确认（独立弹窗，避免嵌套）
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("放弃我的修改？", color = Chrome.text) },
            text = {
                Text(
                    "将删除你的本地编辑，且无法恢复。",
                    color = Chrome.muted,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    LyricsRepository.discardMyEdits()
                    showDiscardConfirm = false
                }) {
                    Text("放弃修改", color = Chrome.text)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("取消", color = Chrome.muted)
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("版本管理", color = Chrome.text) },
        text = {
            Column {
                // ── 状态块 ──
                Text("服务端版本：v${song.serverVersion}", color = Chrome.muted, fontSize = 13.sp)
                Text("本地编辑：${if (song.hasLocalEdit) "有" else "无"}", color = Chrome.muted, fontSize = 13.sp)
                Text("当前使用：${if (usingLocal) "我的版本" else "服务端版本"}", color = Chrome.muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                // ── 操作 ──
                VersionAction("同步最新", canSync && !busy) {
                    scope.launch {
                        busy = true
                        try {
                            LyricsRepository.syncLatest()
                        } finally {
                            busy = false
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                VersionAction("用我的版本", song.hasLocalEdit && !busy) {
                    LyricsRepository.useMyVersion()
                }
                Spacer(Modifier.height(8.dp))
                VersionAction("用服务端版本", song.hasLocalEdit && !busy) {
                    LyricsRepository.useServerVersion()
                }
                Spacer(Modifier.height(8.dp))
                VersionAction("放弃我的修改", song.hasLocalEdit && !busy) {
                    showDiscardConfirm = true
                }
                Spacer(Modifier.height(8.dp))
                VersionAction("服务端重新生成", canSync && !busy) {
                    scope.launch {
                        busy = true
                        try {
                            LyricsRepository.refreshAndSync()
                        } finally {
                            busy = false
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    if (canSync) {
                        "同步/重新生成会覆盖服务端版文件，不影响你的本地编辑。"
                    } else {
                        "内置歌曲无服务端文档，只能本地编辑与切换版本。"
                    },
                    color = Chrome.muted,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!busy) onDismiss() }) {
                Text("关闭", color = Chrome.text)
            }
        }
    )
}

/** 版本管理里的一个操作按钮（整行，固定配色） */
@Composable
private fun VersionAction(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Chrome.text, fontSize = 14.sp)
    }
}

/** 单行歌词编辑弹窗：4 字段，标签按语言区分（粤拼/罗马音/原文/中文谐音），固定配色 */
@Composable
private fun LineEditDialog(
    index: Int,
    line: LyricLine,
    language: String,
    onDismiss: () -> Unit,
    onSave: (LyricLine) -> Unit
) {
    // 字段标签按语言：粤语=粤拼两版，韩日语两注音字段同存罗马音
    val labels = when (language) {
        "ko" -> listOf("罗马音", "罗马音（同上）", "韩语原文", "中文谐音")
        "ja" -> listOf("罗马音", "罗马音（同上）", "日语原文", "中文谐音")
        else -> listOf("粤拼（带调）", "粤拼（不带调）", "普通话原文", "中文谐音")
    }
    // 弹窗每次显示都是全新组合（editingLineIndex 置空即离开组合），故无需 key，直接取当前行初始化
    var jyutping by remember { mutableStateOf(line.jyutping) }
    var jyutpingToneless by remember { mutableStateOf(line.jyutpingToneless) }
    var mandarin by remember { mutableStateOf(line.mandarin) }
    var homophone by remember { mutableStateOf(line.homophone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑第 ${index + 1} 行", color = Chrome.text) },
        text = {
            Column {
                LineField(labels[0], jyutping) { jyutping = it }
                Spacer(Modifier.height(8.dp))
                LineField(labels[1], jyutpingToneless) { jyutpingToneless = it }
                Spacer(Modifier.height(8.dp))
                LineField(labels[2], mandarin) { mandarin = it }
                Spacer(Modifier.height(8.dp))
                LineField(labels[3], homophone) { homophone = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(LyricLine(jyutping, jyutpingToneless, mandarin, homophone))
            }) {
                Text("保存", color = Chrome.text)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Chrome.muted)
            }
        }
    )
}

/** 编辑弹窗里的一个字段：小标签 + 单行输入框（固定配色） */
@Composable
private fun LineField(label: String, value: String, onValueChange: (String) -> Unit) {
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
