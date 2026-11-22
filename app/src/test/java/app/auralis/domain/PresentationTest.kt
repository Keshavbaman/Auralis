package app.auralis.domain

import app.auralis.data.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class PresentationTest {
    @Test
    fun detailTitleSeparatesSeriesPrefixWithoutChangingTheStoredTitle() {
        val book = BookEntity("dune", "Dune 1: Dune", "Frank Herbert")
        assertEquals("Dune", bookDetailTitle(book))
        assertEquals("Dune 1: Dune", book.title)
        assertEquals(book.title, bookDetailTitle(book.copy(seriesInferenceDisabled = true)))
        val named = book.copy(title = "1984", seriesName = "Classics", seriesNumber = 2.0)
        assertEquals("1984", bookDetailTitle(named))
    }

    @Test
    fun explicitDurationUnits() {
        assertEquals("31 min", listeningDuration(31 * 60000))
        assertEquals("1h 25m", listeningDuration(85 * 60000))
        assertEquals("4m 27s", listeningDuration(267000))
        assertEquals("0 min", listeningDuration(0))
    }

    @Test
    fun calendarContainsEachDateExactlyOnceAndEndsToday() {
        listOf("2026-11-15", "2024-03-31", "2025-01-01").forEach {
            val end = LocalDate.parse(it)
            val calendar = listeningCalendar(end)
            assertTrue(calendar.weeks.size in 26..28)
            assertEquals(java.time.DayOfWeek.MONDAY, calendar.weeks.first().first().dayOfWeek)
            val dates = calendar.weeks.flatten().filter(calendar::contains)
            assertEquals(calendar.start, dates.first())
            assertEquals(end, dates.last())
            assertEquals(dates.size, dates.distinct().size)
            assertEquals(
                java.time.temporal.ChronoUnit.DAYS.between(calendar.start, end) + 1,
                dates.size.toLong(),
            )
        }
    }

    @Test
    fun seekingProgressIsSeparateFromUniqueCoverageAndCompletion() {
        val b =
            LibraryBook(
                BookEntity("b", "Book"),
                listOf(
                    ChapterEntity("a", "b", "f", "First", 0, endMs = 100000),
                    ChapterEntity("c", "b", "f", "Second", 1, endMs = 100000),
                ),
                emptyList(),
                PlaybackProgressEntity("b", 1, 44000),
            )
        b.listenedFraction = .01f
        assertEquals(.72f, b.fraction, .001f)
        assertEquals(.01f, b.coverageFraction, .001f)
        assertFalse(b.isFinished)
        val restored =
            b.copy(progress = b.progress!!.copy(positionMs = 20000)).also {
                it.listenedFraction = b.listenedFraction
            }
        assertEquals(.6f, restored.fraction, .001f)
        assertEquals(.01f, restored.coverageFraction, .001f)
    }

    @Test
    fun seriesOrdersNumbersRatherThanTextAndAllowsOverrides() {
        fun b(title: String) = LibraryBook(BookEntity(title, title), emptyList(), emptyList(), null)
        val ordered =
            orderedSeries(listOf(b("Series 10: Ten"), b("Series 2: Two"), b("Series 1: One")))
        assertEquals(listOf(1.0, 2.0, 10.0), ordered.map { seriesInfo(it.book)!!.number })
        assertEquals(
            SeriesInfo("Manual", 2.5),
            seriesInfo(
                BookEntity("b", "Unrelated title", seriesName = "Manual", seriesNumber = 2.5)
            ),
        )
    }

    @Test
    fun allSurfacesResolveTheSameMediaIdentityAndClampSeekPosition() {
        val book =
            LibraryBook(
                    BookEntity("b", "Book"),
                    listOf(
                        ChapterEntity("first", "b", "f", "I", 0, endMs = 100000),
                        ChapterEntity("second", "b", "f", "V", 1, endMs = 100000),
                    ),
                    emptyList(),
                    PlaybackProgressEntity("b", 0, 1000, lastPlayed = 9),
                )
                .also { it.listenedFraction = .01f }
        val live = book.withPlayback("second", 44000, 2f, 100)
        assertEquals(1, live.progress!!.chapterIndex)
        assertEquals(.72f, live.playbackFraction, .001f)
        assertEquals(.01f, live.coverageFraction, .001f)
        assertEquals(9L, live.progress.lastPlayed)
        assertSame(book, book.withPlayback("another-book", 9999, 1f, 100))
        assertEquals(100000L, book.withPlayback("second", 500000, 1f, 100).progress!!.positionMs)
        assertEquals(0L, book.withPlayback("first", -50, 1f, 100).progress!!.positionMs)
        assertNotEquals(book, book.copy().also { it.listenedFraction = .5f })
    }

    @Test
    fun rewindOnlyAfterLongPausesAndWhenEnabled() {
        assertEquals(0L, smartRewindMs(299999, true))
        assertEquals(10000L, smartRewindMs(300000, true))
        assertEquals(0L, smartRewindMs(900000, false))
    }

    @Test
    fun skippingChaptersCannotCreateCompletionIndicators() {
        val b =
            LibraryBook(
                BookEntity("b", "Book"),
                listOf(
                    ChapterEntity("first", "b", "f", "I", 0, endMs = 100000),
                    ChapterEntity("second", "b", "f", "II", 1, endMs = 100000),
                ),
                emptyList(),
                PlaybackProgressEntity("b", 1, 50000),
            )
        val row =
            ListeningSegmentEntity(
                "r",
                "s",
                "b",
                "first",
                1000,
                2000,
                "1970-01-01",
                "UTC",
                1000,
                1000,
                0,
                1000,
                1f,
            )
        val coverage = chapterCoverageMs(b, listOf(row, row.copy(id = "replay")))
        assertEquals(1000L, coverage["first"])
        assertEquals(0L, coverage["second"])
        assertEquals(
            0L,
            chapterCoverageMs(b.copy(book = b.book.copy(progressResetAt = 3000)), listOf(row))
                .values
                .sum(),
        )
    }
}
