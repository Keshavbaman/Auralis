package app.auralis.domain

import java.time.LocalDate

/** One day on the goal page's week strip. */
data class DayMark(val date: LocalDate, val listenedMs: Long, val goalMet: Boolean, val streakDay: Boolean)

/** What the goal page celebrates or encourages; always positive, never a scolding. */
enum class GoalMood {
    /** Today's goal is done. */
    Reached,
    /** At least half way there today. */
    Close,
    /** A streak is alive and today still needs its streak minutes. */
    KeepStreak,
    /** Nothing yet today and no streak to protect: a gentle invitation. */
    Begin,
}

data class GoalJourney(
    val todayMs: Long,
    val goalMs: Long,
    val streakMs: Long,
    val streak: Int,
    val bestStreak: Int,
    val newBest: Boolean,
    /** Share of the last 30 days (or of the days since you started, if fewer) that met the goal. */
    val successRate: Float,
    val goalDays: Int,
    val countedDays: Int,
    val monthMs: Long,
    val week: List<DayMark>,
    val mood: GoalMood,
) {
    val fraction: Float
        get() = if (goalMs > 0) (todayMs.toFloat() / goalMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (goalMs - todayMs).coerceAtLeast(0)
}

fun goalJourney(
    daily: Map<LocalDate, Long>,
    goalMinutes: Int,
    streakMinutes: Int,
    today: LocalDate,
): GoalJourney {
    val goalMs = goalMinutes * 60_000L
    val streakMs = streakMinutes * 60_000L
    val runs = streak(daily, streakMs, today)
    val todayMs = daily[today] ?: 0L
    val first = daily.filterValues { it > 0 }.keys.minOrNull()
    val windowStart = listOfNotNull(first, today.minusDays(29)).maxOrNull() ?: today
    val counted = generateSequence(windowStart) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.toList()
    val goalDays = counted.count { (daily[it] ?: 0L) >= goalMs }
    val week =
        (6 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val ms = daily[day] ?: 0L
            DayMark(day, ms, ms >= goalMs, ms >= streakMs)
        }
    val mood =
        when {
            todayMs >= goalMs -> GoalMood.Reached
            todayMs * 2 >= goalMs -> GoalMood.Close
            runs.current > 0 -> GoalMood.KeepStreak
            else -> GoalMood.Begin
        }
    return GoalJourney(
        todayMs = todayMs,
        goalMs = goalMs,
        streakMs = streakMs,
        streak = runs.current,
        bestStreak = runs.longest,
        newBest = runs.current >= 2 && runs.current >= runs.longest,
        successRate = if (counted.isEmpty()) 0f else goalDays.toFloat() / counted.size,
        goalDays = goalDays,
        countedDays = counted.size,
        monthMs = daily.filterKeys { it.year == today.year && it.month == today.month }.values.sum(),
        week = week,
        mood = mood,
    )
}
