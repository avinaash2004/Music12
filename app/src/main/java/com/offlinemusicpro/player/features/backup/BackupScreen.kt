package com.offlinemusicpro.player.features.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.common.rememberContainer
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.playback.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                status = try {
                    val json = container.backupRepository.export()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            ?: error("Could not write to the chosen location")
                    }
                    "Backup saved."
                } catch (e: Exception) {
                    "Backup failed: ${e.message}"
                }
                busy = false
            }
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                status = try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    } ?: error("Could not read the file")
                    val result = container.backupRepository.import(text)
                    PlaybackEngine.effects.setState(container.settingsRepository.eqState(), notify = false)
                    "Restored ${result.favorites} favorites, ${result.history} history entries, " +
                        "${result.playlists} playlists, ${result.presets} equalizer presets and your settings."
                } catch (e: Exception) {
                    "Restore failed: ${e.message}"
                }
                busy = false
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader("Backup and restore", onBack)
        Text(
            "Saves favorites, playlists, play history, equalizer presets and settings to a JSON file you choose. " +
                "Songs are matched by title, artist and length, so a backup also works after reinstalling.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Button(enabled = !busy, onClick = { exporter.launch("offline-music-pro-backup.json") }) { Text("Create backup") }
        OutlinedButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "*/*")) }) {
            Text("Restore from file")
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium) }
    }
}
