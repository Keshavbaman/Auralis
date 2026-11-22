package app.auralis.domain

/** One spoken word with times from the start of the book, in milliseconds. */
data class CueWord(val text: String, val startMs: Long, val endMs: Long)

/**
 * One caption line. Times count from the start of the book, in milliseconds. [words] holds exact
 * per-word timing when the transcript has it (generated transcripts do; most imported files don't).
 */
data class Cue(
    val startMs: Long,
    val endMs: Long,
    val speaker: String?,
    val text: String,
    val words: List<CueWord> = emptyList(),
) {
    /**
     * Word timings for word-by-word highlighting: exact when available, otherwise the line's
     * duration is shared out by word length, which is close enough to follow along.
     */
    fun timedWords(): List<CueWord> {
        if (words.isNotEmpty()) return words
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val weight = tokens.sumOf { it.length + 1 }.toDouble()
        val span = (endMs - startMs).coerceAtLeast(1).toDouble()
        var at = startMs.toDouble()
        return tokens.map { token ->
            val length = span * (token.length + 1) / weight
            CueWord(token, at.toLong(), (at + length).toLong()).also { at += length }
        }
    }
}

/** Sorted cues with O(log n) lookup of the line that is active at a book position. */
class CaptionTrack(val cues: List<Cue>) {
    /**
     * Index of the last cue that has started by [positionMs], or -1 before the first cue. A gap
     * between cues keeps the previous line highlighted, which reads better than flickering.
     */
    fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

private val timingLine =
    Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")
private val voiceTag = Regex("""<v(?:\.[^\s>]+)?\s+([^>]+)>""")
private val anyTag = Regex("""<[^>]+>""")
/** WebVTT karaoke timestamp, e.g. `<00:01:02.500>`, marking when the following word starts. */
private val timestampTag = Regex("""<((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})>""")
/** Any markup tag that is not a timestamp (voice, italics, classes). */
private val markupTag = Regex("""<(?!\d)[^>]+>""")
private val blankLines = Regex("""\n[ \t]*\n+""")
private val whitespace = Regex("""\s+""")

/**
 * Parses WebVTT or SubRip text. Malformed blocks are skipped rather than failing the whole file,
 * so a caption file with one bad cue still produces a usable transcript.
 */
fun parseCaptions(raw: String): List<Cue> {
    val text = raw.removePrefix("﻿").replace("\r\n", "\n").replace('\r', '\n')
    val cues = mutableListOf<Cue>()
    for (block in text.split(blankLines)) {
        val lines = block.lines().filter { it.isNotBlank() }
        val timingIndex = lines.indexOfFirst { timingLine.containsMatchIn(it) }
        if (timingIndex < 0) continue
        val match = timingLine.find(lines[timingIndex]) ?: continue
        val start = parseTimestamp(match.groupValues[1]) ?: continue
        val end = parseTimestamp(match.groupValues[2]) ?: continue
        val body = lines.drop(timingIndex + 1).joinToString(" ")
        val speaker = voiceTag.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val clean =
            decodeEntities(anyTag.replace(body, ""))
                .replace(whitespace, " ")
                .trim()
        if (clean.isEmpty()) continue
        val stop = maxOf(start, end)
        cues += Cue(start, stop, speaker, clean, timedWordsOf(markupTag.replace(body, ""), start, stop))
    }
    return cues.sortedBy { it.startMs }
}

/**
 * Reads WebVTT inline timestamps into per-word timings. Text before the first timestamp starts at
 * the cue start; each timestamp starts the text after it. Returns empty when there are none.
 */
private fun timedWordsOf(body: String, start: Long, end: Long): List<CueWord> {
    val marks = timestampTag.findAll(body).toList()
    if (marks.isEmpty()) return emptyList()
    val segments = mutableListOf(start to body.substring(0, marks.first().range.first))
    marks.forEachIndexed { index, mark ->
        val at = parseTimestamp(mark.groupValues[1]) ?: return emptyList()
        val until = marks.getOrNull(index + 1)?.range?.first ?: body.length
        segments += at to body.substring(mark.range.last + 1, until)
    }
    val words = mutableListOf<CueWord>()
    segments.forEachIndexed { index, (segmentStart, raw) ->
        val tokens = decodeEntities(raw).split(whitespace).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return@forEachIndexed
        val segmentEnd = (segments.getOrNull(index + 1)?.first ?: end).coerceAtLeast(segmentStart)
        val step = (segmentEnd - segmentStart) / tokens.size
        tokens.forEachIndexed { i, token ->
            val wordStart = segmentStart + step * i
            words += CueWord(token, wordStart, if (i == tokens.lastIndex) segmentEnd else wordStart + step)
        }
    }
    return words
}

/** Accepts `HH:MM:SS.mmm`, `MM:SS.mmm`, and SubRip's comma separator. */
internal fun parseTimestamp(value: String): Long? {
    val parts = value.replace(',', '.').split(':')
    if (parts.size !in 2..3) return null
    val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0L
    val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
    val secondParts = parts.last().split('.')
    val seconds = secondParts[0].toLongOrNull() ?: return null
    val millis =
        secondParts.getOrNull(1)?.let { ((it + "00").take(3)).toLongOrNull() ?: return null } ?: 0L
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

private fun decodeEntities(value: String): String =
    value
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

/** Where one chapter sits: its range inside its audio file and its start in the whole book. */
data class ChapterSpan(val fileStartMs: Long, val fileEndMs: Long, val bookStartMs: Long)

/**
 * Moves cues timed against one audio file onto the book timeline, chapter by chapter. Cues that
 * fall outside every chapter of that file (e.g. trimmed intros) are dropped.
 */
fun mapFileCuesToBook(cues: List<Cue>, spans: List<ChapterSpan>): List<Cue> =
    cues.mapNotNull { cue ->
        val span =
            spans.firstOrNull { cue.startMs >= it.fileStartMs && cue.startMs < it.fileEndMs }
                ?: return@mapNotNull null
        val shift = span.bookStartMs - span.fileStartMs
        cue.copy(
            startMs = cue.startMs + shift,
            endMs = cue.endMs + shift,
            words = cue.words.map { it.copy(startMs = it.startMs + shift, endMs = it.endMs + shift) },
        )
    }
