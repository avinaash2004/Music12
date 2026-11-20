package com.offlinemusicpro.player.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinemusicpro.player.Route
import com.offlinemusicpro.player.core.common.LabeledSlider
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.common.SectionTitle
import com.offlinemusicpro.player.core.common.rememberContainer
import com.offlinemusicpro.player.core.theme.AccentOptions
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.data.repository.AppSettings
import com.offlinemusicpro.player.features.songs.SongsUiState
import com.offlinemusicpro.player.features.songs.SongsViewModel
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    state: SongsUiState,
    viewModel: SongsViewModel,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    val repo = rememberContainer().settingsRepository
    val scope = rememberCoroutineScope()
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var crossfade by remember(settings.crossfadeSec) { mutableFloatStateOf(settings.crossfadeSec.toFloat()) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings", onBack)

        SectionTitle("Appearance")
        Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AccentOptions.forEachIndexed { index, accent ->
                val selected = index == settings.accentIndex
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.primary)
                        .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                        .clickable { scope.launch { repo.setAccent(index) } }
                )
            }
        }
        Text(
            "Accent colour: ${AccentOptions.getOrNull(settings.accentIndex)?.name ?: "Purple"}",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        SectionTitle("Playback")
        LabeledSlider(
            label = "Fade between songs",
            valueText = if (crossfade.toInt() == 0) "Off" else "${crossfade.toInt()} s",
            value = crossfade,
            range = 0f..12f,
            steps = 11,
            onChange = { crossfade = it },
            onFinished = { scope.launch { repo.setCrossfade(crossfade.toInt()) } }
        )
        Text(
            "Songs fade out and the next one fades in. Gapless playback is automatic for supported files.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        SettingsRow(
            title = "Shake to change song",
            subtitle = "Only listens to the motion sensor while music is playing",
            trailing = { Switch(checked = settings.shake, onCheckedChange = { scope.launch { repo.setShake(it) } }) },
            onClick = { scope.launch { repo.setShake(!settings.shake) } }
        )

        SectionTitle("Library")
        SettingsRow(
            title = "Excluded folders",
            subtitle = if (settings.excludedFolders.isEmpty()) "Nothing excluded" else "${settings.excludedFolders.size} excluded",
            onClick = { onNavigate(Route.EXCLUDED) }
        )
        SettingsRow(
            title = "Duplicate songs",
            subtitle = "Find songs that exist more than once",
            onClick = { onNavigate(Route.DUPLICATES) }
        )

        SectionTitle("Tools")
        SettingsRow("Audio trimmer", "Cut a section out of a song and save it", onClick = { onNavigate(Route.TRIMMER) })
        SettingsRow("Backup and restore", "Favorites, playlists, history, presets, settings", onClick = { onNavigate(Route.BACKUP) })
        SettingsRow("Clear play history", "Empties the Recently played list", onClick = { confirmClear = true })

        SectionTitle("About")
        Text(
            "Offline Music Pro has no internet permission, no ads, no analytics and no account. " +
                "Everything stays on your device.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = CardDark,
            title = { Text("Clear play history?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearHistory(); confirmClear = false }) {
                    Text("Clear", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        trailing?.invoke()
    }
}
