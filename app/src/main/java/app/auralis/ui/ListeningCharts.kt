package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import app.auralis.data.ListeningSegmentEntity
import app.auralis.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ListeningCalendar(end: LocalDate, daily: Map<LocalDate, Long>, onDay: (LocalDate) -> Unit) {
    val range = remember(end) { listeningCalendar(end) }
    val scroll = rememberScrollState()
    // Latest weeks are visible first; all 26–28 columns remain accessible.
    LaunchedEffect(end, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${range.start.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} – ${end.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
            style = MaterialTheme.typography.labelMedium,
        )
        Row(Modifier.padding(top = 12.dp)) {
            Column(
                Modifier.padding(top = 24.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(it, Modifier.height(22.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                Modifier.horizontalScroll(scroll),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                range.weeks.forEachIndexed { index, week ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val monthStart =
                            week.firstOrNull { it.dayOfMonth <= 7 && range.contains(it) }
                        Text(
                            if (index == 0 || monthStart != null)
                                (monthStart ?: week.first()).format(
                                    DateTimeFormatter.ofPattern("MMM")
                                )
                            else "",
                            Modifier.width(22.dp).height(20.dp),
                            style = MaterialTheme.typography.labelSmall,
                            softWrap = false,
                        )
                        week.forEach { date ->
                            val value = daily[date] ?: 0L
                            val level =
                                when {
                                    value == 0L -> 0
                                    value < 300000 -> 1
                                    value < 900000 -> 2
                                    value < 2700000 -> 3
                                    else -> 4
                                }
                            Box(
                                Modifier.size(22.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (!range.contains(date)) Color.Transparent
                                        else heatColor(level)
                                    )
                                    .then(
                                        if (range.contains(date))
                                            Modifier.semantics {
                                                    contentDescription =
                                                        "$date: ${listeningDuration(value)} listening"
                                                }
                                                .clickable { onDay(date) }
                                        else Modifier
                                    )
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Less", style = MaterialTheme.typography.labelSmall)
            (0..4).forEach {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(heatColor(it)))
            }
            Text(
                "More · Swipe to explore",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "0 · under 5 · 5–14 · 15–44 · 45+ min",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun heatColor(level: Int): Color =
    if (level == 0) MaterialTheme.colorScheme.surfaceContainerHighest
    else MaterialTheme.colorScheme.primary.copy(alpha = listOf(0f, .25f, .45f, .7f, 1f)[level])

@Composable
fun ActivityBars(values: List<Pair<String, Long>>, goalMs: Long = 0) {
    var selected by remember(values) { mutableStateOf<Int?>(null) }
    val max = maxOf(values.maxOfOrNull { it.second } ?: 0L, goalMs, 60000L)
    val muted = MaterialTheme.colorScheme.outlineVariant
    val accent = MaterialTheme.colorScheme.primary
    Surface(shape = AuralisShape.panel, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp)) {
            Text(
                selected?.let { "${values[it].first} · ${listeningDuration(values[it].second)}" }
                    ?: "${listeningDuration(values.sumOf { it.second })} in this period",
                style = MaterialTheme.typography.titleMedium,
            )
            Row(Modifier.fillMaxWidth().padding(top = 16.dp).height(180.dp)) {
                Column(
                    Modifier.width(58.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf(max, max / 2, 0L).forEach {
                        Text(
                            listeningDuration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(Modifier.fillMaxSize().padding(bottom = 24.dp)) {
                        listOf(0f, .5f, 1f).forEach { y ->
                            drawLine(
                                muted.copy(alpha = .4f),
                                androidx.compose.ui.geometry.Offset(0f, size.height * y),
                                androidx.compose.ui.geometry.Offset(size.width, size.height * y),
                                1.dp.toPx(),
                            )
                        }
                        if (goalMs > 0) {
                            val y = size.height * (1f - goalMs.toFloat() / max)
                            drawLine(
                                accent.copy(alpha = .6f),
                                androidx.compose.ui.geometry.Offset(0f, y),
                                androidx.compose.ui.geometry.Offset(size.width, y),
                                1.dp.toPx(),
                                pathEffect =
                                    androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(8f, 8f)
                                    ),
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        values.forEachIndexed { i, (label, value) ->
                            Column(
                                Modifier.weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selected = i }
                                    .semantics {
                                        contentDescription =
                                            "$label, ${listeningDuration(value)} actual listening. Tap for details."
                                    },
                                verticalArrangement = Arrangement.Bottom,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height((156f * value / max).coerceAtLeast(2f).dp)
                                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                        .background(
                                            accent.copy(
                                                alpha =
                                                    if (value == 0L) .1f
                                                    else if (selected == i) 1f else .65f
                                            )
                                        )
                                )
                                Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
                                    if (values.size <= 12 || i % 5 == 0 || i == values.lastIndex)
                                        Text(
                                            label.substringBefore(" "),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                }
                            }
                        }
                    }
                }
            }
            if (values.isNotEmpty())
                Slider(
                    value = (selected ?: values.lastIndex).toFloat(),
                    onValueChange = { selected = it.toInt().coerceIn(values.indices) },
                    valueRange = 0f..values.lastIndex.coerceAtLeast(1).toFloat(),
                    steps = (values.size - 2).coerceAtLeast(0),
                    enabled = values.size > 1,
                    modifier =
                        Modifier.heightIn(min = 48.dp).semantics {
                            contentDescription = "Choose listening chart date"
                            stateDescription =
                                selected?.let {
                                    "${values[it].first}: ${listeningDuration(values[it].second)}"
                                } ?: "All dates"
                        },
                )
            Text(
                if (goalMs > 0) "Dashed line · ${goalMs / 60000} min daily goal"
                else "Actual listening time · Tap a bar or use the date slider",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Expensive history reductions never block navigation, chart drawing or scrolling. */
@Composable
fun rememberListeningStats(
    rows: List<ListeningSegmentEntity>,
    threshold: Int,
    today: LocalDate,
): ListeningStats {
    val result by
        produceState(statistics(emptyList(), threshold, today), rows, threshold, today) {
            value = withContext(Dispatchers.Default) { statistics(rows, threshold, today) }
        }
    return result
}
