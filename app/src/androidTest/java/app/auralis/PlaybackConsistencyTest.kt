@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.session.*
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import app.auralis.playback.PlaybackService
import app.auralis.ui.AuralisViewModel
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

class PlaybackConsistencyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context
        get() = instrumentation.targetContext

    @Before
    fun isolatedOnly() {
        Assume.assumeTrue(context.packageName.endsWith(".verification"))
    }

    @Test
    fun rapidBookSwitchKeepsLatestSeekAndEverySurfaceSharesTheLiveChapter() = runBlocking {
        val dao =
            EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java)
                .database()
                .dao()
        val id = "consistency-${UUID.randomUUID()}"
        val books = listOf("$id-a", "$id-b")
        val files = books.map { bookId ->
            File(context.cacheDir, "$bookId.wav").apply {
                val samples = 8000 * 10
                writeBytes(
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
            }
        }
        books.forEachIndexed { i, b ->
            dao.books(listOf(BookEntity(b, "Consistency Book ${i+1}")))
            dao.files(
                listOf(
                    AudioFileEntity("$b-f", b, files[i].toURI().toString(), files[i].name, 10000)
                )
            )
            dao.chapters(
                listOf(
                    ChapterEntity("$b-1", b, "$b-f", "Chapter I", 0, 0, 5000),
                    ChapterEntity("$b-2", b, "$b-f", "Chapter II", 1, 5000, 10000),
                )
            )
        }
        lateinit var c: MediaController
        lateinit var vm: AuralisViewModel
        lateinit var future: com.google.common.util.concurrent.ListenableFuture<MediaController>
        instrumentation.runOnMainSync {
            vm = ViewModelProvider(compose.activity)[AuralisViewModel::class.java]
            future =
                MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, PlaybackService::class.java)),
                    )
                    .buildAsync()
        }
        c = future.get(15, TimeUnit.SECONDS)
        fun command(
            action: String,
            bookId: String,
        ): com.google.common.util.concurrent.ListenableFuture<SessionResult> {
            lateinit var result: com.google.common.util.concurrent.ListenableFuture<SessionResult>
            instrumentation.runOnMainSync {
                result =
                    c.sendCustomCommand(
                        SessionCommand(action, Bundle.EMPTY),
                        Bundle().apply {
                            putString("id", bookId)
                            putBoolean("play", false)
                        },
                    )
            }
            return result
        }
        try {
            assertEquals(
                SessionResult.RESULT_SUCCESS,
                command("load", books[0]).get(15, TimeUnit.SECONDS).resultCode,
            )
            instrumentation.runOnMainSync { c.seekTo(1, 2000) }
            val second = command("load", books[1])
            val first = command("load", books[0])
            assertEquals(SessionResult.RESULT_SUCCESS, second.get(15, TimeUnit.SECONDS).resultCode)
            assertEquals(SessionResult.RESULT_SUCCESS, first.get(15, TimeUnit.SECONDS).resultCode)
            compose.waitUntil(10000) {
                vm.player.value.mediaId == "${books[0]}-2" &&
                    vm.library.value.find { it.book.id == books[0] }?.progress?.chapterIndex == 1
            }
            val live = vm.library.value.first { it.book.id == books[0] }
            assertEquals(.7f, live.playbackFraction, .01f)
            assertEquals(0f, live.coverageFraction, .001f)
            assertEquals(2000L, live.progress!!.positionMs)
            compose.onNodeWithText("2/2 · Chapter II").assertIsDisplayed()
            compose.onNodeWithContentDescription("Open player").performClick()
            compose.onNodeWithText("2 / 2").assertIsDisplayed()
            assertEquals(1, dao.book(books[0])!!.progress!!.chapterIndex)
        } finally {
            books.forEach { command("removeBook", it).get(15, TimeUnit.SECONDS) }
            instrumentation.runOnMainSync { c.release() }
            files.forEach { it.delete() }
        }
    }
}
