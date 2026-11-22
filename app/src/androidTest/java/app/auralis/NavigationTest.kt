package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fourDestinationsAndImportReviewEntryAreAccessible() {
        compose.onNodeWithTag("nav-library").performClick()
        compose.onAllNodesWithText("Library").onFirst().assertIsDisplayed()
        compose.onNodeWithTag("nav-statistics").performClick()
        compose.onNodeWithText("Your Listening").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("Your world").assertIsDisplayed()
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithContentDescription("Import audiobooks").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Choose audio files").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Choose audio files").assertIsDisplayed()
        compose.onNodeWithText("Choose a folder").assertIsDisplayed()
    }
}
