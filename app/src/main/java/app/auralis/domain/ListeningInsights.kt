package app.auralis.domain

import app.auralis.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Read-only projections. These never alter listening history or infer listening from player
 * position.
 */
data class ListeningDay(val elapsedMs: Long, val sessions: Int, val books: Map<String, Long>)

data class ListeningRank(
    val current: String,
    val next: String?,
    val fraction: Float,
    val remainingMs: Long,
)

data class ListeningInsights(
    val stats: ListeningStats,
    val days: Map<LocalDate, ListeningDay>,
    val rank: ListeningRank,
    val thisWeekMs: Long,
    val thisMonthMs: Long,
    val previousMonthMs: Long,
    val dailyAverageMs: Long,
    val authors: List<Pair<String, Long>>,
    val genres: List<Pair<String, Long>>,
    val narrators: List<Pair<String, Long>>,
    val favouriteHour: Int?,
    val favouriteDay: DayOfWeek?,
    val booksStarted: Int,
    val thisYearMs: Long,
)

fun listeningRank(elapsedMs: Long): ListeningRank {
    val index = levels.indexOfLast { elapsedMs >= it.second * 3_600_000L }.coerceAtLeast(0)
    val current = levels[index]
    val next = levels.getOrNull(index + 1)
    val base = current.second * 3_600_000L
    val end = next?.second?.times(3_600_000L)
    return ListeningRank(
        current.first,
        next?.first,
        if (end == null) 1f
        else ((elapsedMs - base).toDouble() / (end - base)).toFloat().coerceIn(0f, 1f),
        if (end == null) 0 else (end - elapsedMs).coerceAtLeast(0),
    )
}

fun listeningInsights(
    rows: List<ListeningSegmentEntity>,
    books: List<LibraryBook>,
    threshold: Int,
    today: LocalDate,
): ListeningInsights {
    val unique = rows.distinctBy { it.id }
    val stats = statistics(unique, threshold, today)
    val byId = books.associateBy { it.book.id }
    val days =
        unique
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, segments) ->
                ListeningDay(
                    segments.sumOf { it.elapsedMs },
                    segments.map { it.sessionId }.distinct().size,
                    segments.groupBy { it.bookId }.mapValues { (_, r) -> r.sumOf { it.elapsedMs } },
                )
            }
    fun leaders(field: (BookEntity) -> String, split: Boolean = false): List<Pair<String, Long>> {
        val result = mutableMapOf<String, Long>()
        unique.forEach { row ->
            val value = byId[row.bookId]?.book?.let(field).orEmpty()
            (if (split) value.split(';') else listOf(value))
                .map { it.trim() }
                .distinct()
                .filter { it.isNotEmpty() && !it.startsWith("Unknown", true) }
                .forEach { result[it] = (result[it] ?: 0) + row.elapsedMs }
        }
        return result
            .toList()
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first })
    }
    // Divide real elapsed time across local hour boundaries, including DST transitions.
    val hourly = LongArray(24)
    unique
        .filter { it.elapsedMs > 0 && it.endedAt > it.startedAt }
        .forEach { r ->
            val zone = runCatching { ZoneId.of(r.zoneId) }.getOrDefault(ZoneId.systemDefault())
            var cursor = Instant.ofEpochMilli(r.startedAt)
            val end = Instant.ofEpochMilli(r.endedAt)
            var allocated = 0L
            while (cursor < end) {
                val local = cursor.atZone(zone)
                val boundary =
                    local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
                val next = minOf(end, boundary)
                val amount =
                    if (next == end) r.elapsedMs - allocated
                    else
                        (r.elapsedMs.toDouble() * Duration.between(cursor, next).toMillis() /
                                (r.endedAt - r.startedAt))
                            .toLong()
                hourly[local.hour] += amount
                allocated += amount
                cursor = next
            }
        }
    val first = stats.daily.keys.minOrNull()
    val month = YearMonth.from(today)
    fun monthTotal(m: YearMonth) = stats.daily.filterKeys { YearMonth.from(it) == m }.values.sum()
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    return ListeningInsights(
        stats,
        days,
        listeningRank(stats.elapsed),
        stats.daily.filterKeys { it in monday..today }.values.sum(),
        monthTotal(month),
        monthTotal(month.minusMonths(1)),
        if (first == null) 0
        else stats.elapsed / (ChronoUnit.DAYS.between(first, today) + 1).coerceAtLeast(1),
        leaders({ it.author }),
        leaders({ it.genre }, true),
        leaders({ it.narrator }),
        hourly.indices.maxByOrNull { hourly[it] }?.takeIf { hourly[it] > 0 },
        stats.daily.entries
            .groupBy { it.key.dayOfWeek }
            .maxByOrNull { (_, r) -> r.sumOf { it.value } }
            ?.key,
        unique.map { it.bookId }.distinct().size,
        stats.daily.filterKeys { it.year == today.year && it <= today }.values.sum(),
    )
}

enum class InsightPeriod(val label: String) {
    WEEK("7 days"),
    MONTH("30 days"),
    YEAR("12 months"),
    ANNUAL("This year"),
}

fun insightBuckets(
    daily: Map<LocalDate, Long>,
    period: InsightPeriod,
    today: LocalDate,
): List<Pair<String, Long>> =
    when (period) {
        InsightPeriod.WEEK,
        InsightPeriod.MONTH -> {
            val count = if (period == InsightPeriod.WEEK) 7 else 30
            (count - 1 downTo 0).map {
                val d = today.minusDays(it.toLong())
                d.format(DateTimeFormatter.ofPattern("d MMM")) to (daily[d] ?: 0L)
            }
        }
        InsightPeriod.YEAR,
        InsightPeriod.ANNUAL -> {
            val current = YearMonth.from(today)
            val months =
                if (period == InsightPeriod.YEAR)
                    (11 downTo 0).map { current.minusMonths(it.toLong()) }
                else (1..today.monthValue).map { YearMonth.of(today.year, it) }
            months.map { m ->
                m.format(DateTimeFormatter.ofPattern("MMM yy")) to
                    daily.filterKeys { YearMonth.from(it) == m && it <= today }.values.sum()
            }
        }
    }

/** Monday-first month, padded to complete weeks. Null cells are outside the requested month. */
fun calendarMonthCells(month: YearMonth): List<LocalDate?> {
    val leading = month.atDay(1).dayOfWeek.value - 1
    val count = ((leading + month.lengthOfMonth() + 6) / 7) * 7
    return (0 until count).map { i ->
        (i - leading + 1).takeIf { it in 1..month.lengthOfMonth() }?.let(month::atDay)
    }
}
