package app.auralis.ui

import android.graphics.BitmapFactory
import app.auralis.cast.CastRouteButton
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import app.auralis.domain.SwipeAxis
import app.auralis.domain.swipeCommit
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import app.auralis.data.LibraryBook
import app.auralis.data.Preferences
import app.auralis.domain.CaptionTrack
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val PlayerInk = Color(0xFFF8F6F2)
internal val PlayerMuted = Color(0xFFBBB9B9)
internal val PlayerScrim = Color(0xFF101012)
private val PlayerChrome = Color.Black.copy(alpha = .28f)

fun shelfTitle(title: String) =
    title.replace(Regex("\\s*\\(Full-Cast(?: Edition)?\\)\\s*", RegexOption.IGNORE_CASE), "").trim()

/** Book-wide position: completed chapters plus the position inside the current one. */
internal fun bookPositionMs(book: LibraryBook, state: PlayerSnapshot): Long =
    book.orderedChapters.take(state.chapterIndex).sumOf { it.durationMs } + state.position

/**
 * Small decoded thumbnail, calculated off the UI thread; no full-size artwork bitmap is retained.
 */
@Composable
internal fun artworkTint(path: String?): Color {
    val tint by
        produceState(Color(0xFF514438), path) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching {
                            if (path == null) return@runCatching Color(0xFF514438)
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(path, bounds)
                            val options =
                                BitmapFactory.Options().apply {
                                    inSampleSize =
                                        (maxOf(bounds.outWidth, bounds.outHeight) / 48)
                                            .coerceAtLeast(1)
                                }
                            val bitmap =
                                BitmapFactory.decodeFile(path, options)
                                    ?: return@runCatching Color(0xFF514438)
                            val bins = mutableMapOf<Int, Int>()
                            for (y in 0 until bitmap.height step 2) for (x in
                                0 until bitmap.width step 2) {
                                val pixel = bitmap.getPixel(x, y)
                                val r = android.graphics.Color.red(pixel)
                                val g = android.graphics.Color.green(pixel)
                                val b = android.graphics.Color.blue(pixel)
                                if (maxOf(r, g, b) > 45 && minOf(r, g, b) < 220) {
                                    val bin = ((r / 32) shl 6) or ((g / 32) shl 3) or (b / 32)
                                    bins[bin] = (bins[bin] ?: 0) + 1
                                }
                            }
                            bitmap.recycle()
                            val bin =
                                bins.maxByOrNull { it.value }?.key
                                    ?: return@runCatching Color(0xFF514438)
                            Color(
                                ((bin shr 6) * 32 + 16) / 255f,
                                (((bin shr 3) and 7) * 32 + 16) / 255f,
                                ((bin and 7) * 32 + 16) / 255f,
                            )
                        }
                        .getOrDefault(Color(0xFF514438))
                }
        }
    return tint
}

/**
 * The cover's most vivid colour (e.g. Dune's orange sun rather than its dark background), tuned
 * to read well on the dark player. Null when the cover has no strong colour.
 */
@Composable
internal fun artworkAccent(path: String?): Color? {
    val accent by
        produceState<Color?>(null, path) {
            value =
                withContext(Dispatchers.Default) {
                    runCatching {
                            if (path == null) return@runCatching null
                            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(path, bounds)
                            val options =
                                BitmapFactory.Options().apply {
                                    inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 64).coerceAtLeast(1)
                                }
                            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@runCatching null
                            // Weight each hue by how vivid its pixels are, in 24 bins of 15 degrees.
                            val weight = FloatArray(24)
                            val sums = Array(24) { FloatArray(3) }
                            val hsv = FloatArray(3)
                            for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
                                android.graphics.Color.colorToHSV(bitmap.getPixel(x, y), hsv)
                                if (hsv[1] < .35f || hsv[2] < .45f) continue
                                val bin = (hsv[0] / 15f).toInt().coerceIn(0, 23)
                                val w = hsv[1] * hsv[2]
                                weight[bin] += w
                                sums[bin][0] += hsv[0] * w
                                sums[bin][1] += hsv[1] * w
                                sums[bin][2] += hsv[2] * w
                            }
                            bitmap.recycle()
                            val best = weight.indices.maxBy { weight[it] }
                            if (weight[best] < 8f) return@runCatching null
                            val w = weight[best]
                            val chosen =
                                floatArrayOf(
                                    sums[best][0] / w,
                                    (sums[best][1] / w).coerceIn(.55f, .9f),
                                    (sums[best][2] / w).coerceIn(.88f, .98f),
                                )
                            Color(android.graphics.Color.HSVToColor(chosen))
                        }
                        .getOrNull()
                }
        }
    return accent
}

/** Stateless playback UI: transport and persistence continue to belong to the media service. */
@Composable
fun PremiumPlayer(
    book: LibraryBook,
    state: PlayerSnapshot,
    preferences: Preferences,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onChapter: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBook: (Long) -> Unit,
    onFavourite: () -> Unit,
    onTool: (String) -> Unit,
    captions: CaptionTrack? = null,
    onImportCaptions: () -> Unit = {},
    transcription: TranscriptionUi? = null,
    onGenerateTranscript: () -> Unit = {},
    onCancelTranscription: () -> Unit = {},
    onDetails: () -> Unit = {},
    onOpenPdf: () -> Unit = {},
    voiceNames: Map<String, String> = emptyMap(),
    onRenameVoice: (String) -> Unit = {},
) {
    val reduce = preferences.reducedMotion
    val targetTint = artworkTint(book.book.artwork)
    val tint by
        animateColorAsState(targetTint, tween(if (reduce) 0 else 800), label = "Artwork atmosphere")
    var showTranscript by rememberSaveable(book.book.id) { mutableStateOf(false) }
    // Shown once ever, the first time the player opens, then remembered.
    val hintContext = androidx.compose.ui.platform.LocalContext.current
    var coverHint by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val hints = hintContext.getSharedPreferences("auralis_hints", android.content.Context.MODE_PRIVATE)
        if (!hints.getBoolean("cover_gestures", false)) {
            hints.edit().putBoolean("cover_gestures", true).apply()
            delay(900)
            coverHint = true
            delay(4_500)
            coverHint = false
        }
    }
    val accent by
        animateColorAsState(
            artworkAccent(book.book.artwork) ?: readableAccent(targetTint),
            tween(if (reduce) 0 else 800),
            label = "Book accent",
        )
    androidx.activity.compose.BackHandler(showTranscript) { showTranscript = false }
    if (showTranscript) {
        CompositionLocalProvider(LocalPlayerAccent provides accent) {
        TranscriptScreen(
            book = book,
            state = state,
            track = captions,
            tint = tint,
            reduce = reduce,
            skipSeconds = preferences.skipSeconds,
            job = transcription,
            onClose = { showTranscript = false },
            onToggle = onToggle,
            onSkip = onSkip,
            onSeek = onSeek,
            onSeekBook = onSeekBook,
            onImport = onImportCaptions,
            onGenerate = onGenerateTranscript,
            onCancel = onCancelTranscription,
            voiceNames = voiceNames,
            onRenameVoice = onRenameVoice,
        )
        }
        return
    }
    // The whole player follows a downward pull 1:1 like a lifted card, then either continues off
    // screen at the release speed and closes, or springs home. A pull up opens the book details.
    // Offsets live in plain state written straight from the touch handler (no coroutine per move),
    // and are read only inside graphicsLayer, so dragging never recomposes the player.
    val playerScroll = rememberScrollState()
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val wideScreen =
        configuration.screenWidthDp >= 840 ||
            (configuration.screenWidthDp >= 640 && configuration.screenWidthDp > configuration.screenHeightDp)
    val canPull = !wideScreen && playerScroll.maxValue == 0
    var pull by remember { mutableFloatStateOf(0f) }
    var pullJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val pullScope = rememberCoroutineScope()
    val pullHaptics = LocalHapticFeedback.current
    Box(
        Modifier.fillMaxSize()
            .then(
                if (!canPull) Modifier
                else
                    Modifier.liveSwipe(
                        book.book.id,
                        setOf(SwipeAxis.Vertical),
                        first = true,
                        onDrag = { _, offset ->
                            pullJob?.cancel()
                            pull = if (offset > 0) offset else offset * .25f
                        },
                        onRelease = { _, offset, velocity, height ->
                            pullJob =
                                pullScope.launch {
                                    when (swipeCommit(offset, velocity, height, distanceFraction = .2f, flingVelocity = 900f)) {
                                        -1 -> {
                                            pullHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            if (!reduce)
                                                animate(pull, height, velocity.coerceAtLeast(0f), tween(200, easing = FastOutLinearInEasing)) { value, _ ->
                                                    pull = value
                                                }
                                            onBack()
                                            // Only matters if the player is somehow still shown.
                                            delay(700)
                                            animate(pull, 0f, animationSpec = spring(dampingRatio = .85f)) { value, _ -> pull = value }
                                        }
                                        1 -> {
                                            pullHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onDetails()
                                            animate(pull, 0f, animationSpec = spring(dampingRatio = .8f)) { value, _ -> pull = value }
                                        }
                                        else ->
                                            animate(
                                                pull,
                                                0f,
                                                velocity,
                                                spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow),
                                            ) { value, _ -> pull = value }
                                    }
                                }
                        },
                    )
            )
            .graphicsLayer {
                val lifted = (pull / size.height).coerceIn(0f, 1f)
                translationY = pull
                scaleX = 1f - lifted * .07f
                scaleY = scaleX
                if (lifted > 0f) {
                    shape = RoundedCornerShape((lifted * 120f).coerceAtMost(32f).dp)
                    clip = true
                }
            }
            .background(PlayerScrim)
    ) {
        // Artwork colour washes down from the top and settles into near-black, as on the book page.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.verticalGradient(
                    0f to lerp(Color(0xFF121216), tint, .62f),
                    .55f to lerp(PlayerScrim, tint, .12f),
                    1f to PlayerScrim,
                )
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // Three-part bar: the centre is centred on the screen itself, not on whatever space
            // the side buttons leave, so "Now listening" never drifts.
            Box(
                Modifier.fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
            ) {
                ChromeButton(Modifier.align(Alignment.CenterStart)) {
                    PressIcon(Icons.Rounded.KeyboardArrowDown, "Close player", reduce, onBack, size = 24.dp)
                }
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListeningGlyph(state.playing, reduce)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "NOW LISTENING",
                        color = PlayerInk.copy(alpha = .78f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                    )
                }
                Row(
                    Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChromeButton { CastRouteButton(Modifier.size(CHROME_SIZE)) }
                    ChromeButton {
                        PressIcon(
                            if (book.book.favourite) Icons.Rounded.Favorite
                            else Icons.Rounded.FavoriteBorder,
                            "Toggle favourite",
                            reduce,
                            onFavourite,
                            tint = if (book.book.favourite) accent else PlayerInk,
                            size = 24.dp,
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 840.dp || (maxWidth >= 640.dp && maxWidth > maxHeight)
                // Phone layout fits one screen: the controls report their height and the cover
                // takes exactly the space that is left, so nothing needs scrolling.
                val density = LocalDensity.current
                var controlsHeight by remember { mutableStateOf(400.dp) }
                val artworkSize by
                    animateDpAsState(
                        (maxHeight - controlsHeight - 54.dp)
                            .coerceIn(150.dp, minOf(maxWidth - 56.dp, 380.dp)),
                        if (reduce) snap() else tween(220),
                        label = "Cover fit",
                    )
                @Composable
                fun art(modifier: Modifier, side: Dp) {
                    val playingScale by
                        animateFloatAsState(
                            if (state.playing) 1f else .95f,
                            if (reduce) snap() else spring(dampingRatio = .85f, stiffness = 130f),
                            label = "Artwork breath",
                        )
                    // Cover gestures: sideways changes chapter, up opens details, down closes,
                    // double-tap either half skips. The cover follows the finger throughout.
                    var coverX by remember(book.book.id) { mutableFloatStateOf(0f) }
                    var coverJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
                    val gestures = rememberCoroutineScope()
                    val haptics = LocalHapticFeedback.current
                    var skipFlash by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
                    val hasPrevious = state.chapterIndex > 0
                    val hasNext = state.chapterIndex < book.chapters.lastIndex
                    val hintAlpha by
                        animateFloatAsState(if (coverHint) 1f else 0f, tween(400), label = "Cover hint")
                    Box(modifier, contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(side)
                                .graphicsLayer {
                                    val width = side.toPx()
                                    scaleX = playingScale
                                    scaleY = scaleX
                                    translationX = coverX
                                    rotationZ = coverX / width * 7f
                                    alpha = 1f - (abs(coverX) / (width * 2.2f)).coerceIn(0f, .6f)
                                }
                                .liveSwipe(
                                    book.book.id to state.chapterIndex,
                                    setOf(SwipeAxis.Horizontal),
                                    onDrag = { _, offset ->
                                        coverJob?.cancel()
                                        val possible = if (offset < 0) hasNext else hasPrevious
                                        coverX = if (possible) offset else offset * .18f
                                    },
                                    onRelease = { _, offset, velocity, extent ->
                                        coverJob =
                                            gestures.launch {
                                                val direction = swipeCommit(offset, velocity, extent, distanceFraction = .28f)
                                                val target = state.chapterIndex + direction
                                                if (direction != 0 && target in 0..book.chapters.lastIndex) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    val away = -direction * extent * 1.15f
                                                    if (!reduce)
                                                        animate(coverX, away, velocity, tween(140, easing = FastOutLinearInEasing)) { value, _ ->
                                                            coverX = value
                                                        }
                                                    onChapter(target)
                                                    coverX = if (reduce) 0f else -away
                                                    animate(coverX, 0f, animationSpec = spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                                                        coverX = value
                                                    }
                                                } else {
                                                    animate(coverX, 0f, velocity, spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium)) { value, _ ->
                                                        coverX = value
                                                    }
                                                }
                                            }
                                    },
                                )
                                .pointerInput(book.book.id) {
                                    detectTapGestures(
                                        onDoubleTap = { position ->
                                            val forward = position.x > size.width / 2
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSkip(forward)
                                            skipFlash = forward to System.nanoTime()
                                        }
                                    )
                                }
                                .shadow(
                                    if (state.playing) 36.dp else 20.dp,
                                    RoundedCornerShape(16.dp),
                                    ambientColor = Color.Black,
                                    spotColor = Color.Black,
                                )
                        ) {
                            Cover(book.book, Modifier.fillMaxSize(), corner = 16.dp)
                            skipFlash?.let { (forward, stamp) ->
                                SkipFlash(
                                    forward,
                                    stamp,
                                    preferences.skipSeconds,
                                    Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart),
                                )
                            }
                            if (hintAlpha > 0f)
                                Text(
                                    "Swipe for chapters · double-tap to skip",
                                    color = PlayerInk,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                            .padding(bottom = 14.dp)
                                            .graphicsLayer { alpha = hintAlpha }
                                            .clip(RoundedCornerShape(50))
                                            .background(Color.Black.copy(alpha = .55f))
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                )
                        }
                    }
                }
                @Composable
                fun stage(modifier: Modifier, side: Dp, @Suppress("UNUSED_PARAMETER") height: Dp) {
                    art(modifier, side)
                }
                val toggleTranscript = { showTranscript = !showTranscript }
                // Computed here: BoxWithConstraints' scope marker hides maxHeight inside nested rows.
                val wideSide =
                    minOf(maxHeight - 100.dp, (maxWidth - 120.dp) * .45f, 440.dp)
                        .coerceAtLeast(160.dp)
                val wideStageHeight = maxHeight - 48.dp
                if (wide) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(
                            Modifier.widthIn(max = 1240.dp).fillMaxSize().padding(horizontal = 36.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(48.dp),
                        ) {
                            stage(Modifier.weight(1f).fillMaxHeight(), wideSide, wideStageHeight)
                            CompositionLocalProvider(LocalPlayerAccent provides accent) {
                            PlayerControls(
                                book,
                                state,
                                preferences,
                                onToggle,
                                onSkip,
                                onChapter,
                                onSeek,
                                onSeekBook,
                                onTool,
                                showTranscript,
                                toggleTranscript,
                                onDetails,
                                onOpenPdf,
                                Modifier.weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(vertical = 24.dp),
                            )
                            }
                        }
                    }
                } else {
                    // Scroll remains only as a fallback for very large font sizes.
                    Column(
                        Modifier.fillMaxSize().verticalScroll(playerScroll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(8.dp))
                        stage(
                            Modifier.fillMaxWidth().height(artworkSize + 24.dp),
                            artworkSize,
                            artworkSize + 24.dp,
                        )
                        Spacer(Modifier.height(14.dp))
                        CompositionLocalProvider(LocalPlayerAccent provides accent) {
                        PlayerControls(
                            book,
                            state,
                            preferences,
                            onToggle,
                            onSkip,
                            onChapter,
                            onSeek,
                            onSeekBook,
                            onTool,
                            showTranscript,
                            toggleTranscript,
                            onDetails,
                            onOpenPdf,
                            Modifier.widthIn(max = 520.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .onSizeChanged {
                                    controlsHeight = with(density) { it.height.toDp() }
                                },
                        )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControls(
    book: LibraryBook,
    state: PlayerSnapshot,
    prefs: Preferences,
    onToggle: () -> Unit,
    onSkip: (Boolean) -> Unit,
    onChapter: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBook: (Long) -> Unit,
    onTool: (String) -> Unit,
    transcriptOn: Boolean,
    onTranscript: () -> Unit,
    onDetails: () -> Unit,
    onOpenPdf: () -> Unit,
    modifier: Modifier,
) {
    val reduce = prefs.reducedMotion
    val title = shelfTitle(book.book.title).replace(Regex("^\\d+\\.\\s*"), "")
    val chapter = book.orderedChapters.getOrNull(state.chapterIndex)
    val fullPosition = bookPositionMs(book, state)
    val remaining = (book.durationMs - fullPosition).coerceAtLeast(0)
    var expanded by remember(book.book.id) { mutableStateOf(false) }
    var drag by remember(book.book.id, state.chapterIndex) { mutableStateOf<Float?>(null) }
    var pendingSeek by remember(book.book.id, state.chapterIndex) { mutableStateOf<Float?>(null) }
    LaunchedEffect(pendingSeek) {
        if (pendingSeek != null) {
            delay(1200)
            pendingSeek = null
        }
    }
    LaunchedEffect(state.position) {
        pendingSeek?.let { if (abs(state.position - it) < 1200) pendingSeek = null }
    }
    val smoothed =
        remember(book.book.id, state.chapterIndex) { Animatable(state.position.toFloat()) }
    LaunchedEffect(state.position, state.playing, reduce) {
        if (!reduce && state.playing && abs(state.position - smoothed.value) < 2000)
            smoothed.animateTo(state.position.toFloat(), tween(400, easing = LinearEasing))
        else smoothed.snapTo(state.position.toFloat())
    }
    val shown = drag ?: pendingSeek ?: state.position.toFloat()
    val speedText = "${"%.2f".format(state.speed).trimEnd('0').trimEnd('.')}×"
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Identity: the chapter leads, the book sits quietly underneath.
        Text(
            "CHAPTER ${state.chapterIndex + 1} OF ${book.chapters.size}",
            color = PlayerMuted.copy(alpha = .75f),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.6.sp,
        )
        AnimatedContent(
            targetState = state.chapterIndex to chapter?.title.orEmpty(),
            transitionSpec = {
                if (reduce) ContentTransform(EnterTransition.None, ExitTransition.None)
                else
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                        fadeOut(tween(120))
            },
            label = "Chapter change",
        ) { (_, text) ->
            Text(
                text.ifBlank { "Chapter ${state.chapterIndex + 1}" },
                color = PlayerInk,
                fontSize = 24.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.4).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "${title.substringBefore(":")}  ·  ${book.book.author}",
            Modifier.padding(top = 4.dp).semantics {
                heading()
                contentDescription = book.book.title
            },
            color = PlayerMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Secondary destinations as pills, like the reference player.
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayerPill(
                    null,
                    Icons.AutoMirrored.Rounded.FormatListBulleted,
                    "Chapter navigation and queue",
                ) {
                    onTool("chapters")
                }
                PlayerPill(
                    "Transcript",
                    Icons.Rounded.Subtitles,
                    if (transcriptOn) "Show cover" else "Show transcript",
                    active = transcriptOn,
                    click = onTranscript,
                )
                PlayerPill("Listen log", Icons.Rounded.History, "Open listen log") { onTool("log") }
                if (book.book.companionPdfUri != null) {
                    PlayerPill("Read along", Icons.Rounded.MenuBook, "Open companion PDF", click = onOpenPdf)
                }
                PlayerPill("Details", Icons.Rounded.Info, "Open book details", click = onDetails)
            }
        }

        // Chapter timeline; the middle label shows the whole book and expands a book-wide scrubber.
        Spacer(Modifier.height(18.dp))
        FineSlider(
            drag ?: pendingSeek ?: smoothed.value,
            state.duration.toFloat(),
            { drag = it },
            {
                drag?.let {
                    pendingSeek = it
                    onSeek(it.toLong())
                }
                drag = null
            },
            "Chapter progress",
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { TimeLabel(time(shown.toLong())) }
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .clickable(onClickLabel = "Seek across the book") { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${durationLabel((remaining / state.speed).toLong())} left",
                    color = PlayerInk.copy(alpha = .85f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    "Book progress",
                    tint = PlayerMuted,
                    modifier = Modifier.padding(start = 2.dp).size(16.dp),
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                TimeLabel("−${time((state.duration - shown.toLong()).coerceAtLeast(0))}")
            }
        }
        AnimatedVisibility(
            expanded,
            enter = if (reduce) EnterTransition.None else expandVertically(tween(250)) + fadeIn(),
            exit = if (reduce) ExitTransition.None else shrinkVertically(tween(200)) + fadeOut(),
        ) {
            var bookDrag by remember { mutableStateOf<Float?>(null) }
            Column(Modifier.padding(top = 10.dp)) {
                Text(
                    "WHOLE BOOK · ${if (book.durationMs > 0) (fullPosition * 100 / book.durationMs).coerceIn(0, 100) else 0}% · FINISH AROUND ${LocalTime.now().plusSeconds((remaining / state.speed / 1000).toLong()).format(DateTimeFormatter.ofPattern("HH:mm"))}",
                    color = PlayerMuted.copy(alpha = .8f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                FineSlider(
                    bookDrag ?: fullPosition.toFloat(),
                    book.durationMs.toFloat(),
                    { bookDrag = it },
                    {
                        bookDrag?.let { onSeekBook(it.toLong()) }
                        bookDrag = null
                    },
                    "Whole book progress",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TimeLabel(time(bookDrag?.toLong() ?: fullPosition))
                    TimeLabel(time(book.durationMs))
                }
            }
        }

        // Transport: a solid light play button anchors the row.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PressIcon(
                Icons.Rounded.SkipPrevious,
                "Previous chapter",
                reduce,
                { onChapter(state.chapterIndex - 1) },
                enabled = state.chapterIndex > 0,
                size = 28.dp,
            )
            SkipButton(false, prefs.skipSeconds, reduce) { onSkip(false) }
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by
                animateFloatAsState(
                    if (pressed) .92f else 1f,
                    if (reduce) snap() else spring(dampingRatio = .65f, stiffness = 550f),
                    label = "Transport press",
                )
            IconButton(
                onToggle,
                Modifier.size(80.dp)
                    .scale(scale)
                    .shadow(12.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .background(PlayerInk, CircleShape),
                interactionSource = interaction,
            ) {
                Crossfade(
                    state.playing,
                    animationSpec = tween(if (reduce) 0 else 140),
                    label = "Play pause",
                ) { playing ->
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (playing) "Pause" else "Play",
                        tint = PlayerScrim,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            SkipButton(true, prefs.skipSeconds, reduce) { onSkip(true) }
            PressIcon(
                Icons.Rounded.SkipNext,
                "Next chapter",
                reduce,
                { onChapter(state.chapterIndex + 1) },
                enabled = state.chapterIndex < book.chapters.lastIndex,
                size = 28.dp,
            )
        }

        // Quiet bottom toolbar for in-the-moment settings.
        HorizontalDivider(
            Modifier.padding(top = 6.dp),
            color = PlayerInk.copy(alpha = .08f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            ToolCell("Speed", "Playback speed", null, speedText, active = false, Modifier.weight(1f)) {
                onTool("speed")
            }
            ToolCell(
                if (state.timerRemaining > 0) time(state.timerRemaining)
                else if (state.endChapter) "Chapter end" else "Sleep",
                "Sleep timer",
                Icons.Rounded.Bedtime,
                active = state.timerRemaining > 0 || state.endChapter,
                modifier = Modifier.weight(1f),
            ) {
                onTool("sleep")
            }
            ToolCell(
                "Bookmark",
                "Add bookmark",
                Icons.Rounded.BookmarkBorder,
                active = false,
                modifier = Modifier.weight(1f),
            ) {
                onTool("bookmark")
            }
            ToolCell(
                "Car mode",
                "Open car mode",
                Icons.Rounded.DirectionsCar,
                active = false,
                modifier = Modifier.weight(1f),
            ) {
                onTool("car")
            }
        }
    }
}

/** "−30" / "+30" bubble that pops and fades on the side of the cover that was double-tapped. */
@Composable
private fun SkipFlash(forward: Boolean, stamp: Long, seconds: Int, modifier: Modifier) {
    val fade = remember(stamp) { Animatable(1f) }
    LaunchedEffect(stamp) { fade.animateTo(0f, tween(650, delayMillis = 300)) }
    Box(
        modifier
            .padding(16.dp)
            .size(78.dp)
            .graphicsLayer {
                alpha = fade.value
                scaleX = .85f + .15f * fade.value
                scaleY = scaleX
            }
            .background(Color.Black.copy(alpha = .5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (forward) "+$seconds" else "−$seconds",
            color = PlayerInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Round translucent backing for top-bar controls, matching the book page. */
@Composable
private fun ChromeButton(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Every top-bar control gets the same circle; children are clipped to it, so a 48 dp touch
    // target inside cannot make one button look bigger than another.
    Box(
        modifier.size(CHROME_SIZE).clip(CircleShape).background(PlayerChrome),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private val CHROME_SIZE = 44.dp

/** Accent colour for the current book, used by sliders, active tools and pills. */
internal val LocalPlayerAccent = staticCompositionLocalOf { Gold }

/**
 * A bright, readable version of the cover's dominant colour for accents on the dark player.
 * Covers without a real hue (greys, black and white) keep the Auralis gold.
 */
internal fun readableAccent(tint: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(tint.toArgb(), hsv)
    if (hsv[1] < .12f) return Gold
    hsv[1] = hsv[1].coerceIn(.35f, .65f)
    hsv[2] = .96f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Outlined pill used for the player's secondary destinations. */
@Composable
private fun PlayerPill(
    label: String?,
    icon: ImageVector,
    description: String,
    active: Boolean = false,
    click: () -> Unit,
) {
    val accent = LocalPlayerAccent.current
    val ink = if (active) PlayerScrim else PlayerInk.copy(alpha = .92f)
    Row(
        Modifier.heightIn(min = 40.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) accent else Color.Transparent)
            .border(
                1.dp,
                if (active) accent else PlayerInk.copy(alpha = .22f),
                RoundedCornerShape(50),
            )
            .clickable(role = Role.Button, onClickLabel = description, onClick = click)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Text pills like the reference; only the chapters pill is a bare icon.
        if (label == null) Icon(icon, null, tint = ink, modifier = Modifier.size(18.dp))
        else Text(label, color = ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

fun durationLabel(ms: Long): String {
    val minutes = ms.coerceAtLeast(0) / 60000
    return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
}

@Composable
internal fun TimeLabel(value: String) {
    Text(
        value,
        color = PlayerMuted.copy(alpha = .8f),
        fontSize = 11.sp,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FineSlider(
    value: Float,
    maximum: Float,
    change: (Float) -> Unit,
    finish: () -> Unit,
    description: String,
) {
    val max = maximum.coerceAtLeast(1f)
    val accent = LocalPlayerAccent.current
    Slider(
        value.coerceIn(0f, max),
        change,
        onValueChangeFinished = finish,
        valueRange = 0f..max,
        modifier =
            Modifier.fillMaxWidth().height(36.dp).semantics { contentDescription = description },
        thumb = {
            Box(Modifier.size(14.dp).shadow(3.dp, CircleShape).background(PlayerInk, CircleShape))
        },
        track = { s ->
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                drawRoundRect(
                    Color.White.copy(alpha = .18f),
                    cornerRadius = CornerRadius(size.height / 2),
                )
                val fraction = (s.value / max).coerceIn(0f, 1f)
                drawRoundRect(
                    accent,
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
        },
    )
}

/** One cell of the bottom toolbar. Icon-only cells are labelled so nothing is guessed. */
@Composable
private fun ToolCell(
    label: String,
    description: String,
    icon: ImageVector?,
    value: String? = null,
    active: Boolean,
    modifier: Modifier = Modifier,
    click: () -> Unit,
) {
    val accent = LocalPlayerAccent.current
    val ink = if (active) accent else PlayerInk.copy(alpha = .88f)
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClickLabel = description, role = Role.Button, onClick = click)
            .padding(vertical = 10.dp, horizontal = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
            } else {
                Text(
                    value.orEmpty(),
                    color = ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-.2).sp,
                    maxLines = 1,
                )
            }
        }
        Text(
            label,
            color = if (active) accent.copy(alpha = .9f) else PlayerMuted.copy(alpha = .85f),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SkipButton(forward: Boolean, seconds: Int, reduce: Boolean, click: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) .86f else 1f,
            if (reduce) snap() else spring(stiffness = 550f),
            label = "Skip press",
        )
    IconButton(
        click,
        Modifier.size(56.dp).scale(scale).semantics {
            contentDescription =
                if (forward) "Skip forward $seconds seconds" else "Skip back $seconds seconds"
        },
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Replay,
                null,
                tint = PlayerInk,
                modifier = Modifier.size(40.dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
            )
            Text(
                seconds.toString(),
                color = PlayerInk,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun PressIcon(
    icon: ImageVector,
    description: String,
    reduce: Boolean,
    click: () -> Unit,
    tint: Color = PlayerInk,
    enabled: Boolean = true,
    size: Dp = 26.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by
        animateFloatAsState(
            if (pressed) .84f else 1f,
            if (reduce) snap() else spring(stiffness = 550f),
            label = "Control press",
        )
    IconButton(
        click,
        Modifier.size(48.dp).scale(scale),
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(
            icon,
            description,
            tint = tint.copy(alpha = if (enabled) tint.alpha else .22f),
            modifier = Modifier.size(size),
        )
    }
}

@Composable
private fun ListeningGlyph(playing: Boolean, reduce: Boolean) {
    val levels =
        if (playing && !reduce) {
            val transition = rememberInfiniteTransition(label = "Listening indicator")
            List(3) { i ->
                transition
                    .animateFloat(
                        .3f,
                        1f,
                        infiniteRepeatable(
                            tween(550 + i * 150, easing = FastOutSlowInEasing),
                            RepeatMode.Reverse,
                            StartOffset(i * 130),
                        ),
                        label = "Listening bar $i",
                    )
                    .value
            }
        } else listOf(.45f, .8f, .6f)
    Row(
        Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        levels.forEach { level ->
            Box(
                Modifier.width(2.dp)
                    .height(14.dp * level)
                    .background(PlayerInk.copy(alpha = .7f), RoundedCornerShape(2.dp))
            )
        }
    }
}
