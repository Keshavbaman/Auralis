package app.auralis.domain

import app.auralis.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ListeningInsightsTest {
    private fun row(
        id: String,
        day: LocalDate,
        minutes: Int,
        speed: Float = 1f,
    ): ListeningSegmentEntity {
        val start = day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val elapsed = minutes * 60_000L
        return ListeningSegmentEntity(
            id,
            "session-$id",
            "book",
            "chapter",
            start,
            start + elapsed,
            day.toString(),
            "UTC",
            elapsed,
            (elapsed * speed).toLong(),
            0,
            (elapsed * speed).toLong(),
            speed,
        )
    }

    @Test
    fun monthlyTotalsUseElapsedTimeAndDeduplicateRecords() {
        val today = LocalDate.of(2026, 11, 16)
        val a = row("a", today, 30, 2f)
        val b = row("b", today.minusDays(1), 15)
        val c = row("c", today.minusMonths(1), 20)
        val data = listeningInsights(listOf(a, a, b, c), emptyList(), 10, today)
        assertEquals(45 * 60_000L, data.thisMonthMs)
        assertEquals(20 * 60_000L, data.previousMonthMs)
        assertEquals(65 * 60_000L, data.stats.elapsed)
        assertEquals(95 * 60_000L, data.stats.content)
        assertEquals(30 * 60_000L, data.stats.saved)
        assertEquals(1, data.days.getValue(today).sessions)
        assertEquals(30 * 60_000L, data.days.getValue(today).books.getValue("book"))
    }

    @Test
    fun monthBucketsRemainTotalsAcrossYearBoundary() {
        val today = LocalDate.of(2026, 1, 15)
        val daily =
            mapOf(
                today to 90 * 60_000L,
                today.minusDays(1) to 30 * 60_000L,
                LocalDate.of(2025, 12, 31) to 300 * 60_000L,
            )
        val buckets = insightBuckets(daily, InsightPeriod.YEAR, today)
        assertEquals(12, buckets.size)
        assertEquals(300 * 60_000L, buckets[buckets.lastIndex - 1].second)
        assertEquals(120 * 60_000L, buckets.last().second)
        assertEquals(420 * 60_000L, buckets.sumOf { it.second })
        assertEquals(1, insightBuckets(daily, InsightPeriod.ANNUAL, today).size)
    }

    @Test
    fun chartsExcludeFutureDatesAndIncludeZeroDays() {
        val today = LocalDate.of(2026, 11, 16)
        val buckets =
            insightBuckets(
                mapOf(today to 1_800_000L, today.plusDays(1) to 9_000_000L),
                InsightPeriod.WEEK,
                today,
            )
        assertEquals(7, buckets.size)
        assertEquals(1_800_000L, buckets.sumOf { it.second })
        assertEquals(6, buckets.count { it.second == 0L })
        assertEquals(
            1_800_000L,
            insightBuckets(
                    mapOf(today to 1_800_000L, today.plusDays(1) to 9_000_000L),
                    InsightPeriod.YEAR,
                    today,
                )
                .last()
                .second,
        )
    }

    @Test
    fun leapMonthIsMondayFirstWithNoInventedDates() {
        val cells = calendarMonthCells(YearMonth.of(2024, 2))
        assertEquals(3, cells.takeWhile { it == null }.size)
        assertEquals(29, cells.count { it != null })
        assertEquals(LocalDate.of(2024, 2, 29), cells.filterNotNull().last())
        assertEquals(0, cells.size % 7)
        assertEquals(28, calendarMonthCells(YearMonth.of(2021, 2)).size)
    }

    @Test
    fun ranksUseActualListeningAndProgressWithinTheCurrentLevel() {
        assertEquals("Novice", listeningRank(0).current)
        val explorer = listeningRank(30 * 3_600_000L)
        assertEquals("Explorer", explorer.current)
        assertEquals("Bookworm", explorer.next)
        assertEquals(.5f, explorer.fraction, .0001f)
        assertEquals(20 * 3_600_000L, explorer.remainingMs)
        assertNull(listeningRank(5000 * 3_600_000L).next)
        assertEquals(1f, listeningRank(6000 * 3_600_000L).fraction)
    }

    @Test
    fun dailyAverageIncludesQuietDaysAndSessionsSpanMidnight() {
        val today = LocalDate.of(2026, 11, 16)
        val a = row("a", today.minusDays(2), 15)
        val b = row("b", today, 15).copy(sessionId = a.sessionId)
        val data = listeningInsights(listOf(a, b), emptyList(), 10, today)
        assertEquals(10 * 60_000L, data.dailyAverageMs)
        assertEquals(1, data.stats.sessions)
        assertEquals(30 * 60_000L, data.stats.longestSession)
    }

    @Test
    fun favouriteHourAllocatesAcrossTheHourBoundary() {
        val date = LocalDate.of(2026, 11, 16)
        val source = row("a", date, 60)
        val shifted =
            source.copy(
                startedAt = source.startedAt + 45 * 60_000L,
                endedAt = source.endedAt + 45 * 60_000L,
            )
        assertEquals(13, listeningInsights(listOf(shifted), emptyList(), 10, date).favouriteHour)
    }

    @Test
    fun daylightSavingJumpDoesNotInventAnHour() {
        val zone = ZoneId.of("America/New_York")
        val start = ZonedDateTime.of(2026, 3, 8, 1, 30, 0, 0, zone).toInstant().toEpochMilli()
        val source =
            row("dst", LocalDate.of(2026, 3, 8), 60)
                .copy(startedAt = start, endedAt = start + 3_600_000L, zoneId = zone.id)
        val data = listeningInsights(listOf(source), emptyList(), 10, LocalDate.of(2026, 3, 8))
        assertEquals(3_600_000L, data.stats.elapsed)
        assertNotEquals(2, data.favouriteHour)
    }

    @Test
    fun missingMetadataAndEmptyHistoryProduceNoFabricatedInsights() {
        val data = listeningInsights(emptyList(), emptyList(), 10, LocalDate.of(2026, 11, 16))
        assertTrue(data.authors.isEmpty())
        assertTrue(data.genres.isEmpty())
        assertTrue(data.narrators.isEmpty())
        assertNull(data.favouriteHour)
        assertNull(data.favouriteDay)
        assertEquals(0L, data.dailyAverageMs)
    }
}
