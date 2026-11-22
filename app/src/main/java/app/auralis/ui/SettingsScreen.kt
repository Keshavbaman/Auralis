@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.*
import app.auralis.domain.*

@Composable
fun SettingsScreen(
    vm: AuralisViewModel,
    initialSection: String = "Overview",
    back: () -> Unit = {},
) {
    var section by remember(initialSection) { mutableStateOf(initialSection) }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    if (section == "App icon") {
        AppIconScreen(vm) { section = "Appearance" }
        return
    }
    if (section == "Overview") {
        SettingsOverview(back) { section = it }
        return
    }
    val segments by vm.segments.collectAsStateWithLifecycle()
    val unlocked by vm.achievements.collectAsStateWithLifecycle()
    val challenges by vm.challenges.collectAsStateWithLifecycle()
    val completions by vm.completions.collectAsStateWithLifecycle()
    var challengeDialog by remember { mutableStateOf(false) }
    val total = segments.sumOf { it.elapsedMs }
    val level = levels.last { total >= it.second * 3600000L }
    val next = levels.getOrNull(levels.indexOf(level) + 1)
    var goalDialog by remember { mutableStateOf(false) }
    var goal by remember(prefs.goalMinutes) { mutableStateOf(prefs.goalMinutes.toString()) }
    var streak by remember(prefs.streakMinutes) { mutableStateOf(prefs.streakMinutes.toString()) }
    var restoreWarning by remember { mutableStateOf(false) }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) {
            it?.let(vm::export)
        }
    val restore =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(vm::restore)
        }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(AuralisSpace.lg),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RefinedHeader(
                if (section == "Overview") "Settings" else section,
                {
                    if (section == "Overview" || initialSection != "Overview") back()
                    else section = "Overview"
                },
            )
        }
        if (section == "Goals") {
            item {
                SectionTitle("Your daily ritual")
                ListItem(
                    headlineContent = { Text("Listening goal & streak") },
                    supportingContent = {
                        Text(
                            "${prefs.goalMinutes} min goal • ${prefs.streakMinutes} min for a streak day"
                        )
                    },
                    trailingContent = { TextButton({ goalDialog = true }) { Text("Edit") } },
                )
            }
        }
        if (section == "Challenges") {
            item {
                SectionTitle("Personal challenges")
                Text(
                    "Choose a monthly or annual book or listening-hour target. Your completed challenges stay in this history.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton({ challengeDialog = true }) { Text("Create a challenge") }
            }
            items(challenges, key = { it.id }) { c ->
                val start = java.time.LocalDate.parse(c.startsOn)
                val end = java.time.LocalDate.parse(c.endsOn)
                val amount =
                    if (c.metric == "books")
                        completions
                            .count {
                                java.time.Instant.ofEpochMilli(it.completedAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate() in start..end
                            }
                            .toLong()
                    else
                        segments
                            .filter { java.time.LocalDate.parse(it.date) in start..end }
                            .sumOf { it.elapsedMs }
                ElevatedCard {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (c.metric == "books") "$amount / ${c.target} books"
                            else "${hours(amount)} / ${hours(c.target)}",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        LinearProgressIndicator(
                            progress = {
                                (amount.toFloat() / c.target.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "${c.startsOn} – ${c.endsOn}" +
                                if (amount >= c.target) " • Goal reached!"
                                else if (java.time.LocalDate.now() > end) " • Archived" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        if (section == "Google Cast")
            item {
                SectionTitle("Your books, on your speaker")
                Text(
                    "Connect this phone and your Cast speaker to the same Wi-Fi, then choose a device. Auralis streams the active book from your storage over the local network; keep the phone connected while listening.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                app.auralis.cast.CastRouteButton()
                Text(
                    "Cast playback supports 0.5×–2× speed. Supported audio formats depend on the receiver. Sleep-timer fade-out remains local-only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (section == "Playback") {
            item {
                SectionTitle("Playback")
                Text("Skip interval")
                Row {
                    listOf(10, 15, 30, 60).forEach { s ->
                        FilterChip(
                            prefs.skipSeconds == s,
                            { vm.settings(prefs.copy(skipSeconds = s)) },
                            label = { Text("${s}s") },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
                Text("Default speed: ${"%.2f".format(prefs.speed)}×")
                Slider(
                    prefs.speed,
                    { vm.settings(prefs.copy(speed = it)) },
                    valueRange = .5f..3f,
                    steps = 49,
                )
                SettingSwitch("Sleep timer fade-out", prefs.fade) {
                    vm.settings(prefs.copy(fade = it))
                }
                SettingSwitch("Smart rewind after a long pause", prefs.smartRewind) {
                    vm.settings(prefs.copy(smartRewind = it))
                }
                Text(
                    "Rewinds 10 seconds after a pause of at least 5 minutes. Replayed content does not inflate unique coverage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (section == "Appearance") {
            item {
                SectionTitle("Make yourself at home")
                ListItem(
                    headlineContent = { Text("App icon") },
                    supportingContent = {
                        Text(app.auralis.branding.AuralisIcon.fromKey(prefs.appIcon).title)
                    },
                    leadingContent = {
                        app.auralis.branding.BrandMark(
                            app.auralis.branding.AuralisIcon.fromKey(prefs.appIcon),
                            Modifier.size(48.dp),
                        )
                    },
                    trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.clickable { section = "App icon" },
                )
                Row {
                    FilterChip(
                        prefs.theme == "dark",
                        { vm.settings(prefs.copy(theme = "dark")) },
                        label = { Text("Dark") },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        prefs.theme == "light",
                        { vm.settings(prefs.copy(theme = "light")) },
                        label = { Text("Light") },
                    )
                }
                SettingSwitch("Use wallpaper colors", prefs.dynamic) {
                    vm.settings(prefs.copy(dynamic = it))
                }
            }
        }
        if (section == "Accessibility")
            item {
                SectionTitle("Accessibility")
                SettingSwitch("Reduce motion", prefs.reducedMotion) {
                    vm.settings(prefs.copy(reducedMotion = it))
                }
                SettingSwitch("Milestone haptics", prefs.milestoneHaptics) {
                    vm.settings(prefs.copy(milestoneHaptics = it))
                }
                Text(
                    "Auralis follows your device’s text size and screen reader settings. Charts include spoken values and touch controls support keyboard focus.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (section == "Notifications")
            item {
                SectionTitle("Notifications")
                Text(
                    "Playback controls appear while listening. Auralis does not schedule reminders or promotional notifications.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton({
                    context.startActivity(
                        android.content
                            .Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(
                                android.provider.Settings.EXTRA_APP_PACKAGE,
                                context.packageName,
                            )
                    )
                }) {
                    Text("Android notification settings")
                }
            }
        if (section == "Storage and imports")
            item {
                SectionTitle("Your audio stays yours")
                Text(
                    "Audio is read from the files and folders you select. Removing a book from Auralis keeps its original audio. Relink missing files from book details.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(vm::rescan) { Text("Rescan imported folders") }
                SettingSwitch("Automatic book details", prefs.onlineBookDetails) {
                    vm.settings(prefs.copy(onlineBookDetails = it))
                }
                Text(
                    "When you open a book, Auralis looks up details when you import or open a book. It fills in missing credits for strong matches. You can review other suggestions on the book page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        if (section == "Backup and restore") {
            item {
                SectionTitle("Keep your world safe")
                Text(
                    "Backups include your library references, listening history, notes, achievements, collections and preferences. Audio files and cover images are not included. On another device, relink audio from book details.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    TextButton({ export.launch("auralis-backup.json") }) { Text("Export backup") }
                    TextButton({ restoreWarning = true }) { Text("Restore backup") }
                }
                TextButton(vm::rescan) { Text("Rescan imported folders") }
            }
        }
        if (section == "About Auralis") {
            item {
                SectionTitle("Auralis ${app.auralis.BuildConfig.VERSION_NAME}")
                Text(
                    "Your books. Your world.\nBuilt for offline listening with Kotlin, Compose and Media3.\nCore listening works offline. Google Cast uses network access to stream locally to your speaker.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (challengeDialog) {
        var title by remember { mutableStateOf("") }
        var metric by remember { mutableStateOf("books") }
        var target by remember { mutableStateOf("3") }
        var annual by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { challengeDialog = false },
            title = { Text("Your next milestone") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        title,
                        { title = it },
                        label = { Text("Personal challenge name") },
                    )
                    Row {
                        FilterChip(
                            metric == "books",
                            { metric = "books" },
                            label = { Text("Books") },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            metric == "hours",
                            { metric = "hours" },
                            label = { Text("Hours") },
                        )
                    }
                    OutlinedTextField(
                        target,
                        { target = it.filter(Char::isDigit) },
                        label = { Text("Target ${if(metric=="books") "books" else "hours"}") },
                    )
                    Row {
                        FilterChip(!annual, { annual = false }, label = { Text("This month") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(annual, { annual = true }, label = { Text("This year") })
                    }
                }
            },
            confirmButton = {
                TextButton({
                    target
                        .toLongOrNull()
                        ?.takeIf { it in 1..100000 }
                        ?.let {
                            vm.challenge(
                                title.ifBlank {
                                    if (metric == "books") "A shelf of stories"
                                    else "Time for wonder"
                                },
                                metric,
                                if (metric == "books") it else it * 3600000,
                                annual,
                            )
                            challengeDialog = false
                        }
                }) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton({ challengeDialog = false }) { Text("Cancel") } },
        )
    }
    if (goalDialog)
        AlertDialog(
            onDismissRequest = { goalDialog = false },
            title = { Text("A rhythm that fits you") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Goals count actual listening minutes, at any playback speed.")
                    OutlinedTextField(
                        goal,
                        { goal = it.filter(Char::isDigit) },
                        label = { Text("Daily goal (minutes)") },
                    )
                    OutlinedTextField(
                        streak,
                        { streak = it.filter(Char::isDigit) },
                        label = { Text("Minimum for a streak day") },
                    )
                    Text(
                        "Changes recalculate streaks from your existing history. No listening records are deleted.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton({
                    val g = goal.toIntOrNull()
                    val s = streak.toIntOrNull()
                    if (g != null && s != null && g in 1..1440 && s in 1..1440) {
                        vm.settings(prefs.copy(goalMinutes = g, streakMinutes = s))
                        goalDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton({ goalDialog = false }) { Text("Cancel") } },
        )
    if (restoreWarning)
        AlertDialog(
            onDismissRequest = { restoreWarning = false },
            title = { Text("Restore your listening data?") },
            text = {
                Text(
                    "Records merge by their original IDs. Matching book metadata, bookmarks and playback positions will be replaced with the backup values. Audio access may need to be relinked."
                )
            },
            confirmButton = {
                TextButton({
                    restoreWarning = false
                    restore.launch(arrayOf("application/json", "text/plain"))
                }) {
                    Text("Choose backup")
                }
            },
            dismissButton = { TextButton({ restoreWarning = false }) { Text("Cancel") } },
        )
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}
