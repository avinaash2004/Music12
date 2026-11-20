package com.offlinemusicpro.player.data.scanner

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import com.offlinemusicpro.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Loads album art through the system thumbnail API (API 29+). Missing art simply returns null. */
object ArtworkLoader {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16).toInt().coerceAtLeast(1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val missing: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private fun key(song: Song, px: Int) = "${song.id}:$px"

    fun cached(song: Song, px: Int): Bitmap? = cache.get(key(song, px))

    suspend fun load(context: Context, song: Song, px: Int): Bitmap? {
        val k = key(song, px)
        cache.get(k)?.let { return it }
        if (k in missing) return null
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.loadThumbnail(song.uri, Size(px, px), null)
                    .also { cache.put(k, it) }
            } catch (_: Exception) {
                missing.add(k)
                null
            }
        }
    }
}
