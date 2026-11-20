package com.offlinemusicpro.player.playback

import android.content.Intent
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.offlinemusicpro.player.OfflineMusicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the ExoPlayer and the MediaSession. Media3 builds the notification, lock-screen controls and
 * Bluetooth/headset button handling from the session automatically.
 */
class MusicPlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null
    private var tickJob: Job? = null
    private var shake: ShakeDetector? = null
    private var shakeEnabled = false
    private var lastRecordedId: Long? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as OfflineMusicApp).container

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // A fixed session id lets the equalizer/visualizer bind once and keep working across songs.
        val audioManager = getSystemService(AudioManager::class.java)
        player.audioSessionId = audioManager.generateAudioSessionId()
        PlaybackEngine.player = player
        PlaybackEngine.effects.attach(player.audioSessionId)
        PlaybackEngine.effects.onUserChange = { state ->
            scope.launch { container.settingsRepository.saveEq(state) }
        }
        shake = ShakeDetector(this) { player.seekToNextMediaItem() }

        scope.launch {
            PlaybackEngine.effects.setState(container.settingsRepository.eqState(), notify = false)
        }
        scope.launch {
            container.settingsRepository.settings.collect { s ->
                PlaybackEngine.crossfadeMs = s.crossfadeSec * 1000
                shakeEnabled = s.shake
                updateShake(player)
            }
        }

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                // Deleted or unsupported file: skip it instead of stopping playback.
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateShake(player)
                if (isPlaying) {
                    recordCurrent(player)
                    startTick(player)
                } else {
                    stopTick()
                    saveResume(player)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (player.isPlaying) recordCurrent(player)
                saveResume(player)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    player.pauseAtEndOfMediaItems = false
                    PlaybackEngine.sleepTimer.onStoppedAfterSong()
                }
            }
        })

        session = MediaSession.Builder(this, player).build()
    }

    private fun recordCurrent(player: ExoPlayer) {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        if (id == lastRecordedId) return
        lastRecordedId = id
        val container = (application as OfflineMusicApp).container
        scope.launch { container.libraryRepository.recordPlayed(id) }
    }

    private fun saveResume(player: ExoPlayer) {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val position = player.currentPosition
        val container = (application as OfflineMusicApp).container
        scope.launch { container.settingsRepository.saveResume(id, position) }
    }

    private fun startTick(player: ExoPlayer) {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            var n = 0
            while (isActive) {
                player.volume = PlaybackEngine.computeVolume(player)
                if (++n % 40 == 0) saveResume(player) // roughly every 10 seconds
                delay(250)
            }
        }
    }

    private fun stopTick() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun updateShake(player: ExoPlayer) {
        if (shakeEnabled && player.isPlaying) shake?.start() else shake?.stop()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopTick()
        shake?.stop()
        PlaybackEngine.sleepTimer.cancel(null)
        PlaybackEngine.effects.release()
        PlaybackEngine.player = null
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
