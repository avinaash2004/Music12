package com.offlinemusicpro.player.features.trimmer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.features.songs.SongsUiState
import com.offlinemusicpro.player.playback.PlaybackEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimmerScreen(state: SongsUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Uri?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var durationMs by remember { mutableLongStateOf(0L) }
    var startMs by remember { mutableFloatStateOf(0f) }
    var endMs by remember { mutableFloatStateOf(0f) }
    var destTree by remember { mutableStateOf<Uri?>(null) }
    var destName by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val preview = remember { TrimPreview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    fun setSource(uri: Uri, name: String) {
        preview.stop()
        source = uri
        sourceName = name
        fileName = name.substringBeforeLast('.') + " (trimmed)"
        message = null
        durationMs = 0L
        scope.launch {
            val d = withContext(Dispatchers.IO) { AudioTrimmer.durationMs(context, uri) }
            durationMs = d
            startMs = 0f
            endMs = d.toFloat()
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) setSource(uri, displayName(context, uri))
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            destTree = uri
            destName = uri.lastPathSegment?.substringAfterLast(':')?.ifEmpty { "Storage root" } ?: "Selected folder"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("Audio trimmer", onBack)

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1. Choose a song", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showPicker = true }) { Text("From my library") }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Browse files") }
            }
            if (sourceName.isNotEmpty()) {
                Text(sourceName, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            if (source != null && durationMs > 0) {
                Text("2. Select the part to keep", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                RangeSlider(
                    value = startMs..endMs,
                    onValueChange = { range ->
                        startMs = range.start
                        endMs = range.endInclusive
                    },
                    valueRange = 0f..durationMs.toFloat()
                )
                TimeAdjustRow("Start", startMs) { delta ->
                    startMs = (startMs + delta).coerceIn(0f, (endMs - 500f).coerceAtLeast(0f))
                }
                TimeAdjustRow("End", endMs) { delta ->
                    endMs = (endMs + delta).coerceIn((startMs + 500f).coerceAtMost(durationMs.toFloat()), durationMs.toFloat())
                }
                OutlinedButton(onClick = {
                    if (preview.playing) {
                        preview.stop()
                    } else {
                        PlaybackEngine.player?.pause()
                        preview.start(source!!, startMs.toLong(), endMs.toLong())
                    }
                }) { Text(if (preview.playing) "Stop preview" else "Preview selection") }

                Text("3. Save", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                TextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    singleLine = true,
                    label = { Text("File name (saved as .m4a)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("Choose folder") }
                    Text(
                        if (destName.isEmpty()) "No folder chosen" else destName,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Button(
                    enabled = !busy && destTree != null && fileName.isNotBlank(),
                    onClick = {
                        preview.stop()
                        busy = true
                        progress = 0f
                        message = null
                        val uri = source!!
                        val tree = destTree!!
                        val from = startMs.toLong()
                        val to = endMs.toLong()
                        val name = fileName
                        scope.launch {
                            val result = withContext(Dispatchers.Default) {
                                AudioTrimmer.trim(context, uri, from, to, tree, name) { progress = it }
                            }
                            busy = false
                            when (result) {
                                is TrimResult.Success -> {
                                    isError = false
                                    message = "Saved \"$name.m4a\". ${result.note}"
                                }
                                is TrimResult.Failure -> {
                                    isError = true
                                    message = result.message
                                }
                            }
                        }
                    }
                ) { Text(if (busy) "Trimming…" else "Trim and save") }
                if (busy) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }

            message?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "AAC/M4A files are cut losslessly. MP3, FLAC, WAV, OGG and other formats your phone can decode " +
                    "are re-encoded to AAC. Formats Android cannot decode are reported instead of failing silently.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            containerColor = CardDark,
            title = { Text("Choose a song") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(state.librarySongs, key = { it.id }) { song ->
                        Column(
                            Modifier.fillMaxWidth().clickable {
                                setSource(song.uri, song.displayName)
                                showPicker = false
                            }.padding(vertical = 10.dp)
                        ) {
                            Text(song.title, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeAdjustRow(label: String, valueMs: Float, onAdjust: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label  ${formatPrecise(valueMs.toLong())}", color = TextPrimary, modifier = Modifier.weight(1f))
        TextButton(onClick = { onAdjust(-1000f) }) { Text("-1 s") }
        TextButton(onClick = { onAdjust(-100f) }) { Text("-0.1") }
        TextButton(onClick = { onAdjust(100f) }) { Text("+0.1") }
        TextButton(onClick = { onAdjust(1000f) }) { Text("+1 s") }
    }
}

private fun formatPrecise(ms: Long): String {
    val minutes = ms / 60_000
    val seconds = (ms % 60_000) / 1000f
    return String.format(Locale.US, "%d:%04.1f", minutes, seconds)
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) return c.getString(0).orEmpty().ifEmpty { "audio" }
    }
    return uri.lastPathSegment ?: "audio"
}
