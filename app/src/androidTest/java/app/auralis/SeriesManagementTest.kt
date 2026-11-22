package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import app.auralis.domain.seriesInfo
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class SeriesManagementTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val db get() = EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java).database()

    @Test
    fun editInferredSeriesThenCreateAnotherOne() {
        Assume.assumeTrue(context.packageName.endsWith(".verification"))
        val keep = BookEntity("series-test-a", "Original Test Series 1: First")
        val remove = BookEntity("series-test-b", "Original Test Series 2: Second")
        val add = BookEntity("series-test-c", "An independent title")
        val dao = db.dao()
        runBlocking {
            dao.books(listOf(keep, remove, add))
            dao.progress(PlaybackProgressEntity(remove.id, 0, 1234L))
        }
        try {
            compose.onNodeWithTag("nav-library").performClick()
            compose.onNodeWithText("Series").performClick()
            compose.onNodeWithTag("library-groups-grid").performScrollToNode(hasText("Original Test Series"))
            compose.onNode(hasText("Original Test Series") and hasClickAction()).performClick()
            compose.onNodeWithText("Edit series").performClick()
            compose.onNodeWithTag("series-editor-name").performTextReplacement("Renamed Test Series")
            compose.onNodeWithTag("series-book-${remove.id}").performClick()
            compose.onNodeWithTag("series-book-${add.id}").performClick()
            compose.onNodeWithText("Save series").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Renamed Test Series").fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking {
                assertEquals(1.0, dao.book(keep.id)?.book?.seriesNumber)
                assertNull(seriesInfo(dao.book(remove.id)!!.book))
                assertEquals(1234L, dao.book(remove.id)?.progress?.positionMs)
                assertEquals("Renamed Test Series", dao.book(add.id)?.book?.seriesName)
            }
            compose.onNodeWithContentDescription("Back to library").performClick()
            compose.onNodeWithText("New series").performClick()
            compose.onNodeWithTag("series-editor-name").performTextInput("Second Test Series")
            compose.onNodeWithTag("series-book-${remove.id}").performClick()
            compose.onNodeWithText("Save series").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("Second Test Series").fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking {
                assertEquals("Second Test Series", dao.book(remove.id)?.book?.seriesName)
                assertFalse(dao.book(remove.id)!!.book.seriesInferenceDisabled)
            }
        } finally {
            db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id LIKE 'series-test-%'")
        }
    }
}
