package com.offlinemusicpro.player.features.songs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.offlinemusicpro.player.core.common.Artwork
import com.offlinemusicpro.player.core.permissions.AudioPermission
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.core.utils.formatDuration
import com.offlinemusicpro.player.data.model.Song

@Composable
fun SongsScreen(
    state: SongsUiState,
    viewModel: SongsViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(AudioPermission.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var askedNotifications by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        granted = AudioPermission.isGranted(context)
    }
    LaunchedEffect(granted) { if (granted) viewModel.startScanning() }
    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    var detailsFor by remember { mutableStateOf<Song?>(null) }
    var playlistTarget by remember { mutableStateOf<List<Long>?>(null) } // songs waiting for a playlist
    var newPlaylistFor by remember { mutableStateOf<List<Long>?>(null) }
    var confirmDeletePlaylist by remember { mutableStateOf<Long?>(null) }
    val base = modifier.fillMaxSize().statusBarsPadding()

    if (!granted) {
        PermissionPrompt(
            onAllow = { launcher.launch(AudioPermission.permission) },
            onOpenSettings = { AudioPermission.openAppSettings(context) },
            modifier = base
        )
        return
    }

    Column(base) {
        AnimatedContent(targetState = state.selectionMode, label = "header") { selecting ->
            if (selecting) {
                SelectionHeader(
                    count = state.selectedIds.size,
                    onClose = viewModel::clearSelection,
                    onSelectAll = { viewModel.selectAllVisible(state.songs.map { it.id }) },
                    onPlay = { viewModel.playSongs(viewModel.selectedSongs()); viewModel.clearSelection() },
                    onPlayNext = { viewModel.playNext(viewModel.selectedSongs()); viewModel.clearSelection() },
                    onQueue = { viewModel.addToQueue(viewModel.selectedSongs()); viewModel.clearSelection() },
                    onFavorite = { viewModel.toggleFavorites(state.selectedIds.toList()); viewModel.clearSelection() },
                    onPlaylist = { playlistTarget = state.selectedIds.toList() },
                    onShare = { shareSongs(context, viewModel.selectedSongs()) }
                )
            } else {
                val playlistFilter = state.filter as? LibraryFilter.Playlist
                TitleHeader(
                    state = state,
                    onSort = viewModel::setSort,
                    onSettings = onOpenSettings,
                    onDeletePlaylist = playlistFilter?.let { f -> { confirmDeletePlaylist = f.id } }
                )
            }
        }

        SearchField(query = state.query, onQueryChange = viewModel::setQuery)

        FilterRow(
            state = state,
            onFilter = viewModel::setFilter,
            onNewPlaylist = { newPlaylistFor = emptyList() }
        )

        when {
            state.isLoading -> CenteredBox { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            state.totalCount == 0 -> EmptyMessage(
                "No music found",
                "Songs stored on this device will appear here automatically."
            )
            state.songs.isEmpty() -> EmptyMessage(
                "Nothing here yet",
                when (state.filter) {
                    LibraryFilter.Favorites -> "Open a song's menu and choose Add to favorites."
                    LibraryFilter.RecentlyPlayed -> "Songs you play will show up here."
                    is LibraryFilter.Playlist -> "Add songs from their menu with Add to playlist."
                    else -> "Try a different search or filter."
                }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
            ) {
                items(state.songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        isCurrent = song.id == state.currentSong?.id,
                        isFavorite = song.id in state.favoriteIds,
                        selected = song.id in state.selectedIds,
                        selectionMode = state.selectionMode,
                        onClick = {
                            if (state.selectionMode) {
                                viewModel.toggleSelection(song.id)
                            } else {
                                if (!askedNotifications && !AudioPermission.hasNotificationPermission(context)) {
                                    askedNotifications = true
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                viewModel.onSongTapped(song)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(song.id) },
                        onPlayNext = { viewModel.playNext(listOf(song)) },
                        onQueue = { viewModel.addToQueue(listOf(song)) },
                        onFavorite = { viewModel.toggleFavorites(listOf(song.id)) },
                        onPlaylist = { playlistTarget = listOf(song.id) },
                        onShare = { shareSongs(context, listOf(song)) },
                        onDetails = { detailsFor = song }
                    )
                }
            }
        }
    }

    detailsFor?.let { song ->
        AlertDialog(
            onDismissRequest = { detailsFor = null },
            containerColor = CardDark,
            title = { Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DetailLine("Artist", song.artist)
                    DetailLine("Album", song.album)
                    DetailLine("Duration", formatDuration(song.durationMs))
                    DetailLine("Size", Formatter.formatShortFileSize(context, song.sizeBytes))
                    DetailLine("Folder", song.folder.ifEmpty { "Unknown" })
                    DetailLine("File", song.displayName)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsFor = null }) {
                    Text("Close", color = MaterialTheme.colorScheme.secondary)
                }
            }
        )
    }

    playlistTarget?.let { ids ->
        AlertDialog(
            onDismissRequest = { playlistTarget = null },
            containerColor = CardDark,
            title = { Text("Add to playlist") },
            text = {
                Column {
                    if (state.playlists.isEmpty()) {
                        Text("You have no playlists yet.", color = TextSecondary)
                    }
                    state.playlists.forEach { playlist ->
                        Text(
                            playlist.name,
                            color = TextPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addToPlaylist(playlist.id, ids)
                                    viewModel.clearSelection()
                                    playlistTarget = null
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { newPlaylistFor = ids; playlistTarget = null }) {
                    Text("New playlist", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { playlistTarget = null }) { Text("Cancel") } }
        )
    }

    newPlaylistFor?.let { ids ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newPlaylistFor = null },
            containerColor = CardDark,
            title = { Text("New playlist") },
            text = {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Playlist name") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        viewModel.createPlaylist(name, ids)
                        viewModel.clearSelection()
                        newPlaylistFor = null
                    }
                ) { Text("Create", color = MaterialTheme.colorScheme.secondary) }
            },
            dismissButton = { TextButton(onClick = { newPlaylistFor = null }) { Text("Cancel") } }
        )
    }

    confirmDeletePlaylist?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeletePlaylist = null },
            containerColor = CardDark,
            title = { Text("Delete playlist?") },
            text = { Text("The songs stay on your device; only the playlist is removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deletePlaylist(id); confirmDeletePlaylist = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.secondary)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePlaylist = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
    }
}

@Composable
private fun TitleHeader(
    state: SongsUiState,
    onSort: (SortField) -> Unit,
    onSettings: () -> Unit,
    onDeletePlaylist: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Songs", style = MaterialTheme.typography.headlineLarge, color = TextPrimary)
            Text(
                "${state.totalCount} songs",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (onDeletePlaylist != null) {
            IconButton(onClick = onDeletePlaylist) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist", tint = TextPrimary)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.SortByAlpha, contentDescription = "Sort", tint = TextPrimary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                SortField.entries.forEach { field ->
                    val marker = if (state.sortField == field) (if (state.ascending) "  ↑" else "  ↓") else ""
                    DropdownMenuItem(
                        text = {
                            Text(
                                field.label + marker,
                                color = if (state.sortField == field) MaterialTheme.colorScheme.secondary else TextPrimary
                            )
                        },
                        onClick = { onSort(field); menuOpen = false }
                    )
                }
            }
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = TextPrimary)
        }
    }
}

@Composable
private fun SelectionHeader(
    count: Int,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Clear selection", tint = TextPrimary)
        }
        Text(
            "$count selected",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play selected", tint = TextPrimary)
        }
        IconButton(onClick = onSelectAll) {
            Icon(Icons.Rounded.SelectAll, contentDescription = "Select all", tint = TextPrimary)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = TextPrimary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; onQueue() })
                DropdownMenuItem(text = { Text("Toggle favorite") }, onClick = { menuOpen = false; onFavorite() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuOpen = false; onPlaylist() })
                DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search songs, artists, albums") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CardDark,
            unfocusedContainerColor = CardDark,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedPlaceholderColor = TextSecondary,
            unfocusedPlaceholderColor = TextSecondary,
            focusedLeadingIconColor = MaterialTheme.colorScheme.secondary,
            unfocusedLeadingIconColor = TextSecondary,
            focusedTrailingIconColor = TextSecondary,
            unfocusedTrailingIconColor = TextSecondary
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun FilterRow(
    state: SongsUiState,
    onFilter: (LibraryFilter) -> Unit,
    onNewPlaylist: () -> Unit
) {
    val fixed = listOf(
        "All songs" to LibraryFilter.All,
        "Favorites" to LibraryFilter.Favorites,
        "Recently added" to LibraryFilter.RecentlyAdded,
        "Recently played" to LibraryFilter.RecentlyPlayed
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(fixed) { (label, filter) ->
            FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(label) }, border = null, colors = chipColors())
        }
        items(state.playlists, key = { "playlist-${it.id}" }) { playlist ->
            val filter = LibraryFilter.Playlist(playlist.id)
            FilterChip(selected = state.filter == filter, onClick = { onFilter(filter) }, label = { Text(playlist.name) }, border = null, colors = chipColors())
        }
        item {
            FilterChip(
                selected = false,
                onClick = onNewPlaylist,
                label = { Text("New playlist") },
                leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                border = null,
                colors = chipColors()
            )
        }
    }
}

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    containerColor = CardDark,
    labelColor = TextSecondary,
    iconColor = TextSecondary,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.secondary
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onPlaylist: () -> Unit,
    onShare: () -> Unit,
    onDetails: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        label = "rowBackground"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(song = song, size = 52.dp)
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = TextPrimary)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.secondary else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isFavorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = "Favorite",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 6.dp).size(16.dp)
            )
        }
        Text(
            formatDuration(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        if (!selectionMode) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext() })
                    DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; onQueue() })
                    DropdownMenuItem(
                        text = { Text(if (isFavorite) "Remove from favorites" else "Add to favorites") },
                        onClick = { menuOpen = false; onFavorite() }
                    )
                    DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menuOpen = false; onPlaylist() })
                    DropdownMenuItem(text = { Text("Select") }, onClick = { menuOpen = false; onLongClick() })
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menuOpen = false; onShare() })
                    DropdownMenuItem(text = { Text("Details") }, onClick = { menuOpen = false; onDetails() })
                }
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = song != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        if (song != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CardDark,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onClick)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(song = song, size = 44.dp, corner = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            "Allow access to your music",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(8.dp))
        Text(
            "Offline Music Pro only reads audio files stored on this device. Nothing ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.size(24.dp))
        Button(
            onClick = onAllow,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = TextPrimary
            )
        ) { Text("Allow access") }
        TextButton(onClick = onOpenSettings) {
            Text("Open app settings", color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EmptyMessage(title: String, body: String) {
    CenteredBox {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.size(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

fun shareSongs(context: Context, songs: List<Song>) {
    if (songs.isEmpty()) return
    val intent = if (songs.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, songs.first().uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "audio/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(songs.map { it.uri }))
        }
    }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
