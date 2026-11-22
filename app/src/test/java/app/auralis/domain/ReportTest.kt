package app.auralis.domain

import app.auralis.data.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReportTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val book =
        LibraryBook(
            BookEntity("b", "Private title", author = "Author", genre = "Fantasy"),
            emptyList(),
            emptyList(),
            null,
        )

    private fun row(id: String, date: String) =
        ListeningSegmentEntity(
            id,
            "session",
            "b",
            "c",
            Instant.parse("${date}T12:00:00Z").toEpochMilli(),
            Instant.parse("${date}T12:01:00Z").toEpochMilli(),
            date,
            "UTC",
            60000,
            120000,
            0,
            120000,
            2f,
        )

    @Test
    fun monthlyReportExcludesOtherMonthsAndDeduplicatesEvents() {
        val r = row("nov", "2026-11-14")
        val report =
            listeningReport(
                listOf(r, r, row("oct", "2026-10-14")),
                listOf(book),
                emptyList(),
                false,
                today,
                10,
            )
        assertEquals(60000L, report.stats.elapsed)
        assertEquals(120000L, report.stats.content)
        assertEquals(60000L, report.stats.saved)
        assertEquals("Private title", report.mostBook!!.title)
        assertEquals("Author", report.author)
        assertEquals("Fantasy", report.genre)
        assertFalse(report.page(2, false).first.contains("Private"))
        assertFalse(report.page(3, false).first.contains("Author"))
    }

    @Test
    fun insufficientHistoryDoesNotFabricateMetadata() {
        val report = listeningReport(emptyList(), listOf(book), emptyList(), true, today, 10)
        assertNull(report.mostBook)
        assertNull(report.author)
        assertNull(report.genre)
        assertEquals(0, report.completed)
        assertEquals(0L, report.stats.elapsed)
        assertEquals("Your story is beginning", report.personality)
    }

    @Test
    fun badgeProgressAgreesWithAwardRules() {
        val rows = listOf(row("r", "2026-11-14"))
        val awarded = eligibleBadges(rows, 0, 10, 30, setOf("Fantasy"))
        badges.forEach { badge ->
            assertEquals(
                badge.key,
                badge.key in awarded,
                badgeProgress(badge.key, rows, 0, listOf(book), 10, 30).fraction >= 1f,
            )
        }
    }
}
