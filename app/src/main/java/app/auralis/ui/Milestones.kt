package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.domain.*
import kotlin.math.*

/** Original, resolution-independent medal geometry, with twelve individually drawn symbols. */
@Composable
fun Medal(key: String, unlocked: Boolean, modifier: Modifier = Modifier) {
    val keys = badges.map { it.key }
    val index = keys.indexOf(key).coerceAtLeast(0)
    val colors =
        listOf(
            Color(0xFFF5BE70),
            Color(0xFF9D9AEB),
            Color(0xFFEF9865),
            Color(0xFF80BCA7),
            Color(0xFFD8B570),
            Color(0xFF8DBDD3),
            Color(0xFF93BB8A),
            Color(0xFFB6A1DD),
            Color(0xFFE39DA8),
            Color(0xFF8ED5CB),
            Color(0xFF9BAAE5),
            Color(0xFFEAC580),
        )
    val accent = if (unlocked) colors[index] else Color(0xFF77777D)
    Canvas(
        modifier.semantics {
            contentDescription =
                "${badges.getOrNull(index)?.title} ${if (unlocked) "unlocked" else "locked"} badge"
        }
    ) {
        val unit = minOf(size.width, size.height) / 100
        withTransform({
            translate((size.width - 100 * unit) / 2, (size.height - 100 * unit) / 2)
            scale(unit, unit, Offset.Zero)
        }) {
            val shield =
                Path().apply {
                    moveTo(50f, 3f)
                    lineTo(89f, 24f)
                    lineTo(89f, 63f)
                    quadraticTo(83f, 84f, 50f, 97f)
                    quadraticTo(17f, 84f, 11f, 63f)
                    lineTo(11f, 24f)
                    close()
                }
            drawPath(
                shield,
                Brush.linearGradient(
                    listOf(accent.copy(alpha = .35f), accent.copy(alpha = .06f)),
                    Offset(0f, 0f),
                    Offset(100f, 100f),
                ),
            )
            drawPath(shield, accent.copy(alpha = if (unlocked) .9f else .4f), style = Stroke(2f))
            drawCircle(accent.copy(alpha = .08f), 29f, Offset(50f, 48f))
            fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = 3f) =
                drawLine(accent, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
            fun path(vararg xy: Float) {
                val p =
                    Path().apply {
                        moveTo(xy[0], xy[1])
                        for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
                    }
                drawPath(
                    p,
                    accent,
                    style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            when (key) {
                "early" -> {
                    drawArc(
                        accent,
                        180f,
                        180f,
                        false,
                        Offset(34f, 36f),
                        androidx.compose.ui.geometry.Size(32f, 32f),
                        style = Stroke(3f),
                    )
                    line(28f, 53f, 72f, 53f)
                    for (i in 0..4) {
                        val a = PI + i * PI / 4
                        line(
                            50f + 25 * cos(a).toFloat(),
                            52f + 25 * sin(a).toFloat(),
                            50f + 31 * cos(a).toFloat(),
                            52f + 31 * sin(a).toFloat(),
                        )
                    }
                }
                "night" -> {
                    val moon =
                        Path().apply {
                            moveTo(61f, 24f)
                            cubicTo(20f, 26f, 25f, 78f, 65f, 67f)
                            cubicTo(41f, 60f, 42f, 36f, 61f, 24f)
                        }
                    drawPath(moon, accent)
                    drawCircle(accent, 3f, Offset(68f, 34f))
                    drawCircle(accent, 2f, Offset(73f, 44f))
                }
                "fire" -> {
                    val fire =
                        Path().apply {
                            moveTo(51f, 22f)
                            cubicTo(49f, 42f, 73f, 43f, 67f, 62f)
                            cubicTo(59f, 82f, 26f, 69f, 34f, 50f)
                            lineTo(43f, 36f)
                            lineTo(45f, 52f)
                            close()
                        }
                    drawPath(fire, accent)
                    drawCircle(Color(0xFF282323), 6f, Offset(51f, 62f))
                }
                "dedicated" -> {
                    drawCircle(accent, 20f, Offset(50f, 48f), style = Stroke(3f))
                    path(39f, 49f, 47f, 57f, 63f, 39f)
                    line(35f, 69f, 30f, 82f)
                    line(65f, 69f, 70f, 82f)
                }
                "centurion" -> {
                    path(32f, 65f, 32f, 33f, 68f, 33f, 68f, 65f, 32f, 65f)
                    line(40f, 33f, 40f, 25f)
                    line(60f, 33f, 60f, 25f)
                    for (i in 0..2) for (j in 0..1) drawCircle(
                        accent,
                        2.5f,
                        Offset(40f + i * 10, 44f + j * 12),
                    )
                }
                "first",
                "bookworm",
                "librarian" -> {
                    path(
                        50f,
                        36f,
                        34f,
                        30f,
                        27f,
                        33f,
                        27f,
                        65f,
                        34f,
                        62f,
                        50f,
                        68f,
                        66f,
                        62f,
                        73f,
                        65f,
                        73f,
                        33f,
                        66f,
                        30f,
                        50f,
                        36f,
                        50f,
                        68f,
                    )
                    if (key == "bookworm") {
                        path(34f, 45f, 42f, 48f)
                        path(34f, 53f, 42f, 56f)
                        path(58f, 48f, 66f, 45f)
                    }
                    if (key == "librarian") {
                        line(23f, 76f, 77f, 76f)
                        line(34f, 22f, 66f, 22f)
                        drawCircle(accent, 3f, Offset(50f, 16f))
                    }
                }
                "marathon" -> {
                    drawCircle(accent, 6f, Offset(57f, 28f))
                    path(38f, 44f, 49f, 38f, 61f, 47f, 73f, 44f)
                    path(50f, 39f, 44f, 57f, 30f, 68f)
                    path(44f, 57f, 59f, 62f, 64f, 75f)
                }
                "speed" -> {
                    path(27f, 36f, 44f, 49f, 27f, 62f)
                    path(48f, 36f, 65f, 49f, 48f, 62f)
                    line(31f, 73f, 70f, 73f)
                    line(24f, 25f, 56f, 25f)
                }
                "explorer" -> {
                    drawCircle(accent, 24f, Offset(50f, 49f), style = Stroke(3f))
                    path(60f, 32f, 54f, 54f, 38f, 66f, 44f, 44f, 60f, 32f)
                    drawCircle(accent, 3f, Offset(50f, 49f))
                }
                "weekend" -> {
                    path(27f, 36f, 73f, 36f, 73f, 69f, 27f, 69f, 27f, 36f)
                    line(36f, 29f, 36f, 42f)
                    line(64f, 29f, 64f, 42f)
                    line(28f, 47f, 72f, 47f)
                    drawCircle(accent, 4f, Offset(42f, 58f))
                    drawCircle(accent, 4f, Offset(60f, 58f))
                }
            }
            if (unlocked) {
                drawCircle(accent, 2f, Offset(50f, 85f))
                drawCircle(accent.copy(alpha = .5f), 1.5f, Offset(43f, 83f))
                drawCircle(accent.copy(alpha = .5f), 1.5f, Offset(57f, 83f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrophyRoom(vm: AuralisViewModel, back: () -> Unit) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val books by vm.library.collectAsStateWithLifecycle()
    val completed by vm.completions.collectAsStateWithLifecycle()
    val unlocked by vm.achievements.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Badge?>(null) }
    val haptic = LocalHapticFeedback.current
    var previous by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(unlocked) {
        val now = unlocked.map { it.key }.toSet()
        if (previous != null && (now - previous!!).isNotEmpty() && prefs.milestoneHaptics)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        previous = now
    }
    LazyVerticalGrid(
        GridCells.Adaptive(148.dp),
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AuralisSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                BackTitle("Trophy room", back)
                Text(
                    "${unlocked.size} of ${badges.size} milestones · Every story takes you further",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(badges, key = { it.key }) { badge ->
            val earned = unlocked.any { it.key == badge.key }
            val progress =
                remember(badge.key, rows, completed, books, prefs) {
                    badgeProgress(
                        badge.key,
                        rows,
                        completed.size,
                        books,
                        prefs.streakMinutes,
                        prefs.goalMinutes,
                    )
                }
            Surface(
                onClick = { selected = badge },
                shape = AuralisShape.panel,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Medal(badge.key, earned, Modifier.size(108.dp))
                    Text(badge.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (earned) "Unlocked" else progress.label,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (earned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ShelfProgress(
                        if (earned) 1f else progress.fraction,
                        Modifier.fillMaxWidth().height(3.dp),
                    )
                }
            }
        }
    }
    selected?.let { badge ->
        val earned = unlocked.find { it.key == badge.key }
        val progress =
            badgeProgress(
                badge.key,
                rows,
                completed.size,
                books,
                prefs.streakMinutes,
                prefs.goalMinutes,
            )
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Medal(badge.key, earned != null, Modifier.size(160.dp))
                Text(badge.title, style = MaterialTheme.typography.headlineMedium)
                Text(badge.description)
                Text(
                    earned?.let {
                        "Unlocked ${java.time.Instant.ofEpochMilli(it.unlockedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()}"
                    } ?: progress.label,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton({ selected = null }) { Text("Keep exploring") }
            }
        }
    }
}

@Composable
fun BackTitle(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
fun RankEmblem(index: Int, modifier: Modifier = Modifier, earned: Boolean = true) {
    val accent = if (earned) Gold else Color(0xFF6D6D76)
    Canvas(
        modifier.semantics {
            contentDescription = "${levels[index.coerceIn(levels.indices)].first} rank emblem"
        }
    ) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) * .43f
        val vertices = 6 + index
        val path =
            Path().apply {
                for (i in 0 until vertices * 2) {
                    val angle = -PI / 2 + i * PI / vertices
                    val r = radius * if (i % 2 == 0) 1f else .78f
                    val x = center.x + cos(angle).toFloat() * r
                    val y = center.y + sin(angle).toFloat() * r
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        drawPath(
            path,
            Brush.radialGradient(
                listOf(accent.copy(alpha = .35f), accent.copy(alpha = .06f)),
                center,
                radius,
            ),
        )
        drawPath(path, accent, style = Stroke(2.dp.toPx()))
        drawCircle(accent.copy(alpha = .5f), radius * .58f, center, style = Stroke(1.dp.toPx()))
        for (i in 0..index) {
            val x = center.x + (i - index / 2f) * radius * .15f
            drawLine(
                accent,
                Offset(x, center.y - radius * .24f),
                Offset(x, center.y + radius * .24f),
                3.dp.toPx(),
                StrokeCap.Round,
            )
        }
    }
}

@Composable
fun RanksScreen(vm: AuralisViewModel, back: () -> Unit) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val total = rows.sumOf { it.elapsedMs }
    val index = levels.indexOfLast { total >= it.second * 3600000L }.coerceAtLeast(0)
    val next = levels.getOrNull(index + 1)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AuralisSpace.lg),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { BackTitle("Your listening journey", back) }
        item {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RankEmblem(index, Modifier.size(180.dp))
                Text(levels[index].first, style = MaterialTheme.typography.headlineLarge)
                Text(
                    "${hours(total)} of real listening",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (next != null) {
                    ShelfProgress(
                        ((total - levels[index].second * 3600000L).toFloat() /
                                ((next.second - levels[index].second) * 3600000L))
                            .coerceIn(0f, 1f),
                        Modifier.fillMaxWidth().height(6.dp),
                    )
                    Text(
                        "${hours(next.second*3600000L-total)} until ${next.first}",
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else Text("Every world is yours.")
            }
        }
        item {
            SectionTitle(
                "All ranks",
                "Ranks grow with actual listening time, at any playback speed.",
            )
        }
        items(levels.size) { i ->
            ListItem(
                leadingContent = { RankEmblem(i, Modifier.size(64.dp), i <= index) },
                headlineContent = { Text(levels[i].first) },
                supportingContent = { Text("${levels[i].second} hours") },
                trailingContent = {
                    if (i == index) Text("You are here", color = MaterialTheme.colorScheme.primary)
                },
            )
        }
    }
}
