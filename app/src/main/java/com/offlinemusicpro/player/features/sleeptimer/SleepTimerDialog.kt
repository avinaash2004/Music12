package com.offlinemusicpro.player.features.sleeptimer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.core.utils.formatDuration
import com.offlinemusicpro.player.playback.MusicPlayerManager
import com.offlinemusicpro.player.playback.PlaybackEngine

@Composable
fun SleepTimerDialog(player: MusicPlayerManager, onDismiss: () -> Unit) {
    val remaining by PlaybackEngine.sleepTimer.remainingMs.collectAsStateWithLifecycle()
    val afterSong by PlaybackEngine.sleepTimer.stopAfterSong.collectAsStateWithLifecycle()
    var custom by remember { mutableStateOf("") }
    val active = remaining != null || afterSong

    val status = when {
        remaining != null -> "Stopping in ${formatDuration(remaining ?: 0L)}"
        afterSong -> "Playback will stop after this song"
        else -> "Volume fades out gradually before playback stops."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Sleep timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                listOf(5, 10, 15, 30, 45, 60).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { minutes ->
                            FilterChip(
                                selected = false,
                                onClick = { player.startSleepTimer(minutes); onDismiss() },
                                label = { Text("$minutes min") }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = custom,
                        onValueChange = { custom = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        label = { Text("Custom (minutes)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = custom.toIntOrNull()?.let { it in 1..720 } == true,
                        onClick = { player.startSleepTimer(custom.toInt()); onDismiss() }
                    ) { Text("Start", color = MaterialTheme.colorScheme.secondary) }
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = { player.stopAfterCurrentSong(); onDismiss() }) {
                    Text("Stop after current song", color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = { player.cancelSleepTimer(); onDismiss() }) {
                    Text("Cancel timer", color = MaterialTheme.colorScheme.secondary)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = if (active) { { TextButton(onClick = onDismiss) { Text("Close") } } } else null
    )
}
