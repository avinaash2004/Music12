package com.offlinemusicpro.player.features.trimmer

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.nio.ByteBuffer
import kotlin.math.min

sealed interface TrimResult {
    data class Success(val uri: Uri, val note: String) : TrimResult
    data class Failure(val message: String) : TrimResult
}

/**
 * Local audio trimmer built on MediaExtractor/MediaCodec/MediaMuxer.
 *  - AAC sources (.m4a/.aac): lossless cut, samples are copied without re-encoding.
 *  - Everything else the device can decode (MP3, FLAC, WAV, OGG...): decoded and re-encoded to AAC 192 kbps.
 * Output is always an .m4a file written into the folder the user picked.
 */
object AudioTrimmer {

    private const val TIMEOUT_US = 10_000L

    fun durationMs(context: Context, uri: Uri): Long = try {
        val r = MediaMetadataRetriever()
        r.setDataSource(context, uri)
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        r.release()
        d
    } catch (_: Exception) {
        0L
    }

    fun trim(
        context: Context,
        source: Uri,
        startMs: Long,
        endMs: Long,
        destinationTree: Uri,
        baseName: String,
        onProgress: (Float) -> Unit
    ): TrimResult {
        if (endMs - startMs < 500) return TrimResult.Failure("Selected range is too short.")
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            destinationTree, DocumentsContract.getTreeDocumentId(destinationTree)
        )
        val safeName = baseName.trim().replace(Regex("""[\\/:*?"<>|]"""), "_").ifEmpty { "trimmed" }
        val created: Uri? = try {
            DocumentsContract.createDocument(resolver, parent, "audio/mp4", "$safeName.m4a")
        } catch (_: Exception) {
            null
        }
        val outUri: Uri = created ?: return TrimResult.Failure("Could not create the output file in that folder.")

        var pfd: ParcelFileDescriptor? = null
        val extractor = MediaExtractor()
        return try {
            pfd = resolver.openFileDescriptor(outUri, "rw")
                ?: throw IllegalStateException("Could not open the output file.")
            extractor.setDataSource(context, source, null)

            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("audio/")) {
                    track = i; format = f; break
                }
            }
            if (track < 0 || format == null) throw UnsupportedOperationException("No audio track found in this file.")
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val startUs = startMs * 1000
            val endUs = endMs * 1000

            val note = if (mime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                remux(extractor, track, format, startUs, endUs, pfd, onProgress)
                "Lossless cut (no re-encoding)."
            } else {
                Transcoder(extractor, track, format, startUs, endUs, pfd, onProgress).run()
                "Re-encoded to AAC 192 kbps because the source is $mime."
            }
            TrimResult.Success(outUri, note)
        } catch (e: UnsupportedOperationException) {
            runCatching { DocumentsContract.deleteDocument(resolver, outUri) }
            TrimResult.Failure(e.message ?: "Unsupported audio format.")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, outUri) }
            TrimResult.Failure("Trimming failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { extractor.release() }
            runCatching { pfd?.close() }
        }
    }

    private fun remux(
        extractor: MediaExtractor, track: Int, format: MediaFormat,
        startUs: Long, endUs: Long, out: ParcelFileDescriptor, onProgress: (Float) -> Unit
    ) {
        val muxer = MediaMuxer(out.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        try {
            val dst = muxer.addTrack(format)
            muxer.start()
            extractor.selectTrack(track)
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val buffer = ByteBuffer.allocate(1 shl 18)
            val info = MediaCodec.BufferInfo()
            var wrote = false
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                val t = extractor.sampleTime
                if (size < 0 || t < 0 || t > endUs) break
                if (t >= startUs) {
                    val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                        MediaCodec.BUFFER_FLAG_KEY_FRAME
                    } else 0
                    info.set(0, size, t - startUs, flags)
                    muxer.writeSampleData(dst, buffer, info)
                    wrote = true
                    onProgress(((t - startUs).toFloat() / (endUs - startUs)).coerceIn(0f, 1f))
                }
                extractor.advance()
            }
            if (!wrote) throw IllegalStateException("The selected range contains no audio.")
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
        }
    }

    private class Transcoder(
        val extractor: MediaExtractor, val track: Int, val format: MediaFormat,
        val startUs: Long, val endUs: Long, val out: ParcelFileDescriptor, val onProgress: (Float) -> Unit
    ) {
        private var decoder: MediaCodec? = null
        private var encoder: MediaCodec? = null
        private var muxer: MediaMuxer? = null
        private var muxTrack = -1
        private var muxStarted = false
        private var encoderDone = false
        private var sampleRate = 0
        private var channels = 0
        private var framesFed = 0L
        private val encInfo = MediaCodec.BufferInfo()

        fun run() {
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            val dec = try {
                MediaCodec.createDecoderByType(mime)
            } catch (e: Exception) {
                throw UnsupportedOperationException("This device cannot decode $mime audio.")
            }
            decoder = dec
            try {
                extractor.selectTrack(track)
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                dec.configure(format, null, null, 0)
                dec.start()

                val decInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var decoderDone = false
                var produced = false

                while (!encoderDone) {
                    if (!inputDone) {
                        val idx = dec.dequeueInputBuffer(TIMEOUT_US)
                        if (idx >= 0) {
                            val buf = dec.getInputBuffer(idx)!!
                            val size = extractor.readSampleData(buf, 0)
                            val t = extractor.sampleTime
                            if (size < 0 || t < 0 || t > endUs) {
                                dec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                dec.queueInputBuffer(idx, 0, size, t, 0)
                                extractor.advance()
                            }
                        }
                    }
                    if (!decoderDone) {
                        val outIdx = dec.dequeueOutputBuffer(decInfo, TIMEOUT_US)
                        if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            setupEncoder(dec.outputFormat)
                        } else if (outIdx >= 0) {
                            if (encoder == null) setupEncoder(dec.outputFormat)
                            val buf = dec.getOutputBuffer(outIdx)!!
                            if (decInfo.size > 0 && decInfo.presentationTimeUs >= startUs && decInfo.presentationTimeUs <= endUs) {
                                val chunk = ByteArray(decInfo.size)
                                buf.position(decInfo.offset)
                                buf.limit(decInfo.offset + decInfo.size)
                                buf.get(chunk)
                                feedEncoder(chunk)
                                produced = true
                                onProgress(((decInfo.presentationTimeUs - startUs).toFloat() / (endUs - startUs)).coerceIn(0f, 1f))
                            }
                            dec.releaseOutputBuffer(outIdx, false)
                            if (decInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                decoderDone = true
                                if (!produced) throw IllegalStateException("The selected range contains no audio.")
                                sendEncoderEos()
                            }
                        }
                    }
                    drainEncoder()
                }
                muxer?.stop()
            } finally {
                runCatching { decoder?.stop() }; runCatching { decoder?.release() }
                runCatching { encoder?.stop() }; runCatching { encoder?.release() }
                runCatching { muxer?.release() }
            }
        }

        private fun setupEncoder(outFormat: MediaFormat) {
            if (encoder != null) return
            if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT
            ) {
                throw UnsupportedOperationException("This file decodes to a PCM format the trimmer does not support.")
            }
            sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            if (channels > 2) throw UnsupportedOperationException("Only mono and stereo audio can be trimmed.")
            val enc = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1 shl 16)
            }
            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec.configure(enc, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encoder = codec
            muxer = MediaMuxer(out.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        }

        private fun feedEncoder(pcm: ByteArray) {
            val enc = encoder ?: return
            val frameBytes = 2 * channels
            var offset = 0
            while (offset < pcm.size) {
                val idx = enc.dequeueInputBuffer(TIMEOUT_US)
                if (idx < 0) { drainEncoder(); continue }
                val ib = enc.getInputBuffer(idx)!!
                ib.clear()
                var n = min(ib.remaining(), pcm.size - offset)
                n -= n % frameBytes
                if (n <= 0) break
                ib.put(pcm, offset, n)
                val ptsUs = framesFed * 1_000_000L / sampleRate
                enc.queueInputBuffer(idx, 0, n, ptsUs, 0)
                framesFed += n / frameBytes
                offset += n
            }
        }

        private fun sendEncoderEos() {
            val enc = encoder ?: return
            while (true) {
                val idx = enc.dequeueInputBuffer(TIMEOUT_US)
                if (idx >= 0) {
                    val ptsUs = framesFed * 1_000_000L / sampleRate
                    enc.queueInputBuffer(idx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    return
                }
                drainEncoder()
            }
        }

        private fun drainEncoder() {
            val enc = encoder ?: return
            while (true) {
                val idx = enc.dequeueOutputBuffer(encInfo, 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        muxTrack = muxer!!.addTrack(enc.outputFormat)
                        muxer!!.start()
                        muxStarted = true
                    }
                    idx >= 0 -> {
                        val buf = enc.getOutputBuffer(idx)!!
                        if (encInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) encInfo.size = 0
                        if (encInfo.size > 0 && muxStarted) {
                            buf.position(encInfo.offset)
                            buf.limit(encInfo.offset + encInfo.size)
                            muxer!!.writeSampleData(muxTrack, buf, encInfo)
                        }
                        enc.releaseOutputBuffer(idx, false)
                        if (encInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                            return
                        }
                    }
                }
            }
        }
    }
}
