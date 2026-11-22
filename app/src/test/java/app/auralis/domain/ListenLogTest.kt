package app.auralis.domain

import app.auralis.data.ListeningSegmentEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenLogTest {
    private fun segment(
        id: String,
        session: String,
        book: String = "dune",
        chapter: String = "c1",
        startedAt: Long,
        date: String = "2026-11-20",
        elapsed: Long,
        from: Long = 0,
    ) =
        ListeningSegmentEntity(
            id = id,
            sessionId = session,
            bookId = book,
            chapterId = chapter,
            startedAt = startedAt,
            endedAt = startedAt + elapsed,
            date = date,
            zoneId = "UTC",
            elapsedMs = elapsed,
            contentMs = elapsed,
            fromMs = from,
            toMs = from + elapsed,
            speed = 1f,
        )

    @Test
    fun groupsSegmentsIntoSittingsAndNewestDaysFirst() {
        val rows =
            listOf(
                segment("a", "s1", startedAt = 1_000, elapsed = 60_000),
                segment("b", "s1", startedAt = 61_000, elapsed = 30_000),
                segment("c", "s2", startedAt = 9_000_000, date = "2026-11-21", elapsed = 120_000),
                segment("x", "o", book = "other", startedAt = 9_500_000, date = "2026-11-21", elapsed = 99_000),
            )
        val days = listenLog("dune", rows)
        assertEquals(listOf(LocalDate.of(2026, 11, 21), LocalDate.of(2026, 11, 20)), days.map { it.date })
        val earlier = days.last().sessions.single()
        assertEquals(90_000L, earlier.elapsedMs)
        assertEquals(1_000L, earlier.startedAt)
        assertEquals(120_000L, days.first().elapsedMs)
    }

    @Test
    fun mergesSessionsSeparatedByAShortPause() {
        val rows =
            listOf(
                segment("a", "s1", startedAt = 0, elapsed = 60_000),
                // Two-minute pause, then playback resumes in a new session.
                segment("b", "s2", startedAt = 180_000, elapsed = 60_000, from = 60_000),
            )
        val sitting = listenLog("dune", rows).single().sessions.single()
        assertEquals(120_000L, sitting.elapsedMs)
        assertEquals(240_000L, sitting.endedAt)
        assertEquals(0L, sitting.fromMs)
        assertEquals(120_000L, sitting.toMs)
    }

    @Test
    fun splitsSittingsAfterALongPause() {
        val rows =
            listOf(
                segment("a", "s1", startedAt = 0, elapsed = 60_000),
                segment("b", "s2", startedAt = 60_000 + 10 * 60_000, elapsed = 60_000),
            )
        assertEquals(2, listenLog("dune", rows).single().sessions.size)
    }

    @Test
    fun dropsAccidentalTapsAndEmptyDays() {
        val rows =
            listOf(
                segment("tap", "s1", startedAt = 0, date = "2026-11-22", elapsed = 2_000),
                segment("real", "s2", startedAt = 0, date = "2026-11-21", elapsed = 45_000),
            )
        val days = listenLog("dune", rows)
        assertEquals(listOf(LocalDate.of(2026, 11, 21)), days.map { it.date })
    }

    @Test
    fun tracksChapterRangeAcrossASitting() {
        val rows =
            listOf(
                segment("a", "s1", chapter = "c1", startedAt = 0, elapsed = 60_000, from = 500_000),
                segment("b", "s1", chapter = "c2", startedAt = 60_000, elapsed = 30_000, from = 0),
            )
        val sitting = listenLog("dune", rows).single().sessions.single()
        assertEquals("c1", sitting.firstChapterId)
        assertEquals("c2", sitting.lastChapterId)
        assertEquals(500_000L, sitting.fromMs)
        assertEquals(30_000L, sitting.toMs)
    }

    @Test
    fun ignoresSegmentsBeforeProgressReset() {
        val rows =
            listOf(
                segment("old", "s1", startedAt = 1_000, elapsed = 60_000),
                segment("new", "s2", startedAt = 1_000_000, elapsed = 10_000),
            )
        val days = listenLog("dune", rows, resetAt = 2_000)
        assertEquals(10_000L, days.single().elapsedMs)
    }

    @Test
    fun spanLabelsNeverShowZeroMinutes() {
        assertEquals("42s", listenSpanLabel(42_000))
        assertEquals("4m 12s", listenSpanLabel(252_000))
        assertEquals("4m", listenSpanLabel(240_000))
        assertEquals("38m", listenSpanLabel(38 * 60_000L + 5_000))
        assertEquals("1h 5m", listenSpanLabel(65 * 60_000L))
        assertTrue(listenSpanLabel(0).endsWith("s"))
    }
}
