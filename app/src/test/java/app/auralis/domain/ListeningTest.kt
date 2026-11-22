package app.auralis.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ListeningTest {
    private val utc = ZoneId.of("UTC")

    private fun sample(
        t: ListeningTracker,
        mono: Long,
        wall: Long,
        pos: Long,
        speed: Float = 1f,
        playing: Boolean = true,
        zone: ZoneId = utc,
    ) = t.sample(mono, wall, pos, "book", "chapter", speed, playing, zone)

    @Test
    fun realAndContentTimeAtDoubleSpeed() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0, 2f)
        val rows = sample(t, 1000, 11000, 2000, 2f)
        assertEquals(1000, rows.sumOf { it.elapsedMs })
        assertEquals(2000, rows.sumOf { it.contentMs })
        val s = statistics(rows, 10)
        assertEquals(1000, s.saved)
        assertEquals(2f, s.averageSpeed, .001f)
    }

    @Test
    fun pauseAndBufferingDoNotCount() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertEquals(1000, sample(t, 1000, 11000, 1000, playing = false).sumOf { it.elapsedMs })
        assertTrue(sample(t, 20000, 30000, 1000, playing = false).isEmpty())
        assertTrue(sample(t, 21000, 31000, 1000).isEmpty())
        assertEquals(1000, sample(t, 22000, 32000, 2000).sumOf { it.elapsedMs })
    }

    @Test
    fun stalledPlaybackDoesNotCount() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertTrue(sample(t, 1000, 11000, 0).isEmpty())
    }

    @Test
    fun forwardSeekIsRejected() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertTrue(sample(t, 1000, 11000, 100000).isEmpty())
    }

    @Test
    fun backwardSeekIsRejected() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 10000)
        assertTrue(sample(t, 1000, 11000, 1000).isEmpty())
    }

    @Test
    fun explicitSeekResetPreventsSmallJumpCounting() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        t.reset()
        assertTrue(sample(t, 1000, 11000, 1000).isEmpty())
    }

    @Test
    fun suspendedClockGapIsRejected() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertTrue(sample(t, 30000, 40000, 30000).isEmpty())
    }

    @Test
    fun clockAdjustmentIsRejected() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertTrue(sample(t, 1000, 60000, 1000).isEmpty())
    }

    @Test
    fun midnightSplitsExactly() {
        val start = Instant.parse("2026-01-01T23:59:59.500Z").toEpochMilli()
        val t = ListeningTracker()
        sample(t, 0, start, 0)
        val rows = sample(t, 1000, start + 1000, 1000)
        assertEquals(listOf("2026-01-01", "2026-01-02"), rows.map { it.date })
        assertEquals(listOf(500L, 500L), rows.map { it.elapsedMs })
        assertEquals(1000, rows.sumOf { it.contentMs })
    }

    @Test
    fun localMidnightUsesChosenZone() {
        val start = Instant.parse("2026-01-01T18:29:59.500Z").toEpochMilli()
        val zone = ZoneId.of("Asia/Kolkata")
        val t = ListeningTracker()
        sample(t, 0, start, 0, zone = zone)
        assertEquals(
            listOf("2026-01-01", "2026-01-02"),
            sample(t, 1000, start + 1000, 1000, zone = zone).map { it.date },
        )
    }

    @Test
    fun dstFallbackCountsElapsedOnce() {
        val start = Instant.parse("2026-11-01T05:59:59.500Z").toEpochMilli()
        val zone = ZoneId.of("America/New_York")
        val t = ListeningTracker()
        sample(t, 0, start, 0, zone = zone)
        val rows = sample(t, 1000, start + 1000, 1000, zone = zone)
        assertEquals(1000, rows.sumOf { it.elapsedMs })
        assertEquals("2026-11-01", rows.first().date)
    }

    @Test
    fun timezoneChangeDropsAmbiguousInterval() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        assertTrue(sample(t, 1000, 11000, 1000, zone = ZoneId.of("Asia/Kolkata")).isEmpty())
    }

    @Test
    fun overlapCannotInflateCompletion() {
        assertEquals(
            150,
            mergedDuration(
                listOf(
                    ContentRange(0, 100),
                    ContentRange(0, 100),
                    ContentRange(50, 150),
                    ContentRange(70, 80),
                )
            ),
        )
    }

    @Test
    fun disjointAndInvalidRanges() {
        assertEquals(
            20,
            mergedDuration(listOf(ContentRange(0, 10), ContentRange(20, 30), ContentRange(8, 4))),
        )
    }

    @Test
    fun yesterdayKeepsCurrentStreakAlive() {
        val d = LocalDate.of(2026, 1, 10)
        val s = streak(mapOf(d.minusDays(1) to 600000, d.minusDays(2) to 600000), 600000, d)
        assertEquals(Streak(2, 2, 2), s)
    }

    @Test
    fun missedDayBreaksStreak() {
        val d = LocalDate.of(2026, 1, 10)
        assertEquals(Streak(0, 1, 1), streak(mapOf(d.minusDays(2) to 600000), 600000, d))
    }

    @Test
    fun thresholdAndFutureDatesDoNotQualify() {
        val d = LocalDate.of(2026, 1, 10)
        assertEquals(
            Streak(0, 0, 0),
            streak(mapOf(d to 599999, d.plusDays(1) to 600000), 600000, d),
        )
    }

    @Test
    fun duplicateSegmentsAreIdempotent() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        val rows = sample(t, 1000, 11000, 1000)
        assertEquals(1000, statistics(rows + rows, 10).elapsed)
    }

    @Test
    fun achievementsAreDeterministic() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0)
        val rows = sample(t, 1000, 11000, 1000)
        val a = eligibleBadges(rows, 1, 10, 30, emptySet())
        assertEquals(a, eligibleBadges(rows, 1, 10, 30, emptySet()))
        assertTrue("early" in a)
        assertTrue("first" in a)
        assertFalse("speed" in a)
    }

    @Test
    fun naturalChapterOrderingHandlesLargeNumbers() {
        val input =
            listOf(
                "Chapter 10.mp3",
                "Chapter 2.mp3",
                "Chapter 001.mp3",
                "Chapter 999999999999999999999.mp3",
            )
        assertEquals(listOf(input[2], input[1], input[0], input[3]), input.sortedWith(naturalOrder))
    }

    @Test
    fun speedChangesUsePreviousIntervalRate() {
        val t = ListeningTracker()
        sample(t, 0, 10000, 0, 1f)
        val rows = sample(t, 1000, 11000, 1000, 2f)
        assertEquals(1000, rows.sumOf { it.elapsedMs })
        assertEquals(1f, rows.first().speed, 0f)
    }
}
