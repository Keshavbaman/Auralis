package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.*
import app.auralis.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*

data class HistoryEntry(
    val id: String,
    val book: BookEntity?,
    val date: LocalDate,
    val started: String,
    val elapsed: Long,
    val content: Long,
    val speed: Float,
)

@Composable
fun HistoryScreen(vm: AuralisViewModel, back: () -> Unit, openBook: (String) -> Unit = {}) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val books by vm.catalogue.collectAsStateWithLifecycle()
    val entries by
        produceState<List<HistoryEntry>?>(null, rows, books) {
            value =
                withContext(Dispatchers.Default) {
                    val byId = books.associateBy { it.book.id }
                    rows
                        .distinctBy { it.id }
                        .groupBy { it.sessionId }
                        .values
                        .sortedByDescending { it.maxOf { r -> r.endedAt } }
                        .map { session ->
                            val first = session.minBy { it.startedAt }
                            val elapsed = session.sumOf { it.elapsedMs }
                            val content = session.sumOf { it.contentMs }
                            val zone = runCatching {
                                ZoneId.of(first.zoneId)
                            }.getOrDefault(ZoneId.systemDefault())
                            HistoryEntry(
                                first.sessionId,
                                byId[first.bookId]?.book,
                                LocalDate.parse(first.date),
                                Instant.ofEpochMilli(first.startedAt)
                                    .atZone(zone)
                                    .format(DateTimeFormatter.ofPattern("h:mm a")),
                                elapsed,
                                content,
                                if (elapsed > 0) content.toFloat() / elapsed else 1f,
                            )
                        }
                }
        }
    if (entries == null)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    else HistoryContent(entries!!, back, openBook)
}

@Composable
fun HistoryContent(entries: List<HistoryEntry>, back: () -> Unit, openBook: (String) -> Unit) {
    val today = rememberListeningDate()
    val groups =
        remember(entries) { entries.groupBy { it.date }.toSortedMap(compareByDescending { it }) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                RefinedHeader("Listening history", back, "The stories that stayed with you.")
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("history-scroll"),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "summary") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Icon(
                                Icons.Rounded.History,
                                null,
                                Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    hours(entries.sumOf { it.elapsed }),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Time spent listening",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${entries.size} sessions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (entries.isEmpty())
                    item(key = "empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Your story starts here",
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "Listening sessions appear after you play an audiobook.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                groups.forEach { (date, sessions) ->
                    item(key = "date-$date") {
                        Text(
                            when (date) {
                                today -> "Today"
                                today.minusDays(1) -> "Yesterday"
                                else -> date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
                            },
                            Modifier.padding(top = 12.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(sessions, key = { it.id }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable(enabled = entry.book != null) {
                                        entry.book?.let { openBook(it.id) }
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                if (entry.book != null)
                                    Cover(entry.book, Modifier.size(64.dp), corner = 12.dp)
                                else
                                    Icon(
                                        Icons.Rounded.AutoStories,
                                        null,
                                        Modifier.size(42.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                Column(
                                    Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Text(
                                        entry.book?.title ?: "Removed book",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        "${entry.started} · ${hours(entry.elapsed)} listened",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        "${hours(entry.content)} content · ${"%.2f".format(entry.speed)}×",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (entry.book != null)
                                    Icon(
                                        Icons.Rounded.ChevronRight,
                                        null,
                                        Modifier.padding(top = 4.dp).size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}
