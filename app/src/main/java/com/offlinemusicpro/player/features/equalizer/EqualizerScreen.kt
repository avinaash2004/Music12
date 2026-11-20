package com.offlinemusicpro.player.features.equalizer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.offlinemusicpro.player.core.common.LabeledSlider
import com.offlinemusicpro.player.core.common.ScreenHeader
import com.offlinemusicpro.player.core.common.SectionTitle
import com.offlinemusicpro.player.core.common.rememberContainer
import com.offlinemusicpro.player.core.theme.CardDark
import com.offlinemusicpro.player.core.theme.TextSecondary
import com.offlinemusicpro.player.data.local.EqPresetEntity
import com.offlinemusicpro.player.playback.EqPresets
import com.offlinemusicpro.player.playback.PlaybackEngine
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val effects = PlaybackEngine.effects
    val info by effects.info.collectAsStateWithLifecycle()
    val eq by effects.state.collectAsStateWithLifecycle()
    val custom by container.libraryRepository.customPresets.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var saveDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Equalizer", onBack) {
            Switch(
                checked = eq.enabled,
                enabled = info.available,
                onCheckedChange = { effects.setState(effects.state.value.copy(enabled = it)) }
            )
            Spacer(Modifier.width(16.dp))
        }

        if (!info.available) {
            Text(
                "The audio equalizer is not available right now. Start a song, then open this screen again. " +
                    "Some devices or audio outputs do not provide a system equalizer.",
                color = TextSecondary,
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                item {
                    Text(
                        "${info.bandCount}-band equalizer (the bands your device reports)" +
                            if (!eq.enabled) " · currently off" else "",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(EqPresets.builtIn.keys.toList()) { name ->
                            PresetChip(name, eq.preset == name) {
                                EqPresets.levelsFor(name, info)?.let { levels ->
                                    effects.setState(
                                        effects.state.value.copy(levelsMb = levels, preset = name, enabled = true)
                                    )
                                }
                            }
                        }
                        items(custom, key = { "custom-${it.name}" }) { preset ->
                            PresetChip(preset.name, eq.preset == preset.name) {
                                val levels = EqPresets.resample(
                                    preset.levels.split(",").mapNotNull { it.toIntOrNull() },
                                    info.bandCount
                                )
                                effects.setState(
                                    effects.state.value.copy(
                                        levelsMb = levels, preset = preset.name, enabled = true,
                                        bass = preset.bass, virtualizer = preset.virtualizer, treble = preset.treble
                                    )
                                )
                            }
                        }
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { saveDialog = true },
                                label = { Text("Save as preset") },
                                border = null,
                                colors = presetColors()
                            )
                        }
                    }
                }
                if (custom.any { it.name == eq.preset }) {
                    item {
                        TextButton(
                            onClick = { scope.launch { container.libraryRepository.deletePreset(eq.preset) } },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) { Text("Delete preset \"${eq.preset}\"", color = MaterialTheme.colorScheme.secondary) }
                    }
                }

                item { SectionTitle("Bands") }
                itemsIndexed(info.centerHz) { i, hz ->
                    val level = eq.levelsMb.getOrElse(i) { 0 }
                    LabeledSlider(
                        label = formatHz(hz),
                        valueText = String.format(Locale.US, "%+.1f dB", level / 100f),
                        value = level.toFloat(),
                        range = info.minMb.toFloat()..info.maxMb.toFloat(),
                        onChange = { v ->
                            val cur = effects.state.value
                            val levels = cur.levelsMb.toMutableList().also { list ->
                                if (i < list.size) list[i] = v.toInt()
                            }
                            effects.setState(cur.copy(levelsMb = levels, preset = "Custom"), notify = false)
                        },
                        onFinished = { effects.setState(effects.state.value) }
                    )
                }

                item { SectionTitle("Effects") }
                item {
                    LabeledSlider(
                        label = if (info.bassSupported) "Bass boost" else "Bass boost (not supported on this device)",
                        valueText = "${eq.bass / 10}%",
                        value = eq.bass.toFloat(),
                        range = 0f..1000f,
                        enabled = info.bassSupported,
                        onChange = { effects.setState(effects.state.value.copy(bass = it.toInt()), notify = false) },
                        onFinished = { effects.setState(effects.state.value) }
                    )
                }
                item {
                    LabeledSlider(
                        label = if (info.bandCount >= 3) "Treble" else "Treble (needs 3+ bands)",
                        valueText = String.format(Locale.US, "%+.1f dB", eq.treble / 100f),
                        value = eq.treble.toFloat(),
                        range = -1000f..1000f,
                        enabled = info.bandCount >= 3,
                        onChange = { effects.setState(effects.state.value.copy(treble = it.toInt()), notify = false) },
                        onFinished = { effects.setState(effects.state.value) }
                    )
                }
                item {
                    LabeledSlider(
                        label = if (info.virtualizerSupported) "Virtualizer" else "Virtualizer (not supported on this device)",
                        valueText = "${eq.virtualizer / 10}%",
                        value = eq.virtualizer.toFloat(),
                        range = 0f..1000f,
                        enabled = info.virtualizerSupported,
                        onChange = { effects.setState(effects.state.value.copy(virtualizer = it.toInt()), notify = false) },
                        onFinished = { effects.setState(effects.state.value) }
                    )
                }
                item {
                    Text(
                        "Treble raises or lowers the two highest bands. Android's built-in equalizer has no separate treble control.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    if (saveDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialog = false },
            containerColor = CardDark,
            title = { Text("Save preset") },
            text = {
                TextField(value = name, onValueChange = { name = it }, singleLine = true, placeholder = { Text("Preset name") })
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val cur = effects.state.value
                        var finalName = name.trim()
                        if (finalName in EqPresets.builtIn || finalName == "Custom") finalName += " (mine)"
                        scope.launch {
                            container.libraryRepository.savePreset(
                                EqPresetEntity(finalName, cur.levelsMb.joinToString(","), cur.bass, cur.virtualizer, cur.treble)
                            )
                            effects.setState(cur.copy(preset = finalName))
                        }
                        saveDialog = false
                    }
                ) { Text("Save", color = MaterialTheme.colorScheme.secondary) }
            },
            dismissButton = { TextButton(onClick = { saveDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PresetChip(name: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(name) }, border = null, colors = presetColors())
}

@Composable
private fun presetColors() = FilterChipDefaults.filterChipColors(
    containerColor = CardDark,
    labelColor = TextSecondary,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    selectedLabelColor = MaterialTheme.colorScheme.secondary
)

private fun formatHz(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k % 1f == 0f) "${k.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", k)
    } else {
        "$hz Hz"
    }
