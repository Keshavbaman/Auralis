@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.*
import app.auralis.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.*
import kotlinx.coroutines.*

private val ListeningNight = Color(0xFF03131F)
private val ListeningBlue = Color(0xFF57AEF5)
private val ListeningMuted = Color(0xFFBDCFDF)
private val ListeningBorder = Color(0xFF294155)

@Composable
fun StatisticsScreen(vm: AuralisViewModel, back: () -> Unit = {}, detail: (String) -> Unit = {}) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val books by vm.catalogue.collectAsStateWithLifecycle()
    val completed by vm.completions.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val today = rememberListeningDate()
    var more by rememberSaveable { mutableStateOf(false) }
    if (more) {
        BackHandler { more = false }
        Column(Modifier.fillMaxSize()) {
            TextButton({ more = false }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                Text("Your Listening")
            }
            Box(Modifier.weight(1f)) { DetailedStatisticsScreen(vm) }
        }
        return
    }
    val dashboard by
        produceState<ListeningDashboard?>(null, rows, books, prefs.streakMinutes, today) {
            value =
                withContext(Dispatchers.Default) {
                    listeningDashboard(rows, books, prefs.streakMinutes, today)
                }
        }
    val data = dashboard
    if (data == null)
        Box(
            Modifier.fillMaxSize().background(ListeningNight),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = ListeningBlue)
        }
    else
        ListeningDashboardContent(
            data,
            books,
            completed,
            prefs.reducedMotion,
            back,
            detail,
            { more = true },
        )
}

@Composable
fun ListeningDashboardContent(
    data: ListeningDashboard,
    books: List<LibraryBook>,
    completed: List<CompletionEntity>,
    reduced: Boolean,
    back: () -> Unit = {},
    detail: (String) -> Unit = {},
    more: () -> Unit = {},
) {
    var periodName by rememberSaveable { mutableStateOf(ListeningPeriod.MONTHLY.name) }
    val period = ListeningPeriod.valueOf(periodName)
    val panel = data.panels.getValue(period)
    val context = LocalContext.current
    val finished =
        remember(books, completed) {
            val dates = completed.associate { it.bookId to it.completedAt }
            books
                .filter { it.book.id in dates || it.isFinished }
                .sortedByDescending { dates[it.book.id] ?: 0 }
        }
    val palette =
        darkColorScheme(
            background = ListeningNight,
            surface = ListeningNight,
            surfaceContainer = Color(0xFF0C2131),
            primary = ListeningBlue,
            onPrimary = ListeningNight,
            onSurface = Color.White,
            onSurfaceVariant = ListeningMuted,
            outline = ListeningBorder,
        )
    MaterialTheme(colorScheme = palette) {
        Box(Modifier.fillMaxSize().background(ListeningNight)) {
            CenteredScreen(940.dp) {
                Column(Modifier.fillMaxSize().testTag("listening-dashboard")) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                        Text(
                            "Your Listening",
                            Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("listening-scroll"),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        item(key = "periods") {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ListeningPeriod.entries.forEach { p ->
                                    Surface(
                                        onClick = { periodName = p.name },
                                        modifier =
                                            Modifier.weight(1f).semantics {
                                                selected = period == p
                                                role = Role.Tab
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFF304A63),
                                        border =
                                            if (period == p) BorderStroke(2.dp, Color(0xFF2189D8))
                                            else null,
                                    ) {
                                        Text(
                                            p.label,
                                            Modifier.padding(horizontal = 3.dp, vertical = 12.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "hero") {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    panel.title,
                                    color = ListeningMuted,
                                    style = MaterialTheme.typography.labelLarge,
                                    letterSpacing = 1.4.sp,
                                )
                                Text(
                                    hours(panel.valueMs),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Text(
                                    panel.range,
                                    color = ListeningMuted,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        item(key = "chart-$period") {
                            ListeningReferenceChart(panel.buckets, panel.averageMs, reduced, false)
                        }
                        item(key = "share") {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                TextButton({
                                    val text =
                                        "My Auralis listening stats\n${panel.title.lowercase().replaceFirstChar{it.uppercase()}}: ${hours(panel.valueMs)}\n${panel.range}\n${data.weeklyStreak} weeks in a row\nYour books. Your world."
                                    context.startActivity(
                                        Intent.createChooser(
                                            Intent(Intent.ACTION_SEND)
                                                .setType("text/plain")
                                                .putExtra(Intent.EXTRA_TEXT, text),
                                            "Share my stats",
                                        )
                                    )
                                }) {
                                    Icon(
                                        Icons.Rounded.Share,
                                        null,
                                        Modifier.size(26.dp),
                                        tint = Color.White,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Share my stats",
                                        color = Color.White,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                        item(key = "trend") {
                            val current = data.currentMonthMs
                            val previous = data.previousMonthMs
                            OutlineListeningCard {
                                Row(
                                    Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    Icon(
                                        if (data.monthlyChange > 0) Icons.AutoMirrored.Rounded.TrendingUp
                                        else Icons.Rounded.Headphones,
                                        null,
                                        Modifier.size(36.dp),
                                        tint = Color(0xFF7CD583),
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (previous > 0 && data.monthlyChange > 0)
                                                "You’re listening more"
                                            else "Your listening, your pace",
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (previous > 0 && data.monthlyChange > 0)
                                                "${hours(data.monthlyChange)} more than last month"
                                            else "${hours(current)} of listening this month",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = ListeningMuted,
                                        )
                                    }
                                }
                            }
                        }
                        item(key = "weekly") {
                            Column(
                                Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    "Your weekly listening stats",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = ListeningNight,
                                    border = BorderStroke(1.dp, ListeningBorder),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.CalendarMonth,
                                            null,
                                            Modifier.size(40.dp),
                                            tint = ListeningBlue,
                                        )
                                        Text(
                                            if (data.weeklyStreak == 0) "A new week awaits"
                                            else
                                                "${data.weeklyStreak} ${if(data.weeklyStreak==1)"week" else "weeks"} in a row",
                                            style = MaterialTheme.typography.headlineSmall,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            if (data.weeklyStreak == 0)
                                                "Listen on any day to begin your weekly rhythm."
                                            else
                                                "You’ve listened at least 1 day every week for ${data.weeklyStreak} ${if(data.weeklyStreak==1)"week" else "weeks"}.",
                                            color = ListeningMuted,
                                            textAlign = TextAlign.Center,
                                        )
                                        HorizontalDivider(
                                            Modifier.padding(vertical = 8.dp),
                                            color = ListeningBorder,
                                        )
                                        Text(
                                            "${data.activeWeeks} ${if(data.activeWeeks==1)"week" else "weeks"}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleLarge,
                                        )
                                        Text("All-time", color = ListeningMuted)
                                    }
                                }
                            }
                        }
                        item(key = "finished") {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "You’ve finished ${finished.size} ${if(finished.size==1)"title" else "titles"}",
                                    Modifier.padding(horizontal = 16.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (finished.isEmpty())
                                    Text(
                                        "Your finished audiobooks will appear here.",
                                        Modifier.padding(horizontal = 16.dp),
                                        color = ListeningMuted,
                                    )
                                else
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        items(finished, key = { it.book.id }) { book ->
                                            Surface(
                                                onClick = { detail(book.book.id) },
                                                shape = RoundedCornerShape(18.dp),
                                                color = Color(0xFF0D2232),
                                                modifier = Modifier.widthIn(max = 340.dp).width(300.dp),
                                            ) {
                                                Row(
                                                    Modifier.padding(16.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                                ) {
                                                    Cover(book.book, Modifier.size(88.dp), corner = 8.dp)
                                                    Column(
                                                        Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                                    ) {
                                                        Text(
                                                            shelfTitle(book.book.title),
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            book.book.narrator
                                                                .takeIf { it.isNotBlank() }
                                                                ?.let { "Narrated by $it" }
                                                                ?: book.book.author,
                                                            color = ListeningMuted,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                        Text(
                                                            "Audiobook · ${hours(book.durationMs)}",
                                                            color = ListeningMuted,
                                                            style = MaterialTheme.typography.bodySmall,
                                                        )
                                                        completed
                                                            .find { it.bookId == book.book.id }
                                                            ?.let {
                                                                Text(
                                                                    Instant.ofEpochMilli(it.completedAt)
                                                                        .atZone(ZoneId.systemDefault())
                                                                        .format(
                                                                            DateTimeFormatter.ofPattern(
                                                                                "d MMM yyyy"
                                                                            )
                                                                        ),
                                                                    style =
                                                                        MaterialTheme.typography.bodySmall,
                                                                )
                                                            }
                                                    }
                                                }
                                            }
                                        }
                                    }
                            }
                        }
                        item(key = "library-count") {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "IN YOUR LIBRARY",
                                    color = ListeningMuted,
                                    style = MaterialTheme.typography.labelLarge,
                                    letterSpacing = 1.4.sp,
                                )
                                Text(
                                    "${books.size} titles",
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    data.firstListening?.let {
                                        "Started listening ${it.format(DateTimeFormatter.ofPattern("MMM yyyy"))}"
                                    } ?: "Your listening story starts here",
                                    color = ListeningMuted,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                        item(key = "library-growth") {
                            ListeningReferenceChart(
                                data.libraryGrowth.map {
                                    ListeningBucket(it.first.toString(), it.second.toLong())
                                },
                                0,
                                reduced,
                                true,
                            )
                        }
                        item(key = "details") {
                            Column(
                                Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    "Library growth shows when your current books were added. Listening time excludes pauses and buffering.",
                                    color = ListeningMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                OutlinedButton(more, Modifier.fillMaxWidth()) {
                                    Text("Goals, calendar & more insights")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutlineListeningCard(content: @Composable () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = ListeningNight,
        border = BorderStroke(1.dp, ListeningBorder),
        content = content,
    )
}

/** One Canvas per plot: animation invalidates drawing, never rebuilds the screen each frame. */
@Composable
fun ListeningReferenceChart(
    buckets: List<ListeningBucket>,
    average: Long,
    reduced: Boolean,
    growth: Boolean,
) {
    var selected by remember(buckets) { mutableStateOf<Int?>(null) }
    val animation = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(buckets, reduced) {
        if (reduced) animation.snapTo(1f)
        else {
            animation.snapTo(0f)
            animation.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }
    val maxValue = buckets.maxOfOrNull { it.elapsedMs } ?: 0
    val unit = if (growth) 1.0 else if (maxValue < 3600000L) 60000.0 else 3600000.0
    val raw = max(1.0, maxValue / unit)
    val power = 10.0.pow(floor(log10(raw)))
    val nice = listOf(1.0, 2.0, 4.0, 5.0, 10.0).firstOrNull { it * power >= raw } ?: 10.0
    val top = nice * power
    val summary =
        remember(buckets, growth) {
            buckets.joinToString("; ") {
                "${it.label}: ${if(growth)it.elapsedMs.toString()+" titles" else hours(it.elapsedMs)}"
            }
        }
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp)
            .testTag(if (growth) "library-growth-chart" else "listening-chart")
    ) {
        Row(Modifier.fillMaxWidth().height(208.dp)) {
            Canvas(
                Modifier.weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = summary }
                    .pointerInput(buckets) {
                        detectTapGestures { p ->
                            if (buckets.isNotEmpty())
                                selected =
                                    (p.x / size.width * buckets.size)
                                        .toInt()
                                        .coerceIn(buckets.indices)
                        }
                    }
            ) {
                val progress = animation.value
                val floorY = size.height - 2.dp.toPx()
                drawRect(
                    Brush.verticalGradient(
                        listOf(ListeningNight, Color(0xFF073250), ListeningNight)
                    )
                )
                drawLine(
                    ListeningBorder,
                    Offset(0f, floorY),
                    Offset(size.width, floorY),
                    1.dp.toPx(),
                )
                if (average > 0 && !growth) {
                    val y = floorY - (average / unit / top).toFloat() * floorY
                    drawLine(
                        Color(0xFF7596B2),
                        Offset(0f, y),
                        Offset(size.width, y),
                        1.dp.toPx(),
                        pathEffect =
                            PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    )
                }
                if (buckets.isNotEmpty()) {
                    val step = size.width / buckets.size
                    fun x(i: Int) = step * (i + .5f)
                    fun y(i: Int) =
                        floorY - (buckets[i].elapsedMs / unit / top).toFloat() * floorY * progress
                    if (growth) {
                        val line =
                            Path().apply {
                                buckets.indices.forEach {
                                    if (it == 0) moveTo(x(it), y(it)) else lineTo(x(it), y(it))
                                }
                            }
                        val area =
                            Path().apply {
                                addPath(line)
                                lineTo(x(buckets.lastIndex), floorY)
                                lineTo(x(0), floorY)
                                close()
                            }
                        drawPath(
                            area,
                            Brush.verticalGradient(
                                listOf(ListeningBlue.copy(alpha = .7f), Color.Transparent)
                            ),
                        )
                        drawPath(line, ListeningBlue, style = Stroke(3.dp.toPx()))
                        buckets.indices.forEach {
                            drawCircle(Color(0xFFA5D7FF), 4.dp.toPx(), Offset(x(it), y(it)))
                        }
                    } else
                        buckets.indices.forEach { i ->
                            val height = (floorY - y(i)).coerceAtLeast(0f)
                            if (height > 0)
                                drawRoundRect(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFFA0D4FF), Color(0xFF329DF1)),
                                        startY = y(i),
                                        endY = floorY,
                                    ),
                                    Offset(x(i) - step * .27f, y(i)),
                                    Size(step * .54f, height),
                                    CornerRadius(4.dp.toPx()),
                                    alpha = if (selected == null || selected == i) 1f else .5f,
                                )
                        }
                }
            }
            Column(
                Modifier.width(38.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                (4 downTo 0).forEach { i ->
                    val v = top * i / 4
                    Text(
                        if (growth) "${v.toInt()}"
                        else
                            "${if(v%1==0.0)v.toInt().toString() else "%.1f".format(v)}${if(unit==60000.0)"m" else "h"}",
                        color = Color(0xFF8DAAC4),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 38.dp, top = 8.dp)) {
            buckets.forEachIndexed { i, b ->
                Text(
                    if (buckets.size <= 8 || i % 4 == 0) b.label else "",
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = ListeningMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        selected?.let { i ->
            buckets.getOrNull(i)?.let { b ->
                Text(
                    "${b.label}: ${if(growth)"${b.elapsedMs} titles" else hours(b.elapsedMs)}",
                    Modifier.fillMaxWidth().padding(top = 8.dp).semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    textAlign = TextAlign.Center,
                    color = ListeningMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (!growth && maxValue == 0L)
            Text(
                "Listening activity will appear here.",
                Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
                color = ListeningMuted,
                style = MaterialTheme.typography.bodySmall,
            )
    }
}
