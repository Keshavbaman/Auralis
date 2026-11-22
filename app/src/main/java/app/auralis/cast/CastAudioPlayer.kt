@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.cast

import androidx.media3.cast.*
import androidx.media3.common.*
import androidx.media3.exoplayer.source.ForwardingTimeline
import com.google.android.gms.cast.MediaQueueItem
import com.google.common.util.concurrent.*
import kotlinx.coroutines.*

/**
 * Stable local chapter identities round-trip through Cast, while only LAN URLs reach the receiver.
 */
class LocalMediaConverter(private val server: LocalCastServer) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()
    private val originals = mutableMapOf<String, MediaItem>()

    override fun toMediaQueueItem(item: MediaItem): MediaQueueItem {
        originals[item.mediaId] = item
        val config = requireNotNull(item.localConfiguration)
        val art = item.mediaMetadata.artworkUri?.let { runCatching { server.url(it) }.getOrNull() }
        val remote =
            item
                .buildUpon()
                .setUri(server.url(config.uri))
                .setMimeType(server.mime(config.uri))
                .setMediaMetadata(item.mediaMetadata.buildUpon().setArtworkUri(art).build())
                .build()
        val base = delegate.toMediaQueueItem(remote)
        val clip = item.clippingConfiguration
        val builder = MediaQueueItem.Builder(base).setStartTime(clip.startPositionMs / 1000.0)
        if (clip.endPositionMs != C.TIME_END_OF_SOURCE && clip.endPositionMs != C.TIME_UNSET)
            builder.setPlaybackDuration(
                (clip.endPositionMs - clip.startPositionMs).coerceAtLeast(0) / 1000.0
            )
        return builder.build()
    }

    override fun toMediaItem(item: MediaQueueItem): MediaItem =
        originals[item.media?.contentId] ?: delegate.toMediaItem(item)
}

fun chapterPosition(raw: Long, start: Long, duration: Long): Long =
    (raw - start).coerceIn(0, duration.coerceAtLeast(0))

/**
 * Present clipped file chapters in chapter coordinates even though the receiver seeks in file time.
 */
class ChapterRemotePlayer(private val remote: RemoteCastPlayer) : ForwardingPlayer(remote) {
    private var queue: List<MediaItem> = emptyList()
    private val listenerWrappers = mutableMapOf<Player.Listener, Player.Listener>()

    private fun item(index: Int) = queue.getOrNull(index)

    override fun getMediaItemAt(index: Int): MediaItem =
        queue.getOrNull(index) ?: remote.getMediaItemAt(index)

    override fun getCurrentMediaItem(): MediaItem? =
        remote.currentMediaItem?.let { reported ->
            queue.firstOrNull { it.mediaId == reported.mediaId } ?: item(currentMediaItemIndex) ?: reported
        }

    override fun addListener(listener: Player.Listener) {
        val wrapped =
            object : Player.Listener by listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    listener.onEvents(this@ChapterRemotePlayer, events)
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val original = mediaItem?.let { reported ->
                        queue.firstOrNull { it.mediaId == reported.mediaId } ?: reported
                    }
                    listener.onMediaItemTransition(original, reason)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    fun relative(info: Player.PositionInfo): Player.PositionInfo {
                        val original = info.mediaItem?.let { reported ->
                            queue.firstOrNull { it.mediaId == reported.mediaId }
                        } ?: item(info.mediaItemIndex) ?: info.mediaItem
                        val clip = original?.clippingConfiguration
                        val start = clip?.startPositionMs ?: 0L
                        val length =
                            clip
                                ?.endPositionMs
                                ?.takeIf { it != C.TIME_END_OF_SOURCE && it != C.TIME_UNSET }
                                ?.minus(start) ?: Long.MAX_VALUE
                        return Player.PositionInfo(
                            info.windowUid,
                            info.mediaItemIndex,
                            original,
                            info.periodUid,
                            info.periodIndex,
                            chapterPosition(info.positionMs, start, length),
                            chapterPosition(info.contentPositionMs, start, length),
                            info.adGroupIndex,
                            info.adIndexInAdGroup,
                        )
                    }
                    listener.onPositionDiscontinuity(
                        relative(oldPosition),
                        relative(newPosition),
                        reason,
                    )
                }
            }
        listenerWrappers.put(listener, wrapped)?.let { super.removeListener(it) }
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        listenerWrappers.remove(listener)?.let { super.removeListener(it) }
    }

    private fun start(index: Int) = item(index)?.clippingConfiguration?.startPositionMs ?: 0L

    private fun duration(index: Int): Long {
        val clip = item(index)?.clippingConfiguration ?: return remote.duration
        return if (clip.endPositionMs != C.TIME_END_OF_SOURCE && clip.endPositionMs != C.TIME_UNSET)
            (clip.endPositionMs - clip.startPositionMs).coerceAtLeast(0)
        else (remote.duration - clip.startPositionMs).takeIf { it >= 0 } ?: C.TIME_UNSET
    }

    // Some receivers keep streaming the source file past a queue item's clip boundary.
    // Detect this in file coordinates, before the chapter clock is clamped to its duration.
    private var lastBoundaryAttempt = 0L

    fun advancePastIgnoredClipEnd() {
        if (!remote.isPlaying) return
        val index = remote.currentMediaItemIndex
        val current = item(index) ?: return
        val end = current.clippingConfiguration.endPositionMs
        if (end == C.TIME_END_OF_SOURCE || end == C.TIME_UNSET ||
            remote.currentPosition < end + 750L) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBoundaryAttempt < 3_000L) return
        lastBoundaryAttempt = now
        val next = item(index + 1)
        if (next == null) {
            remote.pause()
        } else {
            val nextStart = next.clippingConfiguration.startPositionMs
            val carry = if (next.localConfiguration?.uri == current.localConfiguration?.uri &&
                nextStart == end) (remote.currentPosition - nextStart).coerceAtLeast(0L) else 0L
            seekTo(index + 1, carry.coerceAtMost(duration(index + 1).takeIf { it > 0 }?.minus(1) ?: 0L))
        }
    }

    private fun absolute(items: List<MediaItem>, index: Int, position: Long): Long =
        (items.getOrNull(index)?.clippingConfiguration?.startPositionMs ?: 0) +
            if (position == C.TIME_UNSET) 0 else position.coerceAtLeast(0)

    override fun getCurrentPosition() =
        chapterPosition(
            remote.currentPosition,
            start(currentMediaItemIndex),
            duration(currentMediaItemIndex).takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE,
        )

    override fun getContentPosition() = currentPosition

    override fun getDuration() = duration(currentMediaItemIndex)

    override fun getContentDuration() = duration

    override fun getBufferedPosition() =
        chapterPosition(
            remote.bufferedPosition,
            start(currentMediaItemIndex),
            duration(currentMediaItemIndex).takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE,
        )

    override fun getContentBufferedPosition() = bufferedPosition

    override fun seekTo(index: Int, positionMs: Long) {
        remote.seekTo(
            index,
            start(index) + (if (positionMs == C.TIME_UNSET) 0 else positionMs.coerceAtLeast(0)),
        )
    }

    override fun seekTo(positionMs: Long) = seekTo(currentMediaItemIndex, positionMs)

    override fun seekToDefaultPosition() = seekTo(currentMediaItemIndex, 0)

    override fun seekToDefaultPosition(index: Int) = seekTo(index, 0)

    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0))

    override fun seekForward() =
        seekTo(
            (currentPosition + seekForwardIncrement).coerceAtMost(
                duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
            )
        )

    override fun seekToNextMediaItem() {
        if (hasNextMediaItem()) seekTo(nextMediaItemIndex, 0)
    }

    override fun seekToPreviousMediaItem() {
        if (hasPreviousMediaItem()) seekTo(previousMediaItemIndex, 0)
    }

    override fun setMediaItems(items: List<MediaItem>, index: Int, positionMs: Long) {
        queue = items.toList()
        remote.setMediaItems(items, index, absolute(items, index, positionMs))
    }

    override fun setMediaItems(items: List<MediaItem>) = setMediaItems(items, 0, 0)

    override fun setMediaItems(items: List<MediaItem>, resetPosition: Boolean) =
        setMediaItems(
            items,
            if (resetPosition) 0 else currentMediaItemIndex,
            if (resetPosition) 0 else currentPosition,
        )

    override fun setMediaItem(item: MediaItem) = setMediaItems(listOf(item), 0, 0)

    override fun setMediaItem(item: MediaItem, startPositionMs: Long) =
        setMediaItems(listOf(item), 0, startPositionMs)

    override fun setMediaItem(item: MediaItem, resetPosition: Boolean) =
        setMediaItems(listOf(item), 0, if (resetPosition) 0 else currentPosition)

    override fun getCurrentTimeline(): Timeline {
        val timeline = remote.currentTimeline
        return object : ForwardingTimeline(timeline) {
            override fun getWindow(
                index: Int,
                window: Timeline.Window,
                projection: Long,
            ): Timeline.Window {
                super.getWindow(index, window, projection)
                item(index)?.let { window.mediaItem = it }
                window.durationUs =
                    duration(index).let { if (it == C.TIME_UNSET) it else it * 1000 }
                window.defaultPositionUs = 0
                window.positionInFirstPeriodUs = 0
                return window
            }

            override fun getPeriod(
                index: Int,
                period: Timeline.Period,
                setIds: Boolean,
            ): Timeline.Period {
                super.getPeriod(index, period, setIds)
                return period.set(
                    period.id,
                    period.uid,
                    period.windowIndex,
                    duration(period.windowIndex).let { if (it == C.TIME_UNSET) it else it * 1000 },
                    0,
                )
            }
        }
    }
}

/**
 * Media3's state-snapshot bridge adapts listener events too, retaining the one
 * service/session/tracker.
 */
class CastAudioPlayer(
    private val local: Player,
    private val remote: RemoteCastPlayer,
    private val server: LocalCastServer,
    private val prepareMedia: suspend () -> Unit,
    private val changed: () -> Unit,
    private val error: (Throwable) -> Unit,
) : ForwardingSimpleBasePlayer(local) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val chapterRemote = ChapterRemotePlayer(remote)
    private var active: Player = local
    private var transfer: Job? = null
    private var lastRemoteState: PlayerTransferState? = null
    private var resumeLocalOnCancel = false
    val isRemote
        get() = active === chapterRemote

    private fun connect() {
        transfer?.cancel()
        transfer = scope.launch {
            try {
                prepareMedia()
                if (!remote.isCastSessionAvailable || local.mediaItemCount == 0) return@launch
                val state = PlayerTransferState.fromPlayer(local)
                val index = local.currentMediaItemIndex
                val identity = local.currentMediaItem?.mediaId
                val wanted = local.playWhenReady
                val speed = local.playbackParameters.speed
                resumeLocalOnCancel = wanted
                local.pause()
                lastRemoteState = state
                // Keep the service/UI on the valid local timeline until the receiver acknowledges
                // its queue. A receiver may take tens of seconds to index a long MP4 file.
                state.buildUpon().setPlayWhenReady(false).build().setToPlayer(chapterRemote)
                chapterRemote.prepare()
                withTimeout(60_000) {
                    while (
                        remote.isCastSessionAvailable &&
                            (remote.mediaItemCount != local.mediaItemCount ||
                                remote.currentMediaItemIndex != index ||
                                remote.playbackState != Player.STATE_READY)
                    ) delay(100)
                }
                if (!remote.isCastSessionAvailable) return@launch
                check(local.currentMediaItem?.mediaId == identity) {
                    "Book changed while connecting; try Cast again"
                }
                chapterRemote.setPlaybackSpeed(speed.coerceIn(.5f, 2f))
                active = chapterRemote
                setPlayer(chapterRemote)
                local.stop()
                val playAfterConnect = resumeLocalOnCancel
                resumeLocalOnCancel = false
                changed()
                chapterRemote.playWhenReady = playAfterConnect
            } catch (e: TimeoutCancellationException) {
                if (resumeLocalOnCancel) local.play()
                resumeLocalOnCancel = false
                error(java.io.IOException("Speaker took too long to load the book", e))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (resumeLocalOnCancel) local.play()
                resumeLocalOnCancel = false
                error(e)
            }
        }
    }

    init {
        scope.launch {
            while (isActive) {
                delay(250)
                if (isRemote && remote.isCastSessionAvailable) chapterRemote.advancePastIgnoredClipEnd()
            }
        }
        remote.setSessionAvailabilityListener(
            object : SessionAvailabilityListener {
                override fun onCastSessionAvailable() = connect()

                override fun onCastSessionUnavailable() {
                    transfer?.cancel()
                    if (isRemote) {
                        val state = lastRemoteState
                        if (state != null) {
                            state.setToPlayer(local)
                            local.prepare()
                        }
                        remote.stop()
                        active = local
                        setPlayer(local)
                        changed()
                    } else if (resumeLocalOnCancel) local.play()
                    resumeLocalOnCancel = false
                    server.stop()
                }
            }
        )
        if (remote.isCastSessionAvailable) connect()
    }

    fun cancelPendingResume() {
        resumeLocalOnCancel = false
    }

    /** Route new books through the acknowledged handoff too, preserving the old book checkpoint. */
    fun prepareBookLoad() {
        transfer?.cancel()
        resumeLocalOnCancel = false
        if (isRemote) {
            rememberRemoteState()
            lastRemoteState?.buildUpon()?.setPlayWhenReady(false)?.build()?.setToPlayer(local)
            remote.pause()
            active = local
            setPlayer(local)
            changed()
        }
    }

    fun mediaLoaded() {
        if (remote.isCastSessionAvailable && !isRemote) connect()
    }

    /** Preserve original storage items; receiver queue placeholders must never enter ExoPlayer. */
    fun rememberRemoteState() {
        if (
            isRemote &&
                remote.isCastSessionAvailable &&
                chapterRemote.mediaItemCount > 0 &&
                chapterRemote.playbackState != Player.STATE_IDLE &&
                (0 until chapterRemote.mediaItemCount).all {
                    chapterRemote.getMediaItemAt(it).localConfiguration != null
                }
        ) {
            lastRemoteState = PlayerTransferState.fromPlayer(chapterRemote)
        }
    }

    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        remote.setSessionAvailabilityListener(null)
        remote.release()
        local.release()
        server.stop()
        return Futures.immediateVoidFuture()
    }
}
