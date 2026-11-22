package app.auralis

import androidx.compose.foundation.layout.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.auralis.data.*
import app.auralis.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/**
 * Pure presentation tests: never import files or change the user's database or listening history.
 */
@OptIn(ExperimentalTestApi::class)
class PremiumPlayerLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val book =
        LibraryBook(
            BookEntity(
                "preview",
                "Harry Potter and the Order of the Phoenix (Full-Cast Edition)",
                "J.K. Rowling",
                narrator = "Cush Jumbo and full cast",
            ),
            listOf(
                ChapterEntity(
                    "chapter",
                    "preview",
                    "file",
                    "Chapter One · Dudley Demented",
                    0,
                    0,
                    3600000,
                ),
                ChapterEntity("chapter2", "preview", "file", "Chapter Two", 1, 3600000, 7200000),
            ),
            listOf(AudioFileEntity("file", "preview", "content://test", "preview.m4b", 7200000)),
            null,
        )

    @Test
    fun phoneTransportAndSecondaryToolsRemainUsable() {
        var toggles = 0
        var skipped = false
        var selected = ""
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                PremiumPlayer(
                    book,
                    PlayerSnapshot(duration = 3600000, position = 123000),
                    Preferences(reducedMotion = true),
                    {},
                    { toggles++ },
                    { skipped = it },
                    {},
                    {},
                    {},
                    {},
                    { selected = it },
                )
            }
        }
        compose.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, toggles) }
        compose.onNodeWithContentDescription("Skip forward 30 seconds").performClick()
        compose.runOnIdle { assertTrue(skipped) }
        compose.onNodeWithContentDescription("Playback speed").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("speed", selected) }
        compose.onNodeWithContentDescription("Sleep timer").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("sleep", selected) }
        compose.onNodeWithContentDescription("Book progress").performScrollTo().performClick()
        compose
            .onNodeWithContentDescription("Whole book progress")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun tabletLandscapeKeepsArtworkAndTransportVisible() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1400.dp, 876.dp))
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    PremiumPlayer(
                        book,
                        PlayerSnapshot(duration = 3600000, playing = true),
                        Preferences(reducedMotion = true),
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Pause").assertIsDisplayed()
        compose.onNodeWithContentDescription("Playback speed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cover of ${book.book.title}").assertIsDisplayed()
    }

    @Test
    fun smallPhoneWithLargeTextCanReachEveryControl() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 720.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    PremiumPlayer(
                        book,
                        PlayerSnapshot(duration = 3600000),
                        Preferences(reducedMotion = true),
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                        {},
                    )
                }
            }
        }
        compose.onNodeWithContentDescription("Play").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Add bookmark").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Sleep timer").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithContentDescription("Chapter navigation and queue")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
