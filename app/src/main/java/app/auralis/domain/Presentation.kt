package app.auralis.domain

import app.auralis.data.*
import java.time.*
import java.time.temporal.TemporalAdjusters

/** Explicit units for analytics; player clocks intentionally keep colon notation. */
fun listeningDuration(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return when {
        seconds == 0L -> "0 min"
        seconds < 60 -> "${seconds}s"
        seconds < 600 -> "${seconds / 60}m ${seconds % 60}s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600}h ${seconds / 60 % 60}m"
    }
}

data class CalendarRange(
    val start: LocalDate,
    val end: LocalDate,
    val weeks: List<List<LocalDate>>,
) {
    fun contains(date: LocalDate) = date >= start && date <= end
}

fun listeningCalendar(end: LocalDate, months: Long = 6): CalendarRange {
    val start = end.minusMonths(months).plusDays(1)
    val aligned = start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks =
        generateSequence(aligned) { it.plusWeeks(1) }
            .takeWhile { it <= end }
            .map { monday -> (0L..6).map { monday.plusDays(it) } }
            .toList()
    return CalendarRange(start, end, weeks)
}

data class SeriesInfo(val name: String, val number: Double?)

fun seriesInfo(book: BookEntity): SeriesInfo? {
    if (book.seriesName.isNotBlank()) return SeriesInfo(book.seriesName, book.seriesNumber)
    if (book.seriesInferenceDisabled) return null
    val match = Regex("^(.+?)\\s+(\\d+(?:\\.\\d+)?)\\s*[:–-]\\s+.+").find(book.title)
    return match?.let { SeriesInfo(it.groupValues[1], it.groupValues[2].toDoubleOrNull()) }
}

/** Leave the stored title intact and move known series prefixes out of the display title. */
fun bookDetailTitle(book: BookEntity, series: SeriesInfo? = seriesInfo(book)): String {
    if (series == null) return book.title
    val prefix = Regex(
        "^${Regex.escape(series.name)}\\s+\\d+(?:\\.\\d+)?\\s*[:–-]\\s+",
        RegexOption.IGNORE_CASE,
    )
    return book.title.replaceFirst(prefix, "").trim().ifBlank { book.title }
}

fun orderedSeries(books: List<LibraryBook>) =
    books.sortedWith(
        compareBy<LibraryBook> { seriesInfo(it.book)?.number ?: Double.MAX_VALUE }
            .thenComparator { a, b -> naturalOrder.compare(a.book.title, b.book.title) }
    )

/** Media ID, rather than a potentially stale playlist index, identifies the live chapter. */
fun LibraryBook.withPlayback(
    mediaId: String,
    position: Long,
    speed: Float,
    now: Long,
): LibraryBook {
    val index = orderedChapters.indexOfFirst { it.id == mediaId }
    if (index < 0) return this
    return copy(
            progress =
                PlaybackProgressEntity(
                    book.id,
                    index,
                    position.coerceIn(0, orderedChapters[index].durationMs),
                    progress?.lastPlayed ?: now,
                    speed,
                )
        )
        .also { it.listenedFraction = listenedFraction }
}

fun smartRewindMs(pausedMs: Long, enabled: Boolean): Long =
    if (enabled && pausedMs >= 5 * 60000L) 10000L else 0L

/** Only actual, unique ranges in the current coverage epoch can complete a chapter. */
fun chapterCoverageMs(book: LibraryBook, rows: List<ListeningSegmentEntity>): Map<String, Long> {
    val byChapter =
        rows
            .filter { it.bookId == book.book.id && it.startedAt >= book.book.progressResetAt }
            .groupBy { it.chapterId }
    return book.chapters.associate { ch ->
        ch.id to
            mergedDuration(
                byChapter[ch.id].orEmpty().map {
                    ContentRange(
                        it.fromMs.coerceIn(0, ch.durationMs),
                        it.toMs.coerceIn(0, ch.durationMs),
                    )
                }
            )
    }
}
