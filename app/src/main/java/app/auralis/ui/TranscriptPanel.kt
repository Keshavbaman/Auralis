package app.auralis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.data.LibraryBook
import app.auralis.domain.CaptionTrack
import app.auralis.domain.Cue

/** Beyond this distance past the last line, the listener is in an untranscribed part. */
private const val UNTRANSCRIBED_GAP_MS = 20_000L

/** What the player shows about an on-device transcription job. Null when none is known. */
data class TranscriptionUi(
    val running: Boolean,
    val label: String,
    val fraction: Float?,
    val error: String?,
)

/**
 * Book position that advances every frame between the player's 400 ms updates, so word-by-word
 * highlighting moves smoothly. Small backward corrections are ignored to avoid flicker.
 */
@Composable
internal fun rememberLivePosition(base: Long, playing: Boolean, speed: Float): State<Long> {
    val live = remember { mutableLongStateOf(base) }
    LaunchedEffect(base, playing, speed) {
        val current = live.longValue
        val start = if (playing && base < current && current - base < 800) current else base
        live.longValue = start
        if (!playing) return@LaunchedEffect
        val origin = withFrameMillis { it }
        while (true) {
            withFrameMillis { now -> live.longValue = start + ((now - origin) * speed).toLong() }
        }
    }
    return live
}

/**
 * Full-screen, Spotify-style transcript: the screen takes the cover's colour, lines are large and
 * bold, and the current line lights up word by word. Tapping a line plays from it.
 */
@Composable
internal fun TranscriptScreen(
    book: LibraryBook,
    state: PlayerSnapshot,
    track: CaptionTrack?,
    tint: Color,
    reduce: Boolean,
    skipSeconds: Int,
    job: TranscriptionUi?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBook: (Long) -> Unit,
    onImport: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    voiceNames: Map<String, String> = emptyMap(),
    onRenameVoice: (String) -> Unit = {},
) {
    val background = lerp(tint, Color.Black, .38f)
    Column(
        Modifier.fillMaxSize()
            .background(background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClose, Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .22f))) {
                Icon(Icons.Rounded.KeyboardArrowDown, "Close transcript", tint = PlayerInk)
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    book.orderedChapters.getOrNull(state.chapterIndex)?.title
                        ?: "Chapter ${state.chapterIndex + 1}",
                    color = PlayerInk,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (track != null && job?.running == true)
                        job.label.ifBlank { "Updating transcript" }
                    else shelfTitle(book.book.title),
                    color = PlayerInk.copy(alpha = .7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (track != null && job?.running != true) {
                // Redo: older transcripts gain word timing and voices when made again.
                IconButton(
                    onGenerate,
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .22f)),
                ) {
                    Icon(Icons.Rounded.Refresh, "Make transcript again", tint = PlayerInk)
                }
            } else if (track != null && job?.running == true) {
                // While a run fills in lines there is no empty state, so offer Stop here.
                IconButton(
                    onCancel,
                    Modifier.clip(CircleShape).background(Color.Black.copy(alpha = .22f)),
                ) {
                    Icon(Icons.Rounded.Stop, "Stop transcribing", tint = PlayerInk)
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (track == null) {
                TranscriptEmpty(job, onGenerate, onImport, onCancel, Modifier.align(Alignment.Center))
            } else {
                Lyrics(
                    track,
                    rememberLivePosition(bookPositionMs(book, state), state.playing, state.speed),
                    reduce,
                    background,
                    job?.running == true,
                    voiceNames,
                    onSeekBook,
                    onRenameVoice,
                )
            }
        }
        TranscriptControls(state, reduce, skipSeconds, onToggle, onSkip, onSeek)
    }
}

@Composable
private fun Lyrics(
    track: CaptionTrack,
    live: State<Long>,
    reduce: Boolean,
    background: Color,
    transcribing: Boolean,
    voiceNames: Map<String, String>,
    onSeekBook: (Long) -> Unit,
    onRenameVoice: (String) -> Unit,
) {
    // No line is "current" when the listener is well past the last transcribed line, e.g. in a
    // chapter a running transcription has not reached yet.
    val active by
        remember(track) {
            derivedStateOf {
                val index = track.indexAt(live.value)
                if (index >= 0 && live.value - track.cues[index].endMs > UNTRANSCRIBED_GAP_MS) -1
                else index
            }
        }
    val listState = rememberLazyListState()
    var following by remember(track) { mutableStateOf(true) }
    // A finger-driven scroll means the reader wants to browse, so stop auto-following.
    val stopFollowing = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) following = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(active, following) {
        if (following && active >= 0) {
            // Keep the spoken line about a third of the way down the screen.
            val offset = -(listState.layoutInfo.viewportSize.height / 3)
            if (reduce) listState.scrollToItem(active, offset)
            else listState.animateScrollToItem(active, offset)
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(stopFollowing)
                    .semantics { contentDescription = "Transcript" }
                    .drawWithContent {
                        drawContent()
                        // Lines dissolve into the background at both edges instead of being cut.
                        val fade = 56.dp.toPx()
                        drawRect(
                            Brush.verticalGradient(listOf(background, Color.Transparent), 0f, fade)
                        )
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, background),
                                size.height - fade * 2,
                                size.height,
                            )
                        )
                    },
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 260.dp),
        ) {
            itemsIndexed(track.cues) { index, cue ->
                val previousSpeaker = track.cues.getOrNull(index - 1)?.speaker
                LyricLine(
                    cue = cue,
                    index = index,
                    active = active,
                    live = live,
                    speakerLabel =
                        cue.speaker?.takeIf { it != previousSpeaker }?.let { voiceNames[it] ?: it },
                    onSpeaker = { cue.speaker?.let(onRenameVoice) },
                    onClick = {
                        following = true
                        onSeekBook(cue.startMs)
                    },
                )
            }
        }
        if (active == -1 && live.value > (track.cues.firstOrNull()?.startMs ?: 0)) {
            Text(
                if (transcribing) "This part isn't transcribed yet. It's on the way."
                else "There are no lines for this part of the book.",
                color = PlayerInk,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = .3f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
        if (!following) {
            Button(
                { following = true },
                Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                shape = RoundedCornerShape(50),
                colors =
                    ButtonDefaults.buttonColors(containerColor = PlayerInk, contentColor = background),
            ) {
                Text("Follow along", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LyricLine(
    cue: Cue,
    index: Int,
    active: Int,
    live: State<Long>,
    speakerLabel: String?,
    onSpeaker: () -> Unit,
    onClick: () -> Unit,
) {
    val isActive = index == active
    val baseColor by
        animateColorAsState(
            when {
                index < active -> PlayerInk.copy(alpha = .58f)
                isActive -> PlayerInk
                else -> PlayerInk.copy(alpha = .30f)
            },
            label = "Line colour",
        )
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        if (speakerLabel != null) {
            Text(
                speakerLabel.uppercase(),
                color = PlayerInk,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
                modifier =
                    Modifier.padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = .22f))
                        .clickable(role = Role.Button, onClickLabel = "Name this voice", onClick = onSpeaker)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        val words = remember(cue) { cue.timedWords() }
        val text =
            if (isActive && words.isNotEmpty()) {
                // Reading the live position here keeps per-frame work to the one active line.
                val now = live.value
                buildAnnotatedString {
                    words.forEachIndexed { i, word ->
                        withStyle(
                            SpanStyle(
                                color = if (word.startMs <= now) PlayerInk else PlayerInk.copy(alpha = .40f)
                            )
                        ) {
                            append(word.text)
                        }
                        if (i != words.lastIndex) append(' ')
                    }
                }
            } else {
                buildAnnotatedString { append(cue.text) }
            }
        Text(
            text,
            color = baseColor,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-.3).sp,
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Play from ${time(cue.startMs)}",
                        onClick = onClick,
                    )
                    .semantics { selected = isActive },
        )
    }
}

@Composable
private fun TranscriptControls(
    state: PlayerSnapshot,
    reduce: Boolean,
    skipSeconds: Int,
    onToggle: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onSeek: (Long) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 8.dp)) {
        FineSlider(
            drag ?: state.position.toFloat(),
            state.duration.toFloat(),
            { drag = it },
            {
                drag?.let { onSeek(it.toLong()) }
                drag = null
            },
            "Chapter progress",
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeLabel(time((drag ?: state.position.toFloat()).toLong()))
            TimeLabel("−${time((state.duration - (drag ?: state.position.toFloat()).toLong()).coerceAtLeast(0))}")
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SkipButton(false, skipSeconds, reduce) { onSkip(false) }
            IconButton(onToggle, Modifier.size(64.dp).background(PlayerInk, CircleShape)) {
                Icon(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playing) "Pause" else "Play",
                    tint = PlayerScrim,
                    modifier = Modifier.size(36.dp),
                )
            }
            SkipButton(true, skipSeconds, reduce) { onSkip(true) }
        }
    }
}

@Composable
private fun TranscriptEmpty(
    job: TranscriptionUi?,
    onGenerate: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Rounded.Subtitles,
            null,
            tint = PlayerInk.copy(alpha = .8f),
            modifier = Modifier.size(32.dp),
        )
        if (job?.running == true) {
            Text(
                job.label.ifBlank { "Preparing transcript" },
                color = PlayerInk,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            LinearProgressIndicator(
                progress = { job.fraction ?: 0f },
                modifier = Modifier.fillMaxWidth(.7f),
                color = PlayerInk,
                trackColor = PlayerInk.copy(alpha = .2f),
            )
            Text(
                "Runs on this phone. You can keep listening.",
                color = PlayerInk.copy(alpha = .75f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            TextButton(onCancel) { Text("Cancel", color = PlayerInk) }
            return@Column
        }
        Text(
            "No transcript yet",
            color = PlayerInk,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        job?.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            "Create one on this phone from the audio, or import an .srt or .vtt file. Nothing is uploaded.",
            color = PlayerInk.copy(alpha = .75f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onGenerate,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = PlayerInk, contentColor = PlayerScrim),
        ) {
            Text(if (job?.error != null) "Try again" else "Make transcript", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onImport) { Text("Import captions instead", color = PlayerInk.copy(alpha = .85f)) }
    }
}
