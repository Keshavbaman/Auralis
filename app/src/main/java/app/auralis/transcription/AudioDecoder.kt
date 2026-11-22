package app.auralis.transcription

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Decodes part of an audio file into the 16 kHz mono PCM the speech recogniser expects. */
object AudioDecoder {
    const val SAMPLE_RATE = 16_000
    /** Waiting for an empty input buffer is pointless while decoded output is ready to drain. */
    private const val INPUT_TIMEOUT_US = 0L
    private const val OUTPUT_TIMEOUT_US = 5_000L

    /**
     * Passes samples in [startMs, endMs) to [sink] as (buffer, count). Times the recogniser reports
     * are relative to [startMs]. The sink is called on the decoding thread.
     */
    fun decodeRange(
        context: Context,
        uri: Uri,
        startMs: Long,
        endMs: Long,
        sink: (ShortArray, Int) -> Unit,
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)
                        ?.startsWith("audio/") == true
                } ?: error("This file has no audio track.")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Unknown audio format.")
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                pump(extractor, codec, format, startMs * 1000, endMs * 1000, sink)
            } finally {
                codec.stop()
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun pump(
        extractor: MediaExtractor,
        codec: MediaCodec,
        initialFormat: MediaFormat,
        startUs: Long,
        endUs: Long,
        sink: (ShortArray, Int) -> Unit,
    ) {
        var sampleRate = initialFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = initialFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var resampler = Resampler(sampleRate, sink)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val inputIndex = codec.dequeueInputBuffer(INPUT_TIMEOUT_US)
                if (inputIndex >= 0) {
                    val buffer = codec.getInputBuffer(inputIndex) ?: error("Decoder unavailable.")
                    val size = extractor.readSampleData(buffer, 0)
                    val time = extractor.sampleTime
                    if (size < 0 || time > endUs) {
                        codec.queueInputBuffer(
                            inputIndex,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inputIndex, 0, size, time, 0)
                        extractor.advance()
                    }
                }
            }
            when (val outputIndex = codec.dequeueOutputBuffer(info, OUTPUT_TIMEOUT_US)) {
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val changed = codec.outputFormat
                    sampleRate = changed.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = changed.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    resampler.finish()
                    resampler = Resampler(sampleRate, sink)
                }
                MediaCodec.INFO_TRY_AGAIN_LATER,
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    val buffer = codec.getOutputBuffer(outputIndex)
                    if (buffer != null && info.size > 0) {
                        val samples =
                            buffer
                                .slice(info.offset, info.size)
                                .order(ByteOrder.nativeOrder())
                                .asShortBuffer()
                        val frames = samples.remaining() / channels
                        for (frame in 0 until frames) {
                            val time = info.presentationTimeUs + frame * 1_000_000L / sampleRate
                            if (time < startUs) continue
                            if (time >= endUs) {
                                outputDone = true
                                break
                            }
                            var sum = 0
                            for (c in 0 until channels) sum += samples.get(frame * channels + c)
                            resampler.push((sum / channels).toShort())
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        resampler.finish()
    }

    /** Box-filter downsampler: each output sample is the average of the input samples it spans. */
    private class Resampler(inputRate: Int, private val sink: (ShortArray, Int) -> Unit) {
        private val step = inputRate.toDouble() / SAMPLE_RATE
        private val buffer = ShortArray(4096)
        private var fill = 0
        private var consumed = 0L
        private var produced = 0L
        private var sum = 0.0
        private var count = 0

        fun push(sample: Short) {
            sum += sample
            count++
            consumed++
            if (consumed >= (produced + 1) * step) {
                buffer[fill++] = (sum / count).roundToInt().coerceIn(-32768, 32767).toShort()
                produced++
                sum = 0.0
                count = 0
                if (fill == buffer.size) flush()
            }
        }

        fun finish() {
            if (count > 0) {
                buffer[fill++] = (sum / count).roundToInt().coerceIn(-32768, 32767).toShort()
                produced++
                sum = 0.0
                count = 0
            }
            flush()
        }

        private fun flush() {
            if (fill > 0) sink(buffer, fill)
            fill = 0
        }
    }
}
