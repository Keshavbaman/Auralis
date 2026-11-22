package app.auralis

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.domain.*
import app.auralis.ui.*
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ListeningJourneyLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 16)

    private fun row(id: String, date: LocalDate, minutes: Int): ListeningSegmentEntity {
        val start = date.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        return ListeningSegmentEntity(
            id,
            id,
            "book",
            "chapter",
            start,
            start + minutes * 60_000L,
            date.toString(),
            "UTC",
            minutes * 60_000L,
            minutes * 120_000L,
            0,
            minutes * 120_000L,
            2f,
        )
    }

    private val rows
        get() = listOf(row("today", today, 30), row("yesterday", today.minusDays(1), 15))

    private val data
        get() = listeningInsights(rows, emptyList(), 10, today)

    @Test
    fun goalEditorChangesTargetWithoutChangingHistory() {
        val actual = listeningInsights(listOf(row("a", today, 49)), emptyList(), 10, today)
        var saved = 0
        compose.setContent {
            var prefs by remember { mutableStateOf(Preferences(reducedMotion = true)) }
            var edit by remember { mutableStateOf(false) }
            AuralisTheme(prefs) {
                ListeningSanctuaryContent(actual, emptyList(), prefs, today, { edit = true })
                if (edit)
                    ListeningGoalEditor(prefs, { edit = false }) { g, s ->
                        prefs = prefs.copy(goalMinutes = g, streakMinutes = s)
                        saved = g
                        edit = false
                    }
            }
        }
        compose.onNodeWithText("Daily goal complete").assertIsDisplayed()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasTestTag("edit-listening-goals"))
        compose.onNodeWithTag("edit-listening-goals").performClick()
        compose.onNodeWithText("60 min").performClick()
        compose.onNodeWithText("Save goal").performClick()
        compose.onNodeWithTag("journey-scroll").performScrollToIndex(0)
        compose.onNodeWithText("11 min left for today").assertIsDisplayed()
        assertEquals(60, saved)
        assertEquals(49 * 60_000L, actual.stats.elapsed)
    }

    @Test
    fun monthlyInsightsShowActualTotalsRatherThanAcceleratedContent() {
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                ListeningSanctuaryContent(data, emptyList(), Preferences(), today)
            }
        }
        compose.onNodeWithTag("journey-tab-Insights").performClick()
        compose.onNodeWithTag("insight-month-total").assertTextEquals("45 min")
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasContentDescription("Choose listening chart date"))
        compose.onNodeWithContentDescription("Choose listening chart date").performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress
        ) {
            it(0f)
        }
        compose.onNodeWithText("18 Oct · 0 min").assertExists()
    }

    @Test
    fun calendarSelectsRealDayAndPreventsFutureMonthNavigation() {
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                ListeningSanctuaryContent(data, emptyList(), Preferences(), today)
            }
        }
        compose.onNodeWithTag("journey-tab-Calendar").performClick()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasContentDescription("Next month"))
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasContentDescription("15 November 2026: 15 min listening"))
        compose.onNodeWithContentDescription("15 November 2026: 15 min listening").performClick()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasTestTag("calendar-day-details"))
        compose.onNodeWithTag("calendar-day-details").assert(hasAnyDescendant(hasText("15 min")))
        compose
            .onNodeWithTag("calendar-day-details")
            .assert(hasAnyDescendant(hasText("1 session · actual listening")))
    }

    @Test
    fun narrowPhoneWithLargeTextKeepsMilestoneAndReportsReachable() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 740.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    ListeningSanctuaryContent(data, emptyList(), Preferences(), today)
                }
            }
        }
        compose.onNodeWithTag("journey-scroll").performScrollToNode(hasText("YOUR NEXT MILESTONE"))
        compose.onNodeWithText("YOUR NEXT MILESTONE").assertIsDisplayed()
        compose.onNodeWithTag("journey-tab-Insights").performClick()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(hasTestTag("open-listening-report"))
        compose.onNodeWithTag("open-listening-report").assertIsDisplayed()
    }

    @Test
    fun tabletCalendarAndLightThemeRetainSelectionAndLabels() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1200.dp, 800.dp))
            ) {
                AuralisTheme(Preferences(theme = "light", reducedMotion = true)) {
                    ListeningSanctuaryContent(
                        data,
                        emptyList(),
                        Preferences(theme = "light"),
                        today,
                    )
                }
            }
        }
        compose.onNodeWithTag("journey-tab-Calendar").performClick()
        compose
            .onNodeWithTag("journey-scroll")
            .performScrollToNode(
                hasContentDescription("16 November 2026: 30 min listening, selected")
            )
        compose
            .onNodeWithContentDescription("16 November 2026: 30 min listening, selected")
            .assertIsDisplayed()
            .assertIsSelected()
    }

    @Test
    fun emptyGoalsAndInsightsNeverInventListening() {
        val empty = listeningInsights(emptyList(), emptyList(), 10, today)
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                ListeningSanctuaryContent(empty, emptyList(), Preferences(), today)
            }
        }
        compose.onNodeWithText("30 min left for today").assertIsDisplayed()
        compose.onNodeWithTag("journey-tab-Insights").performClick()
        compose.onNodeWithTag("insight-month-total").assertTextEquals("0 min")
    }
}
