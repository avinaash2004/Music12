package com.offlinemusicpro.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offlinemusicpro.player.core.theme.AmoledBlack
import com.offlinemusicpro.player.core.theme.SurfaceDark
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.features.equalizer.EqualizerScreen
import com.offlinemusicpro.player.features.backup.BackupScreen
import com.offlinemusicpro.player.features.lyrics.LyricsScreen
import com.offlinemusicpro.player.features.player.PlayerScreen
import com.offlinemusicpro.player.features.player.QueueScreen
import com.offlinemusicpro.player.features.settings.DuplicatesScreen
import com.offlinemusicpro.player.features.settings.ExcludedFoldersScreen
import com.offlinemusicpro.player.features.settings.SettingsScreen
import com.offlinemusicpro.player.features.songs.MiniPlayer
import com.offlinemusicpro.player.features.songs.SongsScreen
import com.offlinemusicpro.player.features.songs.SongsViewModel
import com.offlinemusicpro.player.features.trimmer.TrimmerScreen
import com.offlinemusicpro.player.features.visualizer.VisualizerScreen

/** The only main navigation tab. Do not add tabs: everything else lives inside Songs. */
private enum class MainTab(val label: String, val icon: ImageVector) {
    SONGS("Songs", Icons.Rounded.MusicNote)
}

@Composable
fun AppRoot(viewModel: SongsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var route by rememberSaveable { mutableStateOf(Route.SONGS) }

    BackHandler(enabled = route.parent() != null) { route = route.parent() ?: Route.SONGS }

    Crossfade(targetState = route, label = "route") { current ->
        when (current) {
            Route.SONGS -> SongsHome(
                state = state,
                viewModel = viewModel,
                onOpenPlayer = { if (state.currentSong != null) route = Route.PLAYER },
                onOpenSettings = { route = Route.SETTINGS }
            )
            else -> FullScreen {
                when (current) {
                    Route.PLAYER -> PlayerScreen(state, viewModel, onBack = { route = Route.SONGS }, onNavigate = { route = it })
                    Route.QUEUE -> QueueScreen(state, viewModel, onBack = { route = Route.PLAYER })
                    Route.EQUALIZER -> EqualizerScreen(onBack = { route = Route.PLAYER })
                    Route.VISUALIZER -> VisualizerScreen(onBack = { route = Route.PLAYER })
                    Route.LYRICS -> LyricsScreen(state, viewModel, onBack = { route = Route.PLAYER })
                    Route.SETTINGS -> SettingsScreen(state, viewModel, onBack = { route = Route.SONGS }, onNavigate = { route = it })
                    Route.EXCLUDED -> ExcludedFoldersScreen(state, onBack = { route = Route.SETTINGS })
                    Route.DUPLICATES -> DuplicatesScreen(state, onBack = { route = Route.SETTINGS })
                    Route.TRIMMER -> TrimmerScreen(state, onBack = { route = Route.SETTINGS })
                    Route.BACKUP -> BackupScreen(onBack = { route = Route.SETTINGS })
                    Route.SONGS -> Unit
                }
            }
        }
    }
}

@Composable
private fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(AmoledBlack).statusBarsPadding().navigationBarsPadding()) {
        content()
    }
}

@Composable
private fun SongsHome(
    state: com.offlinemusicpro.player.features.songs.SongsUiState,
    viewModel: SongsViewModel,
    onOpenPlayer: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val selectedTab = MainTab.SONGS
    Scaffold(
        containerColor = AmoledBlack,
        bottomBar = {
            Column {
                MiniPlayer(
                    song = state.currentSong,
                    isPlaying = state.isPlaying,
                    onPlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipNext,
                    onClick = onOpenPlayer
                )
                NavigationBar(containerColor = SurfaceDark) {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tab == selectedTab,
                            onClick = { },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.secondary,
                                selectedTextColor = MaterialTheme.colorScheme.secondary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                unselectedIconColor = TextSecondary,
                                unselectedTextColor = TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        SongsScreen(
            state = state,
            viewModel = viewModel,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.padding(padding)
        )
    }
}
