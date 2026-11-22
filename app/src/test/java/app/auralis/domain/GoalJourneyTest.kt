package app.auralis.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalJourneyTest {
    private val today = LocalDate.of(2026, 11, 22)
    private fun minutes(m: Int) = m * 60_000L

    @Test
    fun celebratesAReachedGoalAndCountsTheStreak() {
        val daily =
            mapOf(
                today to minutes(42),
                today.minusDays(1) to minutes(31),
                today.minusDays(2) to minutes(12),
                today.minusDays(3) to minutes(35),
            )
        val journey = goalJourney(daily, goalMinutes = 30, streakMinutes = 10, today = today)
        assertEquals(GoalMood.Reached, journey.mood)
        assertEquals(4, journey.streak)
        assertTrue(journey.newBest)
        assertEquals(1f, journey.fraction)
        // Started 4 days ago, 3 of those 4 days met the 30-minute goal.
        assertEquals(.75f, journey.successRate, .001f)
        assertEquals(7, journey.week.size)
        assertTrue(journey.week.last().goalMet)
        assertFalse(journey.week[4].goalMet) // the 12-minute day: a streak day, not a goal day
        assertTrue(journey.week[4].streakDay)
    }

    @Test
    fun encouragesRatherThanScolds() {
        assertEquals(GoalMood.Close, goalJourney(mapOf(today to minutes(16)), 30, 10, today).mood)
        val streakAlive = mapOf(today.minusDays(1) to minutes(20))
        assertEquals(GoalMood.KeepStreak, goalJourney(streakAlive, 30, 10, today).mood)
        assertEquals(GoalMood.Begin, goalJourney(emptyMap(), 30, 10, today).mood)
    }

    @Test
    fun reportsWhatIsLeftForToday() {
        val journey = goalJourney(mapOf(today to minutes(10)), 30, 10, today)
        assertEquals(minutes(20), journey.remainingMs)
        assertEquals(1f / 3, journey.fraction, .001f)
    }
}
