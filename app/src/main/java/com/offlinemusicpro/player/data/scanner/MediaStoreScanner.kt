package com.offlinemusicpro.player.data.scanner

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.offlinemusicpro.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/**
 * Reads audio from MediaStore. MediaStore is kept up to date by the system, so we never walk the
 * file system ourselves; a ContentObserver tells us when something changed (no polling).
 */
class MediaStoreScanner(private val context: Context) {

    private val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        val songs = ArrayList<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        try {
            context.contentResolver.query(collection, projection, selection, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val displayName = c.getString(nameCol).orEmpty()
                    songs += Song(
                        id = c.getLong(idCol),
                        title = c.getString(titleCol).cleanOr(displayName.substringBeforeLast('.')),
                        artist = c.getString(artistCol).cleanOr("Unknown artist"),
                        album = c.getString(albumCol).cleanOr("Unknown album"),
                        albumId = c.getLong(albumIdCol),
                        durationMs = c.getLong(durCol),
                        dateAddedSec = c.getLong(addedCol),
                        sizeBytes = c.getLong(sizeCol),
                        folder = c.getString(pathCol).orEmpty(),
                        displayName = displayName
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission revoked while running: behave as "no songs" instead of crashing.
        }
        songs
    }

    /** Emits Unit whenever the system audio index changes. Does not emit an initial value. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(collection, true, observer)
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private fun String?.cleanOr(fallback: String): String {
        val value = this?.trim().orEmpty()
        return if (value.isEmpty() || value == "<unknown>") fallback else value
    }
}
