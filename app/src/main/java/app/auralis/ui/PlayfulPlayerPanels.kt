package app.auralis.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.auralis.data.LibraryBook
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.round


private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun pace(value: Float) = java.text.DecimalFormat("0.##").format(value) + "×"

@Composable
internal fun SpeedPanel(initial: Float, remainingMs: Long, apply: (Float) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var speed by rememberSaveable { mutableFloatStateOf(initial.coerceIn(.5f, 3f)) }
    fun set(value: Float) {
        speed = (round(value * 20) / 20).coerceIn(.5f, 3f)
        apply(speed)
    }
    val reduce = LocalReduceMotion.current
    val fraction by animateFloatAsState(
        (speed - .5f) / 2.5f,
        if (reduce) snap() else spring(dampingRatio = .78f, stiffness = 260f),
        label = "Rocket position",
    )
    val finish = LocalTime.now().plusSeconds((remainingMs / speed / 1_000).toLong()).format(clockFormat)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(pace(speed), style = MaterialTheme.typography.displaySmall, color = accent, fontWeight = FontWeight.Bold)
                BoxWithConstraints(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.CenterStart) {
                    Canvas(Modifier.fillMaxSize()) {
                        val start = 20.dp.toPx()
                        val span = (size.width - start * 2).coerceAtLeast(0f)
                        for (mark in 0..10) {
                            val point = Offset(start + span * mark / 10, center.y)
                            drawCircle(accent.copy(alpha = if (mark / 10f <= fraction) .75f else .22f), 2.dp.toPx(), point)
                        }
                    }
                    val travel = (maxWidth - 40.dp).coerceAtLeast(0.dp)
                    Box(
                        Modifier.size(40.dp).graphicsLayer { translationX = travel.toPx() * fraction.coerceIn(0f, 1f) }
                            .background(accent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.RocketLaunch, null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Text("Finish around $finish", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedIconButton({ set(speed - .05f) }, enabled = speed > .5f) { Icon(Icons.Rounded.Remove, "Slower") }
            Slider(
                speed,
                { speed = round(it * 20) / 20 },
                onValueChangeFinished = { apply(speed) },
                valueRange = .5f..3f,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Playback speed" },
            )
            OutlinedIconButton({ set(speed + .05f) }, enabled = speed < 3f) { Icon(Icons.Rounded.Add, "Faster") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(.75f, 1f, 1.5f, 2f).forEach { value ->
                FilterChip(
                    selected = speed == value,
                    onClick = { set(value) },
                    label = { Text(pace(value), maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
internal fun SleepPanel(
    timerRemaining: Long,
    endChapter: Boolean,
    chapterLeftMs: Long,
    fade: Boolean,
    setFade: (Boolean) -> Unit,
    sleep: (Int) -> Unit,
    extend: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    var minutes by rememberSaveable { mutableIntStateOf(15) }
    var custom by rememberSaveable { mutableStateOf(false) }
    val reduce = LocalReduceMotion.current
    val orbit by animateFloatAsState(
        if (minutes < 0) 1f else minutes / 120f,
        if (reduce) snap() else spring(dampingRatio = .72f, stiffness = 220f),
        label = "Sleep orbit",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Box(Modifier.fillMaxWidth().heightIn(min = 112.dp)) {
                Canvas(Modifier.matchParentSize()) {
                    val radius = size.height * .31f
                    val hub = Offset(size.width * .19f, center.y)
                    drawCircle(accent.copy(alpha = .25f), radius, hub, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
                    drawCircle(accent.copy(alpha = .65f), radius * .56f, hub)
                    val angle = orbit * 4.5f - 2.2f
                    drawCircle(
                        accent,
                        radius * .23f,
                        hub + Offset(kotlin.math.cos(angle), kotlin.math.sin(angle)) * radius,
                    )
                }
                Column(Modifier.fillMaxWidth(.64f).align(Alignment.CenterEnd).padding(12.dp)) {
                    Text(
                        when {
                            timerRemaining > 0 -> time(timerRemaining)
                            endChapter -> "End of chapter"
                            minutes < 0 -> "End of chapter"
                            else -> "$minutes min"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (timerRemaining > 0) "Time remaining"
                        else if (endChapter || minutes < 0) "${durationLabel(chapterLeftMs)} left in chapter"
                        else "Stops at ${LocalTime.now().plusMinutes(minutes.toLong()).format(clockFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (timerRemaining > 0 || endChapter) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (timerRemaining > 0) FilledTonalButton(extend) { Text("Add 5 min") }
                TextButton({ sleep(0) }) { Text("Turn off") }
            }
        }
        listOf(5, 10, 15, 30, 45, 60).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { preset ->
                    FilterChip(
                        selected = !custom && minutes == preset,
                        onClick = { custom = false; minutes = preset },
                        label = { Text("$preset min") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = minutes < 0,
                onClick = { custom = false; minutes = -1 },
                label = { Text("End of chapter") },
            )
            FilterChip(
                selected = custom,
                onClick = { custom = !custom; if (minutes < 0) minutes = 25 },
                label = { Text("Custom") },
            )
        }
        if (custom) {
            Slider(
                minutes.toFloat(),
                { minutes = (round(it / 5) * 5).toInt() },
                valueRange = 5f..120f,
                steps = 22,
                modifier = Modifier.semantics { contentDescription = "Custom sleep duration" },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Fade out", style = MaterialTheme.typography.titleSmall)
                Text("Lower volume in the last 10 seconds", style = MaterialTheme.typography.bodySmall)
            }
            Switch(fade, setFade, modifier = Modifier.semantics { contentDescription = "Fade out" })
        }
        Button({ sleep(minutes) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (minutes < 0) "Stop at chapter end" else "Set $minutes minute timer")
        }
    }
}

@Composable
internal fun BookmarkPanel(
    book: LibraryBook,
    chapterTitle: String,
    positionMs: Long,
    quote: String?,
    save: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val hasWriting = title.isNotBlank() || note.isNotBlank()
    val reduce = LocalReduceMotion.current
    val stampAngle by animateFloatAsState(
        if (hasWriting) 0f else -12f,
        if (reduce) snap() else spring(dampingRatio = .55f, stiffness = 280f),
        label = "Bookmark stamp",
    )
    val ticketColor by animateColorAsState(
        if (hasWriting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
        if (reduce) snap() else tween(180),
        label = "Bookmark ticket",
    )
    val ink = MaterialTheme.colorScheme.onPrimary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = ticketColor, contentColor = ink, shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cover(book.book, Modifier.size(48.dp), corner = 7.dp)
                    Column(Modifier.weight(1f)) {
                        Text(chapterTitle, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(time(positionMs), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
                    }
                    Icon(
                        Icons.Rounded.Bookmark, null,
                        Modifier.graphicsLayer { rotationZ = stampAngle }.size(40.dp)
                            .background(ink.copy(alpha = .12f), CircleShape).padding(9.dp),
                    )
                }
                if (!quote.isNullOrBlank()) {
                    HorizontalDivider(color = ink.copy(alpha = .23f))
                    Text("“$quote”", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        OutlinedTextField(title, { title = it.take(80) }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it.take(2_000) }, label = { Text("Note (optional)") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
        Button(
            onClick = { if (!saved) { saved = true; save(title, note) } },
            enabled = !saved,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Save bookmark")
        }
    }
}
