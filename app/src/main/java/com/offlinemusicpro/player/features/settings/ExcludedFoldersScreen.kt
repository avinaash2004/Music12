package com.offlinemusicpro.player.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.common.rememberContainer
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.features.songs.SongsUiState
import kotlinx.coroutines.launch

@Composable
fun ExcludedFoldersScreen(state: SongsUiState, onBack: () -> Unit) {
    val repo = rememberContainer().settingsRepository
    val scope = rememberCoroutineScope()
    val folders = (state.folders + state.excludedFolders).distinct().sorted()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Excluded folders", onBack)
        Text(
            "Songs in excluded folders are hidden from the app. Nothing is deleted.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (folders.isEmpty()) {
            Text("No folders found yet.", color = TextSecondary, modifier = Modifier.padding(24.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(folders, key = { it }) { folder ->
                val excluded = folder in state.excludedFolders
                val toggle = {
                    scope.launch {
                        repo.setExcluded(if (excluded) state.excludedFolders - folder else state.excludedFolders + folder)
                    }
                    Unit
                }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle() }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(folder, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = excluded, onCheckedChange = { toggle() })
                }
            }
        }
    }
}
