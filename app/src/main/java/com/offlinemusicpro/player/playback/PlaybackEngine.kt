package com.offlinemusicpro.player.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer

/**
 * Process-wide handle to the player owned by [MusicPlaybackService]. The service and the UI live in the
 * same process, so screens such as the equalizer and visualizer reach the audio session through here.
 */
object PlaybackEngine {
    @Volatile
    var player: ExoPlayer? = null

    val effects = AudioEffectsManager()
    val sleepTimer = SleepTimer()

    /** Fade length at the start and end of every song. 0 = off. */
    @Volatile
    var crossfadeMs: Int = 0

    val audioSessionId: Int get() = player?.audioSessionId ?: 0

    /**
     * Crossfade here is a smooth fade-out of the ending song and fade-in of the next one (one player,
     * so the songs do not overlap). Gapless transitions come from ExoPlayer itself.
     */
    fun computeVolume(p: ExoPlayer): Float {
        var factor = 1f
        val fade = crossfadeMs
        val duration = p.duration
        if (fade > 0 && duration != C.TIME_UNSET && duration > fade * 2L) {
            val pos = p.currentPosition
            factor = minOf(1f, (duration - pos).toFloat() / fade, pos.toFloat() / fade).coerceIn(0f, 1f)
        }
        return (factor * sleepTimer.fadeFactor).coerceIn(0f, 1f)
    }
}
