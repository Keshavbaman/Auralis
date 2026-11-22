@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.room.withTransaction
import app.auralis.MainActivity
import app.auralis.cast.*
import app.auralis.data.*
import app.auralis.domain.*
import com.google.android.gms.cast.framework.CastContext
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PlaybackStatus @Inject constructor() {
    val serviceAlive = MutableStateFlow(false)
    val timerEnd = MutableStateFlow(0L)
    val chapterTimer = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
}

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var db: AuralisDatabase
    @Inject lateinit var preferences: PreferenceStore
    @Inject lateinit var status: PlaybackStatus
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private lateinit var player: Player
    private lateinit var castServer: LocalCastServer
    private var castPlayer: CastAudioPlayer? = null
    private val castExecutor = Executors.newSingleThreadExecutor()
    private var session: MediaSession? = null
    private var active: LibraryBook? = null
    private var prefs = Preferences()
    private val tracker = ListeningTracker()
    private val libraryOperations = Mutex()
    private val pending = mutableListOf<ListeningSegmentEntity>()
    private var sessionStart = System.currentTimeMillis()
    private var ticks = 0
    private var pausedAt = 0L

    override fun onCreate() {
        super.onCreate()
        status.serviceAlive.value = true
        val localPlayer =
            ExoPlayer.Builder(this)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                )
                .setSeekBackIncrementMs(30000)
                .setSeekForwardIncrementMs(30000)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        player = localPlayer
        castServer = LocalCastServer(this)
        val callbacks =
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (
                        !playWhenReady &&
                            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
                    )
                        pausedAt = SystemClock.elapsedRealtime()
                    if (playWhenReady && pausedAt > 0) {
                        val rewind =
                            smartRewindMs(
                                SystemClock.elapsedRealtime() - pausedAt,
                                prefs.smartRewind,
                            )
                        pausedAt = 0
                        if (rewind > 0) rewindBy(rewind)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    sample()
                    checkpoint()
                    if (!isPlaying) {
                        tracker.reset()
                    }
                }

                override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                    sample()
                    tracker.reset()
                    checkpoint()
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    sample(oldPosition.mediaItemIndex, oldPosition.positionMs)
                    tracker.reset()
                    if (
                        status.chapterTimer.value &&
                            reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
                    ) {
                        player.pause()
                        clearTimer()
                    }
                    checkpoint()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        sample()
                        checkpoint()
                        if (status.chapterTimer.value) clearTimer()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    status.error.value =
                        "Audio is unavailable or unsupported. Reselect the file or folder in Library. ${error.errorCodeName}"
                    sample()
                    checkpoint()
                }
            }
        player.addListener(callbacks)
        val launch =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        session =
            MediaSession.Builder(this, player)
                .setSessionActivity(launch)
                .setCallback(
                    object : MediaSession.Callback {
                        override fun onConnect(
                            s: MediaSession,
                            c: MediaSession.ControllerInfo,
                        ): MediaSession.ConnectionResult {
                            val commands =
                                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                                    .add(SessionCommand("load", Bundle.EMPTY))
                                    .add(SessionCommand("sleep", Bundle.EMPTY))
                                    .apply {
                                        if (c.uid == android.os.Process.myUid())
                                            add(SessionCommand("removeBook", Bundle.EMPTY))
                                    }
                                    .build()
                            return MediaSession.ConnectionResult.AcceptedResultBuilder()
                                .setAvailablePlayerCommands(
                                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                                )
                                .setAvailableSessionCommands(commands)
                                .build()
                        }

                        override fun onCustomCommand(
                            s: MediaSession,
                            c: MediaSession.ControllerInfo,
                            command: SessionCommand,
                            args: Bundle,
                        ): ListenableFuture<SessionResult> {
                            if (command.customAction == "removeBook") {
                                if (c.uid != android.os.Process.myUid())
                                    return Futures.immediateFuture(
                                        SessionResult(SessionError.ERROR_PERMISSION_DENIED)
                                    )
                                val result = SettableFuture.create<SessionResult>()
                                scope.launch {
                                    runCatching {
                                        libraryOperations.withLock {
                                            removeBook(args.getString("id").orEmpty())
                                        }
                                    }
                                        .onSuccess {
                                            result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                                        }
                                        .onFailure {
                                            status.error.value =
                                                "Could not remove book: ${it.message}"
                                            result.set(SessionResult(SessionError.ERROR_IO))
                                        }
                                }
                                return result
                            }
                            if (command.customAction == "sleep") {
                                val minutes = args.getInt("minutes")
                                status.chapterTimer.value = minutes == -1
                                status.timerEnd.value =
                                    if (minutes > 0)
                                        (if (args.getBoolean("extend"))
                                            maxOf(
                                                SystemClock.elapsedRealtime(),
                                                status.timerEnd.value,
                                            )
                                        else SystemClock.elapsedRealtime()) + minutes * 60000L
                                    else 0
                                player.volume = 1f
                                return Futures.immediateFuture(
                                    SessionResult(SessionResult.RESULT_SUCCESS)
                                )
                            }
                            if (command.customAction == "load") {
                                val result = SettableFuture.create<SessionResult>()
                                scope.launch {
                                    runCatching {
                                        libraryOperations.withLock {
                                            load(
                                                args.getString("id").orEmpty(),
                                                args.getBoolean("play", true),
                                            )
                                        }
                                    }
                                        .onSuccess {
                                            result.set(SessionResult(SessionResult.RESULT_SUCCESS))
                                        }
                                        .onFailure {
                                            status.error.value = it.message
                                            result.set(SessionResult(SessionError.ERROR_BAD_VALUE))
                                        }
                                }
                                return result
                            }
                            return Futures.immediateFuture(
                                SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                            )
                        }
                    }
                )
                .build()
        scope.launch(Dispatchers.IO) {
            for (write in writes) runCatching { write() }
                .onFailure { status.error.value = "Could not save playback: ${it.message}" }
        }
        scope.launch { preferences.flow.collect { prefs = it } }
        scope.launch {
            combine(
                    db.dao().observeSegments(),
                    db.dao().observeCompletions(),
                    db.dao().observeLibrary(),
                    preferences.flow,
                ) { rows, completed, books, p ->
                    eligibleBadges(
                        rows,
                        completed.size,
                        p.streakMinutes,
                        p.goalMinutes,
                        books
                            .filter { b -> rows.any { it.bookId == b.book.id } }
                            .map { it.book.genre }
                            .toSet(),
                    )
                }
                .distinctUntilChanged()
                .collect { keys ->
                    for (key in keys) db.dao()
                        .unlock(UserAchievementEntity(key, System.currentTimeMillis()))
                }
        }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (player.isPlaying) {
                    sample()
                    ticks++
                    if (ticks % 10 == 0) checkpoint()
                }
                castPlayer?.rememberRemoteState()
                castServer.power(
                    castPlayer?.isRemote == true &&
                        player.playWhenReady &&
                        player.playbackState != Player.STATE_IDLE
                )
                val end = status.timerEnd.value
                if (end > 0) {
                    val left = end - SystemClock.elapsedRealtime()
                    if (left <= 0) {
                        castPlayer?.cancelPendingResume()
                        player.pause()
                        clearTimer()
                    } else if (prefs.fade && left < 10000) player.volume = left / 10000f
                }
            }
        }
        scope.launch {
            CastActivation.requested.first { it }
            CastContext.getSharedInstance(this@PlaybackService, castExecutor)
                .addOnSuccessListener { castContext ->
                    if (!status.serviceAlive.value || session == null) return@addOnSuccessListener
                    runCatching {
                        val remote =
                            RemoteCastPlayer.Builder(this@PlaybackService)
                                .setMediaItemConverter(LocalMediaConverter(castServer))
                                .setSeekBackIncrementMs(30000)
                                .setSeekForwardIncrementMs(30000)
                                .build()
                        val bridge =
                            CastAudioPlayer(
                                localPlayer,
                                remote,
                                castServer,
                                prepareMedia = {
                                    active?.let { book ->
                                        withContext(Dispatchers.IO) { castServer.register(book) }
                                    }
                                },
                                changed = {
                                    pausedAt = 0
                                    status.error.value = null
                                    tracker.reset()
                                    checkpoint()
                                },
                                error = { failure ->
                                    android.util.Log.w("AuralisCast", "Transfer failed", failure)
                                    status.error.value =
                                        "Could not cast: ${failure.message}. Phone and speaker must share Wi-Fi."
                                    castContext.sessionManager.endCurrentSession(true)
                                },
                            )
                        localPlayer.removeListener(callbacks)
                        bridge.addListener(callbacks)
                        castPlayer = bridge
                        player = bridge
                        session?.setPlayer(bridge)
                    }
                        .onFailure {
                            /* Cast is optional; keep the local ExoPlayer when unavailable. */
                        }
                }
                .addOnFailureListener { /* Missing Play services never blocks offline playback. */ }
        }
    }

    /**
     * Drain existing checkpoints before deletion, so an active book cannot be recreated by a late
     * save.
     */
    private suspend fun removeBook(id: String) {
        require(id.isNotBlank()) { "No book selected" }
        if (active?.book?.id == id) {
            castPlayer?.prepareBookLoad()
            castServer.stop()
            sample()
            checkpoint()
            player.pause()
            active = null
            tracker.newSession()
            clearTimer()
            player.stop()
            player.clearMediaItems()
        }
        val deleted = CompletableDeferred<Result<Unit>>()
        writes.send {
            deleted.complete(
                runCatching {
                    val current = preferences.flow.first()
                    if (current.lastBook == id) preferences.setLastBook("")
                    db.dao().deleteBook(id)
                }
            )
        }
        deleted.await().getOrThrow()
    }

    private suspend fun load(id: String, play: Boolean) {
        // A rapid A → B → A switch must read A's latest queued checkpoint.
        sample()
        checkpoint()
        val drained = CompletableDeferred<Unit>()
        writes.send { drained.complete(Unit) }
        drained.await()
        val book = db.dao().book(id) ?: error("Book no longer exists")
        if (
            active?.book?.id == id &&
                active?.files == book.files &&
                active?.chapters == book.chapters &&
                active?.book?.progressResetAt == book.book.progressResetAt &&
                player.mediaItemCount > 0
        ) {
            if (play) player.play()
            return
        }
        sample()
        checkpoint()
        castPlayer?.prepareBookLoad()
        player.pause()
        tracker.newSession()
        sessionStart = System.currentTimeMillis()
        require(book.orderedChapters.isNotEmpty()) { "No playable chapters" }
        active = book
        status.error.value = null
        clearTimer()
        if (castPlayer?.isRemote == true) withContext(Dispatchers.IO) { castServer.register(book) }
        val items =
            book.orderedChapters.map { chapter ->
                val file = book.files.first { it.id == chapter.fileId }
                MediaItem.Builder()
                    .setMediaId(chapter.id)
                    .setUri(file.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(chapter.startMs)
                            .setEndPositionMs(chapter.endMs)
                            .build()
                    )
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(book.book.title)
                            .setArtist(book.book.author)
                            .setSubtitle(chapter.title)
                            .setArtworkUri(
                                book.book.artwork?.let { Uri.fromFile(java.io.File(it)) }
                            )
                            .setIsPlayable(true)
                            .build()
                    )
                    .build()
            }
        val progress = book.progress
        player.setMediaItems(
            items,
            (progress?.chapterIndex ?: 0).coerceIn(items.indices),
            (progress?.positionMs ?: 0).coerceAtLeast(0),
        )
        player.setPlaybackSpeed(progress?.speed ?: prefs.speed)
        player.prepare()
        pausedAt = 0
        if (play && progress != null)
            rewindBy(
                smartRewindMs(System.currentTimeMillis() - progress.lastPlayed, prefs.smartRewind)
            )
        player.playWhenReady = play
        preferences.setLastBook(id)
        castPlayer?.mediaLoaded()
    }

    private fun rewindBy(ms: Long) {
        val chapters = active?.orderedChapters ?: return
        var index = player.currentMediaItemIndex.coerceAtLeast(0)
        var position = player.currentPosition - ms
        while (position < 0 && index > 0) {
            index--
            position += chapters[index].durationMs
        }
        tracker.reset()
        player.seekTo(index, position.coerceAtLeast(0))
    }

    private fun sample(
        index: Int = player.currentMediaItemIndex,
        position: Long = player.currentPosition,
    ) {
        val book = active ?: return
        val chapter = book.orderedChapters.getOrNull(index) ?: return
        val sampled =
            tracker.sample(
                SystemClock.elapsedRealtime(),
                System.currentTimeMillis(),
                position.coerceAtLeast(0),
                book.book.id,
                chapter.id,
                player.playbackParameters.speed,
                player.isPlaying,
            )
        for (row in sampled) {
            val last = pending.lastOrNull()
            if (
                last != null &&
                    last.bookId == row.bookId &&
                    last.chapterId == row.chapterId &&
                    last.date == row.date &&
                    last.zoneId == row.zoneId &&
                    last.speed == row.speed &&
                    last.toMs == row.fromMs &&
                    row.startedAt - last.endedAt in 0..30
            ) {
                pending[pending.lastIndex] =
                    last.copy(
                        endedAt = row.endedAt,
                        elapsedMs = last.elapsedMs + row.elapsedMs,
                        contentMs = last.contentMs + row.contentMs,
                        toMs = row.toMs,
                    )
            } else pending += row
        }
    }

    private fun checkpoint() {
        val book = active ?: return
        val progress =
            PlaybackProgressEntity(
                book.book.id,
                player.currentMediaItemIndex.coerceAtLeast(0),
                player.currentPosition.coerceAtLeast(0),
                System.currentTimeMillis(),
                player.playbackParameters.speed,
            )
        val rows = pending.toList()
        pending.clear()
        val start = sessionStart
        val sessionId = tracker.sessionId
        writes.trySend {
            db.withTransaction {
                db.dao().progress(progress)
                if (rows.isNotEmpty()) {
                    db.dao()
                        .session(
                            ListeningSessionEntity(
                                sessionId,
                                book.book.id,
                                start,
                                rows.last().endedAt,
                            )
                        )
                    rows.forEach { db.dao().segment(it) }
                }
            }
            val resetAt =
                db.dao().book(book.book.id)?.book?.progressResetAt ?: book.book.progressResetAt
            val all = db.dao().bookSegments(book.book.id, resetAt)
            val currentBook = db.dao().book(book.book.id) ?: return@trySend
            val listened = chapterCoverageMs(currentBook, all).values.sum()
            if (book.durationMs > 0 && listened >= book.durationMs * 0.98)
                db.dao().complete(CompletionEntity(book.book.id, System.currentTimeMillis()))
        }
    }

    private fun clearTimer() {
        status.timerEnd.value = 0
        status.chapterTimer.value = false
        player.volume = 1f
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onDestroy() {
        sample()
        checkpoint()
        // Drain the small final checkpoint before releasing the service-owned scope.
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(3000) {
                val done = CompletableDeferred<Unit>()
                writes.send { done.complete(Unit) }
                done.await()
            }
        }
        session?.release()
        player.release()
        castServer.release()
        castExecutor.shutdownNow()
        scope.cancel()
        status.serviceAlive.value = false
        super.onDestroy()
    }
}
