@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.session.*
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import app.auralis.playback.PlaybackService
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

/** Run on the isolated verification build, never the user's installed library. */
class BookRemovalTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    private val graph
        get() = EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java)

    @Before
    fun requireIsolatedInstallation() {
        Assume.assumeTrue(
            "Removal tests require the isolated verification build",
            context.packageName.endsWith(".verification"),
        )
    }

    private suspend fun seed(): Pair<String, File> {
        check(context.packageName.endsWith(".verification")) {
            "Use -PauralisTestBuildType=verification"
        }
        val id = UUID.randomUUID().toString()
        val file = File(context.cacheDir, "remove-$id.wav")
        val samples = 8000 * 10
        file.writeBytes(
            ByteBuffer.allocate(44 + samples * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    put("RIFF".toByteArray())
                    putInt(36 + samples * 2)
                    put("WAVEfmt ".toByteArray())
                    putInt(16)
                    putShort(1)
                    putShort(1)
                    putInt(8000)
                    putInt(16000)
                    putShort(2)
                    putShort(16)
                    put("data".toByteArray())
                    putInt(samples * 2)
                    repeat(samples) { putShort(0) }
                }
                .array()
        )
        val d = graph.database().dao()
        d.books(
            listOf(BookEntity(id, "Removal check $id"), BookEntity("keep-$id", "Keep this story"))
        )
        d.files(listOf(AudioFileEntity("file-$id", id, file.toURI().toString(), file.name, 10000)))
        d.chapters(listOf(ChapterEntity("chapter-$id", id, "file-$id", "Chapter one", 0, 0, 10000)))
        d.progress(PlaybackProgressEntity(id, 0, 1000))
        d.bookmark(BookmarkEntity("mark-$id", id, 0, 1000, note = "Saved thought"))
        d.collection(CollectionEntity("collection-$id", "A shelf"))
        d.membership(CollectionBookCrossRef("collection-$id", id))
        d.session(ListeningSessionEntity("session-$id", id, 10000, 11000))
        d.segment(
            ListeningSegmentEntity(
                "segment-$id",
                "session-$id",
                id,
                "chapter-$id",
                10000,
                11000,
                "1970-01-01",
                "UTC",
                1000,
                1000,
                0,
                1000,
                1f,
            )
        )
        return id to file
    }

    @Test
    fun cancelKeepsBookAndConfirmationRemovesOnlyItsLibraryData() = runBlocking {
        val (id, file) = seed()
        val d = graph.database().dao()
        compose.onNodeWithTag("nav-library").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodesWithText("Removal check $id").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText("Removal check $id").onLast().performClick()
        compose.onNodeWithContentDescription("Remove from library").performClick()
        compose.onNodeWithText("Remove from library?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNotNull(d.book(id))
        compose.onNodeWithContentDescription("Remove from library").performClick()
        compose.onNodeWithText("Remove book").performClick()
        compose.waitUntil(5000) { runBlocking { d.book(id) == null } }
        compose.waitUntil(5000) {
            compose
                .onAllNodesWithContentDescription("Search library")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithContentDescription("Search library").assertIsDisplayed()
        assertNotNull(d.book("keep-$id"))
        assertTrue(d.observeBookmarks().first().none { it.bookId == id })
        assertTrue(d.observeMemberships().first().none { it.bookId == id })
        assertTrue(file.exists())
        assertEquals(1000, d.bookSegments(id, 0).sumOf { it.elapsedMs })
        graph
            .database()
            .openHelper
            .readableDatabase
            .query("SELECT COUNT(*) FROM files WHERE bookId=?", arrayOf(id))
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        graph
            .database()
            .openHelper
            .readableDatabase
            .query("SELECT COUNT(*) FROM chapters WHERE bookId=?", arrayOf(id))
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
        graph
            .database()
            .openHelper
            .readableDatabase
            .query("SELECT COUNT(*) FROM progress WHERE bookId=?", arrayOf(id))
            .use {
                assertTrue(it.moveToFirst())
                assertEquals(0, it.getInt(0))
            }
    }

    @Test
    fun removingLoadedBookDrainsPendingCheckpointsAndClearsQueue() = runBlocking {
        val (id, file) = seed()
        val d = graph.database().dao()
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            future =
                MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        val controller = future.get(15, TimeUnit.SECONDS)
        try {
            lateinit var load: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            instrumentation.runOnMainSync {
                load =
                    controller.sendCustomCommand(
                        SessionCommand("load", Bundle.EMPTY),
                        Bundle().apply {
                            putString("id", id)
                            putBoolean("play", false)
                        },
                    )
            }
            assertEquals(SessionResult.RESULT_SUCCESS, load.get(15, TimeUnit.SECONDS).resultCode)
            lateinit var removal: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            instrumentation.runOnMainSync {
                repeat(20) { controller.seekTo(0, it * 100L) }
                removal =
                    controller.sendCustomCommand(
                        SessionCommand("removeBook", Bundle.EMPTY),
                        Bundle().apply { putString("id", id) },
                    )
            }
            assertEquals(SessionResult.RESULT_SUCCESS, removal.get(15, TimeUnit.SECONDS).resultCode)
            delay(1200)
            assertNull(d.book(id))
            instrumentation.runOnMainSync {
                assertEquals(0, controller.mediaItemCount)
                assertFalse(controller.isPlaying)
            }
            assertTrue(file.exists())
            assertEquals(1000, d.bookSegments(id, 0).sumOf { it.elapsedMs })
            assertNotEquals(id, PreferenceStore(context).flow.first().lastBook)
            graph
                .database()
                .openHelper
                .readableDatabase
                .query("SELECT COUNT(*) FROM progress WHERE bookId=?", arrayOf(id))
                .use {
                    assertTrue(it.moveToFirst())
                    assertEquals(0, it.getInt(0))
                }
        } finally {
            instrumentation.runOnMainSync { controller.release() }
        }
    }
}
