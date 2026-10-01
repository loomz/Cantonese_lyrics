package com.loomz.cantonese.lyrics.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loomz.cantonese.lyrics.data.AppSettings
import com.loomz.cantonese.lyrics.data.AppUpdater
import com.loomz.cantonese.lyrics.data.LyricsApiClient
import com.loomz.cantonese.lyrics.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 设置页：歌词服务地址 + 粤拼声调显示 + 谐音标注说明（固定配色） */
@Composable
fun SettingsScreen(context: android.content.Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()

    var apiserverUrl by remember { mutableStateOf(SettingsStore.settings.apiserverUrl) }
    var showTone by remember { mutableStateOf(SettingsStore.settings.showTone) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    // ── 版本升级 ──
    val currentCode = remember { AppUpdater.currentVersionCode(context) }
    val currentName = remember { AppUpdater.currentVersionName(context) }
    var checking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<AppUpdater.ApkInfo?>(null) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var needPermission by remember { mutableStateOf(false) }

    fun checkUpdate() {
        if (checking) return
        checking = true
        updateStatus = null
        scope.launch {
            val info = withContext(Dispatchers.IO) { AppUpdater.checkLatest() }
            checking = false
            when {
                info == null -> updateStatus = "检查失败（服务未部署 APK 或网络问题）"
                info.versionCode <= currentCode -> updateStatus = "已是最新版本（v${info.versionName}）"
                else -> {
                    needPermission = !AppUpdater.canInstall(context)
                    updateInfo = info
                }
            }
        }
    }

    fun downloadAndInstall(info: AppUpdater.ApkInfo) {
        if (downloading) return
        // 未允许「未知来源应用」时先跳系统设置，允许后返回再点一次
        if (!AppUpdater.canInstall(context)) {
            AppUpdater.openInstallPermission(context)
            needPermission = true
            return
        }
        downloading = true
        progress = 0
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    AppUpdater.download(context, info) { p -> progress = p }
                }
                AppUpdater.launchInstall(context, file)
                updateInfo = null
            } catch (e: Exception) {
                updateStatus = "下载失败：${e.message}"
            } finally {
                downloading = false
            }
        }
    }

    fun save() {
        SettingsStore.saveSettings(
            context,
            AppSettings(apiserverUrl = apiserverUrl.trim().ifBlank { "http://192.168.3.89:8000" }, showTone = showTone)
        )
        saved = true
    }

    fun test() {
        testing = true
        testResult = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    LyricsApiClient.healthWith(apiserverUrl.trim())
                    true
                } catch (e: Exception) {
                    false
                }
            }
            testing = false
            testResult = if (r) "✓ 连接成功" else "✗ 连接失败（检查地址与网络）"
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Chrome.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── 顶栏：返回 + 标题 ──
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
            Text(
                "设置",
                color = Chrome.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
        ) {
            // ── 歌词服务地址 ──
            item {
                Column {
                    Text("歌词服务地址", color = Chrome.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = apiserverUrl,
                        onValueChange = { apiserverUrl = it },
                        placeholder = { Text("http://192.168.3.89:8000", color = Chrome.muted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在线搜歌 / 同步歌曲文档（服务端生成）都走这个服务；手机真机需填电脑局域网 IP",
                        color = Chrome.muted,
                        fontSize = 12.sp
                    )
                }
            }

            // ── 粤拼声调显示 ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("粤拼显示声调数字", color = Chrome.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text("开：nei5 si6 nei5　关：nei si nei（默认关）", color = Chrome.muted, fontSize = 12.sp)
                    }
                    Switch(
                        checked = showTone,
                        onCheckedChange = { showTone = it }
                    )
                }
            }

            // ── 歌词生成说明 ──
            item {
                Column {
                    Text("歌词生成", color = Chrome.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "歌词由服务端自动生成，客户端负责同步与本地编辑。",
                        color = Chrome.muted,
                        fontSize = 13.sp
                    )
                }
            }

            // ── 版本升级 ──
            item {
                Column {
                    Text("版本升级", color = Chrome.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "当前版本 v$currentName（code $currentCode）",
                        color = Chrome.muted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { checkUpdate() },
                        enabled = !checking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (checking) "检查中…" else "升级版本", color = Chrome.text)
                    }
                    if (updateStatus != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            updateStatus!!,
                            color = if (updateStatus!!.startsWith("检查失败")) Chrome.error else Chrome.text,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── 保存 / 测试 ──
            item {
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (saved) "已保存 ✓" else "保存设置", color = Chrome.text)
                    }
                    OutlinedButton(onClick = { test() }, enabled = !testing, modifier = Modifier.fillMaxWidth()) {
                        Text(if (testing) "测试中…" else "测试歌词服务连接", color = Chrome.text)
                    }
                    if (testResult != null) {
                        Text(
                            testResult!!,
                            color = if (testResult!!.startsWith("✓")) Chrome.text else Chrome.error,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ── 关于（版本号） ──
            item {
                Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text("关于", color = Chrome.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("粤拼歌词", color = Chrome.muted, fontSize = 13.sp)
                    Text(
                        "版本 v$currentName（code $currentCode）",
                        color = Chrome.muted,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // ── 更新弹窗 ──
    if (updateInfo != null) {
        UpdateDialog(
            info = updateInfo!!,
            needPermission = needPermission,
            downloading = downloading,
            progress = progress,
            onDismiss = { if (!downloading) updateInfo = null },
            onDownload = { downloadAndInstall(updateInfo!!) }
        )
    }
}

/** 版本更新弹窗：版本信息 + 更新说明 + 下载进度 + 安装（固定配色） */
@Composable
private fun UpdateDialog(
    info: AppUpdater.ApkInfo,
    needPermission: Boolean,
    downloading: Boolean,
    progress: Int,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    val sizeMb = if (info.size > 0) "${"%.1f".format(info.size / 1048576.0)} MB" else ""
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发现新版本 v${info.versionName}", color = Chrome.text) },
        text = {
            Column {
                if (info.changelog.isNotBlank()) {
                    Text(info.changelog, color = Chrome.muted, fontSize = 13.sp)
                    if (sizeMb.isNotBlank()) Spacer(Modifier.height(4.dp))
                }
                if (sizeMb.isNotBlank()) {
                    Text("大小：$sizeMb", color = Chrome.muted, fontSize = 12.sp)
                }
                if (needPermission && !downloading) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "⚠ 需先允许「未知来源应用」。点下方按钮会跳到系统设置，允许后返回再点一次。",
                        color = Chrome.error,
                        fontSize = 12.sp
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "下载中 $progress%",
                        color = Chrome.muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload, enabled = !downloading) {
                Text(if (downloading) "下载中…" else "下载并安装", color = Chrome.text)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) {
                Text("取消", color = Chrome.muted)
            }
        }
    )
}
