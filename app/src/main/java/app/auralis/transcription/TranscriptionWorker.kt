package app.auralis.transcription

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.auralis.data.ApplicationGraph
import app.auralis.data.CaptionStore
import app.auralis.data.LibraryBook
import app.auralis.domain.RecognizedWord
import app.auralis.domain.clusterVoices
import app.auralis.domain.groupWords
import app.auralis.domain.toWebVtt
import app.auralis.domain.TranscriptSlice
import app.auralis.domain.planSlices
import app.auralis.domain.sliceOrder
import app.auralis.domain.voiceLabel
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.SpeakerModel

/**
 * Builds a transcript for one book entirely on the device: words with exact timing, plus voices
 * grouped by speaker fingerprint. Finished chapters are saved as they complete, so an interrupted
 * run resumes where it stopped instead of starting over.
 */
class TranscriptionWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    /** One chapter's recognition: words (times relative to the chapter) and utterance voices. */
    private class ChapterResult(
        val words: List<RecognizedWord>,
        /** Utterance index for each word. */
        val utterances: List<Int>,
        /** Speaker fingerprint per utterance, or null when the utterance was too short. */
        val fingerprints: List<FloatArray?>,
    )

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Preparing", 0f)

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val bookId = inputData.getString(KEY_BOOK_ID) ?: return@withContext failure("Missing book.")
            // A foreground job is not stopped after Android's 10-minute background limit. If the
            // system refuses (app started in the background), carry on; progress is saved anyway.
            runCatching { setForeground(foregroundInfo("Preparing", 0f)) }
            try {
                val book = loadBook(bookId) ?: return@withContext failure("This book is no longer in your library.")
                val store = SpeechModelStore(applicationContext)
                if (!store.isInstalled(SpeechModels.SPEECH)) {
                    progress("Downloading speech model", 0f)
                    store.install(SpeechModels.SPEECH) { reportAsync("Downloading speech model", it * .08f) }
                }
                if (!store.isInstalled(SpeechModels.SPEAKER)) {
                    store.install(SpeechModels.SPEAKER) {
                        reportAsync("Downloading voice model", .08f + it * .02f)
                    }
                }
                val captions = CaptionStore(applicationContext)
                val partial = captions.partialDirectory(bookId).apply { mkdirs() }
                val chapters = book.orderedChapters
                check(chapters.isNotEmpty()) { "This book has no chapters to transcribe." }
                // Work in ~4-minute pieces rather than whole chapters: a one-chapter book can still
                // use several cores, and the piece where the listener is comes back first.
                val slices = planSlices(chapters.map { it.durationMs })
                fun legacy(chapter: Int) = File(partial, "${chapters[chapter].id}.json")
                fun saved(slice: TranscriptSlice) =
                    File(partial, "${chapters[slice.chapter].id}.s${slice.index}.json")
                fun isDone(slice: TranscriptSlice) = legacy(slice.chapter).isFile || saved(slice).isFile
                val totalMs = chapters.sumOf { it.durationMs }.coerceAtLeast(1)
                val order =
                    sliceOrder(slices, book.progress?.chapterIndex ?: 0, book.progress?.positionMs ?: 0)
                        .map { slices[it] }
                        .filterNot(::isDone)
                val queue = ConcurrentLinkedQueue(order)
                val doneMs = AtomicLong(slices.filter(::isDone).sumOf { it.endMs - it.startMs })
                val pieces = AtomicInteger(0)
                val written = AtomicInteger(0)
                val writeLock = Mutex()
                val threads = parallelChapters()
                fun label(ms: Long) = "Transcribed ${ms / 60_000} of ${totalMs / 60_000} min"
                progress(label(doneMs.get()), fraction(doneMs.get(), totalMs))

                val model = Model(store.directory(SpeechModels.SPEECH).absolutePath)
                val speakers = SpeakerModel(store.directory(SpeechModels.SPEAKER).absolutePath)
                try {
                    coroutineScope {
                        repeat(threads) {
                            launch(Dispatchers.IO) {
                                while (true) {
                                    val slice = queue.poll() ?: break
                                    ensureActive()
                                    val chapter = chapters[slice.chapter]
                                    val file = book.files.first { it.id == chapter.fileId }
                                    val length = slice.endMs - slice.startMs
                                    // Hear a little past the end so a word on the boundary is whole.
                                    val decodeEnd =
                                        minOf(chapter.durationMs, slice.endMs + SLICE_OVERLAP_MS)
                                    val began = android.os.SystemClock.elapsedRealtime()
                                    val result =
                                        recognise(
                                            model,
                                            speakers,
                                            android.net.Uri.parse(file.uri),
                                            chapter.startMs + slice.startMs,
                                            chapter.startMs + decodeEnd,
                                            keepUntilMs = length,
                                        )
                                    // Write then rename, so a parallel checkpoint never reads half a file.
                                    val target = saved(slice)
                                    val temporary = File(partial, "${target.name}.tmp")
                                    temporary.writeText(encode(result))
                                    check(temporary.renameTo(target)) { "Could not save progress." }
                                    val took = android.os.SystemClock.elapsedRealtime() - began
                                    android.util.Log.i(
                                        LOG_TAG,
                                        "piece ${slice.chapter}.${slice.index}: ${length / 1000}s audio in ${took / 1000}s " +
                                            "(%.1fx) on $threads threads".format(length.toFloat() / took.coerceAtLeast(1)),
                                    )
                                    val ms = doneMs.addAndGet(length)
                                    progress(label(ms), fraction(ms, totalMs))
                                    // Publish early: the listener's own piece first, then every few
                                    // pieces, so lines appear long before the book is done.
                                    val count = pieces.incrementAndGet()
                                    if (written.get() == 0 || count % CHECKPOINT_EVERY == 0) {
                                        writeLock.withLock {
                                            if (writeTranscript(bookId, chapters, slices, partial, captions) > 0)
                                                progressReady(written.incrementAndGet(), label(ms), fraction(ms, totalMs))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    speakers.close()
                    model.close()
                }

                val (lines, voices) =
                    writeLock.withLock {
                        writeTranscript(bookId, chapters, slices, partial, captions) to lastVoiceCount
                    }
                check(lines > 0) { "No speech was recognised in this book." }
                partial.deleteRecursively()
                Result.success(workDataOf(KEY_LINES to lines, KEY_VOICES to voices))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failure(error.message ?: "Transcription failed.")
            }
        }

    @Volatile private var lastVoiceCount = 0

    /**
     * Writes the transcript from every chapter finished so far, grouping voices across all of
     * them so a character keeps one label throughout. Chapters not yet done simply have no lines.
     *
     * @return number of lines written
     */
    private fun writeTranscript(
        bookId: String,
        chapters: List<app.auralis.data.ChapterEntity>,
        slices: List<TranscriptSlice>,
        partial: File,
        captions: CaptionStore,
    ): Int {
        val bookWords = mutableListOf<RecognizedWord>()
        val wordUtterance = mutableListOf<Int>()
        val fingerprints = mutableListOf<FloatArray?>()
        fun append(result: ChapterResult, offset: Long) {
            val base = fingerprints.size
            fingerprints += result.fingerprints
            result.words.forEachIndexed { i, word ->
                val shifted = word.copy(startMs = word.startMs + offset, endMs = word.endMs + offset)
                // Same rule as joinSliceWords: skip the tail of a word the previous piece heard whole.
                val last = bookWords.lastOrNull()
                if (last != null && shifted.startMs < last.endMs - 50) return@forEachIndexed
                bookWords += shifted
                wordUtterance += base + result.utterances[i]
            }
        }
        var offset = 0L
        chapters.forEachIndexed { index, chapter ->
            val whole = File(partial, "${chapter.id}.json")
            if (whole.isFile) {
                append(decode(whole.readText()), offset)
            } else {
                for (slice in slices) {
                    if (slice.chapter != index) continue
                    val piece = File(partial, "${chapter.id}.s${slice.index}.json")
                    if (piece.isFile) append(decode(piece.readText()), offset + slice.startMs)
                }
            }
            offset += chapter.durationMs
        }
        val voices = clusterVoices(fingerprints)
        val distinct = voices.filter { it >= 0 }.toSet().size
        lastVoiceCount = distinct
        var lastVoice: String? = null
        val labelled =
            bookWords.mapIndexed { i, word ->
                val id = voices.getOrElse(wordUtterance[i]) { -1 }
                // Short utterances without a fingerprint keep the voice before them.
                val voice = if (id >= 0) voiceLabel(id) else lastVoice
                lastVoice = voice
                word.copy(speaker = if (distinct > 1) voice else null)
            }
        val cues = groupWords(labelled)
        if (cues.isNotEmpty()) captions.save(bookId, toWebVtt(cues))
        return cues.size
    }

    /**
     * How many chapters to recognise at once. Each recogniser needs memory and a core; on a phone
     * that is low on memory or already warm, extra parallel work just swaps and throttles.
     */
    private fun parallelChapters(): Int {
        val cores = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, MAX_PARALLEL)
        val activity =
            applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memory = android.app.ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
        val power = applicationContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val warm =
            android.os.Build.VERSION.SDK_INT >= 29 &&
                power.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE
        val tight = memory.lowMemory || memory.availMem < 1_500L * 1024 * 1024
        return if (warm || tight) minOf(cores, 2) else cores
    }

    private fun fraction(doneMs: Long, totalMs: Long) =
        .1f + .9f * doneMs.toFloat() / totalMs.coerceAtLeast(1)

    private suspend fun progressReady(ready: Int, label: String, fraction: Float) {
        setProgress(workDataOf(KEY_LABEL to label, KEY_FRACTION to fraction, KEY_READY to ready))
        runCatching { setForeground(foregroundInfo(label, fraction)) }
    }

    private fun foregroundInfo(label: String, fraction: Float): ForegroundInfo {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                android.app.NotificationChannel(
                    CHANNEL,
                    "Transcripts",
                    android.app.NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Progress while Auralis makes a transcript on this phone" }
            )
        }
        val percent = (fraction * 100).toInt().coerceIn(0, 100)
        val notification =
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(app.auralis.R.drawable.ic_signature_monochrome)
                .setContentTitle("Making transcript")
                .setContentText(label)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setProgress(100, percent, percent == 0)
                .addAction(
                    0,
                    "Cancel",
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id),
                )
                .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun recognise(
        model: Model,
        speakers: SpeakerModel,
        uri: android.net.Uri,
        startMs: Long,
        endMs: Long,
        keepUntilMs: Long = Long.MAX_VALUE,
    ): ChapterResult {
        val words = mutableListOf<RecognizedWord>()
        val utterances = mutableListOf<Int>()
        val fingerprints = mutableListOf<FloatArray?>()
        val recognizer = Recognizer(model, AudioDecoder.SAMPLE_RATE.toFloat(), speakers)
        fun collect(json: String) {
            val result = JSONObject(json)
            val heard = result.optJSONArray("result") ?: return
            if (heard.length() == 0) return
            val utterance = fingerprints.size
            val vector = result.optJSONArray("spk")
            // Very short utterances give unreliable fingerprints; let them inherit a voice.
            fingerprints +=
                if (vector != null && result.optInt("spk_frames", 0) >= MIN_SPEAKER_FRAMES)
                    FloatArray(vector.length()) { vector.getDouble(it).toFloat() }
                else null
            for (i in 0 until heard.length()) {
                val word = heard.getJSONObject(i)
                // Words that start in the overlap belong to the next piece.
                if ((word.getDouble("start") * 1000).toLong() >= keepUntilMs) continue
                words +=
                    RecognizedWord(
                        text = word.getString("word"),
                        startMs = (word.getDouble("start") * 1000).toLong(),
                        endMs = (word.getDouble("end") * 1000).toLong(),
                    )
                utterances += utterance
            }
        }
        try {
            recognizer.setWords(true)
            AudioDecoder.decodeRange(applicationContext, uri, startMs, endMs) { pcm, count ->
                if (isStopped) throw CancellationException("Transcription was cancelled.")
                if (recognizer.acceptWaveForm(pcm, count)) collect(recognizer.result)
            }
            collect(recognizer.finalResult)
        } finally {
            recognizer.close()
        }
        return ChapterResult(words, utterances, fingerprints)
    }

    private suspend fun loadBook(bookId: String): LibraryBook? {
        val graph =
            EntryPointAccessors.fromApplication(applicationContext, ApplicationGraph::class.java)
        return graph.database().dao().observeLibrary().first().firstOrNull { it.book.id == bookId }
    }

    private suspend fun progress(label: String, fraction: Float) {
        setProgress(workDataOf(KEY_LABEL to label, KEY_FRACTION to fraction.coerceIn(0f, 1f)))
        runCatching { setForeground(foregroundInfo(label, fraction)) }
    }

    /** Non-suspending variant for callbacks that cannot suspend, such as download progress. */
    private fun reportAsync(label: String, fraction: Float) {
        setProgressAsync(workDataOf(KEY_LABEL to label, KEY_FRACTION to fraction.coerceIn(0f, 1f)))
    }

    private fun failure(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    companion object {
        const val KEY_BOOK_ID = "bookId"
        const val KEY_LABEL = "label"
        const val KEY_FRACTION = "fraction"
        const val KEY_ERROR = "error"
        const val KEY_LINES = "lines"
        const val KEY_VOICES = "voices"
        /** Increments each time a partial transcript is written, so the player can reload it. */
        const val KEY_READY = "ready"
        private const val MIN_SPEAKER_FRAMES = 100
        private const val MAX_PARALLEL = 4
        private const val CHECKPOINT_EVERY = 3
        private const val SLICE_OVERLAP_MS = 1_500L
        private const val LOG_TAG = "AuralisTranscribe"
        private const val CHANNEL = "transcripts"
        private const val NOTIFICATION_ID = 4107

        fun uniqueName(bookId: String) = "transcript-$bookId"

        private fun encode(result: ChapterResult): String {
            val words = JSONArray()
            result.words.forEachIndexed { i, word ->
                words.put(
                    JSONObject()
                        .put("t", word.text)
                        .put("s", word.startMs)
                        .put("e", word.endMs)
                        .put("u", result.utterances[i])
                )
            }
            val voices = JSONArray()
            result.fingerprints.forEach { vector ->
                voices.put(
                    if (vector == null) JSONObject.NULL
                    else JSONArray().apply { vector.forEach { put(it.toDouble()) } }
                )
            }
            return JSONObject().put("words", words).put("voices", voices).toString()
        }

        private fun decode(json: String): ChapterResult {
            val root = JSONObject(json)
            val words = root.getJSONArray("words")
            val voices = root.getJSONArray("voices")
            return ChapterResult(
                words =
                    List(words.length()) { i ->
                        val word = words.getJSONObject(i)
                        RecognizedWord(word.getString("t"), word.getLong("s"), word.getLong("e"))
                    },
                utterances = List(words.length()) { i -> words.getJSONObject(i).getInt("u") },
                fingerprints =
                    List(voices.length()) { i ->
                        voices.optJSONArray(i)?.let { v ->
                            FloatArray(v.length()) { v.getDouble(it).toFloat() }
                        }
                    },
            )
        }
    }
}
