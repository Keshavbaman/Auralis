package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.Preferences
import app.auralis.domain.statistics
import app.auralis.ui.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ProfileWorldLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val stats = statistics(emptyList(), 10, LocalDate.now())

    @Test
    fun levelGoalAndHistoryKeepTheirDestinations() {
        var destination = ""
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                ProfileWorldContent(stats, 30, emptySet(), 0, 0) { destination = it }
            }
        }
        compose.onNodeWithTag("world-rank").performClick()
        compose.runOnIdle { assertEquals("ranks", destination) }
        compose.onNodeWithText("Your daily ritual").performClick()
        compose.runOnIdle { assertEquals("preferences/Goals", destination) }
        compose.onNodeWithTag("world-scroll").performScrollToNode(hasText("Listening history"))
        compose.onNodeWithText("Listening history").performClick()
        compose.runOnIdle { assertEquals("history", destination) }
    }

    @Test
    fun largeTypeKeepsLongLevelNameAndSettingsReachable() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                AuralisTheme(Preferences(theme = "light", reducedMotion = true)) {
                    ProfileWorldContent(
                        stats.copy(elapsed = 5000L * 3600000),
                        30,
                        emptySet(),
                        0,
                        0,
                    ) {}
                }
            }
        }
        compose.onNodeWithText("Grandmaster").assertIsDisplayed()
        compose
            .onNodeWithTag("world-scroll")
            .performScrollToNode(hasText("Playback, appearance & your library"))
        compose.onNodeWithText("Playback, appearance & your library").assertIsDisplayed()
    }

    @Test
    fun tabletShowsLevelAndDailyGoalTogether() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1200.dp, 800.dp))
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    ProfileWorldContent(stats, 30, emptySet(), 0, 0) {}
                }
            }
        }
        compose.onNodeWithTag("world-rank").assertIsDisplayed()
        compose.onNodeWithText("Your daily ritual").assertIsDisplayed()
    }
}
