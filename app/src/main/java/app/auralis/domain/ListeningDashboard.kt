package app.auralis.domain

import app.auralis.data.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.*

enum class ListeningPeriod(val label: String) {
    TODAY("Today"),
    DAILY("Daily"),
    MONTHLY("Monthly"),
    TOTAL("Total"),
}

data class ListeningBucket(val label: String, val elapsedMs: Long)

data class ListeningPanel(
    val title: String,
    val valueMs: Long,
    val range: String,
    val buckets: List<ListeningBucket>,
    val averageMs: Long,
)

data class ListeningDashboard(
    val stats: ListeningStats,
    val panels: Map<ListeningPeriod, ListeningPanel>,
    val weeklyStreak: Int,
    val activeWeeks: Int,
    val monthlyChange: Long,
    val libraryGrowth: List<Pair<Int, Int>>,
    val firstListening: LocalDate?,
    val currentMonthMs: Long,
    val previousMonthMs: Long,
)

/** Pure aggregates from checkpointed listening, never from the advancing player clock. */
fun listeningDashboard(
    rows: List<ListeningSegmentEntity>,
    books: List<LibraryBook>,
    threshold: Int,
    today: LocalDate,
): ListeningDashboard {
    val unique = rows.distinctBy { it.id }
    val stats = statistics(unique, threshold, today)
    val daily = stats.daily
    val hours = LongArray(24)
    for (r in
        unique.filter {
            it.date == today.toString() && it.elapsedMs > 0 && it.endedAt > it.startedAt
        }) {
        val zone = runCatching { ZoneId.of(r.zoneId) }.getOrDefault(ZoneId.systemDefault())
        var cursor = Instant.ofEpochMilli(r.startedAt)
        val end = Instant.ofEpochMilli(r.endedAt)
        var allocated = 0L
        while (cursor < end) {
            val local = cursor.atZone(zone)
            val boundary = local.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant()
            val next = minOf(boundary, end)
            val amount =
                if (next == end) r.elapsedMs - allocated
                else
                    (r.elapsedMs.toDouble() * Duration.between(cursor, next).toMillis() /
                            (r.endedAt - r.startedAt))
                        .toLong()
            hours[local.hour] += amount
            allocated += amount
            cursor = next
        }
    }
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val months = (6 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
    val monthly =
        daily.entries
            .groupBy { YearMonth.from(it.key) }
            .mapValues { (_, v) -> v.sumOf { it.value } }
    val first = daily.keys.minOrNull()
    val firstYear =
        minOf(
            first?.year ?: today.year,
            books.minOfOrNull {
                Instant.ofEpochMilli(it.book.addedAt).atZone(ZoneId.systemDefault()).year
            } ?: today.year,
        )
    val years = (firstYear..today.year).toList()
    val dayBuckets = days.map {
        ListeningBucket(it.format(DateTimeFormatter.ofPattern("EEE")), daily[it] ?: 0)
    }
    val monthBuckets = months.map {
        ListeningBucket(it.format(DateTimeFormatter.ofPattern("MMM")), monthly[it] ?: 0)
    }
    val totalBuckets = years.map { y ->
        ListeningBucket(y.toString(), daily.filterKeys { it.year == y }.values.sum())
    }
    fun panel(title: String, value: Long, range: String, b: List<ListeningBucket>) =
        ListeningPanel(
            title,
            value,
            range,
            b,
            if (b.isEmpty()) 0 else b.sumOf { it.elapsedMs } / b.size,
        )
    val panels =
        mapOf(
            ListeningPeriod.TODAY to
                panel(
                    "TODAY’S LISTENING",
                    daily[today] ?: 0,
                    today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                    (0..23).map { ListeningBucket(it.toString(), hours[it]) },
                ),
            ListeningPeriod.DAILY to
                panel(
                    "DAILY AVERAGE",
                    dayBuckets.sumOf { it.elapsedMs } / 7,
                    "${days.first().format(DateTimeFormatter.ofPattern("d MMM"))} – ${today.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                    dayBuckets,
                ),
            ListeningPeriod.MONTHLY to
                panel(
                    "MONTHLY AVERAGE",
                    monthBuckets.sumOf { it.elapsedMs } / 7,
                    "${months.first().format(DateTimeFormatter.ofPattern("MMM yyyy"))} – ${months.last().format(DateTimeFormatter.ofPattern("MMM yyyy"))}",
                    monthBuckets,
                ),
            ListeningPeriod.TOTAL to
                panel(
                    "TOTAL LISTENING",
                    stats.elapsed,
                    first?.let { "Since ${it.format(DateTimeFormatter.ofPattern("MMM yyyy"))}" }
                        ?: "Your first chapter awaits",
                    totalBuckets,
                ),
        )
    fun week(d: LocalDate) = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weeks = daily.filterValues { it > 0 }.keys.map(::week).toSet()
    var current = week(today)
    if (current !in weeks) current = current.minusWeeks(1)
    var streak = 0
    while (current in weeks) {
        streak++
        current = current.minusWeeks(1)
    }
    val growth = years.map { year ->
        year to
            books.count {
                Instant.ofEpochMilli(it.book.addedAt).atZone(ZoneId.systemDefault()).year <= year
            }
    }
    return ListeningDashboard(
        stats,
        panels,
        streak,
        weeks.size,
        (monthly[YearMonth.from(today)] ?: 0) -
            (monthly[YearMonth.from(today).minusMonths(1)] ?: 0),
        growth,
        first,
        monthly[YearMonth.from(today)] ?: 0,
        monthly[YearMonth.from(today).minusMonths(1)] ?: 0,
    )
}
