package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomePresentationTest {
    @get:Rule val compose = createComposeRule()
    private val book =
        LibraryBook(
            BookEntity("b", "The Hitchhiker’s Guide to the Galaxy", "Douglas Adams"),
            listOf(
                ChapterEntity(
                    "c",
                    "b",
                    "f",
                    "A long chapter title that must stay within the card",
                    0,
                    0,
                    7200000,
                )
            ),
            listOf(AudioFileEntity("f", "b", "content://test", "chapter.mp3", 7200000)),
            PlaybackProgressEntity("b", 0, 60000),
        )

    @Test
    fun resumeIsIndependentFromBookDetailsAndGoalStillOpens() {
        var action = ""
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                HomeContent(
                    1,
                    book,
                    emptyList(),
                    0,
                    30,
                    1,
                    1f,
                    false,
                    { action = "detail" },
                    { action = "import" },
                    { action = "library" },
                    { action = "goals" },
                    { action = "listen" },
                )
            }
        }
        compose.onNodeWithText("Resume").performClick()
        compose.runOnIdle { assertEquals("listen", action) }
        compose.onNodeWithTag("home-continue").performClick()
        compose.runOnIdle { assertEquals("detail", action) }
        compose
            .onNodeWithContentDescription("Daily listening goal")
            .performScrollTo()
            .performClick()
        compose.runOnIdle { assertEquals("goals", action) }
    }

    @Test
    fun emptyLibraryOffersWorkingImport() {
        var imported = false
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                HomeContent(
                    0,
                    null,
                    emptyList(),
                    0,
                    30,
                    0,
                    1f,
                    false,
                    {},
                    { imported = true },
                    {},
                    {},
                    {},
                )
            }
        }
        compose.onNodeWithText("Import your first book").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(imported) }
    }

    @Test
    fun largeTypeKeepsResumeReachableOnNarrowPhone() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                AuralisTheme(Preferences(theme = "light", reducedMotion = true)) {
                    HomeContent(1, book, emptyList(), 0, 30, 0, 1f, false, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("Resume").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Import audiobooks")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tabletShowsHeroAndLibraryAction() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1400.dp, 876.dp))
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    HomeContent(1, book, emptyList(), 0, 30, 0, 1f, false, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithTag("home-continue").assertIsDisplayed()
        compose.onNodeWithText("Your library").assertIsDisplayed()
        compose.onNodeWithText("Resume").assertIsDisplayed()
    }
}
