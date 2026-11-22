@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.branding.*
import app.auralis.data.*
import app.auralis.domain.*
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class HomeShelf(val title: String, val subtitle: String, val books: List<LibraryBook>)

private fun homeShelves(
    books: List<LibraryBook>,
    segments: List<ListeningSegmentEntity>,
    hero: LibraryBook?,
): List<HomeShelf> {
    val used = mutableSetOf<String>()
    hero?.let { used += it.book.id }
    val result = mutableListOf<HomeShelf>()
    fun shelf(title: String, subtitle: String, candidates: List<LibraryBook>, limit: Int = 6) {
        val chosen = candidates.filter { it.book.id !in used }.take(limit)
        used += chosen.map { it.book.id }
        if (chosen.isNotEmpty()) result += HomeShelf(title, subtitle, chosen)
    }
    val series = hero?.let { seriesInfo(it.book) }
    if (series != null)
        shelf(
            "Continue the series",
            series.name,
            orderedSeries(
                books.filter {
                    seriesInfo(it.book)?.name == series.name &&
                        !it.isFinished &&
                        (seriesInfo(it.book)?.number ?: 0.0) > (series.number ?: 0.0)
                }
            ),
            3,
        )
    val byId = books.associateBy { it.book.id }
    val authors =
        segments
            .groupBy { it.bookId }
            .entries
            .sortedByDescending { it.value.sumOf { r -> r.elapsedMs } }
            .mapNotNull { byId[it.key]?.book?.author }
    shelf(
        "Picked from your shelves",
        "A familiar author. A new adventure.",
        books
            .filter { it.progress == null }
            .sortedBy {
                authors.indexOf(it.book.author).let { i -> if (i < 0) Int.MAX_VALUE else i }
            },
        4,
    )
    shelf(
        "Recently added",
        "Fresh worlds, ready when you are.",
        books.sortedByDescending { it.book.addedAt },
    )
    shelf(
        "Unfinished stories",
        "Come back to something good.",
        books
            .filter { it.progress != null && !it.isFinished }
            .sortedByDescending { it.progress?.lastPlayed },
    )
    return result
}

@Composable
fun HomeScreen(
    vm: AuralisViewModel,
    detail: (String) -> Unit,
    import: () -> Unit,
    openPlayer: () -> Unit = {},
    browse: () -> Unit = {},
    goals: () -> Unit = {},
) {
    val books by vm.catalogue.collectAsStateWithLifecycle()
    val segments by vm.segments.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val player by
        remember(vm) {
                vm.player
                    .map { it.copy(position = 0, duration = 0, timerRemaining = 0) }
                    .distinctUntilChanged()
            }
            .collectAsStateWithLifecycle(initialValue = PlayerSnapshot())
    val today = rememberListeningDate()
    val stats = rememberListeningStats(segments, prefs.streakMinutes, today)
    val active =
        remember(books, player.mediaId) {
            books.find { b -> b.chapters.any { it.id == player.mediaId } }
        }
    val hero =
        remember(books, active) {
            active
                ?: books
                    .filter { it.progress != null && !it.isFinished }
                    .maxByOrNull { it.progress?.lastPlayed ?: 0 }
                ?: books.filter { !it.isFinished }.maxByOrNull { it.book.addedAt }
        }
    val shelves by
        produceState(emptyList<HomeShelf>(), books, segments, hero) {
            value = withContext(Dispatchers.Default) { homeShelves(books, segments, hero) }
        }
    HomeContent(
        books.size,
        hero,
        shelves,
        stats.daily[today] ?: 0,
        prefs.goalMinutes,
        stats.streak.current,
        prefs.speed,
        active?.book?.id == hero?.book?.id && player.playing,
        detail,
        import,
        browse,
        goals,
        {
            hero?.let {
                if (active?.book?.id == it.book.id) {
                    if (!player.playing) vm.toggle()
                } else vm.load(it.book.id)
                openPlayer()
            }
        },
    )
}

@Composable
fun HomeContent(
    bookCount: Int,
    hero: LibraryBook?,
    shelves: List<HomeShelf>,
    todayMs: Long,
    goalMinutes: Int,
    streak: Int,
    speed: Float,
    playing: Boolean,
    detail: (String) -> Unit,
    import: () -> Unit,
    browse: () -> Unit,
    goals: () -> Unit,
    listen: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 1120.dp).fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            LazyColumn(
                Modifier.fillMaxSize().testTag("home-scroll"),
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "welcome") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BrandMark(AuralisIcon.Signature, Modifier.size(24.dp))
                                Text(
                                    "A U R A L I S",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                when (LocalTime.now().hour) {
                                    in 5..11 -> "Good morning."
                                    in 12..17 -> "Good afternoon."
                                    else -> "Good evening."
                                },
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            Text(
                                "Your next chapter starts here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Surface(
                            onClick = import,
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Icon(
                                Icons.Rounded.Add,
                                "Import audiobooks",
                                Modifier.padding(13.dp).size(22.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                if (hero != null)
                    item(key = "continue") {
                        Column(
                            Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (hero.progress == null) "Ready when you are"
                                    else "Continue listening",
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                TextButton(browse) {
                                    Text(
                                        "Your library",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                            val continueCard: @Composable () -> Unit = {
                                HomeContinueCard(
                                    hero,
                                    speed,
                                    playing,
                                    wide,
                                    { detail(hero.book.id) },
                                    listen,
                                )
                            }
                            if (wide) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    Box(Modifier.weight(1f)) { continueCard() }
                                    HomeRitual(todayMs, goalMinutes, streak, goals, Modifier.width(245.dp))
                                }
                            } else continueCard()
                        }
                    }
                if (bookCount == 0)
                    item(key = "empty") {
                        Box(Modifier.padding(horizontal = 24.dp)) {
                            BrandedEmpty(
                                "A world waiting to be heard",
                                "Bring your books. Find your next escape.",
                                "Import your first book",
                                AuralisIcon.Horizon,
                                import,
                            )
                        }
                    }
                if (!wide || hero == null)
                    item(key = "daily-ritual") { HomeRitual(todayMs, goalMinutes, streak, goals) }
                shelves.forEach { shelf ->
                    item(key = "heading-${shelf.title}") {
                        Column(
                            Modifier.padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(shelf.title, style = MaterialTheme.typography.titleLarge)
                            Text(
                                shelf.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item(key = "shelf-${shelf.title}") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(shelf.books, key = { it.book.id }) { book ->
                                HomeShelfBook(book, if (wide) 176.dp else 152.dp) {
                                    detail(book.book.id)
                                }
                            }
                        }
                    }
                }
                if (bookCount > 0)
                    item(key = "browse") {
                        Surface(
                            onClick = browse,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.AutoStories,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Your library, always with you",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "$bookCount books. Countless places to go.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun HomeContinueCard(
    book: LibraryBook,
    globalSpeed: Float,
    playing: Boolean,
    wide: Boolean,
    detail: () -> Unit,
    listen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val chapter = book.orderedChapters.getOrNull(book.progress?.chapterIndex ?: 0)
    val speed = (book.progress?.speed ?: globalSpeed).coerceAtLeast(.5f)
    val remaining = ((book.durationMs - book.positionMs).coerceAtLeast(0) / speed).toLong()
    Surface(
        onClick = detail,
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(.7.dp, colors.primary.copy(alpha = .2f)),
        modifier = Modifier.fillMaxWidth().testTag("home-continue"),
    ) {
        Column(
            Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            colors.primary.copy(alpha = .08f),
                            Color.Transparent,
                            colors.primary.copy(alpha = .025f),
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Cover(book.book, Modifier.size(if (wide) 180.dp else 120.dp), corner = 16.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(
                        if (playing) "NOW PLAYING" else "YOUR NEXT ESCAPE",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.2.sp,
                        color = colors.primary,
                    )
                    Text(
                        shelfTitle(book.book.title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        book.book.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (chapter != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        null,
                        Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                    Text(
                        "Chapter ${(book.progress?.chapterIndex?:0)+1} of ${book.chapters.size} · ${chapter.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            ShelfProgress(
                book.playbackFraction,
                Modifier.fillMaxWidth().height(3.dp).semantics {
                    contentDescription = "Book playback position"
                    progressBarRangeInfo = ProgressBarRangeInfo(book.playbackFraction, 0f..1f)
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${hours(remaining)} left", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "At ${"%.2f".format(speed).trimEnd('0').trimEnd('.')}× speed",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Button(
                    listen,
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (playing) "Open player"
                        else if (book.progress == null) "Listen" else "Resume"
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeRitual(
    elapsed: Long,
    goalMinutes: Int,
    streak: Int,
    open: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
) {
    val colors = MaterialTheme.colorScheme
    val goal = goalMinutes.coerceAtLeast(1)
    val fraction = (elapsed / (goal * 60000f)).coerceIn(0f, 1f)
    Surface(
        onClick = open,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainerLow,
    ) {
        Row(
            Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (fraction >= 1) "A little time, beautifully spent."
                    else "Make time for a little escape.",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${elapsed/60000} / $goal min today",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                ShelfProgress(
                    fraction,
                    Modifier.fillMaxWidth().height(2.dp).semantics {
                        contentDescription = "Daily listening goal"
                        progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    },
                )
            }
            Box(
                Modifier.width(1.dp)
                    .height(36.dp)
                    .background(colors.outlineVariant.copy(alpha = .3f))
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Rounded.LocalFireDepartment,
                    null,
                    Modifier.size(20.dp),
                    tint = colors.primary,
                )
                Text(
                    "$streak ${if(streak==1)"day" else "days"}",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun HomeShelfBook(book: LibraryBook, width: Dp, detail: () -> Unit) {
    Column(
        Modifier.width(width).clickable(onClick = detail),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Cover(book.book, Modifier.fillMaxWidth().aspectRatio(1f), corner = 16.dp)
        Text(
            shelfTitle(book.book.title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        Text(
            book.book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (book.playbackFraction > 0)
            ShelfProgress(book.playbackFraction, Modifier.fillMaxWidth().height(2.dp))
    }
}
