package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import app.auralis.data.*
import app.auralis.domain.*

@Composable
fun BookDetailsContent(
    book: LibraryBook,
    marks: List<BookmarkEntity>,
    back: () -> Unit,
    play: () -> Unit,
    favourite: () -> Unit,
    edit: () -> Unit,
    remove: () -> Unit,
    collection: () -> Unit,
    markFinished: () -> Unit,
    reset: () -> Unit,
    cover: () -> Unit,
    relink: () -> Unit,
    chapter: (Int) -> Unit,
    jump: (BookmarkEntity) -> Unit,
    editMark: (BookmarkEntity) -> Unit,
    deleteMark: (BookmarkEntity) -> Unit,
    seriesBooks: List<LibraryBook> = emptyList(),
    openBook: (String) -> Unit = {},
    catalogState: BookCatalogState = BookCatalogState(),
    retryCatalog: () -> Unit = {},
    saveCatalogNarrator: ((String) -> Unit)? = null,
    attachPdf: () -> Unit = {},
    openPdf: () -> Unit = {},
    removePdf: () -> Unit = {},
) {
    var actions by remember { mutableStateOf(false) }
    var search by rememberSaveable(book.book.id) { mutableStateOf("") }
    val filtered =
        remember(marks, search, book.book.id) {
            marks.filter {
                it.bookId == book.book.id && (it.title + it.note).contains(search, true)
            }
        }
    val colors = MaterialTheme.colorScheme
    val series = remember(book.book) { seriesInfo(book.book) }
    val title = remember(book.book, series) { shelfTitle(bookDetailTitle(book.book, series)) }
    val listState = key(book.book.id) { rememberLazyListState() }
    val showToolbarTitle by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }

    val artworkTone by
        androidx.compose.animation.animateColorAsState(
            artworkTint(book.book.artwork),
            label = "Book atmosphere",
        )
    val chrome = colors.surface.copy(alpha = .45f)
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to lerp(colors.background, artworkTone, .55f),
                    .5f to colors.background,
                    1f to colors.background,
                )
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 820.dp).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(back, Modifier.background(chrome, CircleShape)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                }
                Box(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    if (showToolbarTitle) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(favourite, Modifier.background(chrome, CircleShape)) {
                    Icon(
                        if (book.book.favourite) Icons.Rounded.Favorite
                        else Icons.Rounded.FavoriteBorder,
                        "Toggle favourite",
                        tint = if (book.book.favourite) colors.primary else colors.onSurfaceVariant,
                    )
                }
                IconButton(edit, Modifier.background(chrome, CircleShape)) {
                    Icon(Icons.Rounded.Edit, "Edit book metadata", Modifier.size(20.dp))
                }
                IconButton(remove, Modifier.background(chrome, CircleShape)) {
                    Icon(Icons.Rounded.DeleteOutline, "Remove from library", Modifier.size(20.dp))
                }
            }
            LazyColumn(
                Modifier.fillMaxSize().testTag("book-detail-scroll"),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item(key = "identity") {
                    BookIdentity(book, title, series)
                    if (saveCatalogNarrator != null) {
                        NarratorLookup(book.book, catalogState, retryCatalog, saveCatalogNarrator)
                    }
                }
                item(key = "listen") {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (book.progress != null) {
                            Text(
                                resumeSummary(book),
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.onSurfaceVariant,
                            )
                            ShelfProgress(
                                book.playbackFraction,
                                Modifier.fillMaxWidth(.55f).height(3.dp),
                            )
                        }
                        Button(
                            play,
                            Modifier.fillMaxWidth().height(56.dp),
                            shape = RoundedCornerShape(50),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = colors.onSurface,
                                    contentColor = colors.surface,
                                ),
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (book.progress == null) "Start listening"
                                else "Continue listening",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                collection,
                                Modifier.weight(1f).height(52.dp),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, colors.outline.copy(alpha = .6f)),
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    null,
                                    Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Manage collections", style = MaterialTheme.typography.titleSmall)
                            }
                            Box {
                                OutlinedIconButton(
                                    { actions = true },
                                    Modifier.size(52.dp),
                                    border = BorderStroke(1.dp, colors.outline.copy(alpha = .6f)),
                                ) {
                                    Icon(Icons.Rounded.MoreHoriz, "Book actions")
                                }
                                if (actions) {
                                    BookActionsSheet(
                                        book = book,
                                        title = title,
                                        reduce = LocalReduceMotion.current,
                                        dismiss = { actions = false },
                                        actions =
                                            listOf(
                                                if (book.book.manuallyCompleted)
                                                    BookAction("Mark unfinished", "Put it back on your shelf", Icons.Rounded.Replay, run = markFinished)
                                                else BookAction("Mark finished", "Count it as read", Icons.Rounded.TaskAlt, run = markFinished),
                                                BookAction("Reset progress", "Start again from the beginning", Icons.Rounded.RestartAlt, warning = true, run = reset),
                                                BookAction("Change cover", "Pick new artwork", Icons.Rounded.Image, run = cover),
                                                BookAction("Relink audio", "Point to files that moved", Icons.Rounded.Link, run = relink),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "progress") {
                    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerLow) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                DetailMetric(
                                    "Playback position",
                                    "${(book.playbackFraction*100).toInt()}%",
                                    Modifier.weight(1f),
                                )
                                DetailMetric(
                                    "Uniquely listened",
                                    "${(book.coverageFraction*100).toInt()}%",
                                    Modifier.weight(1f),
                                )
                            }
                            ShelfProgress(
                                book.playbackFraction,
                                Modifier.fillMaxWidth().height(3.dp),
                            )
                        }
                    }
                }
                item(key = "companion-pdf") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colors.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Icon(
                                Icons.Rounded.MenuBook,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(26.dp),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    if (book.book.companionPdfUri == null) "Read along"
                                    else "PDF book linked",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    if (book.book.companionPdfUri == null)
                                        "Add the ebook PDF to read while this audiobook plays."
                                    else "Your reading place is saved as you move through the pages.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            if (book.book.companionPdfUri == null) {
                                TextButton(onClick = attachPdf) { Text("Add PDF") }
                            } else {
                                TextButton(onClick = openPdf) { Text("Read") }
                                Box {
                                    var pdfActions by remember { mutableStateOf(false) }
                                    IconButton(onClick = { pdfActions = true }) {
                                        Icon(Icons.Rounded.MoreVert, "PDF options")
                                    }
                                    DropdownMenu(
                                        expanded = pdfActions,
                                        onDismissRequest = { pdfActions = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Replace PDF") },
                                            leadingIcon = { Icon(Icons.Rounded.SwapHoriz, null) },
                                            onClick = { pdfActions = false; attachPdf() },
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Unlink PDF") },
                                            leadingIcon = { Icon(Icons.Rounded.LinkOff, null) },
                                            onClick = { pdfActions = false; removePdf() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "summary") { BookSummary(catalogState, retryCatalog) }
                item(key = "book-information") { AudiobookInformation(book, catalogState.details) }
                if (seriesBooks.isNotEmpty()) {
                    item(key = "series-books") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("In this series")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(seriesBooks, key = { it.book.id }) { related ->
                                    Column(
                                        Modifier.width(120.dp).clickable { openBook(related.book.id) },
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Cover(related.book, Modifier.fillMaxWidth().aspectRatio(1f))
                                        Text(
                                            shelfTitle(bookDetailTitle(related.book)),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            hours(related.durationMs),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "chapter-title") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Chapters",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "${book.chapters.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                item(key = "chapter-division-list") {
                    ChapterDivisionColumn(
                        chapters = book.orderedChapters,
                        selectedIndex = book.progress?.chapterIndex ?: -1,
                        onSelect = chapter,
                    )
                }
                item(key = "notes-title") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Bookmarks & notes", style = MaterialTheme.typography.titleLarge)
                        RefinedField(
                            search,
                            { search = it },
                            "Search your notes",
                            singleLine = true,
                        )
                    }
                }
                if (filtered.isEmpty())
                    item(key = "notes-empty") {
                        Row(
                            Modifier.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                Icons.Rounded.BookmarkBorder,
                                null,
                                Modifier.size(22.dp),
                                tint = colors.primary,
                            )
                            Text(
                                if (search.isBlank())
                                    "Keep a moment from the player. Your thoughts will find a home here."
                                else "No notes match this search.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                items(filtered, key = { "mark-${it.id}" }) { mark ->
                    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainerLow) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                mark.title.ifBlank { "Saved moment" },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (mark.note.isNotBlank())
                                Text(
                                    mark.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.onSurfaceVariant,
                                )
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton({ jump(mark) }) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        "Jump to bookmark",
                                        Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        time(mark.positionMs),
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                IconButton({ editMark(mark) }) {
                                    Icon(Icons.Rounded.Edit, "Edit bookmark", Modifier.size(18.dp))
                                }
                                IconButton({ deleteMark(mark) }) {
                                    Icon(
                                        Icons.Rounded.DeleteOutline,
                                        "Delete bookmark",
                                        Modifier.size(18.dp),
                                        tint = colors.onSurfaceVariant,
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
private fun BookIdentity(
    book: LibraryBook,
    title: String,
    series: SeriesInfo?,
) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val wide = maxWidth >= 650.dp
        val artwork: @Composable (Modifier) -> Unit = { modifier ->
            Cover(
                book.book,
                modifier
                    .aspectRatio(1f)
                    .shadow(
                        28.dp,
                        RoundedCornerShape(16.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black,
                    ),
                corner = 16.dp,
            )
        }
        val credits: @Composable () -> Unit = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (series != null) {
                    val installment = series.number?.let { java.text.DecimalFormat("0.##").format(it) }
                    Text(
                        (if (installment == null) series.name else "${series.name} · Book $installment")
                            .uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.primary,
                        letterSpacing = 1.6.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 28.dp),
                    )
                }
                Text(
                    title,
                    modifier =
                        Modifier.padding(top = if (series == null) 28.dp else 8.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                val chapterCount = book.chapters.size
                Text(
                    "Audiobook  ·  ${hours(book.durationMs)}  ·  $chapterCount ${if (chapterCount == 1) "chapter" else "chapters"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (book.book.edition.isNotBlank())
                    Text(
                        book.book.edition,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant.copy(alpha = .8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                val narrator = book.book.narrator
                // Shown even when it is the author (e.g. Stephen Fry reading his own books).
                val showNarrator = narrator.isNotBlank()
                Row(
                    Modifier.fillMaxWidth().padding(top = 26.dp).height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.Top,
                ) {
                    BookCreditColumn("BY", book.book.author, Modifier.weight(1f))
                    if (showNarrator) {
                        VerticalDivider(
                            Modifier.fillMaxHeight().padding(vertical = 4.dp),
                            color = colors.outlineVariant.copy(alpha = .55f),
                        )
                        BookCreditColumn("NARRATED BY", narrator, Modifier.weight(1f))
                    }
                }
                if (book.book.genre.isNotBlank()) {
                    Text(
                        book.book.genre,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            Modifier.padding(top = 20.dp)
                                .clip(RoundedCornerShape(50))
                                .border(1.dp, colors.outlineVariant.copy(alpha = .6f), RoundedCornerShape(50))
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
        }
        if (wide) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                artwork(Modifier.width(240.dp))
                Box(Modifier.weight(1f)) { credits() }
            }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                artwork(Modifier.widthIn(max = 280.dp).fillMaxWidth(.68f))
                credits()
            }
        }
    }
}

@Composable
private fun BookCreditColumn(label: String, name: String, modifier: Modifier) {
    val everyone = remember(name) { creditNames(name) }
    val shortened = everyone.size > 2
    var showAll by remember { mutableStateOf(false) }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (shortened)
                    Modifier.clickable(onClickLabel = "Show everyone") { showAll = true }
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.6.sp,
        )
        Text(
            if (shortened) shortCredit(name) else name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (shortened)
            Text(
                "See all ${everyone.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
    if (showAll) CreditRoster(label, everyone) { showAll = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditRoster(label: String, names: List<String>, dismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.78f).padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(label.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.headlineSmall)
                    Text("${names.size} credited", color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium)
                }
                IconButton(dismiss) { Icon(Icons.Rounded.Close, "Close credits") }
            }
            LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                items(names) { person ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val initials = person.split(Regex("\\s+")).take(2)
                            .mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
                        Box(Modifier.size(40.dp).background(colors.secondaryContainer, CircleShape),
                            contentAlignment = Alignment.Center) {
                            Text(initials, color = colors.onSecondaryContainer,
                                style = MaterialTheme.typography.labelLarge)
                        }
                        Text(person, style = MaterialTheme.typography.titleMedium)
                    }
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
                }
            }
        }
    }
}

/** Splits a credit such as "A, B, C and D" or "A & B" into individual names. */
private fun creditNames(names: String): List<String> =
    names.split(Regex("""\s*(?:,|&|;|\band\b)\s*""")).map { it.trim() }.filter { it.isNotEmpty() }

/** "A, B, C and D" becomes "A, B and more" so long full-cast credits stay readable. */
private fun shortCredit(names: String): String {
    val parts = creditNames(names)
    return if (parts.size > 2) "${parts[0]}, ${parts[1]} and more" else names
}

/** Plain resume summary for the primary button, e.g. "Chapter 4 of 12 · 2h 14m left". */
private fun resumeSummary(book: LibraryBook): String {
    val chapters = book.chapters.size
    val chapterWord = if (chapters == 1) "chapter" else "chapters"
    val progress = book.progress ?: return "${hours(book.durationMs)} · $chapters $chapterWord"
    val chapter = progress.chapterIndex + 1
    return "Chapter $chapter of $chapters · ${durationLabel(book.durationMs - book.positionMs)} left"
}

@Composable
private fun DetailMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
