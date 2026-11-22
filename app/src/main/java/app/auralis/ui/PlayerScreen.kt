@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.work.WorkInfo
import app.auralis.transcription.SpeechModelStore
import app.auralis.transcription.TranscriptionWorker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.LibraryBook
import app.auralis.domain.CaptionTrack
import app.auralis.domain.chapterCoverageMs
import app.auralis.domain.bookDetailTitle
import app.auralis.domain.orderedSeries
import app.auralis.domain.seriesInfo

@Composable
fun PlayerScreen(
    vm: AuralisViewModel,
    book: LibraryBook,
    back: () -> Unit,
    openDetails: () -> Unit = {},
    openPdf: () -> Unit = {},
) {
    val p by vm.player.collectAsStateWithLifecycle()
    val catalogue by vm.catalogue.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val coverage =
        remember(book.book.id, book.book.progressResetAt, book.chapters, segments) {
            chapterCoverageMs(book, segments)
        }
    val upcomingInSeries = remember(catalogue, book.book) {
        val currentSeries = seriesInfo(book.book)
        if (currentSeries == null) emptyList()
        else {
            val matching =
                orderedSeries((catalogue + book).distinctBy { it.book.id }.filter {
                    seriesInfo(it.book)?.name.equals(currentSeries.name, ignoreCase = true)
                })
            val currentAt = matching.indexOfFirst { it.book.id == book.book.id }
            matching.drop(currentAt + 1)
        }
    }
    var carMode by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf("") }
    var bookmarkAnchor by remember { mutableStateOf(0 to 0L) }
    var captionVersion by remember(book.book.id) { mutableIntStateOf(0) }
    val captions by
        produceState<CaptionTrack?>(null, book.book.id, captionVersion) {
            value = vm.captions(book.book.id)
        }
    val captionPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) vm.importCaptions(book, uri) { captionVersion++ }
        }
    val context = LocalContext.current
    val workInfo by vm.transcriptionWork(book.book.id).collectAsStateWithLifecycle(null)
    val transcription = workInfo?.toTranscriptionUi()
    var askToTranscribe by remember { mutableStateOf(false) }
    val pendingDownload = remember(askToTranscribe) { SpeechModelStore(context).pendingDownloadBytes() }
    var voiceVersion by remember(book.book.id) { mutableIntStateOf(0) }
    val voiceNames by
        produceState(emptyMap<String, String>(), book.book.id, voiceVersion) {
            value = vm.voiceNames(book.book.id)
        }
    var renamingVoice by remember { mutableStateOf<String?>(null) }
    renamingVoice?.let { voice ->
        var name by remember(voice) { mutableStateOf(voiceNames[voice].orEmpty()) }
        AlertDialog(
            onDismissRequest = { renamingVoice = null },
            title = { Text("Name this voice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Who is $voice? The name appears on every line in this voice. Voices are grouped automatically, so a few lines may be wrong.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        name,
                        { name = it.take(40) },
                        label = { Text("Character or narrator") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    vm.renameVoice(book.book.id, voice, name) { voiceVersion++ }
                    renamingVoice = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton({ renamingVoice = null }) { Text("Cancel") } },
        )
    }
    LaunchedEffect(workInfo?.state) {
        if (workInfo?.state == WorkInfo.State.SUCCEEDED) captionVersion++
    }
    // Reload as the transcript fills in during a run, not only when it finishes.
    val readyCheckpoint = workInfo?.progress?.getInt(TranscriptionWorker.KEY_READY, 0) ?: 0
    LaunchedEffect(readyCheckpoint) { if (readyCheckpoint > 0) captionVersion++ }
    if (askToTranscribe) {
        AlertDialog(
            onDismissRequest = { askToTranscribe = false },
            title = {
                Text(if (captions != null) "Replace this transcript?" else "Make a transcript on this phone")
            },
            text = {
                Text(
                    (if (captions != null)
                        "This book already has a transcript. A new one replaces it. Captions you imported are usually more accurate than the phone's own recognition, but only a new one gets exact word timing and voices.\n\n"
                    else "") +
                        "Auralis recognises the speech in this audiobook and writes it as captions. The audio never leaves your phone.\n\n" +
                        (if (pendingDownload == 0L) ""
                        else "The first time, it downloads English speech and voice models of about ${pendingDownload / 1_000_000} MB.\n\n") +
                        "Lines are grouped by voice so you can name the characters; grouping is approximate. " +
                        "It starts with the chapter you are on, so its lines appear within a few minutes, then carries on through the book with a notification showing progress. " +
                        "It works on English audiobooks only, and long books take a while, so keep the phone charging."
                )
            },
            confirmButton = {
                TextButton({
                    askToTranscribe = false
                    vm.startTranscription(book)
                }) {
                    Text(if (captions != null) "Replace" else "Start")
                }
            },
            dismissButton = { TextButton({ askToTranscribe = false }) { Text("Not now") } },
        )
    }
    if (carMode) CarPlayer(book, p, vm::toggle, vm::skip, chapter = { vm.chapter(it) }) { carMode = false }
    else
        PremiumPlayer(
            book,
            p,
            preferences,
            back,
            vm::toggle,
            vm::skip,
            { vm.chapter(it) },
            vm::seek,
            { vm.seekBook(book, it) },
            { vm.favourite(book.book) },
            { tool ->
                if (tool == "bookmark") bookmarkAnchor = vm.bookmarkPosition()
                dialog = tool
            },
            captions = captions,
            onImportCaptions = {
                captionPicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream"))
            },
            transcription = transcription,
            onGenerateTranscript = { askToTranscribe = true },
            onCancelTranscription = { vm.cancelTranscription(book.book.id) },
            onDetails = openDetails,
            onOpenPdf = openPdf,
            voiceNames = voiceNames,
            onRenameVoice = { renamingVoice = it },
        )
    val sheetTint = artworkTint(book.book.artwork)
    val sheetAccent = artworkAccent(book.book.artwork) ?: readableAccent(sheetTint)
    CompositionLocalProvider(LocalSheetPalette provides SheetPalette(sheetTint, sheetAccent)) {
    when (dialog) {
        "car" -> {
            LaunchedEffect(Unit) {
                dialog = ""
                carMode = true
            }
        }
        "speed" ->
            PlayerSheet(
                "Speed",
                "Changes apply to playback and become your default.",
                { dialog = "" },
                icon = Icons.Rounded.RocketLaunch,
                matchChapters = true,
            ) {
                SpeedPanel(
                    initial = p.speed,
                    remainingMs = (book.durationMs - bookPositionMs(book, p)).coerceAtLeast(0),
                    apply = { vm.speed(it) },
                )
            }
        "sleep" ->
            PlayerSheet(
                "Sleep timer",
                when {
                    p.timerRemaining > 0 -> "Stops in ${time(p.timerRemaining)}."
                    p.endChapter -> "Stops at the end of this chapter."
                    else -> "Drift off. Playback pauses when the timer ends."
                },
                { dialog = "" },
                icon = Icons.Rounded.Bedtime,
                matchChapters = true,
            ) {
                SleepPanel(
                    timerRemaining = p.timerRemaining,
                    endChapter = p.endChapter,
                    chapterLeftMs = (p.duration - p.position).coerceAtLeast(0),
                    fade = preferences.fade,
                    setFade = { vm.settings(preferences.copy(fade = it)) },
                    sleep = {
                        vm.sleep(it)
                        dialog = ""
                    },
                    extend = {
                        vm.extendSleep()
                        dialog = ""
                    },
                )
            }
        "log" -> {
            val days =
                remember(segments, book.book.id, book.book.progressResetAt) {
                    app.auralis.domain.listenLog(
                        book.book.id,
                        segments,
                        book.book.progressResetAt,
                    )
                }
            val sittings = days.sumOf { it.sessions.size }
            PlayerSheet(
                "Listen log",
                if (sittings == 0) "Nothing logged for this book yet."
                else "Every sitting with this book, newest first.",
                { dialog = "" },
                icon = Icons.Rounded.History,
            ) {
                if (days.isEmpty()) {
                    Text(
                        "Start listening and each sitting will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LogStat(
                            app.auralis.domain.listenSpanLabel(days.sumOf { it.elapsedMs }),
                            "Listened",
                            Modifier.weight(1f),
                        )
                        LogStat("$sittings", if (sittings == 1) "Sitting" else "Sittings", Modifier.weight(1f))
                        LogStat("${days.size}", if (days.size == 1) "Day" else "Days", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(24.dp))
                    days.forEachIndexed { dayIndex, day ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                logDayLabel(day.date),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                app.auralis.domain.listenSpanLabel(day.elapsedMs),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        day.sessions.forEachIndexed { index, sitting ->
                            val first = book.orderedChapters.indexOfFirst { it.id == sitting.firstChapterId }
                            val last = book.orderedChapters.indexOfFirst { it.id == sitting.lastChapterId }
                            val title = book.orderedChapters.getOrNull(first)?.title ?: "Listening"
                            val pace = "%.2f".format(sitting.speed).trimEnd('0').trimEnd('.') + "×"
                            val where =
                                if (first == last) "${time(sitting.fromMs)} → ${time(sitting.toMs)}"
                                else "to chapter ${last + 1}"
                            LogEntry(
                                time =
                                    java.time.Instant.ofEpochMilli(sitting.startedAt)
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalTime()
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
                                title = title,
                                detail =
                                    "${app.auralis.domain.listenSpanLabel(sitting.elapsedMs)} at $pace  ·  $where",
                                first = index == 0,
                                last = index == day.sessions.lastIndex,
                                latest = dayIndex == 0 && index == 0,
                            )
                        }
                        if (dayIndex != days.lastIndex) Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        "bookmark" -> {
            val chapterStart = book.orderedChapters.take(bookmarkAnchor.first).sumOf { it.durationMs }
            val quote =
                captions?.let { track ->
                    track.indexAt(chapterStart + bookmarkAnchor.second).takeIf { it >= 0 }?.let { track.cues[it].text }
                }
            PlayerSheet(
                "Add bookmark",
                "Save this moment to come back to it.",
                { dialog = "" },
                icon = Icons.Rounded.BookmarkAdd,
                matchChapters = true,
            ) {
                BookmarkPanel(
                    book = book,
                    chapterTitle =
                        book.orderedChapters.getOrNull(bookmarkAnchor.first)?.title?.ifBlank { null }
                            ?: "Chapter ${bookmarkAnchor.first + 1}",
                    positionMs = bookmarkAnchor.second,
                    quote = quote,
                ) { title, note ->
                    vm.bookmark(book, title, note, bookmarkAnchor.first, bookmarkAnchor.second)
                    dialog = ""
                }
            }
        }
        "chapters" ->
            PlayerSheet(
                book.book.title,
                "",
                { dialog = "" },
                chapterStyle = true,
            ) {
                var chapterTab by remember { mutableIntStateOf(0) }
                val chapterList =
                    rememberLazyListState(
                        initialFirstVisibleItemIndex = chapterDivisionLazyIndex(book.orderedChapters, p.chapterIndex)
                    )
                val accent = MaterialTheme.colorScheme.primary
                val tabMotion by androidx.compose.animation.core.animateFloatAsState(
                    chapterTab.toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(if (preferences.reducedMotion) 0 else 220),
                    label = "Chapter tab indicator",
                )
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        listOf("Chapters", "Up next").forEachIndexed { index, label ->
                            Column(
                                Modifier.weight(1f).clickable { chapterTab = index }
                                    .padding(top = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (chapterTab == index) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 15.dp),
                                )
                            }
                        }
                    }
                    BoxWithConstraints(Modifier.fillMaxWidth().height(3.dp)) {
                        val tabWidth = maxWidth / 2
                        Box(
                            Modifier.width(tabWidth).offset {
                                IntOffset((tabWidth.toPx() * tabMotion).roundToInt(), 0)
                            }
                                .fillMaxHeight().background(accent)
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                if (chapterTab == 0) {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        state = chapterList,
                        contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp),
                    ) {
                        chapterDivisionItems(
                            chapters = book.orderedChapters,
                            selectedIndex = p.chapterIndex,
                            onSelect = {
                                vm.chapter(it)
                                dialog = ""
                            },
                            isCompleted = { ch -> ch.durationMs > 0 && (coverage[ch.id] ?: 0L) >= ch.durationMs },
                        )
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item { Text("Next in series", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        if (upcomingInSeries.isEmpty()) {
                            item { Text(
                                seriesInfo(book.book)?.let { "No later book from ${it.name} is in your library yet." }
                                    ?: "This book is not assigned to a series yet.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) }
                        }
                        items(upcomingInSeries, key = { it.book.id }) { nextInSeries ->
                            Surface(
                                onClick = {
                                    vm.load(nextInSeries.book.id)
                                    dialog = ""
                                },
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                ) {
                                    Cover(nextInSeries.book, Modifier.size(68.dp), corner = 9.dp)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            bookDetailTitle(nextInSeries.book),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("By ${nextInSeries.book.author}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(durationLabel(nextInSeries.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.PlayArrow, "Play next book", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
    }
    }
}

/** The current book's colours for the player's panels. */
internal data class SheetPalette(val tint: Color, val accent: Color)

internal val LocalSheetPalette = staticCompositionLocalOf<SheetPalette?> { null }

/**
 * The player's panels. With a [SheetPalette] in scope, the whole Material colour scheme inside the
 * panel is rebuilt from the book: a dark surface mixed with the cover's colour, and the cover's
 * vivid colour as the accent. Every slider, switch, chip, field and button follows it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSheet(
    title: String,
    subtitle: String,
    dismiss: () -> Unit,
    icon: ImageVector? = null,
    chapterStyle: Boolean = false,
    matchChapters: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalSheetPalette.current
    val chapterState = rememberModalBottomSheetState(skipPartiallyExpanded = !chapterStyle)
    val scope = rememberCoroutineScope()
    val base = MaterialTheme.colorScheme
    val scheme =
        if (chapterStyle || matchChapters) {
            base.copy(
                primary = Color(0xFF62B5FF),
                onPrimary = Color(0xFF00111B),
                primaryContainer = Color(0xFF183246),
                onPrimaryContainer = Color(0xFFC9E7FF),
                secondaryContainer = Color(0xFF183246),
                onSecondaryContainer = Color(0xFFC9E7FF),
                surface = Color(0xFF00111B),
                surfaceContainerLowest = Color(0xFF00111B),
                surfaceContainerLow = Color(0xFF0D2030),
                surfaceContainer = Color(0xFF00111B),
                surfaceContainerHigh = Color(0xFF12283A),
                surfaceContainerHighest = Color(0xFF183246),
                onSurface = Color(0xFFF6F8FC),
                onSurfaceVariant = Color(0xFFC9D9E8),
                outline = Color(0xFF5B7084),
                outlineVariant = Color(0xFF24394C),
            )
        } else if (palette == null) base
        else {
            fun surface(dark: Long, amount: Float) = lerp(Color(dark), palette.tint, amount)
            base.copy(
                primary = palette.accent,
                onPrimary = PlayerScrim,
                primaryContainer = lerp(Color(0xFF202025), palette.accent, .32f),
                onPrimaryContainer = PlayerInk,
                secondaryContainer = lerp(Color(0xFF202025), palette.accent, .26f),
                onSecondaryContainer = PlayerInk,
                surface = surface(0xFF17171A, .30f),
                surfaceContainerLowest = surface(0xFF101012, .22f),
                surfaceContainerLow = surface(0xFF141417, .26f),
                surfaceContainer = surface(0xFF17171A, .30f),
                surfaceContainerHigh = surface(0xFF222226, .34f),
                surfaceContainerHighest = surface(0xFF2A2A2F, .38f),
                onSurface = PlayerInk,
                onSurfaceVariant = PlayerMuted,
                outline = lerp(PlayerMuted, palette.accent, .35f),
                outlineVariant = Color.White.copy(alpha = .12f),
            )
        }
    val expandedChapter = chapterStyle && chapterState.currentValue == SheetValue.Expanded
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography) {
        ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetMaxWidth = if (chapterStyle) androidx.compose.ui.unit.Dp.Unspecified else 560.dp,
            containerColor = scheme.surfaceContainer,
            contentColor = scheme.onSurface,
            sheetState = chapterState,
            sheetGesturesEnabled = !chapterStyle,
            contentWindowInsets = { if (chapterStyle) WindowInsets.navigationBars else BottomSheetDefaults.windowInsets },
            dragHandle = {
                if (chapterStyle) {
                    val expanded = chapterState.currentValue == SheetValue.Expanded
                    val toggle = {
                        scope.launch {
                            if (expanded && chapterState.hasPartiallyExpandedState) chapterState.partialExpand()
                            else chapterState.expand()
                        }
                    }
                    Column {
                    // Carry the chapter sheet's vivid blue all the way behind the system clock
                    // and status icons when the chapter list is expanded edge-to-edge.
                    if (expandedChapter) Spacer(Modifier.fillMaxWidth().background(Color(0xFF4D86D4)).statusBarsPadding())
                    Box(
                        Modifier.fillMaxWidth().height(36.dp)
                            .clickable(onClickLabel = if (expanded) "Collapse chapters" else "Expand chapters") { toggle() }
                            .pointerInput(chapterState) {
                                var distance = 0f
                                detectVerticalDragGestures(
                                    onDragStart = { distance = 0f },
                                    onDragEnd = {
                                        val threshold = 24.dp.toPx()
                                        if (distance < -threshold) scope.launch { chapterState.expand() }
                                        if (distance > threshold && chapterState.hasPartiallyExpandedState) {
                                            scope.launch { chapterState.partialExpand() }
                                        }
                                    },
                                ) { change, delta ->
                                    change.consume()
                                    distance += delta
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(36.dp, 4.dp).background(scheme.onSurfaceVariant, CircleShape))
                    }
                    }
                } else BottomSheetDefaults.DragHandle()
            },
            shape = if (expandedChapter) androidx.compose.ui.graphics.RectangleShape
                else RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .imePadding()
                    .then(
                        if (chapterStyle) Modifier.fillMaxHeight()
                        else Modifier.verticalScroll(rememberScrollState())
                    )
                    .padding(horizontal = if (chapterStyle) 0.dp else 24.dp)
                    .padding(bottom = if (chapterStyle) 0.dp else 24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().then(if (chapterStyle) Modifier.padding(horizontal = 16.dp) else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (chapterStyle) {
                        IconButton(dismiss) {
                            Icon(Icons.Rounded.Close, "Close chapters")
                        }
                        Text(
                            title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else if (icon != null) {
                        Box(
                            Modifier.size(42.dp).background(scheme.primary.copy(alpha = .18f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(icon, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                    }
                    if (!chapterStyle) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(dismiss) { Icon(Icons.Rounded.Close, "Close panel") }
                    }
                }
                if (subtitle.isNotBlank()) Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
                )
                content()
            }
        }
    }
}

private fun WorkInfo.toTranscriptionUi(): TranscriptionUi? =
    when (state) {
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED ->
            TranscriptionUi(
                running = true,
                label = progress.getString(TranscriptionWorker.KEY_LABEL).orEmpty(),
                fraction = progress.getFloat(TranscriptionWorker.KEY_FRACTION, -1f).takeIf { it >= 0f },
                error = null,
            )
        WorkInfo.State.FAILED ->
            TranscriptionUi(
                running = false,
                label = "",
                fraction = null,
                error = outputData.getString(TranscriptionWorker.KEY_ERROR) ?: "Transcription failed.",
            )
        else -> null
    }

private fun logDayLabel(date: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else ->
            date.format(
                java.time.format.DateTimeFormatter.ofPattern(
                    if (date.year == today.year) "EEEE, d MMM" else "d MMM yyyy"
                )
            )
    }
}

@Composable
private fun LogStat(value: String, label: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** One sitting on the day's timeline: start time, a rail with a dot, then what was heard. */
@Composable
private fun LogEntry(
    time: String,
    title: String,
    detail: String,
    first: Boolean,
    last: Boolean,
    latest: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            time,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
        )
        Box(Modifier.width(18.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!(first && last))
                Box(
                    Modifier.padding(top = if (first) 8.dp else 0.dp)
                        .width(2.dp)
                        .then(if (last) Modifier.height(8.dp) else Modifier.fillMaxHeight())
                        .background(colors.outlineVariant.copy(alpha = .6f))
                )
            Box(
                Modifier.padding(top = 4.dp)
                    .size(10.dp)
                    .background(if (latest) colors.primary else colors.outline, androidx.compose.foundation.shape.CircleShape)
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, bottom = if (last) 0.dp else 18.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
