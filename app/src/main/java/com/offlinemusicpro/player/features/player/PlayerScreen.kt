package com.offlinemusicpro.player.features.player

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.offlinemusicpro.player.Route
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.SurfaceDark
import com.offlinemusicpro.player.core.theme.TextPrimary
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.core.utils.formatDuration
import com.offlinemusicpro.player.data.model.Song
import com.offlinemusicpro.player.data.scanner.ArtworkLoader
import com.offlinemusicpro.player.features.sleeptimer.SleepTimerDialog
import com.offlinemusicpro.player.features.songs.SongsUiState
import com.offlinemusicpro.player.features.songs.SongsViewModel
import com.offlinemusicpro.player.playback.PlaybackEngine
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    state: SongsUiState,
    viewModel: SongsViewModel,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    val song = state.currentSong
    if (song == null) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close", tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(120.dp))
            Text("Nothing is playing", color = TextSecondary)
        }
    } else {
        PlayerContent(song, state, viewModel, onBack, onNavigate)
    }
}

@Composable
private fun PlayerContent(
    song: Song,
    state: SongsUiState,
    viewModel: SongsViewModel,
    onBack: () -> Unit,
    onNavigate: (Route) -> Unit
) {
    val player = viewModel.player
    val ps = state.playback
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(song.durationMs) }
    var dragValue by remember { mutableStateOf<Float?>(null) }
    var showSleep by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    val sleepRemaining by PlaybackEngine.sleepTimer.remainingMs.collectAsStateWithLifecycle()
    val sleepAfterSong by PlaybackEngine.sleepTimer.stopAfterSong.collectAsStateWithLifecycle()
    val isFavorite = song.id in state.favoriteIds

    LaunchedEffect(song.id) {
        while (true) {
            position = player.positionMs()
            duration = player.durationMs().takeIf { it > 0 } ?: song.durationMs
            delay(400)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedBackdrop(song)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Close player", tint = TextPrimary)
                }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { onNavigate(Route.QUEUE) }) {
                    Icon(Icons.Rounded.QueueMusic, contentDescription = "Queue", tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(12.dp))
            BigArtwork(song, onNext = player::skipNext, onPrevious = player::skipPrevious)
            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.toggleFavorites(listOf(song.id)) }) {
                    Icon(
                        if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else TextPrimary
                    )
                }
            }

            Slider(
                value = dragValue ?: position.toFloat(),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { player.seekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration((dragValue ?: position.toFloat()).toLong()), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(formatDuration(duration), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (ps.shuffle) MaterialTheme.colorScheme.secondary else TextSecondary
                    )
                }
                IconButton(onClick = player::skipPrevious, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = player::togglePlayPause,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = TextPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(onClick = player::skipNext, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (ps.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (ps.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            else -> "Repeat off"
                        },
                        tint = if (ps.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.secondary else TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    TextButton(onClick = { speedMenu = true }) {
                        Text("${ps.speed}x", color = if (ps.speed != 1f) MaterialTheme.colorScheme.secondary else TextPrimary)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = { player.setSpeed(speed); speedMenu = false }
                            )
                        }
                    }
                }
                IconButton(onClick = { showSleep = true }) {
                    Icon(
                        Icons.Rounded.Timer,
                        contentDescription = "Sleep timer",
                        tint = if (sleepRemaining != null || sleepAfterSong) MaterialTheme.colorScheme.secondary else TextPrimary
                    )
                }
                IconButton(onClick = { onNavigate(Route.EQUALIZER) }) {
                    Icon(Icons.Rounded.Equalizer, contentDescription = "Equalizer", tint = TextPrimary)
                }
                IconButton(onClick = { onNavigate(Route.VISUALIZER) }) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = "Visualizer", tint = TextPrimary)
                }
                IconButton(onClick = { onNavigate(Route.LYRICS) }) {
                    Icon(Icons.Rounded.Notes, contentDescription = "Lyrics", tint = TextPrimary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSleep) SleepTimerDialog(player = player, onDismiss = { showSleep = false })
}

@Composable
private fun AnimatedBackdrop(song: Song) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, song.id) {
        value = ArtworkLoader.load(context, song, 512)
    }
    val transition = rememberInfiniteTransition(label = "backdrop")
    val scale by transition.animateFloat(
        initialValue = 1.3f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale"
    )
    val shift by transition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(11_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "shift"
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = shift
                        translationY = -shift
                    }
                    .blur(40.dp)
                    .alpha(0.5f)
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), Color.Black))
                )
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
            )
        )
    }
}

@Composable
private fun BigArtwork(song: Song, onNext: () -> Unit, onPrevious: () -> Unit) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(ArtworkLoader.cached(song, 800), song.id) {
        value = ArtworkLoader.load(context, song, 800)
    }
    var drag by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth(0.86f)
            .aspectRatio(1f)
            .graphicsLayer { translationX = drag * 0.3f }
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(CardDark, SurfaceDark)))
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (drag < -140f) onNext() else if (drag > 140f) onPrevious()
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f }
                ) { _, amount -> drag += amount }
            },
        contentAlignment = Alignment.Center
    ) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                modifier = Modifier.size(96.dp)
            )
        }
    }
}
