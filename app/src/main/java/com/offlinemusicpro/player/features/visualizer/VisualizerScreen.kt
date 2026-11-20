package com.offlinemusicpro.player.features.visualizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.playback.PlaybackEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val modes = listOf("Bars", "Waveform", "Circular")

@Composable
fun VisualizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var mode by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Visualizer", onBack)
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(
                    "Android requires the microphone permission for any app that reads its own audio spectrum. " +
                        "Offline Music Pro only analyses the music it is playing; nothing is recorded or stored.",
                    color = TextSecondary
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow") }
            }
        } else {
            VisualizerCanvas(mode, Modifier.weight(1f).fillMaxWidth())
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                modes.forEachIndexed { i, label ->
                    FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(label) })
                }
            }
        }
    }
}

@Composable
private fun VisualizerCanvas(mode: Int, modifier: Modifier) {
    val rawBars = remember { mutableStateOf(FloatArray(AudioVisualizer.BAR_COUNT)) }
    val rawWave = remember { mutableStateOf(FloatArray(AudioVisualizer.WAVE_POINTS)) }
    val display = remember { FloatArray(AudioVisualizer.BAR_COUNT) }
    var frame by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Capture only while this screen is visible, to save battery.
    DisposableEffect(lifecycleOwner) {
        val visualizer = AudioVisualizer { bars, wave ->
            rawBars.value = bars
            rawWave.value = wave
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> visualizer.start(PlaybackEngine.audioSessionId)
                Lifecycle.Event.ON_STOP -> visualizer.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            visualizer.start(PlaybackEngine.audioSessionId)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            visualizer.stop()
        }
    }

    // Smooth falloff between the ~20 Hz capture updates.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            val target = rawBars.value
            for (i in display.indices) {
                display[i] = max(target.getOrElse(i) { 0f }, display[i] * 0.88f)
            }
            frame++
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(16.dp)) {
            val tick = frame // read so the canvas redraws every frame
            if (tick < 0) return@Canvas
            val brush = Brush.verticalGradient(listOf(secondary, primary))
            when (mode) {
                0 -> {
                    val n = display.size
                    val slot = size.width / n
                    for (i in 0 until n) {
                        val h = max(4f, display[i] * size.height)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(i * slot + slot * 0.15f, size.height - h),
                            size = Size(slot * 0.7f, h),
                            cornerRadius = CornerRadius(slot * 0.3f)
                        )
                    }
                }
                1 -> {
                    val wave = rawWave.value
                    val path = Path()
                    val midY = size.height / 2f
                    wave.forEachIndexed { i, v ->
                        val x = size.width * i / (wave.size - 1).coerceAtLeast(1)
                        val y = midY - v * size.height * 0.45f
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(path, brush = brush, style = Stroke(width = 5f, cap = StrokeCap.Round))
                }
                else -> {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val base = min(size.width, size.height) * 0.2f
                    val maxLen = min(size.width, size.height) * 0.28f
                    val n = display.size
                    drawCircle(color = primary.copy(alpha = 0.25f), radius = base, center = center, style = Stroke(width = 3f))
                    for (i in 0 until n) {
                        val angle = (2.0 * PI * i / n - PI / 2).toFloat()
                        val len = 6f + display[i] * maxLen
                        val dx = cos(angle)
                        val dy = sin(angle)
                        drawLine(
                            color = if (i % 2 == 0) primary else secondary,
                            start = Offset(center.x + dx * base, center.y + dy * base),
                            end = Offset(center.x + dx * (base + len), center.y + dy * (base + len)),
                            strokeWidth = 7f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
        }
    }
}
