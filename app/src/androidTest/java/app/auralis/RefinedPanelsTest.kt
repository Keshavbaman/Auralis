package app.auralis

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.ui.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class RefinedPanelsTest {
    @get:Rule val compose = createComposeRule()
    private val book =
        LibraryBook(
            BookEntity("b", "Atomic Habits", "James Clear"),
            listOf(ChapterEntity("c", "b", "f", "Original filename.mp3", 0, 0, 3600000)),
            listOf(AudioFileEntity("f", "b", "content://test", "source.mp3", 3600000)),
            null,
        )

    @Composable
    private fun Details(action: (String) -> Unit) {
        BookDetailsContent(
            book,
            emptyList(),
            { action("back") },
            { action("play") },
            { action("favourite") },
            { action("edit") },
            { action("remove") },
            { action("collection") },
            { action("finished") },
            { action("reset") },
            { action("cover") },
            { action("relink") },
            { action("chapter") },
            { action("jump") },
            { action("edit-note") },
            { action("delete-note") },
        )
    }

    @Test
    fun detailsKeepPlaybackCollectionAndManagementActions() {
        var action = ""
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) { Details { action = it } }
        }
        compose.onNodeWithText("Start listening").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("play", action) }
        compose.onNodeWithText("Add to collection").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("collection", action) }
        compose.onNodeWithContentDescription("Book actions").performScrollTo().performClick()
        compose.onNodeWithText("Reset progress").performClick()
        compose.runOnIdle { assertEquals("reset", action) }
        compose.onNodeWithContentDescription("Play chapter").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("chapter", action) }
        compose.onNodeWithContentDescription("Remove from library").performClick()
        compose.runOnIdle { assertEquals("remove", action) }
    }

    @Test
    fun detailsFitNarrowLargeTextAndKeepNotesReachable() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                AuralisTheme(Preferences(theme = "light", reducedMotion = true)) { Details {} }
            }
        }
        compose.onNodeWithText("Start listening").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("book-detail-scroll").performScrollToNode(hasText("Search your notes"))
        compose.onNodeWithText("Search your notes").assertIsDisplayed()
    }

    @Test
    fun bookmarkSheetSavesTheExactTypedTextWithKeyboardOpen() {
        var saved: Pair<String, String>? = null
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                PlayerSheet("Keep this moment", "Chapter One · 14:22", {}) {
                    BookmarkEditor { title, note -> saved = title to note }
                }
            }
        }
        compose
            .onNodeWithText("Title (optional)")
            .performClick()
            .performTextInput("A useful thought")
        compose
            .onNodeWithText("Your note")
            .performClick()
            .performTextInput("Return to this explanation.")
        compose.onNodeWithText("Save bookmark").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("A useful thought" to "Return to this explanation.", saved)
        }
    }

    @Test
    fun settingsSectionsRemainReachableWithLargeText() {
        var section = ""
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(1.5f)) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    SettingsOverview({}) { section = it }
                }
            }
        }
        compose.onNodeWithText("Playback").performClick()
        compose.runOnIdle { assertEquals("Playback", section) }
        compose.onNodeWithTag("settings-scroll").performScrollToNode(hasText("Backup and restore"))
        compose.onNodeWithText("Backup and restore").performClick()
        compose.runOnIdle { assertEquals("Backup and restore", section) }
    }

    @Test
    fun historyShowsRealSessionAndOpensItsBook() {
        var opened = ""
        val entry =
            HistoryEntry("s", book.book, LocalDate.now().minusDays(1), "9:30 PM", 60000, 120000, 2f)
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                HistoryContent(listOf(entry), {}) { opened = it }
            }
        }
        compose.onNodeWithText("Yesterday").assertIsDisplayed()
        compose.onNodeWithText(book.book.title).performClick()
        compose.runOnIdle { assertEquals(book.book.id, opened) }
    }
}
