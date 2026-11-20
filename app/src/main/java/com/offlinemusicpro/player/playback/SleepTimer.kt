package com.offlinemusicpro.player.playback

import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Sleep timer with a gradual volume fade over the last seconds. Main-thread only. */
class SleepTimer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs.asStateFlow()

    private val _stopAfterSong = MutableStateFlow(false)
    val stopAfterSong: StateFlow<Boolean> = _stopAfterSong.asStateFlow()

    @Volatile
    var fadeFactor: Float = 1f
        private set

    fun start(minutes: Int, player: ExoPlayer) {
        cancel(player)
        val total = minutes.coerceAtLeast(1) * 60_000L
        job = scope.launch {
            var left = total
            while (left > 0) {
                _remainingMs.value = left
                fadeFactor = if (left < FADE_MS) left.toFloat() / FADE_MS else 1f
                player.volume = PlaybackEngine.computeVolume(player)
                delay(TICK_MS)
                left -= TICK_MS
            }
            player.pause()
            fadeFactor = 1f
            player.volume = PlaybackEngine.computeVolume(player)
            _remainingMs.value = null
        }
    }

    fun stopAfterCurrentSong(player: ExoPlayer) {
        cancel(player)
        _stopAfterSong.value = true
        player.pauseAtEndOfMediaItems = true
    }

    fun cancel(player: ExoPlayer?) {
        job?.cancel()
        job = null
        _remainingMs.value = null
        _stopAfterSong.value = false
        fadeFactor = 1f
        if (player != null) {
            player.pauseAtEndOfMediaItems = false
            player.volume = PlaybackEngine.computeVolume(player)
        }
    }

    fun onStoppedAfterSong() {
        _stopAfterSong.value = false
    }

    private companion object {
        const val TICK_MS = 500L
        const val FADE_MS = 20_000L
    }
}
