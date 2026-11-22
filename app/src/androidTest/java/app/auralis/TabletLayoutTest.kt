package app.auralis

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.auralis.ui.AuralisRoot
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class TabletLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun tabS8PlusLandscapeUsesNavigationRail() {
        compose.activity.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1400.dp, 876.dp))
            ) {
                AuralisRoot(viewModel())
            }
        }
        compose.onNodeWithTag("nav-library").performClick()
        compose.onAllNodesWithText("Library").onFirst().assertIsDisplayed()
        compose.onNodeWithContentDescription("Import audiobooks").assertIsDisplayed()
        compose.onNodeWithTag("nav-statistics").performClick()
        compose.onNodeWithText("Your Listening").assertIsDisplayed()
    }

    @Test
    fun largeTextKeepsPhoneImportAccessible() {
        compose.activity.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                AuralisRoot(viewModel())
            }
        }
        compose.onNodeWithContentDescription("Import audiobooks").assertIsDisplayed().performClick()
        compose.onNodeWithText("Choose audio files").assertIsDisplayed()
    }
}
