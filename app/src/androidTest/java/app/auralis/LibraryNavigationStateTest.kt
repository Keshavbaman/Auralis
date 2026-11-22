package app.auralis

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Regression checks run only against the isolated verification library. */
class LibraryNavigationStateTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "navigation-state-"
    private val db get() = EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java).database()

    @Before fun seed() {
        Assume.assumeTrue(context.packageName.endsWith(".verification"))
        runBlocking {
            db.dao().books((0..79).map {
                BookEntity("$prefix$it", "Navigation story %02d".format(it), "Navigation author", addedAt = 1000L + it, seriesName = "Navigation series %02d".format(it / 2), seriesNumber = (it % 2 + 1).toDouble())
            })
            db.dao().progress(PlaybackProgressEntity("${prefix}0", lastPlayed = Long.MAX_VALUE - 1))
        }
        compose.onNodeWithTag("nav-library").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("library-books-grid").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        if (context.packageName.endsWith(".verification")) {
            db.openHelper.writableDatabase.execSQL("DELETE FROM progress WHERE bookId LIKE '$prefix%'")
            db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id LIKE '$prefix%'")
        }
    }

    private fun position(tag: String): Float = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun back() {
        compose.waitUntil(5000) { compose.onAllNodesWithTag("book-detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("library-books-grid").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("library-books-list").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun defaultsToPlayedAndRestoresGridAfterDetailsAndTabSwitch() {
        compose.onNodeWithText("Played").assertIsDisplayed()
        compose.onNodeWithText("Navigation story 00").assertIsDisplayed()
        compose.onNodeWithTag("library-books-grid").performScrollToNode(hasText("Navigation story 35"))
        val previous = position("library-books-grid")
        assertTrue(previous > 0)
        compose.onNodeWithText("Navigation story 35").assertIsDisplayed().performClick()
        back()
        assertEquals(previous, position("library-books-grid"), .1f)
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("nav-library").performClick()
        assertEquals(previous, position("library-books-grid"), .1f)
    }

    @Test fun listAndSortSurviveOpeningBook() {
        compose.onNodeWithText("Played").performClick()
        compose.onNodeWithText("Title", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Toggle grid or list").performClick()
        compose.onNodeWithTag("library-books-list").performScrollToNode(hasText("Navigation story 35"))
        val previous = position("library-books-list")
        compose.onNodeWithText("Navigation story 35").assertIsDisplayed().performClick()
        back()
        compose.onNodeWithText("Title").assertIsDisplayed()
        assertEquals(previous, position("library-books-list"), .1f)
    }
    @Test fun returningFromSeriesRestoresShelfAndGroup() {
        compose.onNodeWithText("Series").performClick()
        compose.onNodeWithTag("library-groups-grid").performScrollToNode(hasText("Navigation series 20"))
        val previous = position("library-groups-grid")
        compose.onNodeWithText("Navigation series 20").assertIsDisplayed().performClick()
        compose.onNodeWithTag("library-group-detail").assertIsDisplayed()
        compose.onNode(hasText("Navigation story 40") and hasClickAction()).assertIsDisplayed().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("book-detail-scroll").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithTag("library-group-detail").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to library").performClick()
        assertEquals(previous, position("library-groups-grid"), .1f)
    }

}
