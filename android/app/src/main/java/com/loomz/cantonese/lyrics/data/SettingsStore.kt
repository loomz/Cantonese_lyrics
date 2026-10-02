package com.loomz.cantonese.lyrics.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/** 应用设置：歌词服务地址（apiserver）+ 粤拼声调显示 + 韩日语主行显示 */
data class AppSettings(
    val apiserverUrl: String = "http://192.168.3.89:8000",
    val showTone: Boolean = false,
    val kojaRoma: Boolean = false // 韩日语主行：false=中文谐音，true=罗马音
)

object SettingsStore {
    private const val PREFS = "cl_settings"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_THEME = "theme_index"

    var settings by mutableStateOf(AppSettings())
        private set
    var themeIndex by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_SETTINGS, null)
        if (json != null) {
            try {
                val o = JSONObject(json)
                settings = AppSettings(
                    apiserverUrl = o.optString("apiserverUrl", "http://192.168.3.89:8000"),
                    showTone = o.optBoolean("showTone", false),
                    kojaRoma = o.optBoolean("kojaRoma", false)
                )
            } catch (_: Exception) {
            }
        }
        themeIndex = sp.getInt(KEY_THEME, 0)
    }

    fun saveSettings(context: Context, s: AppSettings) {
        settings = s
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(
                KEY_SETTINGS,
                JSONObject().apply {
                    put("apiserverUrl", s.apiserverUrl)
                    put("showTone", s.showTone)
                    put("kojaRoma", s.kojaRoma)
                }.toString()
            )
            .apply()
    }

    fun saveTheme(context: Context, index: Int) {
        themeIndex = index
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_THEME, index)
            .apply()
    }
}
