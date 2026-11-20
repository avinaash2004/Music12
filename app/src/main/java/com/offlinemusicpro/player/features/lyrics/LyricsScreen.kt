package com.offlinemusicpro.player.features.lyrics

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.common.rememberContainer
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.data.repository.AppSettings
import com.offlinemusicpro.player.features.songs.SongsUiState
import com.offlinemusicpro.player.features.songs.SongsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsScreen(state: SongsUiState, viewModel: SongsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val song = state.currentSong
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val manualUri = song?.let { settings.lyricsUris[it.id] }

    var lyrics by remember { mutableStateOf<Lyrics?>(null) }
    var loading by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }

    LaunchedEffect(song?.id, manualUri) {
        loading = true
        lyrics = song?.let { container.lyricsRepository.load(it, manualUri) }
        loading = false
    }
    LaunchedEffect(Unit) {
        while (true) {
            position = viewModel.player.positionMs()
            delay(200)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && song != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            scope.launch { container.settingsRepository.setLyricsUri(song.id, uri.toString()) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Lyrics", onBack) {
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = song != null) {
                Text("Choose file", color = MaterialTheme.colorScheme.secondary)
            }
        }
        song?.let {
            Text(
                "${it.title} · ${it.artist}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
        val current = lyrics
        when {
            loading -> CenteredMessage { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            current == null -> CenteredMessage {
                Text(
                    "No lyrics found for this song.\n\nPlace a .lrc file next to it, use lyrics embedded in the file, " +
                        "or choose a .lrc / .txt file manually with \"Choose file\". Lyrics never come from the internet.",
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
            current.synced -> {
                val lines = current.lines
                val activeIndex by remember(lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= position } } }
                val listState = rememberLazyListState()
                LaunchedEffect(activeIndex) {
                    if (activeIndex >= 0) listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lines) { index, line ->
                        val active = index == activeIndex
                        Text(
                            line.text.ifEmpty { "♪" },
                            color = if (active) MaterialTheme.colorScheme.secondary else TextSecondary.copy(alpha = 0.7f),
                            style = if (active) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                            else MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.player.seekTo(line.timeMs) }
                        )
                    }
                }
            }
            else -> Text(
                current.plain.orEmpty(),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)
            )
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        content()
    }
}
