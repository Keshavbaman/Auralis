@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.*
import app.auralis.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DetailedStatisticsScreen(vm: AuralisViewModel) {
    val rows by vm.segments.collectAsStateWithLifecycle()
    val books by vm.catalogue.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val today = rememberListeningDate()
    val data by
        produceState<ListeningInsights?>(null, rows, books, prefs.streakMinutes, today) {
            value =
                withContext(Dispatchers.Default) {
                    listeningInsights(rows, books, prefs.streakMinutes, today)
                }
        }
    var editingGoals by remember { mutableStateOf(false) }
    var reportRequest by remember { mutableStateOf<Pair<Boolean, Long>?>(null) }
    val request = reportRequest
    val report by
        produceState<ListeningReport?>(null, request) {
            value = null
            if (request != null)
                value =
                    withContext(Dispatchers.Default) {
                        val end =
                            if (request.first) today.minusYears(request.second)
                            else today.minusMonths(request.second)
                        listeningReport(
                            rows,
                            books,
                            completions,
                            request.first,
                            end,
                            prefs.streakMinutes,
                        )
                    }
        }
    data?.let {
        ListeningSanctuaryContent(
            it,
            books,
            prefs,
            today,
            { editingGoals = true },
            { annual, offset -> reportRequest = annual to offset },
            completedBooks = completions.size,
        )
    }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            CircularProgressIndicator()
        }
    if (editingGoals)
        ListeningGoalEditor(prefs, { editingGoals = false }) { goal, streak ->
            vm.settings(prefs.copy(goalMinutes = goal, streakMinutes = streak))
            editingGoals = false
        }
    if (request != null) {
        val ready = report
        if (ready != null) WrappedStories(ready, { reportRequest = null }) { vm.message.value = it }
        else
            AlertDialog(
                onDismissRequest = { reportRequest = null },
                title = { Text("Preparing your listening report") },
                text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
                confirmButton = { TextButton({ reportRequest = null }) { Text("Cancel") } },
            )
    }
}

@Composable
fun ListeningGoalEditor(prefs: Preferences, dismiss: () -> Unit, save: (Int, Int) -> Unit) {
    var goal by remember { mutableStateOf(prefs.goalMinutes.toString()) }
    var streak by remember { mutableStateOf(prefs.streakMinutes.toString()) }
    val g = goal.toIntOrNull()?.takeIf { it in 1..1440 }
    val s = streak.toIntOrNull()?.takeIf { it in 1..1440 }
    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                "Make time for a story",
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Choose your daily ritual. Your listening history stays with you.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(10, 15, 30, 45, 60).forEach { minutes ->
                        FilterChip(
                            g == minutes,
                            { goal = minutes.toString() },
                            label = { Text("$minutes min") },
                        )
                    }
                }
                OutlinedTextField(
                    goal,
                    { goal = it },
                    label = { Text("Daily goal · minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = g == null,
                    supportingText = { if (g == null) Text("Choose 1–1440 minutes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    streak,
                    { streak = it },
                    label = { Text("Streak threshold · minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = s == null,
                    supportingText = {
                        Text(
                            if (s == null) "Choose 1–1440 minutes"
                            else "Actual listening needed for a streak day"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                { if (g != null && s != null) save(g, s) },
                enabled = g != null && s != null,
            ) {
                Text("Save goal")
            }
        },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}

@Composable
fun StatLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
