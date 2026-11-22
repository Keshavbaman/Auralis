package app.auralis.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.auralis.data.LibraryBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private data class RenderedPdfPage(val bitmap: Bitmap, val count: Int, val index: Int)

/** Immersive local reader. Playback remains in Media3 while the user reads. */
@Composable
fun PdfReaderScreen(vm: AuralisViewModel, book: LibraryBook, back: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val pageState = rememberSaveable(book.book.id) {
        mutableIntStateOf(book.book.companionPdfPage.coerceAtLeast(0))
    }
    val page = pageState.intValue
    var controlsVisible by rememberSaveable(book.book.id) { mutableStateOf(false) }
    var pdfMenu by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    val zoomState = remember(book.book.id) { mutableFloatStateOf(1f) }
    val panState = remember(book.book.id) { mutableStateOf(Offset.Zero) }
    var pageCount by remember(book.book.id) { mutableIntStateOf(0) }
    var lastBitmap by remember(book.book.id) { mutableStateOf<Bitmap?>(null) }
    var pageTurnBitmap by remember(book.book.id) { mutableStateOf<Bitmap?>(null) }
    var turnDirection by remember(book.book.id) { mutableIntStateOf(1) }
    val turnProgress = remember(book.book.id) { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val drag = remember { mutableFloatStateOf(0f) }
    val reduceMotion = LocalReduceMotion.current

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    fun moveToPage(requested: Int) {
        val lastPage = (pageCount - 1).coerceAtLeast(0)
        val destination = requested.coerceIn(0, lastPage)
        if (destination == page || pageCount == 0) return
        turnDirection = if (destination > page) 1 else -1
        pageTurnBitmap = if (reduceMotion) null else lastBitmap
        if (reduceMotion) drag.floatValue = 0f
        scope.launch { turnProgress.snapTo(0f) }
        zoomState.floatValue = 1f
        panState.value = Offset.Zero

        pageState.intValue = destination
        controlsVisible = false
    }

    val relinkPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onSuccess {
                pageState.intValue = 0
                lastBitmap = null
                pageTurnBitmap = null
                vm.attachCompanionPdf(book.book.id, uri)
            }.onFailure {
                vm.message.value = "Couldn't keep access to that PDF. Choose it again from Files."
            }
        }
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("Remove this PDF?") },
            text = {
                Text("Auralis will forget this PDF and its reading position. The original file in your storage will not be deleted.")
            },
            confirmButton = {
                TextButton({
                    confirmUnlink = false
                    vm.attachCompanionPdf(book.book.id, null)
                    back()
                }) { Text("Remove PDF") }
            },
            dismissButton = { TextButton({ confirmUnlink = false }) { Text("Keep PDF") } },
        )
    }

    LaunchedEffect(controlsVisible, page) {
        if (controlsVisible) {
            delay(3_200)
            controlsVisible = false
        }
    }
    LaunchedEffect(page, book.book.id) {
        delay(400)
        vm.saveCompanionPdfPage(book.book.id, page)
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color(0xFF101012)),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(320)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(480)
        val uriString = book.book.companionPdfUri
        val sessionResult by produceState<Result<PdfRenderSession>?>(null, uriString) {
            value = withContext(Dispatchers.IO) { runCatching { openPdfSession(context, uriString) } }
        }
        val session = sessionResult?.getOrNull()
        DisposableEffect(session) {
            onDispose {
                session?.let { closing ->
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch { closing.close() }
                }
            }
        }
        LaunchedEffect(session, book.book.id) {
            session?.let {
                pageCount = it.pageCount
                if (page >= it.pageCount) pageState.intValue = (it.pageCount - 1).coerceAtLeast(0)
            }
        }
        val rendered by produceState<Result<RenderedPdfPage>?>(null, session, page, widthPx, heightPx) {
            if (session != null) {
                // Reuse the open renderer and a small bounded page cache. This avoids reopening
                // the PDF document and rerasterizing neighboring pages on every page turn.
                value = withContext(Dispatchers.IO) {
                    runCatching { session.render(page, widthPx, heightPx) }
                }
            }
        }
        val result = rendered?.getOrNull()
        val targetPage = result?.takeIf { it.index == page }
        val renderError = sessionResult?.exceptionOrNull() ?: rendered?.exceptionOrNull()

        LaunchedEffect(targetPage?.index, book.book.id) {
            targetPage?.let {
                pageCount = it.count
                lastBitmap = it.bitmap
                if (pageTurnBitmap != null) {
                    turnProgress.snapTo(0f)
                    if (!reduceMotion) turnProgress.animateTo(1f, tween(230))
                    drag.floatValue = 0f
                    pageTurnBitmap = null
                }
            }
        }

        LaunchedEffect(renderError, book.book.id) {
            if (renderError != null) {
                pageTurnBitmap = null
                lastBitmap = null
            }
        }

        LaunchedEffect(session, targetPage?.index, widthPx, heightPx) {
            if (session != null && targetPage != null) {
                withContext(Dispatchers.IO) {
                    session.prefetch(page + 1, widthPx, heightPx)
                    session.prefetch(page - 1, widthPx, heightPx)
                }
            }
        }

        Box(
            Modifier.fillMaxSize()
                .pointerInput(page, pageCount) {
                    detectTapGestures { point ->
                        when {
                            zoomState.floatValue > 1.001f -> controlsVisible = !controlsVisible
                            point.x < size.width * .28f -> moveToPage(page - 1)
                            point.x > size.width * .72f -> moveToPage(page + 1)
                            else -> controlsVisible = !controlsVisible
                        }
                    }
                }
                .pointerInput(page, pageCount) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var travel = Offset.Zero
                        var transformed = zoomState.floatValue > 1f
                        var dragging = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.any { it.isConsumed }) break
                            val pan = event.calculatePan()
                            travel += pan
                            transformed = transformed || event.changes.count { it.pressed } > 1
                            dragging = dragging || transformed ||
                                kotlin.math.abs(travel.x) > viewConfiguration.touchSlop
                            if (dragging) {
                                if (transformed) {
                                    drag.floatValue = 0f
                                    val nextZoom = (zoomState.floatValue * event.calculateZoom()).coerceIn(1f, 4f)
                                    zoomState.floatValue = nextZoom
                                    val x = size.width * (nextZoom - 1f) / 2
                                    val y = size.height * (nextZoom - 1f) / 2
                                    val offset = panState.value + pan
                                    panState.value = Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
                                } else if (!reduceMotion) {
                                    drag.floatValue = (travel.x / size.width).coerceIn(-.85f, .85f)
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        val step = when {
                            transformed -> 0
                            travel.x < -size.width * .16f -> 1
                            travel.x > size.width * .16f -> -1
                            else -> 0
                        }
                        if (step != 0 && page + step in 0 until pageCount) moveToPage(page + step)
                        else drag.floatValue = 0f
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                sessionResult?.isFailure == true || rendered?.isFailure == true -> PdfFailure(
                    message = renderError?.message ?: "Choose the PDF again to restore access.",
                    relink = { relinkPdf.launch(arrayOf("application/pdf")) },
                )
                lastBitmap != null -> {
                    Image(
                        bitmap = lastBitmap!!.asImageBitmap(),
                        contentDescription = "PDF page ${page + 1} for ${book.book.title}",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp).graphicsLayer {
                            scaleX = zoomState.floatValue
                            scaleY = zoomState.floatValue
                            translationX = panState.value.x + if (pageTurnBitmap == null) size.width * drag.floatValue * .28f else 0f
                            translationY = panState.value.y
                        },
                    )
                    val turningPage = pageTurnBitmap
                    if (turningPage != null) {
                        val pivot = if (turnDirection > 0) 0f else 1f
                        Image(
                            bitmap = turningPage.asImageBitmap(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                                .graphicsLayer {
                                    val bend = kotlin.math.abs(drag.floatValue) * .65f
                                    val progress = bend + (1f - bend) * turnProgress.value
                                    rotationY = turnDirection * 88f * progress
                                    transformOrigin = TransformOrigin(pivot, .5f)
                                    cameraDistance = 32f * density.density
                                    alpha = 1f - progress * .22f
                                },
                        )
                    }
                }
                else -> CircularProgressIndicator(color = Color(0xFFF2C47C))
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { -it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 3 },
        ) {
            Row(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = .82f), Color.Transparent))
                ).padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(back) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to audiobook", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text("READING ALONG", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFF2C47C))
                    Text(book.book.title, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                }
                Box {
                    IconButton(onClick = { pdfMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "PDF options", tint = Color.White)
                    }
                    DropdownMenu(expanded = pdfMenu, onDismissRequest = { pdfMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Change PDF") },
                            leadingIcon = { Icon(Icons.Rounded.MenuBook, null) },
                            onClick = {
                                pdfMenu = false
                                relinkPdf.launch(arrayOf("application/pdf"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove PDF from book") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            onClick = { pdfMenu = false; confirmUnlink = true },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { it / 3 },
        ) {
            Column(
                Modifier.fillMaxWidth().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f)))
                ).padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { moveToPage(page - 1) }, enabled = page > 0) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous page", tint = Color.White)
                    }
                    Text(
                        if (pageCount > 0) "${page + 1}  /  $pageCount" else "Page ${page + 1}",
                        Modifier.weight(1f),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(onClick = { moveToPage(page + 1) }, enabled = pageCount == 0 || page + 1 < pageCount) {
                        Icon(Icons.Rounded.SkipNext, "Next page", tint = Color.White)
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                    zoomState.floatValue = (zoomState.floatValue - .25f).coerceAtLeast(1f)
                            if (zoomState.floatValue == 1f) panState.value = Offset.Zero
                        },
                        enabled = zoomState.floatValue > 1f,
                    ) { Icon(Icons.Rounded.ZoomOut, "Zoom out", tint = Color.White) }
                    Text(
                        "${(zoomState.floatValue * 100).toInt()}%",
                        Modifier.widthIn(min = 64.dp),
                        color = Color.White.copy(alpha = .84f),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    IconButton(
                        onClick = { zoomState.floatValue = (zoomState.floatValue + .25f).coerceAtMost(4f) },
                        enabled = zoomState.floatValue < 4f,
                    ) { Icon(Icons.Rounded.ZoomIn, "Zoom in", tint = Color.White) }
                }
                Text(
                    "Swipe or tap the page edges to turn",
                    Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = .62f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private data class PdfPageKey(val index: Int, val width: Int, val height: Int)

/** Keeps the SAF file and PdfRenderer open for the reader lifetime and caches nearby pages only. */
private class PdfRenderSession(
    private val renderer: PdfRenderer,
    private val descriptor: ParcelFileDescriptor,
) {
    val pageCount: Int = renderer.pageCount
    private val cache = object : LruCache<PdfPageKey, RenderedPdfPage>(24 * 1024) {
        override fun sizeOf(key: PdfPageKey, value: RenderedPdfPage): Int =
            (value.bitmap.byteCount / 1024).coerceAtLeast(1)
    }

    @Synchronized
    fun render(index: Int, width: Int, height: Int): RenderedPdfPage {
        check(!closed) { "The PDF reader is closed." }
        val safeIndex = index.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        val key = PdfPageKey(safeIndex, width, height)
        cache.get(key)?.let { return it }
        val page = renderer.openPage(safeIndex)
        try {
            val scale = min(width.toFloat() / page.width, height.toFloat() / page.height)
                .coerceAtLeast(.1f)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt().coerceAtLeast(1),
                (page.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            return try {
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPdfPage(bitmap, pageCount, safeIndex).also { cache.put(key, it) }
            } catch (failure: Throwable) {
                bitmap.recycle()
                throw failure
            }
        } finally {
            page.close()
        }
    }

    fun prefetch(index: Int, width: Int, height: Int) {
        if (index in 0 until pageCount) runCatching { render(index, width, height) }
    }

    @Volatile private var closed = false

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        cache.evictAll()
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }
}

private fun openPdfSession(context: Context, uriString: String?): PdfRenderSession {
    require(!uriString.isNullOrBlank()) { "No PDF is linked to this audiobook." }
    val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uriString), "r")
        ?: error("The PDF can't be opened. Relink it from book details.")
    return try {
        val renderer = PdfRenderer(descriptor)
        require(renderer.pageCount > 0) { "This PDF has no readable pages." }
        PdfRenderSession(renderer, descriptor)
    } catch (failure: Throwable) {
        runCatching { descriptor.close() }
        throw failure
    }
}

@Composable
private fun PdfFailure(message: String, relink: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.MenuBook, null, Modifier.size(36.dp), tint = Color(0xFFF2C47C))
        Text("PDF unavailable", style = MaterialTheme.typography.titleLarge, color = Color.White)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .72f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = relink) { Text("Choose PDF") }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
