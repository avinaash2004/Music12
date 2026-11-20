package com.offlinemusicpro.player.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.features.songs.SongsUiState
import com.offlinemusicpro.player.features.songs.SongsViewModel
import com.offlinemusicpro.player.playback.QueueItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun QueueScreen(state: SongsUiState, viewModel: SongsViewModel, onBack: () -> Unit) {
    val player = viewModel.player
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf(emptyList<QueueItem>()) }

    LaunchedEffect(state.playback.queueSize, state.playback.currentSongId) {
        items = player.queueSnapshot()
    }
    fun refreshSoon() {
        scope.launch { delay(150); items = player.queueSnapshot() }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Queue (${items.size})", onBack)
        if (items.isEmpty()) {
            Text("The queue is empty.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(items) { index, item ->
                val current = index == state.playback.queueIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { player.skipToQueueItem(index) }
                        .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (current) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Playing", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                    } else {
                        Text("${index + 1}", color = TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            item.title,
                            color = if (current) MaterialTheme.colorScheme.secondary else TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(item.artist, color = TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(enabled = index > 0, onClick = { player.moveQueueItem(index, index - 1); refreshSoon() }) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", tint = TextSecondary)
                    }
                    IconButton(enabled = index < items.lastIndex, onClick = { player.moveQueueItem(index, index + 1); refreshSoon() }) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", tint = TextSecondary)
                    }
                    IconButton(onClick = { player.removeQueueItem(index); refreshSoon() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove from queue", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
