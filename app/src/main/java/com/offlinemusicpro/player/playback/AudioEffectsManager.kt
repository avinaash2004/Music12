package com.offlinemusicpro.player.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log2

data class EffectsInfo(
    val available: Boolean = false,
    val bandCount: Int = 0,
    val minMb: Int = 0,
    val maxMb: Int = 0,
    val centerHz: List<Int> = emptyList(),
    val bassSupported: Boolean = false,
    val virtualizerSupported: Boolean = false
)

/**
 * Real Android audio effects bound to the player's audio session. The platform Equalizer exposes the
 * bands the device reports (commonly 5, some devices 10); the UI shows exactly those.
 * Treble is applied as an offset to the two highest bands.
 */
class AudioEffectsManager {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    private val _info = MutableStateFlow(EffectsInfo())
    val info: StateFlow<EffectsInfo> = _info.asStateFlow()

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    /** Called when the user (not a restore) changes something, so it can be persisted. */
    var onUserChange: ((EqState) -> Unit)? = null

    fun attach(sessionId: Int) {
        release()
        if (sessionId == 0) return
        var bands = 0
        var minMb = 0
        var maxMb = 0
        var centers = emptyList<Int>()
        try {
            val e = Equalizer(0, sessionId)
            eq = e
            bands = e.numberOfBands.toInt()
            minMb = e.bandLevelRange[0].toInt()
            maxMb = e.bandLevelRange[1].toInt()
            centers = List(bands) { e.getCenterFreq(it.toShort()) / 1000 }
        } catch (_: Exception) {
            eq = null
        }
        try { bass = BassBoost(0, sessionId) } catch (_: Exception) { bass = null }
        try { virtualizer = Virtualizer(0, sessionId) } catch (_: Exception) { virtualizer = null }

        _info.value = EffectsInfo(
            available = eq != null,
            bandCount = bands,
            minMb = minMb,
            maxMb = maxMb,
            centerHz = centers,
            bassSupported = bass?.strengthSupported == true,
            virtualizerSupported = virtualizer?.strengthSupported == true
        )
        setState(_state.value, notify = false)
    }

    fun setState(newState: EqState, notify: Boolean = true) {
        val bands = _info.value.bandCount
        val levels = if (newState.levelsMb.size == bands) newState.levelsMb else List(bands) { 0 }
        _state.value = newState.copy(levelsMb = levels)
        apply()
        if (notify) onUserChange?.invoke(_state.value)
    }

    private fun apply() {
        val s = _state.value
        val info = _info.value
        try {
            eq?.let { e ->
                e.setEnabled(s.enabled)
                for (i in 0 until info.bandCount) {
                    val trebleOffset = if (info.bandCount >= 3 && i >= info.bandCount - 2) s.treble else 0
                    val level = ((s.levelsMb.getOrNull(i) ?: 0) + trebleOffset).coerceIn(info.minMb, info.maxMb)
                    e.setBandLevel(i.toShort(), level.toShort())
                }
            }
        } catch (_: Exception) { }
        try {
            bass?.let { b ->
                if (b.strengthSupported) b.setStrength(s.bass.coerceIn(0, 1000).toShort())
                b.setEnabled(s.enabled && s.bass > 0)
            }
        } catch (_: Exception) { }
        try {
            virtualizer?.let { v ->
                if (v.strengthSupported) v.setStrength(s.virtualizer.coerceIn(0, 1000).toShort())
                v.setEnabled(s.enabled && s.virtualizer > 0)
            }
        } catch (_: Exception) { }
    }

    fun release() {
        try { eq?.release() } catch (_: Exception) { }
        try { bass?.release() } catch (_: Exception) { }
        try { virtualizer?.release() } catch (_: Exception) { }
        eq = null; bass = null; virtualizer = null
    }
}

object EqPresets {
    /** Control curves in dB at these reference frequencies, resampled to the device's actual bands. */
    private val refHz = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)
    val builtIn: Map<String, FloatArray> = linkedMapOf(
        "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f),
        "Rock" to floatArrayOf(5f, 3f, -1f, 3f, 5f),
        "Pop" to floatArrayOf(-1f, 2f, 4f, 2f, -1f),
        "Classical" to floatArrayOf(4f, 3f, -2f, 2f, 4f),
        "Jazz" to floatArrayOf(3f, 2f, -2f, 2f, 4f)
    )

    fun levelsFor(name: String, info: EffectsInfo): List<Int>? {
        val curve = builtIn[name] ?: return null
        return info.centerHz.map { hz -> (interpolate(curve, hz.toFloat()) * 100).toInt().coerceIn(info.minMb, info.maxMb) }
    }

    /** Fits a saved level list to the current number of bands by index position. */
    fun resample(levels: List<Int>, bands: Int): List<Int> {
        if (levels.size == bands) return levels
        if (levels.isEmpty() || bands == 0) return List(bands) { 0 }
        return List(bands) { i ->
            val pos = if (bands == 1) 0f else i * (levels.size - 1).toFloat() / (bands - 1)
            val lo = pos.toInt().coerceIn(0, levels.size - 1)
            val hi = (lo + 1).coerceAtMost(levels.size - 1)
            val t = pos - lo
            (levels[lo] * (1 - t) + levels[hi] * t).toInt()
        }
    }

    private fun interpolate(curve: FloatArray, hz: Float): Float {
        if (hz <= refHz.first()) return curve.first()
        if (hz >= refHz.last()) return curve.last()
        for (i in 0 until refHz.size - 1) {
            if (hz <= refHz[i + 1]) {
                val t = (log2(hz) - log2(refHz[i])) / (log2(refHz[i + 1]) - log2(refHz[i]))
                return curve[i] + (curve[i + 1] - curve[i]) * t
            }
        }
        return curve.last()
    }
}
