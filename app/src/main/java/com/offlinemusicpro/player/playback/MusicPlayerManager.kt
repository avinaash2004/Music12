package com.offlinemusicpro.player.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.offlinemusicpro.player.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackState(
    val currentSongId: Long? = null,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val speed: Float = 1f,
    val hasMedia: Boolean = false,
    val queueSize: Int = 0,
    val queueIndex: Int = 0
)

data class QueueItem(val songId: Long, val title: String, val artist: String)

/** UI-side handle to the playback service (a Media3 MediaController). Main-thread only. */
class MusicPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainExecutor = ContextCompat.getMainExecutor(appContext)
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pending: ((MediaController) -> Unit)? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
        val f = MediaController.Builder(appContext, token).buildAsync()
        future = f
        f.addListener({
            try {
                val c = f.get()
                controller = c
                c.addListener(listener)
                refresh()
                pending?.invoke(c)
            } catch (_: Exception) {
                future = null
            } finally {
                pending = null
            }
        }, mainExecutor)
    }

    // ----- starting playback -----

    fun play(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            c.setMediaItems(items, startIndex, 0L)
            c.prepare()
            c.play()
        }
    }

    /** Puts the last played song back in the player, paused at its saved position. */
    fun restoreIfIdle(songs: List<Song>, startIndex: Int, positionMs: Long) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items, startIndex, positionMs)
                c.prepare()
            }
        }
    }

    fun playNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items, 0, 0L); c.prepare(); c.play()
            } else {
                c.addMediaItems(c.currentMediaItemIndex + 1, items)
            }
        }
    }

    fun addToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        withController { c ->
            if (c.mediaItemCount == 0) {
                c.setMediaItems(items, 0, 0L); c.prepare(); c.play()
            } else {
                c.addMediaItems(items)
            }
        }
    }

    // ----- transport -----

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Off -> repeat all -> repeat one -> off. */
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun setSpeed(speed: Float) { controller?.setPlaybackSpeed(speed) }

    fun positionMs(): Long = controller?.currentPosition ?: 0L
    fun durationMs(): Long = controller?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L

    // ----- queue -----

    fun queueSnapshot(): List<QueueItem> {
        val c = controller ?: return emptyList()
        return List(c.mediaItemCount) { i ->
            val item = c.getMediaItemAt(i)
            QueueItem(
                songId = item.mediaId.toLongOrNull() ?: -1L,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty()
            )
        }
    }

    fun moveQueueItem(from: Int, to: Int) { controller?.moveMediaItem(from, to) }
    fun removeQueueItem(index: Int) { controller?.removeMediaItem(index) }
    fun skipToQueueItem(index: Int) {
        controller?.let { it.seekTo(index, 0L); it.play() }
    }

    // ----- sleep timer (runs inside the service's player) -----

    fun startSleepTimer(minutes: Int) {
        PlaybackEngine.player?.let { PlaybackEngine.sleepTimer.start(minutes, it) }
    }

    fun stopAfterCurrentSong() {
        PlaybackEngine.player?.let { PlaybackEngine.sleepTimer.stopAfterCurrentSong(it) }
    }

    fun cancelSleepTimer() {
        PlaybackEngine.sleepTimer.cancel(PlaybackEngine.player)
    }

    // ----- internals -----

    private fun withController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            pending = block
            connect()
        }
    }

    private fun refresh() {
        val c = controller ?: return
        _state.value = PlaybackState(
            currentSongId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            speed = c.playbackParameters.speed,
            hasMedia = c.mediaItemCount > 0,
            queueSize = c.mediaItemCount,
            queueIndex = c.currentMediaItemIndex
        )
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
