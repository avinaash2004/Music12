package com.offlinemusicpro.player

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinemusicpro.player.core.theme.AccentOptions
import com.offlinemusicpro.player.core.theme.OfflineMusicProTheme
import com.offlinemusicpro.player.data.repository.AppSettings

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val settingsRepo = (application as OfflineMusicApp).container.settingsRepository
        setContent {
            val settings by settingsRepo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            OfflineMusicProTheme(accent = AccentOptions.getOrElse(settings.accentIndex) { AccentOptions[0] }) {
                AppRoot()
            }
        }
    }
}
