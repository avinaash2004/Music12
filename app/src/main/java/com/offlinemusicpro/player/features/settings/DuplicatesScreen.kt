package com.offlinemusicpro.player.features.settings

import android.app.Activity
import android.app.RecoverableSecurityException
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.offlinemusicpro.player.core.utils.formatDuration
import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.features.songs.SongsUiState

@Composable
fun DuplicatesScreen(state: SongsUiState, onBack: () -> Unit) {
    val context = LocalContext.current
    val groups = remember(state.librarySongs) { findDuplicateGroups(state.librarySongs) }
    var pending by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        // Android 10 needs the delete repeated after the user grants access; 11+ deletes by itself.
        if (result.resultCode == Activity.RESULT_OK && Build.VERSION.SDK_INT < 30) {
            pending?.let { runCatching { context.contentResolver.delete(it, null, null) } }
        }
        pending = null
    }

    fun requestDelete(song: Song) {
        val resolver = context.contentResolver
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = MediaStore.createDeleteRequest(resolver, listOf(song.uri))
                launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
            } else {
                resolver.delete(song.uri, null, null)
            }
        } catch (e: RecoverableSecurityException) {
            pending = song.uri
            launcher.launch(IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build())
        } catch (_: SecurityException) {
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Duplicate songs", onBack)
        if (groups.isEmpty()) {
            Text("No duplicates found.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(groups, key = { group -> group.joinToString(",") { it.id.toString() } }) { group ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "${group.first().title} · ${group.first().artist}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    group.forEach { song ->
                        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.folder.ifEmpty { "Unknown folder" } + song.displayName,
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${formatDuration(song.durationMs)} · ${Formatter.formatShortFileSize(context, song.sizeBytes)}",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { requestDelete(song) }) {
                                Text("Delete", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
    }
}
