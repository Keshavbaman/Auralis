@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.session.*
import androidx.room.withTransaction
import app.auralis.data.*
import app.auralis.domain.chapterCoverageMs
import app.auralis.domain.seriesInfo
import app.auralis.domain.withPlayback
import app.auralis.importer.*
import app.auralis.playback.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

data class PlayerSnapshot(
    val mediaId: String = "",
    val chapterIndex: Int = 0,
    val position: Long = 0,
    val duration: Long = 0,
    val playing: Boolean = false,
    val speed: Float = 1f,
    val timerRemaining: Long = 0,
    val endChapter: Boolean = false,
)

@HiltViewModel
class AuralisViewModel
@Inject
constructor(
    application: Application,
    val db: AuralisDatabase,
    private val preferenceStore: PreferenceStore,
    private val importer: ImportEngine,
    private val backup: BackupManager,
    private val playbackStatus: PlaybackStatus,
    val audiobookCatalog: AudiobookCatalog,
    private val launcherIcons: app.auralis.branding.LauncherIcons,
) : AndroidViewModel(application) {
    val segments =
        db.dao().observeSegments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val persistedLibrary =
        combine(db.dao().observeLibrary(), segments) { books, rows ->
                val byBook = rows.groupBy { it.bookId }
                books.map { b ->
                    b.copy().also { result ->
                        val listened =
                            chapterCoverageMs(b, byBook[b.book.id].orEmpty()).values.sum()
                        result.listenedFraction =
                            if (b.durationMs > 0)
                                (listened.toFloat() / b.durationMs).coerceIn(0f, 1f)
                            else 0f
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val catalogue
        get() = persistedLibrary

    val bookmarks =
        db.dao().observeBookmarks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val achievements =
        db.dao().observeAchievements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val completions =
        db.dao().observeCompletions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val collections =
        db.dao().observeCollections().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memberships =
        db.dao().observeMemberships().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val challenges =
        db.dao().observeChallenges().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val preferences =
        preferenceStore.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences(onlineBookDetails = false))
    val player = MutableStateFlow(PlayerSnapshot())
    val library =
        combine(persistedLibrary, player) { books, snapshot ->
                books.map {
                    it.withPlayback(
                        snapshot.mediaId,
                        snapshot.position,
                        snapshot.speed,
                        System.currentTimeMillis(),
                    )
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val review = MutableStateFlow<ImportReview?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    private var controller: MediaController? = null
    private val future =
        MediaController.Builder(
                application,
                SessionToken(application, ComponentName(application, PlaybackService::class.java)),
            )
            .buildAsync()

    init {
        future.addListener(
            {
                runCatching { future.get() }
                    .onSuccess { c ->
                        controller = c
                        c.addListener(
                            object : androidx.media3.common.Player.Listener {
                                override fun onEvents(
                                    player: androidx.media3.common.Player,
                                    events: androidx.media3.common.Player.Events,
                                ) {
                                    publish(c)
                                }
                            }
                        )
                        viewModelScope.launch {
                            val p = preferenceStore.flow.first()
                            if (c.mediaItemCount == 0 && p.lastBook.isNotBlank())
                                load(p.lastBook, false)
                            while (isActive) {
                                publish(c)
                                delay(400)
                            }
                        }
                    }
                    .onFailure { message.value = "Playback connection failed: ${it.message}" }
            },
            ContextCompat.getMainExecutor(application),
        )
        viewModelScope.launch {
            playbackStatus.error.filterNotNull().collect { message.value = it }
        }
    }

    private fun publish(c: MediaController) {
        val mediaId = c.currentMediaItem?.mediaId.orEmpty()
        val chapter =
            persistedLibrary.value
                .asSequence()
                .flatMap { it.chapters.asSequence() }
                .firstOrNull { it.id == mediaId }
        player.value =
            PlayerSnapshot(
                mediaId,
                chapter?.ordinal ?: c.currentMediaItemIndex.coerceAtLeast(0),
                c.currentPosition.coerceAtLeast(0),
                chapter?.durationMs ?: c.duration.coerceAtLeast(0),
                c.isPlaying,
                c.playbackParameters.speed,
                (playbackStatus.timerEnd.value - SystemClock.elapsedRealtime()).coerceAtLeast(0),
                playbackStatus.chapterTimer.value,
            )
    }

    fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.message ?: "Something went wrong"
            }
        }
    }

    fun import(uris: List<Uri>, tree: Boolean) {
        task {
            busy.value = true
            try {
                review.value = importer.scan(uris, tree)
            } finally {
                busy.value = false
            }
        }
    }

    fun rescan() {
        task {
            busy.value = true
            try {
                review.value = importer.scan(db.dao().sources().map { Uri.parse(it.uri) }, true)
            } finally {
                busy.value = false
            }
        }
    }

    fun commit(groups: List<ImportGroup>, split: Boolean) {
        task {
            busy.value = true
            try {
                val imported = importer.commit(groups, split)
                review.value = null
                message.value = "Added to your library"
                if (imported.isNotEmpty()) enrichImportedBooks(imported)
            } finally {
                busy.value = false
            }
        }
    }

    private fun enrichImportedBooks(ids: List<String>) {
        viewModelScope.launch {
            if (!preferenceStore.flow.first().onlineBookDetails || !audiobookCatalog.online().first()) return@launch
            for (id in ids) {
                if (!preferenceStore.flow.first().onlineBookDetails) break
                try {
                    val book = db.dao().book(id) ?: continue
                    val lookupBook = book.book.withFilenameCredits()
                    val details = audiobookCatalog.lookup(lookupBook, durationMs = book.durationMs) ?: continue
                    acceptCatalogMatch(book, details)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Book details can retry the lookup without affecting the import.
                }
            }
        }
    }

    internal suspend fun acceptCatalogMatch(book: LibraryBook, details: CatalogDetails) {
        if (!details.matchesNarratorDuration(book.durationMs)) return
        val previous = book.book
        val inferred = previous.withFilenameCredits()
        val narrator = previous.narrator.ifBlank { details.narrator.trim() }
        if (inferred == previous && narrator == previous.narrator) return
        if (!preferenceStore.flow.first().onlineBookDetails) return
        db.dao().enrichBookCredits(
            id = previous.id,
            oldTitle = previous.title,
            oldAuthor = previous.author,
            oldNarrator = previous.narrator,
            oldEdition = previous.edition,
            title = inferred.title,
            author = inferred.author,
            narrator = narrator,
        )
    }

    fun load(id: String, play: Boolean = true) {
        controller?.sendCustomCommand(
            SessionCommand("load", Bundle.EMPTY),
            Bundle().apply {
                putString("id", id)
                putBoolean("play", play)
            },
        )
    }

    fun loadAt(id: String, index: Int, position: Long = 0) {
        val c = controller ?: return
        val result =
            c.sendCustomCommand(
                SessionCommand("load", Bundle.EMPTY),
                Bundle().apply {
                    putString("id", id)
                    putBoolean("play", false)
                },
            )
        result.addListener(
            {
                runCatching { result.get() }
                    .onSuccess {
                        if (it.resultCode == SessionResult.RESULT_SUCCESS) {
                            c.seekTo(index, position)
                            c.play()
                        }
                    }
            },
            ContextCompat.getMainExecutor(getApplication()),
        )
    }

    fun toggle() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun pause() {
        controller?.pause()
    }

    fun skip(forward: Boolean) {
        controller?.let { c ->
            val b =
                library.value.firstOrNull { b ->
                    b.chapters.any { it.id == c.currentMediaItem?.mediaId }
                } ?: return
            val at =
                b.orderedChapters.take(c.currentMediaItemIndex).sumOf { it.durationMs } +
                    c.currentPosition
            seekBook(
                b,
                (at + (if (forward) 1 else -1) * preferences.value.skipSeconds * 1000L).coerceIn(
                    0,
                    (b.durationMs - 1).coerceAtLeast(0),
                ),
            )
        }
    }

    fun chapter(index: Int, position: Long = 0) {
        controller?.let { if (index in 0 until it.mediaItemCount) it.seekTo(index, position) }
    }

    fun seek(position: Long) {
        controller?.seekTo(position)
    }

    fun seekBook(book: LibraryBook, position: Long) {
        var left = position
        for ((i, ch) in book.orderedChapters.withIndex()) {
            if (left < ch.durationMs || i == book.chapters.lastIndex) {
                chapter(i, left.coerceIn(0, ch.durationMs))
                return
            }
            left -= ch.durationMs
        }
    }

    fun speed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        settings(preferences.value.copy(speed = speed))
    }

    fun extendSleep() {
        controller?.sendCustomCommand(
            SessionCommand("sleep", Bundle.EMPTY),
            Bundle().apply {
                putInt("minutes", 5)
                putBoolean("extend", true)
            },
        )
    }

    fun sleep(minutes: Int) {
        controller?.sendCustomCommand(
            SessionCommand("sleep", Bundle.EMPTY),
            Bundle().apply { putInt("minutes", minutes) },
        )
    }

    val changingIcon = MutableStateFlow(false)

    fun changeIcon(icon: app.auralis.branding.AuralisIcon) {
        task {
            changingIcon.value = true
            try {
                launcherIcons.select(icon)
                message.value = "App icon updated. Your home screen may take a moment to refresh."
            } finally {
                changingIcon.value = false
            }
        }
    }

    fun settings(p: Preferences) {
        task { preferenceStore.update(p) }
    }

    fun favourite(book: BookEntity) {
        task { db.dao().books(listOf(book.copy(favourite = !book.favourite))) }
    }

    fun edit(book: BookEntity) {
        task { db.dao().books(listOf(book)) }
    }

    fun attachCompanionPdf(bookId: String, uri: Uri?) {
        task {
            db.dao().setCompanionPdf(bookId, uri?.toString())
            message.value = if (uri == null) "PDF unlinked." else "PDF linked to this audiobook."
        }
    }

    fun saveCompanionPdfPage(bookId: String, page: Int) {
        task { db.dao().setCompanionPdfPage(bookId, page.coerceAtLeast(0)) }
    }

    fun saveCatalogNarrator(book: BookEntity, narrator: String) {
        task {
            val credits = narrator.trim()
            if (credits.isBlank()) return@task
            val updated = db.dao().setNarrator(book.id, book.narrator, credits)
            message.value = if (updated > 0) "Narrator saved to your library."
            else "The book changed. Open it again before saving credits."
        }
    }

    fun saveSeries(
        originalName: String?,
        name: String,
        bookIds: Set<String>,
        finished: (Boolean) -> Unit,
    ) {
        task {
            var saved = false
            try {
                val trimmed = name.trim()
                require(trimmed.isNotEmpty()) { "Enter a series name." }
                require(originalName != null || bookIds.isNotEmpty()) { "Choose at least one book." }
                val dao = db.dao()
                db.withTransaction {
                    val books = dao.bookMetadata()
                    val seriesById = books.associate { it.id to seriesInfo(it) }
                    require(seriesById.keys.containsAll(bookIds)) { "A selected book is no longer in the library." }
                    val current = seriesById.filterValues { it != null && it.name == originalName }
                    require(originalName == null || current.isNotEmpty()) { "This series has changed. Reopen it to edit." }
                    require(seriesById.values.none { it != null && it.name != originalName && it.name.equals(trimmed, ignoreCase = true) }) {
                        "That series name already exists."
                    }
                    for (id in current.keys - bookIds) dao.setSeries(id, "", null, true)
                    for (id in bookIds) dao.setSeries(id, trimmed, current[id]?.number, false)
                }
                saved = true
            } finally {
                finished(saved)
            }
        }
    }

    fun removeBook(book: LibraryBook, onRemoved: () -> Unit, onFailure: () -> Unit) {
        val c = controller
        if (c == null) {
            message.value = "Player is connecting. Please try again."
            onFailure()
            return
        }
        val result =
            c.sendCustomCommand(
                SessionCommand("removeBook", Bundle.EMPTY),
                Bundle().apply { putString("id", book.book.id) },
            )
        result.addListener(
            {
                runCatching { result.get() }
                    .onSuccess {
                        if (it.resultCode == SessionResult.RESULT_SUCCESS) {
                            CaptionStore(getApplication()).delete(book.book.id)
                            message.value = "Removed from library. Audio files kept."
                            onRemoved()
                        } else {
                            message.value = "Could not remove this book. Please try again."
                            onFailure()
                        }
                    }
                    .onFailure {
                        message.value = "Could not remove this book: ${it.message}"
                        onFailure()
                    }
            },
            ContextCompat.getMainExecutor(getApplication()),
        )
    }

    /** Loads the book's imported captions off the main thread; null when none are imported. */
    suspend fun captions(bookId: String): app.auralis.domain.CaptionTrack? =
        withContext(Dispatchers.IO) {
            val store = CaptionStore(getApplication())
            // A transcript saved beside the audio (made on a computer) is picked up by itself.
            library.value.firstOrNull { it.book.id == bookId }?.let { book ->
                if (runCatching { store.syncSidecars(book) }.getOrDefault(false))
                    message.value = "Transcript found next to the audio and loaded."
            }
            store.load(bookId)
        }

    /** Copies a user-chosen caption file into private storage, then calls [done] on success. */
    fun importCaptions(book: LibraryBook, uri: Uri, done: () -> Unit) {
        task {
            val lines =
                withContext(Dispatchers.IO) {
                    CaptionStore(getApplication()).import(book.book.id, uri)
                }
            message.value = "Imported $lines caption lines."
            done()
        }
    }

    /** Bumped when the Library tab is tapped, asking Library to show its main shelf. */
    val libraryRoot = MutableStateFlow(0)

    fun requestLibraryRoot() {
        libraryRoot.value += 1
    }

    /** Listener-given names for detected voices in [bookId]'s transcript. */
    suspend fun voiceNames(bookId: String): Map<String, String> =
        withContext(Dispatchers.IO) { CaptionStore(getApplication()).voiceNames(bookId) }

    /** Names [voice] (e.g. "Voice 3") for [bookId]; a blank name clears it. */
    fun renameVoice(bookId: String, voice: String, name: String, done: () -> Unit) {
        task {
            withContext(Dispatchers.IO) { CaptionStore(getApplication()).renameVoice(bookId, voice, name) }
            done()
        }
    }

    /**
     * Starts on-device transcription for [book]. A run already in progress for this book is kept;
     * any other book's run is stopped first, because two at once only slow each other down.
     */
    fun startTranscription(book: LibraryBook) {
        val bookTag = "book:${book.book.id}"
        val manager = androidx.work.WorkManager.getInstance(getApplication())
        task {
            val running =
                withContext(Dispatchers.IO) {
                    manager
                        .getWorkInfosByTag(app.auralis.transcription.TranscriptionWorker::class.java.name)
                        .get()
                }
            running
                .filter { !it.state.isFinished && bookTag !in it.tags }
                .forEach { manager.cancelWorkById(it.id) }
            val request =
                androidx.work.OneTimeWorkRequestBuilder<app.auralis.transcription.TranscriptionWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            app.auralis.transcription.TranscriptionWorker.KEY_BOOK_ID to book.book.id
                        )
                    )
                    .addTag(bookTag)
                    .setConstraints(
                        androidx.work.Constraints.Builder().setRequiresStorageNotLow(true).build()
                    )
                    .build()
            manager.enqueueUniqueWork(
                app.auralis.transcription.TranscriptionWorker.uniqueName(book.book.id),
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun cancelTranscription(bookId: String) {
        androidx.work.WorkManager.getInstance(getApplication())
            .cancelUniqueWork(app.auralis.transcription.TranscriptionWorker.uniqueName(bookId))
    }

    /** Latest transcription job for [bookId], or null when none has run. */
    fun transcriptionWork(bookId: String): Flow<androidx.work.WorkInfo?> = callbackFlow {
        val live =
            androidx.work.WorkManager.getInstance(getApplication())
                .getWorkInfosForUniqueWorkLiveData(
                    app.auralis.transcription.TranscriptionWorker.uniqueName(bookId)
                )
        val observer =
            androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { infos ->
                trySend(infos.lastOrNull())
            }
        live.observeForever(observer)
        awaitClose { live.removeObserver(observer) }
    }

    fun reset(book: BookEntity) {
        task {
            if (
                controller?.currentMediaItem?.mediaId in
                    library.value
                        .firstOrNull { it.book.id == book.id }
                        ?.chapters
                        .orEmpty()
                        .map { it.id }
            ) {
                pause()
                chapter(0)
            }
            db.dao().resetProgress(book.id)
            db.dao()
                .books(
                    listOf(
                        book.copy(
                            manuallyCompleted = false,
                            progressResetAt = System.currentTimeMillis(),
                        )
                    )
                )
        }
    }

    fun mark(book: BookEntity, complete: Boolean) {
        task {
            db.dao()
                .books(
                    listOf(
                        book.copy(
                            manuallyCompleted = complete,
                            progressResetAt =
                                if (complete) book.progressResetAt else System.currentTimeMillis(),
                        )
                    )
                )
            if (complete) db.dao().complete(CompletionEntity(book.id, System.currentTimeMillis()))
        }
    }

    fun bookmarkPosition(): Pair<Int, Long> =
        controller?.let {
            it.currentMediaItemIndex.coerceAtLeast(0) to it.currentPosition.coerceAtLeast(0)
        } ?: (player.value.chapterIndex to player.value.position)

    fun bookmark(
        book: LibraryBook,
        title: String,
        note: String,
        chapterIndex: Int,
        positionMs: Long,
    ) {
        task {
            db.dao()
                .bookmark(
                    BookmarkEntity(
                        UUID.randomUUID().toString(),
                        book.book.id,
                        chapterIndex,
                        positionMs,
                        title = title.ifBlank { "Bookmark" },
                        note = note,
                    )
                )
            message.value = "Bookmark saved"
        }
    }

    fun updateBookmark(b: BookmarkEntity) {
        task { db.dao().bookmark(b) }
    }

    fun deleteBookmark(id: String) {
        task { db.dao().deleteBookmark(id) }
    }

    fun challenge(title: String, metric: String, target: Long, annual: Boolean) {
        task {
            val today = java.time.LocalDate.now()
            val start = if (annual) today.withDayOfYear(1) else today.withDayOfMonth(1)
            val end =
                if (annual) start.plusYears(1).minusDays(1) else start.plusMonths(1).minusDays(1)
            db.dao()
                .challenge(
                    ChallengeEntity(
                        UUID.randomUUID().toString(),
                        title,
                        metric,
                        target,
                        start.toString(),
                        end.toString(),
                    )
                )
        }
    }

    fun addCollection(name: String, bookId: String?) {
        task {
            val id = UUID.randomUUID().toString()
            db.dao().collection(CollectionEntity(id, name))
            if (bookId != null) db.dao().membership(CollectionBookCrossRef(id, bookId))
        }
    }

    fun removeFromCollection(collectionId: String, bookId: String) {
        task { db.dao().removeMembership(collectionId, bookId) }
    }

    fun collect(collectionId: String, bookId: String) {
        task {
            db.dao().membership(CollectionBookCrossRef(collectionId, bookId))
            message.value = "Added to collection"
        }
    }

    fun export(uri: Uri) {
        task {
            backup.export(uri)
            message.value =
                "Backup exported. Audio and cover images remain in their original locations."
        }
    }

    fun restore(uri: Uri) {
        task {
            pause()
            backup.restore(uri)
            message.value = "Backup restored. Relink unavailable audio from book details."
        }
    }

    fun relink(book: LibraryBook, uris: List<Uri>) {
        task {
            val documents = uris.mapNotNull {
                androidx.documentfile.provider.DocumentFile.fromSingleUri(getApplication(), it)
            }
            var count = 0
            for (file in book.files) {
                val match =
                    documents.firstOrNull { it.name == file.name }
                        ?: if (book.files.size == 1 && documents.size == 1) documents.first()
                        else null
                if (match != null) {
                    db.dao().files(listOf(file.copy(uri = match.uri.toString())))
                    count++
                }
            }
            message.value = "Relinked $count of ${book.files.size} files. Reload the book to apply."
        }
    }

    override fun onCleared() {
        MediaController.releaseFuture(future)
        super.onCleared()
    }
}
