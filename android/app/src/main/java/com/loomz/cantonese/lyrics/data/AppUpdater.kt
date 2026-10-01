package com.loomz.cantonese.lyrics.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 应用内升级：查服务端最新版本（GET /api/apk/latest）、下载 APK、触发系统安装。
 * 服务端接口与 APK 部署见 apiserver/main.py 与 deploy_apk.sh（同一 apiserver）。
 *
 * 流程：checkLatest() → 比较 versionCode → download()（进度 + sha256 校验）→ launchInstall()。
 * 安装需用户先在系统设置允许「未知来源应用」（[canInstall] / [openInstallPermission]）。
 *
 * checkLatest / download 均为阻塞 IO：调用方须在 Dispatchers.IO 里执行。
 */
object AppUpdater {

    class UpdateException(message: String) : Exception(message)

    data class ApkInfo(
        val versionCode: Int,
        val versionName: String,
        val size: Long,
        val sha256: String,
        val changelog: String,
        val url: String
    )

    private const val APK_DIR_NAME = "update"
    private const val APK_FILE_NAME = "cantonese-lyrics.apk"
    private const val MIME_APK = "application/vnd.android.package-archive"

    /** 当前安装版本（versionCode）。 */
    fun currentVersionCode(context: Context): Int =
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode

    /** 当前安装版本名（versionName，如 "1.0"）。 */
    fun currentVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""

    /** 查服务端最新版本；未部署 / 网络错误返回 null。阻塞 IO。 */
    fun checkLatest(): ApkInfo? {
        val body = try {
            LyricsApiClient.get("${LyricsApiClient.base()}/api/apk/latest", 5_000, 10_000)
        } catch (_: Exception) {
            return null
        }
        val o = JSONObject(body)
        return ApkInfo(
            versionCode = o.optInt("versionCode", 0),
            versionName = o.optString("versionName", ""),
            size = o.optLong("size", 0L),
            sha256 = o.optString("sha256", ""),
            changelog = o.optString("changelog", ""),
            url = o.optString("url", "/api/apk/download")
        )
    }

    /**
     * 下载 APK 到 filesDir/update/，按 Content-Length 回调进度 0–100；
     * info.sha256 非空时校验，不匹配则删文件并抛 [UpdateException]。阻塞 IO。
     */
    fun download(context: Context, info: ApkInfo, onProgress: (Int) -> Unit): File {
        val dir = File(context.filesDir, APK_DIR_NAME).apply { mkdirs() }
        val dest = File(dir, APK_FILE_NAME)
        val path = if (info.url.startsWith("/")) info.url else "/api/apk/download"
        val conn = URL(LyricsApiClient.base() + path).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15_000
        conn.readTimeout = 300_000
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val detail = conn.errorStream?.bufferedReader()?.use { it.readText().take(200) }.orEmpty()
                throw UpdateException("下载失败 HTTP $code：$detail")
            }
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt())
                    }
                }
            }
            if (info.sha256.isNotBlank()) {
                val actual = sha256Hex(dest)
                if (!actual.equals(info.sha256, ignoreCase = true)) {
                    dest.delete()
                    throw UpdateException("校验失败：APK 的 SHA-256 与服务端不一致")
                }
            }
            onProgress(100)
            return dest
        } finally {
            conn.disconnect()
        }
    }

    /** 是否已允许安装未知来源应用（API 26 以下无此限制，恒 true）。 */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    /** 跳到系统「未知来源应用」设置页，让用户允许本应用安装 APK（API 26+）。 */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val intent = Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null)
        )
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 用 FileProvider 把 APK 以 content URI 交给系统安装器。 */
    fun launchInstall(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.update", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, MIME_APK)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun sha256Hex(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
