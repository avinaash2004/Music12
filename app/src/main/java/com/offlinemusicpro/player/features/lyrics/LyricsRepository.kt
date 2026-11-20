package com.offlinemusicpro.player.features.lyrics

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.offlinemusicpro.player.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset

data class LyricLine(val timeMs: Long, val text: String)

/** Either synchronized [lines] (from an LRC) or [plain] text. Fully offline. */
data class Lyrics(val lines: List<LyricLine>, val plain: String?) {
    val synced: Boolean get() = lines.isNotEmpty()
}

class LyricsRepository(private val context: Context) {

    /** Order: manually chosen file, .lrc next to the song, lyrics embedded in the ID3 tag. */
    suspend fun load(song: Song, manualUri: String?): Lyrics? = withContext(Dispatchers.IO) {
        val sources: List<() -> String?> = listOf(
            { manualUri?.let { readUri(Uri.parse(it)) } },
            { readSidecar(song) },
            { readEmbedded(song) }
        )
        for (source in sources) {
            val text = try { source() } catch (_: Exception) { null }
            val lyrics = text?.let { parse(it) }
            if (lyrics != null) return@withContext lyrics
        }
        null
    }

    private fun parse(text: String): Lyrics? {
        val lines = parseLrc(text)
        if (lines.isNotEmpty()) return Lyrics(lines, null)
        val plain = text.trim()
        return if (plain.isNotEmpty()) Lyrics(emptyList(), plain) else null
    }

    private fun readUri(uri: Uri): String? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }

    @Suppress("DEPRECATION")
    private fun readSidecar(song: Song): String? {
        if (song.folder.isEmpty()) return null
        val base = Environment.getExternalStorageDirectory()
        val file = File(base, song.folder + song.displayName.substringBeforeLast('.') + ".lrc")
        return if (file.exists() && file.canRead()) file.readText() else null
    }

    private fun readEmbedded(song: Song): String? {
        context.contentResolver.openInputStream(song.uri)?.use { input ->
            val header = ByteArray(10)
            if (!readFully(input, header, 10)) return null
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return null
            val version = header[3].toInt()
            if (version != 3 && version != 4) return null
            val size = syncSafe(header, 6)
            if (size <= 0 || size > MAX_TAG_BYTES) return null
            val tag = ByteArray(size)
            if (!readFully(input, tag, size)) return null

            var pos = 0
            if (header[5].toInt() and 0x40 != 0 && tag.size >= 4) { // extended header
                pos = if (version == 4) syncSafe(tag, 0) else bigEndian(tag, 0) + 4
            }
            while (pos + 10 <= tag.size) {
                if (tag[pos].toInt() == 0) break
                val id = String(tag, pos, 4, Charsets.ISO_8859_1)
                val frameSize = if (version == 4) syncSafe(tag, pos + 4) else bigEndian(tag, pos + 4)
                if (frameSize <= 0 || pos + 10 + frameSize > tag.size) break
                if (id == "USLT") return decodeUslt(tag, pos + 10, frameSize)
                pos += 10 + frameSize
            }
        }
        return null
    }

    private fun decodeUslt(data: ByteArray, start: Int, length: Int): String? {
        if (length < 5) return null
        val encoding = data[start].toInt()
        val end = start + length
        var i = start + 4 // encoding byte + 3 language bytes
        val wide = encoding == 1 || encoding == 2
        // skip the content descriptor
        if (wide) {
            while (i + 1 < end && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < end && data[i].toInt() != 0) i++
            i += 1
        }
        if (i >= end) return null
        val charset: Charset = when (encoding) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(data, i, end - i, charset).trimEnd('\u0000').ifBlank { null }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var read = 0
        while (read < length) {
            val n = input.read(buffer, read, length - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun syncSafe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun bigEndian(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    private companion object {
        const val MAX_TAG_BYTES = 4 * 1024 * 1024
        private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val offsetTag = Regex("""\[offset:\s*(-?\d+)]""", RegexOption.IGNORE_CASE)

        fun parseLrc(text: String): List<LyricLine> {
            var offset = 0L
            val out = ArrayList<LyricLine>()
            for (raw in text.lineSequence()) {
                val line = raw.trim().removePrefix("\uFEFF")
                if (line.isEmpty()) continue
                val off = offsetTag.find(line)
                if (off != null) {
                    offset = off.groupValues[1].toLongOrNull() ?: 0L
                    continue
                }
                val tags = timeTag.findAll(line).toList()
                if (tags.isEmpty()) continue
                val body = line.substring(tags.last().range.last + 1).trim()
                for (t in tags) {
                    val minutes = t.groupValues[1].toLong()
                    val seconds = t.groupValues[2].toLong()
                    val frac = t.groupValues[3]
                    val millis = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                    out += LyricLine(minutes * 60_000 + seconds * 1_000 + millis, body)
                }
            }
            return out.map { it.copy(timeMs = (it.timeMs - offset).coerceAtLeast(0L)) }.sortedBy { it.timeMs }
        }
    }
}
