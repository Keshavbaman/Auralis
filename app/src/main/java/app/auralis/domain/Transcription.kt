package app.auralis.domain

/** A recognised word with times in milliseconds from the start of the book. */
data class RecognizedWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    /** Voice label such as "Voice 2", or null when voices are not known. */
    val speaker: String? = null,
)

/**
 * Groups recognised words into readable caption lines. Speech recognisers do not emit punctuation,
 * so lines break on pauses, on a maximum word count, and on a maximum length in time.
 */
fun groupWords(
    words: List<RecognizedWord>,
    maxGapMs: Long = 700,
    maxWords: Int = 14,
    maxSpanMs: Long = 8_000,
): List<Cue> {
    val cues = mutableListOf<Cue>()
    val line = mutableListOf<RecognizedWord>()

    fun flush() {
        if (line.isEmpty()) return
        val first = line.first()
        val words =
            line.mapIndexed { index, word ->
                val text = word.text.trim()
                CueWord(
                    if (index == 0) text.replaceFirstChar { it.uppercase() } else text,
                    word.startMs,
                    maxOf(word.startMs, word.endMs),
                )
            }
        cues +=
            Cue(
                first.startMs,
                maxOf(first.startMs, line.last().endMs),
                first.speaker,
                words.joinToString(" ") { it.text },
                words,
            )
        line.clear()
    }

    for (word in words) {
        if (word.text.isBlank()) continue
        val previous = line.lastOrNull()
        val breaks =
            previous != null &&
                (word.speaker != previous.speaker ||
                    word.startMs - previous.endMs > maxGapMs ||
                    line.size >= maxWords ||
                    word.endMs - line.first().startMs > maxSpanMs)
        if (breaks) flush()
        line += word
    }
    flush()
    return cues
}

/** Serialises cues as WebVTT. Text is escaped so it can never be mistaken for markup. */
fun toWebVtt(cues: List<Cue>): String = buildString {
    append("WEBVTT\n\n")
    for (cue in cues) {
        append(vttTimestamp(cue.startMs)).append(" --> ").append(vttTimestamp(cue.endMs)).append('\n')
        cue.speaker?.let { append("<v ").append(escape(it).replace('>', ' ')).append("> ") }
        if (cue.words.isEmpty()) {
            append(escape(cue.text).replace('\n', ' '))
        } else {
            // Karaoke timing: every word after the first is preceded by its start time.
            cue.words.forEachIndexed { index, word ->
                if (index > 0) append(' ').append('<').append(vttTimestamp(word.startMs)).append('>')
                append(escape(word.text))
            }
        }
        append("\n\n")
    }
}

internal fun vttTimestamp(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = safe / 60_000 % 60
    val seconds = safe / 1000 % 60
    val millis = safe % 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

private fun escape(value: String): String =
    value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/**
 * Order to transcribe chapters in: the chapter being listened to first, then onward through the
 * book, then the chapters before it. Lines for where the listener is appear within minutes.
 */
fun transcriptionOrder(count: Int, current: Int): List<Int> {
    if (count <= 0) return emptyList()
    val start = current.coerceIn(0, count - 1)
    return (start until count) + (0 until start)
}

/** A piece of one chapter, transcribed as its own unit. Times are relative to the chapter. */
data class TranscriptSlice(val chapter: Int, val index: Int, val startMs: Long, val endMs: Long)

/**
 * Cuts every chapter into pieces of about [sliceMs], so one long chapter can be transcribed in
 * parallel and start where the listener is. A short trailing remainder joins the last piece.
 */
fun planSlices(chapterDurations: List<Long>, sliceMs: Long = 240_000L): List<TranscriptSlice> =
    chapterDurations.flatMapIndexed { chapter, duration ->
        val count = maxOf(1, ((duration + sliceMs / 2) / sliceMs).toInt())
        List(count) { i ->
            TranscriptSlice(
                chapter,
                i,
                i * sliceMs,
                if (i == count - 1) duration else (i + 1) * sliceMs,
            )
        }
    }

/**
 * Order to transcribe [slices]: the piece at the listener's position first, then onward through
 * the book, then the pieces before it.
 */
fun sliceOrder(slices: List<TranscriptSlice>, chapter: Int, positionMs: Long): List<Int> {
    if (slices.isEmpty()) return emptyList()
    val current =
        slices.indexOfFirst { it.chapter == chapter && positionMs < it.endMs }
            .takeIf { it >= 0 }
            ?: slices.indexOfFirst { it.chapter >= chapter }.takeIf { it >= 0 }
            ?: 0
    return (current until slices.size) + (0 until current)
}

/**
 * Joins pieces in book order, dropping a word that starts before the previous word ended: the
 * tail of a word cut at a piece boundary, which the earlier piece already heard in full.
 */
fun joinSliceWords(pieces: List<List<RecognizedWord>>): List<RecognizedWord> {
    val joined = mutableListOf<RecognizedWord>()
    for (piece in pieces) for (word in piece) {
        val last = joined.lastOrNull()
        if (last != null && word.startMs < last.endMs - 50) continue
        joined += word
    }
    return joined
}
