package com.loomz.cantonese.lyrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.loomz.cantonese.lyrics.data.LyricsRepository
import com.loomz.cantonese.lyrics.data.SettingsStore
import com.loomz.cantonese.lyrics.ui.Chrome
import com.loomz.cantonese.lyrics.ui.MainScreen
import com.loomz.cantonese.lyrics.ui.SearchScreen
import com.loomz.cantonese.lyrics.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SettingsStore.init(this)
        LyricsRepository.init(this)
        setContent {
            // 固定「非歌词区」配色：所有 Material3 组件（弹窗/按钮/输入框/开关）都用这套，
            // 不随歌词主题（LyricsThemes）变化。只有歌词画布与三行歌词文字随主题变。
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Chrome.accent,
                    secondary = Chrome.accent,
                    background = Chrome.background,
                    onBackground = Chrome.text,
                    surface = Chrome.surface,
                    onSurface = Chrome.text,
                    outline = Chrome.outline,
                    error = Chrome.error
                )
            ) {
                var screen by remember { mutableStateOf("main") }
                val context = LocalContext.current
                when (screen) {
                    "search" -> SearchScreen(onBack = { screen = "main" })
                    "settings" -> SettingsScreen(context = context, onBack = { screen = "main" })
                    else -> MainScreen(
                        onOpenSearch = { screen = "search" },
                        onOpenSettings = { screen = "settings" }
                    )
                }
            }
        }
    }
}
