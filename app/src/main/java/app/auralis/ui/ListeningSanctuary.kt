package app.auralis.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.min
import kotlinx.coroutines.launch

/** Scoped editorial styling: the existing Listening landing screen keeps its own identity. */
private val Editorial = FontFamily.Serif
private val Coral
    @Composable
    get() =
        if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFD69878)
        else Color(0xFF914B30)
private val Lavender
    @Composable
    get() =
        if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFB0A5D4)
        else Color(0xFF66538A)

@Composable
fun ListeningSanctuaryContent(
    data: ListeningInsights,
    books: List<LibraryBook>,
    prefs: Preferences,
    today: LocalDate,
    onGoals: () -> Unit = {},
    onReport: (Boolean, Long) -> Unit = { _, _ -> },
    completedBooks: Int = 0,
) {
    var section by rememberSaveable { mutableStateOf("Goals") }
    var periodName by rememberSaveable { mutableStateOf(InsightPeriod.MONTH.name) }
    var monthOffset by rememberSaveable { mutableLongStateOf(0L) }
    var selectedDate by rememberSaveable { mutableStateOf(today.toString()) }
    var annual by rememberSaveable { mutableStateOf(true) }
    var reportOffset by rememberSaveable { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    val goalScroll = rememberLazyListState()
    val insightScroll = rememberLazyListState()
    val calendarScroll = rememberLazyListState()
    val stats = data.stats
    val byId = remember(books) { books.associateBy { it.book.id } }
    val month = YearMonth.from(today).minusMonths(monthOffset)
    val day = LocalDate.parse(selectedDate)
    val period = InsightPeriod.valueOf(periodName)
    val buckets =
        remember(stats.daily, period, today) { insightBuckets(stats.daily, period, today) }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                Text(
                    "YOUR LISTENING",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 3.sp,
                )
                Text(
                    "Your listening journey",
                    fontFamily = Editorial,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).selectableGroup()) {
                listOf("Goals", "Insights", "Calendar").forEach { tab ->
                    Column(
                        Modifier.weight(1f)
                            .selectable(
                                section == tab,
                                onClick = { section = tab },
                                role = Role.Tab,
                            )
                            .testTag("journey-tab-$tab")
                            .heightIn(min = 48.dp)
                            .padding(top = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            tab,
                            color =
                                if (section == tab) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            Modifier.width(32.dp)
                                .height(2.dp)
                                .background(
                                    if (section == tab) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                        )
                    }
                }
            }
            HorizontalDivider(
                Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
            )
            LazyColumn(
                Modifier.fillMaxSize().testTag("journey-scroll"),
                state =
                    when (section) {
                        "Goals" -> goalScroll
                        "Insights" -> insightScroll
                        else -> calendarScroll
                    },
                contentPadding =
                    PaddingValues(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                when (section) {
                    "Goals" -> {
                        item("rings") { JourneyRings(data, prefs, today) }
                        item("goal-actions") {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "A little time, just for you.",
                                        fontFamily = Editorial,
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                    Text(
                                        "${prefs.goalMinutes} min daily goal",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                TextButton(onGoals, Modifier.testTag("edit-listening-goals")) {
                                    Text("Edit goal")
                                }
                            }
                        }
                        item("goal-metrics") {
                            JourneyMetrics(
                                listOf(
                                    hours(stats.elapsed) to "Lifetime",
                                    "${stats.streak.current} days" to "Streak",
                                    hours(stats.saved) to "Time saved",
                                )
                            )
                        }
                        item("rank") {
                            SignaturePanel {
                                Kicker("YOUR NEXT MILESTONE")
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        null,
                                        tint = Lavender,
                                        modifier = Modifier.size(32.dp),
                                    )
                                    Column(Modifier.padding(start = 16.dp).weight(1f)) {
                                        Text(
                                            data.rank.next ?: data.rank.current,
                                            fontFamily = Editorial,
                                            style = MaterialTheme.typography.headlineMedium,
                                        )
                                        Text(
                                            if (data.rank.next == null)
                                                "Your highest listening rank"
                                            else
                                                "${hours(data.rank.remainingMs)} of listening to go",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                ShelfProgress(
                                    data.rank.fraction,
                                    Modifier.fillMaxWidth().padding(top = 20.dp).height(4.dp),
                                )
                                Text(
                                    "Current rank · ${data.rank.current}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 12.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        item("week") {
                            EditorialHeading(
                                "Make it a ritual",
                                "Your last seven days. A filled circle meets your current streak threshold.",
                            )
                            Row(
                                Modifier.fillMaxWidth().padding(top = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                (6 downTo 0).forEach { ago ->
                                    val date = today.minusDays(ago.toLong())
                                    val qualifies =
                                        (stats.daily[date] ?: 0L) >= prefs.streakMinutes * 60_000L
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier =
                                            Modifier.semantics(mergeDescendants = true) {
                                                contentDescription =
                                                    "$date: ${hours(stats.daily[date] ?: 0L)}, ${if (qualifies) "streak day" else "below streak threshold"}"
                                            },
                                    ) {
                                        Box(
                                            Modifier.size(30.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(
                                                    if (qualifies) MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = .07f
                                                        )
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (qualifies)
                                                Icon(
                                                    Icons.Rounded.Check,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(17.dp),
                                                )
                                            else
                                                Text(
                                                    "${date.dayOfMonth}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                )
                                        }
                                        Text(
                                            date.format(DateTimeFormatter.ofPattern("EEEEE")),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                "${prefs.streakMinutes} min for a streak day · ${stats.streak.longest} days longest streak",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                    "Insights" -> {
                        item("month-total") {
                            Kicker("${today.month.name} · ACTUAL LISTENING")
                            Text(
                                hours(data.thisMonthMs),
                                fontFamily = Editorial,
                                style = MaterialTheme.typography.displaySmall,
                                modifier =
                                    Modifier.padding(top = 8.dp).testTag("insight-month-total"),
                            )
                            Text(
                                "Time spent listening this month",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(
                                Modifier.padding(vertical = 20.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f),
                            )
                            val difference = data.thisMonthMs - data.previousMonthMs
                            Text(
                                if (data.previousMonthMs == 0L)
                                    "${hours(data.previousMonthMs)} last month · A new chapter begins."
                                else
                                    "${hours(kotlin.math.abs(difference))} ${if (difference >= 0) "more" else "less"} than last month",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Current month to date compared with the full previous month.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        item("rhythm") {
                            EditorialHeading("Your listening rhythm")
                            Row(
                                Modifier.horizontalScroll(rememberScrollState())
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                InsightPeriod.entries.forEach { p ->
                                    FilterChip(
                                        period == p,
                                        { periodName = p.name },
                                        label = { Text(p.label) },
                                    )
                                }
                            }
                            ActivityBars(
                                buckets,
                                if (period == InsightPeriod.WEEK || period == InsightPeriod.MONTH)
                                    prefs.goalMinutes * 60_000L
                                else 0,
                            )
                        }
                        item("time-metrics") {
                            JourneyMetrics(
                                listOf(
                                    hours(stats.elapsed) to "Actual listening",
                                    hours(stats.content) to "Content played",
                                    hours(stats.saved) to "Time saved",
                                )
                            )
                            Text(
                                "Actual time excludes pauses and seeking. Content played includes replays; unique book coverage is shown on book details.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                        item("overview") {
                            EditorialHeading("The bigger picture")
                            StatLine("This week", hours(data.thisWeekMs))
                            InsightRule()
                            StatLine("This year", hours(data.thisYearMs))
                            InsightRule()
                            StatLine("Daily average", hours(data.dailyAverageMs))
                            InsightRule()
                            StatLine("Longest session", hours(stats.longestSession))
                            InsightRule()
                            StatLine("Sessions", stats.sessions.toString())
                            InsightRule()
                            StatLine("Books started", data.booksStarted.toString())
                            InsightRule()
                            StatLine("Books completed", completedBooks.toString())
                            InsightRule()
                            StatLine(
                                "Average session",
                                hours(
                                    if (stats.sessions == 0) 0L else stats.elapsed / stats.sessions
                                ),
                            )
                            InsightRule()
                            StatLine("Average speed", "%.2f×".format(stats.averageSpeed))
                        }
                        item("patterns") {
                            EditorialHeading("Patterns in your world")
                            PatternLine(
                                "Most heard author",
                                data.authors.firstOrNull()?.first ?: "More listening needed",
                            )
                            PatternLine(
                                "Favourite genre",
                                data.genres.firstOrNull()?.first
                                    ?: "Add genre metadata to your books",
                            )
                            PatternLine(
                                "Most heard narrator",
                                data.narrators.firstOrNull()?.first
                                    ?: "Add narrator metadata to your books",
                            )
                            PatternLine(
                                "Favourite hour",
                                data.favouriteHour?.let {
                                    LocalTime.of(it, 0).format(DateTimeFormatter.ofPattern("h a"))
                                } ?: "More listening needed",
                            )
                            PatternLine(
                                "Favourite day",
                                data.favouriteDay?.name?.lowercase()?.replaceFirstChar {
                                    it.uppercase()
                                } ?: "More listening needed",
                            )
                        }
                        item("reports") {
                            EditorialHeading(
                                "A keepsake of your year",
                                "Your monthly and annual listening reports, built from your own history.",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    annual,
                                    {
                                        annual = true
                                        reportOffset = 0
                                    },
                                    label = { Text("Annual") },
                                )
                                FilterChip(
                                    !annual,
                                    {
                                        annual = false
                                        reportOffset = 0
                                    },
                                    label = { Text("Monthly") },
                                )
                            }
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton({ reportOffset++ }) {
                                    Icon(Icons.Rounded.ChevronLeft, "Earlier report")
                                }
                                Text(
                                    if (annual) (today.year - reportOffset).toString()
                                    else
                                        YearMonth.from(today)
                                            .minusMonths(reportOffset)
                                            .format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                IconButton(
                                    { reportOffset = (reportOffset - 1).coerceAtLeast(0) },
                                    enabled = reportOffset > 0,
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, "Later report")
                                }
                            }
                            OutlinedButton(
                                { onReport(annual, reportOffset) },
                                Modifier.fillMaxWidth().testTag("open-listening-report"),
                            ) {
                                Text("Open listening report")
                            }
                        }
                    }
                    else -> {
                        item("month-summary") {
                            EditorialHeading(
                                "Every day tells a story.",
                                "A quiet record of the time you made for listening.",
                            )
                            val inMonth = stats.daily.filterKeys { YearMonth.from(it) == month }
                            JourneyMetrics(
                                listOf(
                                    hours(inMonth.values.sum()) to "Month total",
                                    inMonth.values.count { it > 0 }.toString() to "Listening days",
                                    inMonth.values
                                        .count { it >= prefs.goalMinutes * 60_000L }
                                        .toString() to "Goals reached",
                                )
                            )
                        }
                        item("month-calendar") {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconButton({
                                    monthOffset++
                                    selectedDate = month.minusMonths(1).atEndOfMonth().toString()
                                }) {
                                    Icon(Icons.Rounded.ChevronLeft, "Previous month")
                                }
                                Text(
                                    month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                    fontFamily = Editorial,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                )
                                IconButton(
                                    {
                                        monthOffset = (monthOffset - 1).coerceAtLeast(0)
                                        selectedDate =
                                            minOf(today, month.plusMonths(1).atEndOfMonth())
                                                .toString()
                                    },
                                    enabled = monthOffset > 0,
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, "Next month")
                                }
                            }
                            SignatureMonth(month, today, day, stats.daily, prefs.goalMinutes) {
                                selectedDate = it.toString()
                            }
                            Text(
                                "Gold dot · current daily goal reached. Swipe the grid on a narrow display.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        }
                        item("selected-day") {
                            val summary = data.days[day]
                            SignaturePanel(Modifier.testTag("calendar-day-details")) {
                                Kicker(day.format(DateTimeFormatter.ofPattern("EEEE · d MMMM")))
                                Text(
                                    hours(summary?.elapsedMs ?: 0L),
                                    fontFamily = Editorial,
                                    style = MaterialTheme.typography.headlineLarge,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                Text(
                                    "${summary?.sessions ?: 0} ${if (summary?.sessions == 1) "session" else "sessions"} · actual listening",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    if ((summary?.elapsedMs ?: 0L) >= prefs.goalMinutes * 60_000L)
                                        "Goal reached"
                                    else if (summary == null)
                                        "No listening recorded. A new story can begin any day."
                                    else
                                        "${hours((prefs.goalMinutes * 60_000L - summary.elapsedMs).coerceAtLeast(0))} below your current goal",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                                summary
                                    ?.books
                                    ?.entries
                                    ?.sortedByDescending { it.value }
                                    ?.forEach { (id, elapsed) ->
                                        val book = byId[id]?.book
                                        Row(
                                            Modifier.fillMaxWidth().padding(top = 20.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (book != null)
                                                Cover(book, Modifier.size(48.dp), 8.dp)
                                            Column(
                                                Modifier.weight(1f)
                                                    .padding(
                                                        start = if (book == null) 0.dp else 12.dp
                                                    )
                                            ) {
                                                Text(
                                                    book?.title ?: "Removed book",
                                                    style = MaterialTheme.typography.titleSmall,
                                                )
                                                Text(
                                                    hours(elapsed),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color =
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                Text(
                                    "Compared with your current ${prefs.goalMinutes} min goal. Changing your goal keeps all listening records.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                        item("heatmap") {
                            EditorialHeading(
                                "Six months of stories",
                                "Each square is a local calendar day. Tap to revisit it.",
                            )
                            ListeningCalendar(
                                month.atEndOfMonth().coerceAtMost(today),
                                stats.daily,
                            ) {
                                selectedDate = it.toString()
                                monthOffset =
                                    java.time.temporal.ChronoUnit.MONTHS.between(
                                        YearMonth.from(it),
                                        YearMonth.from(today),
                                    )
                                scope.launch {
                                    if (prefs.reducedMotion) calendarScroll.scrollToItem(2)
                                    else calendarScroll.animateScrollToItem(2)
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
private fun JourneyRings(data: ListeningInsights, prefs: Preferences, today: LocalDate) {
    val elapsed = data.stats.daily[today] ?: 0L
    val target = prefs.goalMinutes.coerceAtLeast(1) * 60_000L
    val raw =
        listOf(
            (elapsed.toFloat() / target).coerceIn(0f, 1f),
            (data.stats.streak.current / 7f).coerceIn(0f, 1f),
            data.rank.fraction,
        )
    val reduce = LocalReduceMotion.current
    val goal by
        animateFloatAsState(raw[0], if (reduce) snap() else tween(420), label = "daily goal")
    val streak by animateFloatAsState(raw[1], if (reduce) snap() else tween(420), label = "streak")
    val rank by animateFloatAsState(raw[2], if (reduce) snap() else tween(420), label = "rank")
    val colors = listOf(MaterialTheme.colorScheme.primary, Coral, Lavender)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.sizeIn(maxWidth = 276.dp, maxHeight = 276.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${hours(elapsed)} listened today. ${prefs.goalMinutes} minute daily goal. ${data.stats.streak.current} day streak. ${data.rank.current} listening rank."
                    progressBarRangeInfo = ProgressBarRangeInfo(raw[0], 0f..1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val stroke = size.minDimension * .048f
                listOf(goal, streak, rank).forEachIndexed { i, value ->
                    val inset = stroke / 2 + i * stroke * 1.9f
                    val top = Offset(inset, inset)
                    val extent =
                        androidx.compose.ui.geometry.Size(
                            size.width - 2 * inset,
                            size.height - 2 * inset,
                        )
                    drawArc(track, -90f, 360f, false, top, extent, style = Stroke(stroke))
                    if (value > 0)
                        drawArc(
                            colors[i],
                            -90f,
                            360f * value,
                            false,
                            top,
                            extent,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                }
            }
            Column(
                Modifier.widthIn(max = 142.dp).padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    hours(elapsed),
                    fontFamily = Editorial,
                    fontSize = 30.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 34.sp,
                )
                Text(
                    "listened today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (elapsed >= target) "Daily goal complete"
            else "${hours(target - elapsed)} left for today",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp),
        )
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Daily goal", "7-day streak", "Next rank").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(colors[i]))
                    Text(
                        label,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureMonth(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate,
    daily: Map<LocalDate, Long>,
    goal: Int,
    onDay: (LocalDate) -> Unit,
) {
    val cells = remember(month) { calendarMonthCells(month) }
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gridWidth = maxOf(maxWidth, 336.dp)
        Column(
            Modifier.horizontalScroll(rememberScrollState())
                .width(gridWidth)
                .testTag("journey-month-grid")
        ) {
            Row(Modifier.fillMaxWidth()) {
                listOf("M", "T", "W", "T", "F", "S", "S").forEach {
                    Text(
                        it,
                        Modifier.weight(1f).padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val elapsed = daily[date] ?: 0L
                        val enabled = date != null && date <= today
                        val level =
                            when {
                                elapsed == 0L -> 0
                                elapsed < 300_000L -> 1
                                elapsed < 900_000L -> 2
                                elapsed < 2_700_000L -> 3
                                else -> 4
                            }
                        Box(
                            Modifier.weight(1f)
                                .heightIn(min = 48.dp)
                                .then(
                                    if (date != null)
                                        Modifier.selectable(
                                                date == selected,
                                                enabled = enabled,
                                                role = Role.Button,
                                                onClick = { onDay(date) },
                                            )
                                            .semantics {
                                                contentDescription =
                                                    "${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))}: ${hours(elapsed)} listening${if (date == selected) ", selected" else ""}"
                                            }
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (date != null) {
                                val fill =
                                    if (level == 0) Color.Transparent
                                    else
                                        accent.copy(
                                            alpha = listOf(0f, .12f, .22f, .35f, .5f)[level]
                                        )
                                Column(
                                    Modifier.padding(3.dp)
                                        .sizeIn(minWidth = 40.dp, minHeight = 42.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(fill)
                                        .border(
                                            if (date == selected) 1.dp else 0.dp,
                                            if (date == selected) accent else Color.Transparent,
                                            RoundedCornerShape(14.dp),
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        date.dayOfMonth.toString(),
                                        color =
                                            if (enabled) MaterialTheme.colorScheme.onSurface
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                    alpha = .4f
                                                ),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Box(
                                        Modifier.padding(top = 3.dp)
                                            .size(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (elapsed >= goal * 60_000L) accent
                                                else Color.Transparent
                                            )
                                    )
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
private fun JourneyMetrics(values: List<Pair<String, String>>) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        values.forEach { (value, label) ->
            Column(Modifier.weight(1f).widthIn(min = 84.dp)) {
                Text(value, fontFamily = Editorial, style = MaterialTheme.typography.headlineSmall)
                Text(
                    label,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SignaturePanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .025f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .09f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), content = content)
    }
}

@Composable
private fun Kicker(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.6.sp,
    )
}

@Composable
private fun EditorialHeading(title: String, subtitle: String? = null) {
    Text(title, fontFamily = Editorial, style = MaterialTheme.typography.headlineSmall)
    if (subtitle != null)
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
    else Spacer(Modifier.height(16.dp))
}

@Composable
private fun InsightRule() {
    HorizontalDivider(
        Modifier.padding(vertical = 14.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
    )
}

@Composable
private fun PatternLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
