package com.offlinemusicpro.player.features.visualizer

import android.media.audiofx.Visualizer
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow

/**
 * Wraps android.media.audiofx.Visualizer on the player's audio session. Android requires the
 * RECORD_AUDIO permission for this API; only the app's own audio output is captured.
 * Call [stop] when the screen closes so no capture runs in the background.
 */
class AudioVisualizer(private val onFrame: (bars: FloatArray, wave: FloatArray) -> Unit) {

    private var visualizer: Visualizer? = null
    @Volatile private var bars = FloatArray(BAR_COUNT)
    @Volatile private var wave = FloatArray(WAVE_POINTS)

    fun start(sessionId: Int): Boolean {
        stop()
        if (sessionId == 0) return false
        return try {
            val v = Visualizer(sessionId)
            v.setEnabled(false)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform != null) wave = toWave(waveform)
                }

                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) {
                        bars = toBars(fft)
                        onFrame(bars, wave)
                    }
                }
            }, Visualizer.getMaxCaptureRate(), true, true)
            v.setEnabled(true)
            visualizer = v
            true
        } catch (_: Exception) {
            false
        }
    }

    fun stop() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (_: Exception) { }
        visualizer = null
    }

    private fun toBars(fft: ByteArray): FloatArray {
        val usable = fft.size / 2 - 1
        if (usable <= 0) return FloatArray(BAR_COUNT)
        return FloatArray(BAR_COUNT) { b ->
            val start = 1 + (usable * (b.toFloat() / BAR_COUNT).pow(2)).toInt()
            val end = maxOf(start + 1, 1 + (usable * ((b + 1f) / BAR_COUNT).pow(2)).toInt()).coerceAtMost(usable)
            var sum = 0f
            var count = 0
            for (k in start..end) {
                val re = fft[2 * k].toInt()
                val im = fft[2 * k + 1].toInt()
                sum += hypot(re.toFloat(), im.toFloat())
                count++
            }
            val avg = if (count == 0) 0f else sum / count
            (20f * log10(avg + 1f) / 45f).coerceIn(0f, 1f)
        }
    }

    private fun toWave(raw: ByteArray): FloatArray {
        val step = maxOf(1, raw.size / WAVE_POINTS)
        return FloatArray(WAVE_POINTS) { i ->
            val idx = (i * step).coerceAtMost(raw.size - 1)
            ((raw[idx].toInt() and 0xFF) - 128) / 128f
        }
    }

    companion object {
        const val BAR_COUNT = 48
        const val WAVE_POINTS = 256
    }
}
