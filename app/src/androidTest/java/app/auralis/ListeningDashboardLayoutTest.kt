package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.domain.*
import app.auralis.ui.*
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ListeningDashboardLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val dashboard =
        listeningDashboard(emptyList(), emptyList(), 10, LocalDate.of(2026, 11, 16))

    @Test
    fun periodTabsSwitchToRealEmptyStates() {
        compose.setContent { ListeningDashboardContent(dashboard, emptyList(), emptyList(), true) }
        compose.onNodeWithText("MONTHLY AVERAGE").assertIsDisplayed()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("TODAY’S LISTENING").assertIsDisplayed()
        compose.onNodeWithText("Total").performClick()
        compose.onNodeWithText("TOTAL LISTENING").assertIsDisplayed()
        compose.onNodeWithText("Your first chapter awaits").assertIsDisplayed()
    }

    @Test
    fun narrowPhoneAtLargeTextKeepsLowerSectionsReachable() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 700.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                ListeningDashboardContent(dashboard, emptyList(), emptyList(), true)
            }
        }
        compose
            .onNodeWithTag("listening-scroll")
            .performScrollToNode(hasText("Your weekly listening stats"))
        compose.onNodeWithText("Your weekly listening stats").assertIsDisplayed()
        compose
            .onNodeWithTag("listening-scroll")
            .performScrollToNode(hasText("Goals, calendar & more insights"))
        compose.onNodeWithText("Goals, calendar & more insights").assertIsDisplayed()
    }

    @Test
    fun tabletKeepsChartAndPeriodControlsVisible() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1200.dp, 800.dp))
            ) {
                ListeningDashboardContent(dashboard, emptyList(), emptyList(), true)
            }
        }
        compose.onNodeWithText("Monthly").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("listening-chart").assertIsDisplayed()
    }
}
