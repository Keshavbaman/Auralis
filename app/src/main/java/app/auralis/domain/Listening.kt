package app.auralis.domain

import app.auralis.data.ListeningSegmentEntity
import java.time.*
import java.util.UUID
import kotlin.math.roundToLong

data class ContentRange(val start: Long, val end: Long)

fun mergedDuration(ranges: List<ContentRange>): Long {
    var end = Long.MIN_VALUE
    var total = 0L
    for (r in ranges.filter { it.end > it.start }.sortedBy { it.start }) {
        total += (r.end - maxOf(r.start, end)).coerceAtLeast(0)
        end = maxOf(end, r.end)
    }
    return total
}

/**
 * Monotonic clock measures real time; position deltas validate actual progress. Seek callbacks
 * reset anchors.
 */
class ListeningTracker {
    private data class Anchor(
        val monotonic: Long,
        val wall: Long,
        val position: Long,
        val book: String,
        val chapter: String,
        val speed: Float,
        val zone: ZoneId,
    )

    private var anchor: Anchor? = null
    var sessionId: String = UUID.randomUUID().toString()
        private set

    fun reset() {
        anchor = null
    }

    fun newSession() {
        reset()
        sessionId = UUID.randomUUID().toString()
    }

    fun sample(
        monotonic: Long,
        wall: Long,
        position: Long,
        book: String,
        chapter: String,
        speed: Float,
        playing: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ListeningSegmentEntity> {
        val previous = anchor
        anchor =
            if (playing) Anchor(monotonic, wall, position, book, chapter, speed, zone) else null
        if (
            previous == null ||
                previous.book != book ||
                previous.chapter != chapter ||
                previous.zone != zone
        )
            return emptyList()
        val elapsed = monotonic - previous.monotonic
        val content = position - previous.position
        if (
            elapsed !in 1..15000 ||
                content <= 0 ||
                content > elapsed * previous.speed + 1000 ||
                kotlin.math.abs(wall - previous.wall - elapsed) > 2000
        )
            return emptyList()
        // Cap time to actual content progression: stalls cannot accumulate listening time.
        val real = minOf(elapsed, (content / previous.speed).roundToLong())
        if (real <= 0) return emptyList()
        val end = wall
        var start = end - real
        var consumed = 0L
        val result = mutableListOf<ListeningSegmentEntity>()
        while (start < end) {
            val date = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val boundary = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val stop = minOf(end, boundary)
            val part = stop - start
            val amount =
                if (stop == end) content - consumed
                else (content.toDouble() * part / real).roundToLong()
            result +=
                ListeningSegmentEntity(
                    UUID.randomUUID().toString(),
                    sessionId,
                    book,
                    chapter,
                    start,
                    stop,
                    date.toString(),
                    zone.id,
                    part,
                    amount,
                    previous.position + consumed,
                    previous.position + consumed + amount,
                    previous.speed,
                )
            consumed += amount
            start = stop
        }
        return result
    }
}

data class Streak(val current: Int, val longest: Int, val days: Int)

fun streak(daily: Map<LocalDate, Long>, thresholdMs: Long, today: LocalDate): Streak {
    val dates = daily.filter { it.value >= thresholdMs }.keys.filter { it <= today }.sorted()
    var longest = 0
    var run = 0
    var previous: LocalDate? = null
    for (date in dates) {
        run = if (previous?.plusDays(1) == date) run + 1 else 1
        longest = maxOf(longest, run)
        previous = date
    }
    var current = 0
    var day = if (today in dates) today else today.minusDays(1)
    val set = dates.toSet()
    while (day in set) {
        current++
        day = day.minusDays(1)
    }
    return Streak(current, longest, dates.size)
}

data class ListeningStats(
    val elapsed: Long,
    val content: Long,
    val saved: Long,
    val sessions: Int,
    val longestSession: Long,
    val daily: Map<LocalDate, Long>,
    val streak: Streak,
    val averageSpeed: Float,
)

fun statistics(
    rows: List<ListeningSegmentEntity>,
    thresholdMinutes: Int,
    today: LocalDate = LocalDate.now(),
): ListeningStats {
    val unique = rows.distinctBy { it.id }
    val daily =
        unique.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.elapsedMs } }
    val elapsed = unique.sumOf { it.elapsedMs }
    val content = unique.sumOf { it.contentMs }
    val sessions = unique.groupBy { it.sessionId }.values.map { it.sumOf { row -> row.elapsedMs } }
    return ListeningStats(
        elapsed,
        content,
        (content - elapsed).coerceAtLeast(0),
        sessions.size,
        sessions.maxOrNull() ?: 0,
        daily,
        streak(daily, thresholdMinutes * 60000L, today),
        if (elapsed > 0) content.toFloat() / elapsed else 1f,
    )
}

val levels =
    listOf(
        "Novice" to 0L,
        "Explorer" to 10L,
        "Bookworm" to 50L,
        "Scholar" to 100L,
        "Master" to 500L,
        "Legend" to 1000L,
        "Grandmaster" to 5000L,
    )

data class Badge(val key: String, val title: String, val description: String)

val badges =
    listOf(
        Badge("early", "Early Bird", "Listen before 7 AM"),
        Badge("night", "Night Owl", "Listen between midnight and 1 AM"),
        Badge("fire", "On Fire", "Reach a 7-day streak"),
        Badge("dedicated", "Dedicated Listener", "Reach a 30-day streak"),
        Badge("centurion", "Centurion", "Reach a 100-day streak"),
        Badge("first", "First Story", "Complete a book"),
        Badge("bookworm", "Bookworm", "Complete 10 books"),
        Badge("librarian", "The Librarian", "Complete 50 books"),
        Badge("marathon", "Marathon", "Listen 5 hours in a day"),
        Badge("speed", "Speed Listener", "Listen at 2× for a minute"),
        Badge("explorer", "Explorer", "Listen across 5 genres"),
        Badge("weekend", "Weekend Warrior", "Meet your goal Saturday and Sunday"),
    )

fun eligibleBadges(
    rows: List<ListeningSegmentEntity>,
    completed: Int,
    threshold: Int,
    goal: Int,
    genres: Set<String>,
): Set<String> {
    val s = statistics(rows, threshold)
    val result = mutableSetOf<String>()
    fun award(key: String, condition: Boolean) {
        if (condition) result += key
    }
    val hours = rows.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.of(it.zoneId)).hour }
    award("early", hours.any { it < 7 })
    award("night", hours.any { it == 0 })
    award("fire", s.streak.longest >= 7)
    award("dedicated", s.streak.longest >= 30)
    award("centurion", s.streak.longest >= 100)
    award("first", completed >= 1)
    award("bookworm", completed >= 10)
    award("librarian", completed >= 50)
    award("marathon", s.daily.values.any { it >= 5 * 3600000L })
    award(
        "speed",
        rows
            .filter { it.speed >= 2 }
            .groupBy { it.sessionId }
            .values
            .any { it.sumOf { r -> r.elapsedMs } >= 60000 },
    )
    award("explorer", genres.filter { it.isNotBlank() }.size >= 5)
    award(
        "weekend",
        s.daily.any { (d, t) ->
            d.dayOfWeek == DayOfWeek.SATURDAY &&
                t >= goal * 60000L &&
                (s.daily[d.plusDays(1)] ?: 0) >= goal * 60000L
        },
    )
    return result
}

/** Numeric runs compare as numbers, so chapter 2 precedes chapter 10. */
val naturalOrder =
    Comparator<String> { a, b ->
        val pattern = Regex("[0-9]+|[^0-9]+")
        val aa = pattern.findAll(a.lowercase()).map { it.value }.toList()
        val bb = pattern.findAll(b.lowercase()).map { it.value }.toList()
        var result = 0
        for (i in 0 until minOf(aa.size, bb.size)) {
            result =
                if (aa[i].first().isDigit() && bb[i].first().isDigit())
                    aa[i].trimStart('0').length.compareTo(bb[i].trimStart('0').length).takeIf {
                        it != 0
                    } ?: aa[i].trimStart('0').compareTo(bb[i].trimStart('0'))
                else aa[i].compareTo(bb[i])
            if (result != 0) break
        }
        if (result != 0) result else aa.size.compareTo(bb.size)
    }
