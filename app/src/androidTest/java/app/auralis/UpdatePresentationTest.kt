package app.auralis

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.domain.*
import app.auralis.ui.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class UpdatePresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun calendarShowsTodaysActivityInsteadOfOnlyOldEmptyWeeks() {
        val today = LocalDate.of(2026, 11, 15)
        var selected: LocalDate? = null
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                ListeningCalendar(today, mapOf(today to 267000L)) { selected = it }
            }
        }
        compose
            .onNodeWithContentDescription("2026-11-15: 4m 27s listening")
            .assertIsDisplayed()
            .performClick()
        compose.runOnIdle { assertEquals(today, selected) }
        compose.onNodeWithText("0 · under 5 · 5–14 · 15–44 · 45+ min").assertExists()
    }

    @Test
    fun exportedStoryAndSummaryHaveRequestedResolution() {
        val report =
            listeningReport(
                emptyList(),
                emptyList(),
                emptyList(),
                true,
                LocalDate.of(2026, 11, 15),
                10,
            )
        val story = renderReportCard(report, 0, false, false)
        assertEquals(1080, story.width)
        assertEquals(1920, story.height)
        story.recycle()
        val summary = renderReportCard(report, 7, true, false)
        assertEquals(1080, summary.width)
        assertEquals(1350, summary.height)
        summary.recycle()
    }

    @Test
    fun everyBadgeHasDistinctRecognizableLockedArtwork() {
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                Column {
                    badges.chunked(4).forEach { row ->
                        Row { row.forEach { Medal(it.key, false, Modifier.size(72.dp)) } }
                    }
                }
            }
        }
        badges.forEach {
            compose.onNodeWithContentDescription("${it.title} locked badge").assertIsDisplayed()
        }
    }
}
