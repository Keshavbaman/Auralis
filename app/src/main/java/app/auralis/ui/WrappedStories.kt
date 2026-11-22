package app.auralis.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.auralis.domain.*
import kotlin.math.*
import kotlinx.coroutines.*

@Composable
fun WrappedStories(report: ListeningReport, close: () -> Unit, message: (String) -> Unit) {
    val reduced = LocalReduceMotion.current
    val pager = rememberPagerState(pageCount = { 8 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var privacy by remember { mutableStateOf(false) }
    var includeTitles by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Triple<Int, Boolean, Boolean>?>(null) }
    val export =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri
            ->
            val request = pending
            if (uri != null && request != null)
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val bitmap =
                                renderReportCard(
                                    report,
                                    request.first,
                                    request.second,
                                    request.third,
                                )
                            try {
                                context.contentResolver.openOutputStream(uri, "wt")!!.use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                        .onSuccess { message("Listening card exported") }
                        .onFailure { message("Could not export: ${it.message}") }
                }
        }
    Dialog(
        onDismissRequest = close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = Night) {
            Column(Modifier.safeDrawingPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${report.label} · WRAPPED",
                        style = MaterialTheme.typography.labelLarge,
                        color = Gold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(close) { Icon(Icons.Rounded.Close, "Close Wrapped") }
                }
                HorizontalPager(pager, Modifier.weight(1f)) { index ->
                    val content = report.page(index)
                    Box(Modifier.fillMaxSize().padding(16.dp).clipStory(index)) {
                        StoryIllustration(index, Modifier.fillMaxSize())
                        Column(
                            Modifier.fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(28.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                "YOUR LISTENING WORLD",
                                style = MaterialTheme.typography.labelMedium,
                                color = Gold,
                            )
                            if (index == 2)
                                report.mostBook?.let {
                                    Cover(
                                        it,
                                        Modifier.padding(vertical = 24.dp).size(144.dp),
                                    )
                                }
                            Spacer(Modifier.height(20.dp))
                            Text(
                                content.first,
                                style =
                                    if (index == 2 || index == 3 || index == 6)
                                        MaterialTheme.typography.headlineLarge
                                    else MaterialTheme.typography.displayMedium,
                                color = Color(0xFFF5F3EF),
                            )
                            Text(
                                content.second,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFCFCDD0),
                                modifier = Modifier.padding(top = 20.dp),
                            )
                            if (report.stats.elapsed == 0L)
                                Text(
                                    "Listen to a book to begin this chapter of your story.",
                                    Modifier.padding(top = 16.dp),
                                    color = Color(0xFFA5A5AA),
                                )
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(8) { index ->
                        Box(
                            Modifier.weight(1f)
                                .height(3.dp)
                                .background(
                                    if (index == pager.currentPage) Gold
                                    else Gold.copy(alpha = .18f)
                                )
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        {
                            scope.launch {
                                if (reduced)
                                    pager.scrollToPage((pager.currentPage - 1).coerceAtLeast(0))
                                else
                                    pager.animateScrollToPage(
                                        (pager.currentPage - 1).coerceAtLeast(0)
                                    )
                            }
                        },
                        enabled = pager.currentPage > 0,
                    ) {
                        Icon(Icons.Rounded.ChevronLeft, "Previous story")
                    }
                    Text("${pager.currentPage+1} / 8", style = MaterialTheme.typography.labelMedium)
                    IconButton(
                        {
                            scope.launch {
                                if (reduced)
                                    pager.scrollToPage((pager.currentPage + 1).coerceAtMost(7))
                                else
                                    pager.animateScrollToPage(
                                        (pager.currentPage + 1).coerceAtMost(7)
                                    )
                            }
                        },
                        enabled = pager.currentPage < 7,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, "Next story")
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton({
                        summary = false
                        privacy = true
                    }) {
                        Text("Save story")
                    }
                    TextButton({
                        summary = true
                        privacy = true
                    }) {
                        Text("Summary")
                    }
                }
            }
        }
        if (privacy)
            AlertDialog(
                onDismissRequest = { privacy = false },
                title = { Text("Choose what to share") },
                text = {
                    Column {
                        Text(
                            "The image includes your listening totals. Book titles, cover artwork, author and genre are included only when you select the option below."
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(includeTitles, { includeTitles = it })
                            Text("Include private book details", Modifier.weight(1f))
                        }
                        Text(
                            if (summary) "1080 × 1350 summary card" else "1080 × 1920 story card",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
                confirmButton = {
                    TextButton({
                        pending =
                            Triple(if (summary) 7 else pager.currentPage, summary, includeTitles)
                        privacy = false
                        export.launch(
                            "auralis-${report.label}-${if(summary) "summary" else "story-${pager.currentPage+1}"}.png"
                        )
                    }) {
                        Text("Confirm & save")
                    }
                },
                dismissButton = { TextButton({ privacy = false }) { Text("Cancel") } },
            )
    }
}

private fun Modifier.clipStory(index: Int) =
    this.background(
        Brush.verticalGradient(
            listOf(
                listOf(Color(0xFF393025), Color(0xFF263B39), Color(0xFF302F48), Color(0xFF463137))[
                    index % 4],
                Night,
            )
        ),
        RoundedCornerShape(28.dp),
    )

@Composable
private fun StoryIllustration(index: Int, modifier: Modifier) {
    Canvas(modifier) {
        val center = androidx.compose.ui.geometry.Offset(size.width * .8f, size.height * .18f)
        repeat(6) { i ->
            drawCircle(
                Gold.copy(alpha = .04f + i * .009f),
                size.width * (.15f + i * .11f),
                center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
            )
        }
        repeat(18) { i ->
            val angle = i * 2.4
            val x = size.width * (.1f + ((sin(angle) + 1) / 2 * .8).toFloat())
            val y = size.height * (.08f + ((cos(angle * 1.3) + 1) / 2 * .84).toFloat())
            drawCircle(
                Gold.copy(alpha = .18f),
                if (i % 3 == 0) 3.dp.toPx() else 1.dp.toPx(),
                androidx.compose.ui.geometry.Offset(x, y),
            )
        }
    }
}

/** Shares the same report values as the on-device stories, with bounded, wrapping typography. */
fun renderReportCard(
    report: ListeningReport,
    page: Int,
    summary: Boolean,
    privateDetails: Boolean,
): Bitmap {
    val width = 1080
    val height = if (summary) 1350 else 1920
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader =
        LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(0xFF393025.toInt(), 0xFF17171C.toInt(), 0xFF111113.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f
    paint.color = 0x33F5BE70
    repeat(7) { canvas.drawCircle(900f, 240f, 120f + it * 90f, paint) }
    paint.style = Paint.Style.FILL
    fun text(
        value: String,
        y: Float,
        size: Float,
        color: Int = 0xFFF5F3EF.toInt(),
        bold: Boolean = false,
        maxLines: Int = 5,
    ): Float {
        val tp =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface =
                    android.graphics.Typeface.create(
                        "sans-serif",
                        if (bold) android.graphics.Typeface.BOLD
                        else android.graphics.Typeface.NORMAL,
                    )
            }
        val layout =
            StaticLayout.Builder.obtain(value, 0, value.length, tp, 888)
                .setMaxLines(maxLines)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .setLineSpacing(6f, 1.05f)
                .setIncludePad(false)
                .build()
        canvas.save()
        canvas.translate(96f, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }
    text("A U R A L I S", 100f, 32f, 0xFFF5BE70.toInt(), true)
    text("${report.label} · YOUR LISTENING WORLD", 174f, 27f, 0xFFBBB9BE.toInt())
    var y = if (summary) 330f else 530f
    if (page == 2 && privateDetails && report.mostBook?.artwork != null) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(report.mostBook.artwork, options)
        val cover =
            BitmapFactory.decodeFile(
                report.mostBook.artwork,
                BitmapFactory.Options().apply {
                    inSampleSize =
                        (maxOf(options.outWidth, options.outHeight) / 420).coerceAtLeast(1)
                },
            )
        cover?.let {
            val side = 380f
            canvas.drawBitmap(
                it,
                null,
                android.graphics.RectF(96f, 320f, 96f + side, 320f + side),
                paint,
            )
            it.recycle()
            y = 760f
        }
    }
    val values = report.page(page, privateDetails)
    y =
        text(
            values.first,
            y,
            if (page == 2 || page == 3 || page == 6) 76f else 112f,
            bold = true,
            maxLines = 5,
        ) + 36f
    y = text(values.second, y, 38f, 0xFFCFCDD0.toInt(), maxLines = 5) + 36f
    if (summary) text(report.personality, y, 30f, 0xFFF5BE70.toInt())
    text("Your books. Your world.", height - 172f, 30f, 0xFFF5BE70.toInt())
    text(
        if (privateDetails) "Made with Auralis · Your personal listening story"
        else "Made with Auralis · Book details kept private",
        height - 118f,
        23f,
        0xFFA5A5AA.toInt(),
    )
    return bitmap
}
