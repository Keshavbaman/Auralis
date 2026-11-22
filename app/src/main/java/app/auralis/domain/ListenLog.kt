package app.auralis.domain

import app.auralis.data.ListeningSegmentEntity
import java.time.LocalDate

/**
 * One sitting with a book. Playback sessions separated by only a short pause (a phone call, a
 * skipped stop) are merged, so the log reads like real listening rather than every tap.
 */
data class ListenSession(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val elapsedMs: Long,
    val contentMs: Long,
    val speed: Float,
    val firstChapterId: String,
    val lastChapterId: String,
    /** Position inside [firstChapterId] where the sitting began. */
    val fromMs: Long,
    /** Position inside [lastChapterId] where the sitting ended. */
    val toMs: Long,
    val date: LocalDate,
)

data class ListenDay(val date: LocalDate, val sessions: List<ListenSession>) {
    val elapsedMs: Long
        get() = sessions.sumOf { it.elapsedMs }
}

/**
 * Groups one book's listening segments into days and sittings, newest first.
 *
 * Segments before [resetAt] are ignored so a progress reset also starts a fresh log. Sessions on
 * the same day less than [mergeGapMs] apart become one sitting, and sittings shorter than
 * [minimumMs] are dropped as accidental taps.
 */
fun listenLog(
    bookId: String,
    rows: List<ListeningSegmentEntity>,
    resetAt: Long = 0,
    mergeGapMs: Long = 5 * 60_000L,
    minimumMs: Long = 10_000L,
): List<ListenDay> {
    val sittings = mutableListOf<ListenSession>()
    rows
        .asSequence()
        .filter { it.bookId == bookId && it.startedAt >= resetAt }
        .sortedBy { it.startedAt }
        .forEach { segment ->
            val date = LocalDate.parse(segment.date)
            val current = sittings.lastOrNull()
            if (
                current != null &&
                    current.date == date &&
                    segment.startedAt - current.endedAt <= mergeGapMs
            ) {
                sittings[sittings.lastIndex] =
                    current.copy(
                        endedAt = maxOf(current.endedAt, segment.endedAt),
                        elapsedMs = current.elapsedMs + segment.elapsedMs,
                        contentMs = current.contentMs + segment.contentMs,
                        speed = segment.speed,
                        lastChapterId = segment.chapterId,
                        toMs = segment.toMs,
                    )
            } else {
                sittings +=
                    ListenSession(
                        sessionId = segment.sessionId,
                        startedAt = segment.startedAt,
                        endedAt = segment.endedAt,
                        elapsedMs = segment.elapsedMs,
                        contentMs = segment.contentMs,
                        speed = segment.speed,
                        firstChapterId = segment.chapterId,
                        lastChapterId = segment.chapterId,
                        fromMs = segment.fromMs,
                        toMs = segment.toMs,
                        date = date,
                    )
            }
        }
    return sittings
        .filter { it.elapsedMs >= minimumMs }
        .groupBy { it.date }
        .map { (date, daySittings) ->
            ListenDay(date, daySittings.sortedByDescending { it.startedAt })
        }
        .sortedByDescending { it.date }
}

/** Human duration that never rounds a real sitting down to "0m": 42s, 4m 12s, 38m, 1h 5m. */
fun listenSpanLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val minutes = seconds / 60
    return when {
        seconds < 60 -> "${seconds}s"
        minutes < 10 -> if (seconds % 60 == 0L) "${minutes}m" else "${minutes}m ${seconds % 60}s"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
