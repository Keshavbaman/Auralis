package app.auralis

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.media3.session.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import app.auralis.playback.PlaybackService
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuralisFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val graph
        get() = EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java)

    private fun fixture(name: String): android.net.Uri {
        val directory = File(context.filesDir, "test-fixtures").apply { mkdirs() }
        val file = directory.resolve(name)
        val samples = 16000 * 25
        val b = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray())
            .putInt(36 + samples * 2)
            .put("WAVEfmt ".toByteArray())
            .putInt(16)
            .putShort(1)
            .putShort(1)
            .putInt(16000)
            .putInt(32000)
            .putShort(2)
            .putShort(16)
            .put("data".toByteArray())
            .putInt(samples * 2)
        repeat(samples) {
            b.putShort((kotlin.math.sin(it * 2 * Math.PI * 220 / 16000) * 100).toInt().toShort())
        }
        file.writeBytes(b.array())
        return FileProvider.getUriForFile(context, "${context.packageName}.testfiles", file)
    }

    @org.junit.Before
    fun isolatedOnly() {
        org.junit.Assume.assumeTrue(context.packageName.endsWith(".verification"))
    }

    @Test
    fun importPlaybackBackgroundTrackingBookmarkAndBackupRoundTrip() = runBlocking {
        val importer = graph.importer()
        val db = graph.database()
        val dao = db.dao()
        val originalPreferences = PreferenceStore(context).flow.first()
        val originalAchievements =
            if (
                dao.observeSegments().first().isEmpty() &&
                    dao.observeCompletions().first().isEmpty()
            )
                emptySet()
            else dao.observeAchievements().first().map { it.key }.toSet()
        val uris =
            listOf(fixture("Auralis-test-chapter10.wav"), fixture("Auralis-test-chapter2.wav"))
        val review = importer.scan(uris, false)
        assertTrue(review.errors.toString(), review.errors.isEmpty())
        assertEquals(2, review.groups.single().files.size)
        assertTrue(review.groups.single().files.first().name.contains("chapter2"))
        importer.commit(review.groups, false)
        val book =
            dao.observeLibrary().first().first { b ->
                b.files.any { it.uri == uris.first().toString() }
            }
        assertEquals(2, book.chapters.size)
        assertTrue(book.durationMs >= 49000)
        assertEquals(2, importer.scan(uris, false).duplicates)
        var controller: MediaController? = null
        try {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
            instrumentation.runOnMainSync {
                future = MediaController.Builder(context, token).buildAsync()
            }
            controller = future.get(15, TimeUnit.SECONDS)
            val c = controller
            lateinit var load: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            instrumentation.runOnMainSync {
                load =
                    c.sendCustomCommand(
                        SessionCommand("load", Bundle.EMPTY),
                        Bundle().apply {
                            putString("id", book.book.id)
                            putBoolean("play", true)
                        },
                    )
            }
            assertEquals(SessionResult.RESULT_SUCCESS, load.get(15, TimeUnit.SECONDS).resultCode)
            delay(3500)
            compose.onNodeWithContentDescription("Open player").performClick()
            compose
                .onNodeWithContentDescription("Pause", useUnmergedTree = true)
                .assertIsDisplayed()
            compose.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            var progressed = 0L
            instrumentation.runOnMainSync {
                progressed = c.currentPosition
                c.setPlaybackSpeed(2f)
            }
            assertTrue("Audio must progress", progressed > 500)
            for (icon in app.auralis.branding.AuralisIcon.entries) {
                graph.icons().select(icon)
                instrumentation.runOnMainSync {
                    assertTrue("Icon switching must preserve background playback", c.isPlaying)
                }
            }
            delay(1800)
            instrumentation.runOnMainSync { c.pause() }
            delay(1200)
            val rows = dao.observeSegments().first().filter { it.bookId == book.book.id }
            assertTrue("Real listening records were saved", rows.sumOf { it.elapsedMs } > 2500)
            assertTrue(rows.any { it.speed >= 2f })
            val before = rows.sumOf { it.elapsedMs }
            delay(1200)
            assertEquals(
                before,
                dao.observeSegments()
                    .first()
                    .filter { it.bookId == book.book.id }
                    .sumOf { it.elapsedMs },
            )
            instrumentation.runOnMainSync {
                c.seekTo(0, 24000)
                c.setPlaybackSpeed(3f)
                c.sendCustomCommand(
                    SessionCommand("sleep", Bundle.EMPTY),
                    Bundle().apply { putInt("minutes", -1) },
                )
                c.play()
            }
            delay(1800)
            instrumentation.runOnMainSync {
                assertFalse("Chapter-end sleep timer pauses playback", c.isPlaying)
                assertEquals(1, c.currentMediaItemIndex)
            }
            instrumentation.runOnMainSync {
                c.seekTo(1, 2000)
                c.sendCustomCommand(
                    SessionCommand("sleep", Bundle.EMPTY),
                    Bundle().apply { putInt("minutes", 1) },
                )
            }
            delay(500)
            dao.bookmark(
                BookmarkEntity(
                    "test-bookmark",
                    book.book.id,
                    1,
                    2000,
                    title = "Integration bookmark",
                    note = "Offline note",
                )
            )
            val backupFile =
                File(context.filesDir, "test-fixtures/backup.json").apply { writeText("") }
            val backupUri =
                FileProvider.getUriForFile(context, "${context.packageName}.testfiles", backupFile)
            val savedElapsed =
                dao.observeSegments()
                    .first()
                    .filter { it.bookId == book.book.id }
                    .sumOf { it.elapsedMs }
            graph.backup().export(backupUri)
            graph.backup().restore(backupUri)
            graph.backup().restore(backupUri)
            assertEquals(
                savedElapsed,
                dao.observeSegments()
                    .first()
                    .filter { it.bookId == book.book.id }
                    .sumOf { it.elapsedMs },
            )
            assertEquals(1, dao.observeBookmarks().first().count { it.id == "test-bookmark" })
            assertEquals(1, dao.book(book.book.id)!!.progress!!.chapterIndex)
        } finally {
            instrumentation.runOnMainSync {
                controller?.stop()
                controller?.release()
            }
            delay(500)
            graph
                .icons()
                .select(app.auralis.branding.AuralisIcon.fromKey(originalPreferences.appIcon))
            PreferenceStore(context)
                .update(
                    originalPreferences.copy(
                        lastBook =
                            originalPreferences.lastBook.takeUnless { it == book.book.id }.orEmpty()
                    )
                )
            dao.deleteBook(book.book.id)
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM segments WHERE bookId=?",
                arrayOf(book.book.id),
            )
            db.openHelper.writableDatabase.execSQL(
                "DELETE FROM sessions WHERE bookId=?",
                arrayOf(book.book.id),
            )
            dao.observeAchievements()
                .first()
                .filter { it.key !in originalAchievements }
                .forEach {
                    db.openHelper.writableDatabase.execSQL(
                        "DELETE FROM achievements WHERE `key`=?",
                        arrayOf(it.key),
                    )
                }
        }
    }
}
