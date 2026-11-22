package app.auralis.domain

import app.auralis.data.*
import java.time.*

data class ListeningReport(
    val label: String,
    val stats: ListeningStats,
    val completed: Int,
    val mostBook: BookEntity?,
    val author: String?,
    val genre: String?,
    val personality: String,
) {
    fun page(index: Int, privateDetails: Boolean = true): Pair<String, String> =
        when (index) {
            0 -> listeningDuration(stats.elapsed) to "spent in other worlds"
            1 -> "$completed" to "books completed · Every ending is a new beginning"
            2 ->
                (if (privateDetails) mostBook?.title ?: "Your next story awaits"
                else "Your favourite world") to "your most listened-to book"
            3 ->
                (if (privateDetails) author else "Your storytellers").orEmpty().ifBlank {
                    "Still discovering"
                } to
                    (if (privateDetails) "Favourite genre · ${genre ?: "Not enough metadata yet"}"
                    else "Authors and genres kept private")
            4 -> "${stats.streak.longest} days" to "your longest listening streak"
            5 ->
                "%.2f×".format(stats.averageSpeed) to
                    "average pace · ${listeningDuration(stats.saved)} saved"
            6 -> personality to "Inspired by your real listening habits"
            else ->
                listeningDuration(stats.elapsed) to
                    "$completed books finished · ${stats.streak.longest} day streak\n${listeningDuration(stats.saved)} saved · ${"%.2f×".format(stats.averageSpeed)} pace"
        }
}

fun listeningReport(
    rows: List<ListeningSegmentEntity>,
    books: List<LibraryBook>,
    completed: List<CompletionEntity>,
    annual: Boolean,
    end: LocalDate,
    threshold: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): ListeningReport {
    fun inPeriod(date: LocalDate) =
        if (annual) date.year == end.year else YearMonth.from(date) == YearMonth.from(end)
    val period = rows.distinctBy { it.id }.filter { inPeriod(LocalDate.parse(it.date)) }
    val byId = books.associateBy { it.book.id }
    val most =
        period
            .groupBy { it.bookId }
            .maxByOrNull { it.value.sumOf { r -> r.elapsedMs } }
            ?.key
            ?.let { byId[it]?.book }
    fun favourite(value: (BookEntity) -> String): String? =
        period
            .groupBy { byId[it.bookId]?.book?.let(value) }
            .filterKeys { !it.isNullOrBlank() && it != "Unknown author" }
            .maxByOrNull { it.value.sumOf { r -> r.elapsedMs } }
            ?.key
    val stats = statistics(period, threshold, end)
    val personality =
        when {
            period.isEmpty() -> "Your story is beginning"
            stats.streak.longest >= 7 -> "The Steady Explorer"
            stats.averageSpeed >= 1.75f -> "The Swift Storyteller"
            period.map { it.bookId }.distinct().size >= 5 -> "The World Wanderer"
            else -> "The Curious Listener"
        }
    return ListeningReport(
        if (annual) "${end.year}" else YearMonth.from(end).toString(),
        stats,
        completed
            .distinctBy { it.bookId }
            .count { inPeriod(Instant.ofEpochMilli(it.completedAt).atZone(zone).toLocalDate()) },
        most,
        favourite { it.author },
        favourite { it.genre },
        personality,
    )
}
