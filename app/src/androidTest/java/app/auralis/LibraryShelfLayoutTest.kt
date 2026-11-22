package app.auralis

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class LibraryShelfLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun compactTabsLeaveSpaceForBooks() {
        var selected by mutableStateOf("Books")
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(360.dp, 800.dp))
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    LibraryShelfTabs(selected) { selected = it }
                }
            }
        }
        compose.onNodeWithText("Collections").performScrollTo().performClick()
        compose.onNodeWithText("Collections").assertIsDisplayed().assertIsSelected()
        compose.runOnIdle { assertEquals("Collections", selected) }
    }

    @Test
    fun collectionsRemainsReadableAtLargeFontSize() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 800.dp)) then
                    DeviceConfigurationOverride.FontScale(1.8f)
            ) {
                AuralisTheme(Preferences(reducedMotion = true)) {
                    LibraryShelfTabs("Collections") {}
                }
            }
        }
        val label =
            compose
                .onNodeWithText("Collections")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
        val tabs = compose.onNodeWithTag("library-tabs").fetchSemanticsNode().boundsInRoot
        assertTrue(label.left >= tabs.left)
        assertTrue(label.right <= tabs.right)
    }

    @Test
    fun listPlayActionDoesNotOpenBookDetails() {
        val book =
            LibraryBook(
                BookEntity("test", "A long audiobook title for the library", "Author"),
                emptyList(),
                emptyList(),
                null,
            )
        var played = false
        var opened = false
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                LibraryBookRow(book, onPlay = { played = true }) { opened = true }
            }
        }
        compose.onNodeWithContentDescription("Play ${book.book.title}").performClick()
        compose.runOnIdle {
            assertTrue(played)
            assertFalse(opened)
        }
    }
}
