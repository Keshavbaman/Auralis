package app.auralis.domain

import app.auralis.data.*
import java.time.*

data class BadgeProgress(val current: Long, val target: Long, val unit: String) {
    val fraction
        get() = (current.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)

    val label
        get() =
            if (unit == "ms") "${listeningDuration(current)} / ${listeningDuration(target)}"
            else "$current of $target $unit"
}

fun badgeProgress(
    key: String,
    rows: List<ListeningSegmentEntity>,
    completed: Int,
    books: List<LibraryBook>,
    threshold: Int,
    goal: Int,
): BadgeProgress {
    val stats = statistics(rows, threshold)
    val hours = rows.map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.of(it.zoneId)).hour }
    val genres =
        books
            .filter { b -> rows.any { it.bookId == b.book.id } }
            .map { it.book.genre }
            .filter { it.isNotBlank() }
            .distinct()
    return when (key) {
        "early" -> BadgeProgress(if (hours.any { it < 7 }) 1 else 0, 1, "early sessions")
        "night" -> BadgeProgress(if (hours.any { it == 0 }) 1 else 0, 1, "midnight sessions")
        "fire" -> BadgeProgress(stats.streak.longest.toLong(), 7, "days")
        "dedicated" -> BadgeProgress(stats.streak.longest.toLong(), 30, "days")
        "centurion" -> BadgeProgress(stats.streak.longest.toLong(), 100, "days")
        "first" -> BadgeProgress(completed.toLong(), 1, "books")
        "bookworm" -> BadgeProgress(completed.toLong(), 10, "books")
        "librarian" -> BadgeProgress(completed.toLong(), 50, "books")
        "marathon" -> BadgeProgress(stats.daily.values.maxOrNull() ?: 0, 5 * 3600000L, "ms")
        "speed" ->
            BadgeProgress(
                rows
                    .filter { it.speed >= 2 }
                    .groupBy { it.sessionId }
                    .values
                    .maxOfOrNull { r -> r.sumOf { it.elapsedMs } } ?: 0,
                60000,
                "ms",
            )
        "explorer" -> BadgeProgress(genres.size.toLong(), 5, "genres")
        "weekend" ->
            BadgeProgress(
                stats.daily.entries
                    .filter {
                        it.key.dayOfWeek == DayOfWeek.SATURDAY ||
                            it.key.dayOfWeek == DayOfWeek.SUNDAY
                    }
                    .maxOfOrNull { (date, value) ->
                        val saturday =
                            if (date.dayOfWeek == DayOfWeek.SUNDAY) date.minusDays(1) else date
                        listOf(saturday, saturday.plusDays(1))
                            .count { (stats.daily[it] ?: 0) >= goal * 60000L }
                            .toLong()
                    } ?: 0,
                2,
                "weekend days",
            )
        else -> BadgeProgress(0, 1, "milestones")
    }
}
