package app.auralis.domain

import app.auralis.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ListeningDashboardTest {
    private fun segment(
        id: String,
        date: LocalDate,
        startHour: Int = 12,
        minutes: Int = 30,
        speed: Float = 2f,
    ): ListeningSegmentEntity {
        val start = date.atTime(startHour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val elapsed = minutes * 60000L
        return ListeningSegmentEntity(
            id,
            "session-$id",
            "book",
            "chapter",
            start,
            start + elapsed,
            date.toString(),
            "UTC",
            elapsed,
            (elapsed * speed).toLong(),
            0,
            (elapsed * speed).toLong(),
            speed,
        )
    }

    @Test
    fun duplicateCheckpointsAndSpeedNeverInflateRealListening() {
        val today = LocalDate.of(2026, 11, 16)
        val row = segment("a", today)
        val d = listeningDashboard(listOf(row, row), emptyList(), 10, today)
        assertEquals(1800000L, d.stats.elapsed)
        assertEquals(3600000L, d.stats.content)
        assertEquals(1800000L, d.panels.getValue(ListeningPeriod.TOTAL).valueMs)
        assertEquals(
            1800000L,
            d.panels.getValue(ListeningPeriod.TODAY).buckets.sumOf { it.elapsedMs },
        )
        assertEquals(1800000L, d.currentMonthMs)
    }

    @Test
    fun hourlyBarsSplitTimeAtHourBoundary() {
        val today = LocalDate.of(2026, 11, 16)
        val base = segment("a", today, 12, 60)
        val shifted =
            base.copy(startedAt = base.startedAt + 1800000, endedAt = base.endedAt + 1800000)
        val b =
            listeningDashboard(listOf(shifted), emptyList(), 10, today)
                .panels
                .getValue(ListeningPeriod.TODAY)
                .buckets
        assertEquals(1800000L, b[12].elapsedMs)
        assertEquals(1800000L, b[13].elapsedMs)
    }

    @Test
    fun weeklyStreakCrossesYearAndKeepsLastWeekUntilThisWeekIsOver() {
        val monday = LocalDate.of(2026, 1, 5)
        val rows = listOf(segment("a", monday.minusDays(1)), segment("b", monday.minusDays(8)))
        val d = listeningDashboard(rows, emptyList(), 10, monday)
        assertEquals(2, d.weeklyStreak)
        assertEquals(2, d.activeWeeks)
        assertEquals(0, listeningDashboard(rows, emptyList(), 10, monday.plusWeeks(1)).weeklyStreak)
    }

    @Test
    fun missingHistoryHasNoInventedGrowthOrStreaks() {
        val d = listeningDashboard(emptyList(), emptyList(), 10, LocalDate.of(2026, 11, 16))
        assertEquals(0L, d.stats.elapsed)
        assertEquals(0, d.activeWeeks)
        assertEquals(0, d.weeklyStreak)
        assertNull(d.firstListening)
        assertTrue(d.libraryGrowth.all { it.second == 0 })
        assertTrue(d.panels.values.all { p -> p.buckets.all { it.elapsedMs == 0L } })
    }

    @Test
    fun monthlyAverageIncludesQuietMonthsAndComparisonUsesRealMonthTotals() {
        val today = LocalDate.of(2026, 11, 16)
        val rows =
            listOf(
                segment("now", today, minutes = 70),
                segment("then", today.minusMonths(1), minutes = 14),
            )
        val d = listeningDashboard(rows, emptyList(), 10, today)
        assertEquals(12 * 60000L, d.panels.getValue(ListeningPeriod.MONTHLY).valueMs)
        assertEquals(56 * 60000L, d.monthlyChange)
        assertEquals(7, d.panels.getValue(ListeningPeriod.MONTHLY).buckets.size)
    }
}
