package com.offlinemusicpro.player.features.trimmer

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Plays just the selected range with a separate MediaPlayer so the user can check the cut. */
class TrimPreview(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var endMs = 0L

    var playing by mutableStateOf(false)
        private set

    fun start(uri: Uri, startMs: Long, endMs: Long) {
        stop()
        this.endMs = endMs
        try {
            val p = MediaPlayer()
            player = p
            p.setDataSource(context, uri)
            p.setOnPreparedListener {
                it.seekTo(startMs.toInt())
                it.start()
                playing = true
                tick()
            }
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, _, _ -> stop(); true }
            p.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    private fun tick() {
        val p = player ?: return
        val position = try { p.currentPosition } catch (_: Exception) { stop(); return }
        if (position >= endMs) {
            stop()
            return
        }
        handler.postDelayed({ tick() }, 100)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        try { player?.release() } catch (_: Exception) { }
        player = null
        playing = false
    }
}
