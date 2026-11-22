@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.auralis.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.data.*
import app.auralis.domain.*
import kotlinx.coroutines.flow.map

@Composable
fun BookTile(book: LibraryBook, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        Cover(
            book.book,
            Modifier.fillMaxWidth().aspectRatio(1f).shadow(8.dp, RoundedCornerShape(16.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            shelfTitle(book.book.title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        ShelfProgress(book.fraction, Modifier.fillMaxWidth().height(2.dp))
        Text(
            if (book.book.manuallyCompleted) "Finished"
            else if (book.fraction > 0)
                "${(book.fraction*100).toInt()}% position · ${durationLabel(book.durationMs)}"
            else durationLabel(book.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    vm: AuralisViewModel,
    detail: (String) -> Unit,
    import: () -> Unit,
    play: (String) -> Unit,
) {
    val books by vm.catalogue.collectAsStateWithLifecycle()
    val collections by vm.collections.collectAsStateWithLifecycle()
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf("Books") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var narrator by rememberSaveable { mutableStateOf("") }
    var genre by rememberSaveable { mutableStateOf("") }
    var edition by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("Recently played") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var filters by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var seriesEditorOpen by rememberSaveable { mutableStateOf(false) }
    var groupName by rememberSaveable { mutableStateOf<String?>(null) }
    var groupIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    // Tapping the Library tab closes an open series or collection. The last handled request is
    // saved, so simply coming back to Library (e.g. from a book) keeps the group open.
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val rootRequest by vm.libraryRoot.collectAsState()
    var handledRoot by rememberSaveable { mutableIntStateOf(rootRequest) }
    if (rootRequest != handledRoot) {
        handledRoot = rootRequest
        selected = null
        groupName = null
        groupIds = emptyList()
    }
    // Keep scroll states outside conditional shelves: opening a group removes the grid
    // from composition, but must not discard its position.
    val bookGridState = rememberLazyGridState()
    val bookListState = rememberLazyListState()
    val seriesState = rememberLazyGridState()
    val authorsState = rememberLazyGridState()
    val collectionsState = rememberLazyGridState()
    val groupStates = rememberSaveableStateHolder()
    val rows = remember(books, query, filter, narrator, genre, edition, sort) {
        val visible = books.filter { b ->
            (listOf(b.book.title, b.book.author, b.book.narrator, seriesInfo(b.book)?.name.orEmpty())
                    .joinToString(" "))
                .contains(query, true) &&
                when (filter) {
                    "In progress" -> b.progress != null && !b.isFinished
                    "Not started" -> b.progress == null && !b.isFinished
                    "Finished" -> b.isFinished
                    "Favourites" -> b.book.favourite
                    else -> true
                } &&
                (narrator.isBlank() || b.book.narrator.equals(narrator, ignoreCase = true) ||
                    splitNames(b.book.narrator).any { it.equals(narrator, ignoreCase = true) }) &&
                (genre.isBlank() || b.book.genre == genre) &&
                (edition.isBlank() || b.book.edition == edition)
        }
        when (sort) {
            "Title" ->
                visible.sortedWith { a, b -> naturalOrder.compare(a.book.title, b.book.title) }
            "Author" -> visible.sortedBy { it.book.author }
            "Duration" -> visible.sortedBy { it.durationMs }
            "Recently played" -> visible.sortedByDescending { it.progress?.lastPlayed ?: 0 }
            "Position" -> visible.sortedByDescending { it.fraction }
            else -> visible.sortedByDescending { it.book.addedAt }
        }
    }
    val selectedGroup = groupName
    val seriesMembers = if (category == "Series" && selectedGroup != null) {
        books.filter { seriesInfo(it.book)?.name == selectedGroup }
    } else emptyList()
    if (seriesEditorOpen) {
        SeriesEditorDialog(
            originalName = selectedGroup,
            currentMembers = seriesMembers,
            books = books,
            dismiss = { seriesEditorOpen = false },
            save = { name, ids, finished ->
                vm.saveSeries(selectedGroup, name, ids) { saved ->
                    finished()
                    if (saved) {
                        seriesEditorOpen = false
                        groupName = name.trim().takeIf { ids.isNotEmpty() }
                    }
                }
            },
        )
    }
    if (selectedGroup != null) {
        groupStates.SaveableStateProvider(selectedGroup) {
            LibraryGroupDetail(
                selectedGroup,
                if (category == "Series") seriesMembers else books.filter { it.book.id in groupIds },
                { groupName = null },
                detail,
                grid = grid,
                toggleGrid = { grid = !grid },
                editSeries = if (category == "Series") ({ seriesEditorOpen = true }) else null,
            )
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        val chosen = selected?.let { id -> books.find { it.book.id == id } }
        val split = wide && chosen != null
        androidx.activity.compose.BackHandler(enabled = split) { selected = null }
        fun open(id: String) {
            if (wide) selected = id else detail(id)
        }
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(if (split) .48f else 1f).fillMaxHeight()) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (searching) {
                        TextField(
                            query,
                            { query = it },
                            Modifier.weight(1f).testTag("library-search"),
                            placeholder = { Text("Title, author or series") },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor =
                                        androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor =
                                        androidx.compose.ui.graphics.Color.Transparent,
                                ),
                        )
                        IconButton({
                            query = ""
                            searching = false
                        }) {
                            Icon(Icons.Rounded.Close, "Close search")
                        }
                    } else {
                        Text(
                            "Library",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        IconButton({ searching = true }) {
                            Icon(Icons.Rounded.Search, "Search library")
                        }
                        IconButton(import) { Icon(Icons.Rounded.Add, "Import audiobooks") }
                    }
                }
                LibraryShelfTabs(category) { category = it }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${rows.size} titles",
                        Modifier.padding(start = 8.dp).weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box {
                        TextButton({ sorting = true }) {
                            Icon(Icons.AutoMirrored.Rounded.Sort, null)
                            Text(
                                when (sort) {
                                    "Recently added" -> "Recent"
                                    "Recently played" -> "Played"
                                    else -> sort
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        DropdownMenu(sorting, { sorting = false }) {
                            listOf(
                                    "Recently added",
                                    "Recently played",
                                    "Title",
                                    "Author",
                                    "Duration",
                                    "Position",
                                )
                                .forEach { value ->
                                    DropdownMenuItem(
                                        { Text(value) },
                                        {
                                            sort = value
                                            sorting = false
                                        },
                                    )
                                }
                        }
                    }
                    IconButton({ filters = true }) { Icon(Icons.Rounded.Tune, "Filter books") }
                    IconButton({ grid = !grid }) {
                        Icon(
                            if (grid) Icons.AutoMirrored.Rounded.ViewList
                            else Icons.Rounded.GridView,
                            "Toggle grid or list",
                        )
                    }
                }
                if (
                    filter != "All" ||
                        narrator.isNotBlank() ||
                        genre.isNotBlank() ||
                        edition.isNotBlank()
                )
                    Text(
                        "${rows.size} matches · $filter",
                        Modifier.padding(horizontal = 24.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                if (category == "Series") {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { seriesEditorOpen = true }, enabled = books.isNotEmpty()) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("New series")
                        }
                    }
                }
                val groups: List<Pair<String, List<LibraryBook>>> =
                    when (category) {
                        "Series" ->
                            rows
                                .filter { seriesInfo(it.book) != null }
                                .groupBy { seriesInfo(it.book)!!.name }
                                .toList()
                        "Authors" -> rows.groupBy { it.book.author }.toList()
                        "Collections" ->
                            collections.map { c ->
                                c.name to
                                    rows.filter { b ->
                                        memberships.any {
                                            it.bookId == b.book.id && it.collectionId == c.id
                                        }
                                    }
                            }
                        else -> emptyList()
                    }
                if (category != "Books") {
                    if (groups.isEmpty())
                        Empty(
                            if (category == "Series") "No matching series" else "Make room for a collection",
                            if (category == "Series") "Create a series to group books in reading order."
                            else "Add a book to a collection from its detail page.",
                        )
                    else
                        LazyVerticalGrid(
                            if (grid) GridCells.Adaptive(140.dp) else GridCells.Fixed(1),
                            modifier = Modifier.testTag(if (grid) "library-groups-grid" else "library-groups-list"),
                            state = when (category) {
                                "Series" -> seriesState
                                "Authors" -> authorsState
                                else -> collectionsState
                            },
                            contentPadding = PaddingValues(AuralisSpace.lg),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(groups, key = { it.first }) { (name, members) ->
                                Surface(
                                    onClick = {
                                        groupIds = members.map { it.book.id }
                                        groupName = name
                                    },
                                    shape = AuralisShape.panel,
                                    color = MaterialTheme.colorScheme.surface,
                                ) {
                                    if (grid) {
                                        Column(
                                            Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            val coverSpace = Modifier.fillMaxWidth().aspectRatio(1f)
                                            val first = members.firstOrNull()
                                            if (first != null) Cover(first.book, coverSpace)
                                            else Box(coverSpace, contentAlignment = Alignment.Center) {
                                                Icon(Icons.AutoMirrored.Rounded.LibraryBooks, null)
                                            }
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                            Text("${members.size} books", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    } else
                                    Row(
                                        Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        members.firstOrNull()?.let {
                                            Cover(it.book, Modifier.size(72.dp))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${members.size} books · ${hours(members.sumOf { it.durationMs })}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Icon(Icons.Rounded.ChevronRight, null)
                                    }
                                }
                            }
                        }
                } else if (rows.isEmpty())
                    Empty(
                        "Your next story awaits",
                        if (books.isEmpty()) "Import audio to fill this shelf."
                        else "No books match these filters.",
                        if (books.isEmpty()) "Add books" else null,
                        import,
                    )
                else if (grid)
                    LazyVerticalGrid(
                        GridCells.Adaptive(if (split) 126.dp else if (wide) 168.dp else 148.dp),
                        state = bookGridState,
                        modifier = Modifier.testTag("library-books-grid"),
                        contentPadding =
                            PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(rows, key = { it.book.id }) { b ->
                            LibraryCoverTile(b) { open(b.book.id) }
                        }
                    }
                else
                    LazyColumn(
                        state = bookListState,
                        modifier = Modifier.testTag("library-books-list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        items(rows, key = { it.book.id }) { b ->
                            LibraryBookRow(b, onPlay = { play(b.book.id) }) { open(b.book.id) }
                        }
                    }
            }
            if (split && chosen != null) {
                VerticalDivider()
                Box(Modifier.weight(.52f).fillMaxHeight()) {
                    key(chosen.book.id) {
                        DetailScreen(
                            vm, chosen, { selected = null }, { play(chosen.book.id) },
                            books, { selected = it },
                        )
                    }
                }
            }
        }
    }
    if (filters)
        ModalBottomSheet(
            onDismissRequest = { filters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Shape your shelf", style = MaterialTheme.typography.headlineMedium)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("All", "In progress", "Not started", "Finished", "Favourites").forEach {
                        value ->
                        FilterChip(filter == value, { filter = value }, { Text(value) })
                    }
                }
                MetadataFilter("Narrator", books.flatMap { splitNames(it.book.narrator) }, narrator) {
                    narrator = it
                }
                MetadataFilter("Genre", books.map { it.book.genre }, genre) { genre = it }
                MetadataFilter("Edition", books.map { it.book.edition }, edition) { edition = it }
                Row {
                    TextButton({
                        filter = "All"
                        narrator = ""
                        genre = ""
                        edition = ""
                    }) {
                        Text("Reset filters")
                    }
                    Spacer(Modifier.weight(1f))
                    Button({ filters = false }) { Text("Show ${rows.size} books") }
                }
            }
        }
}

@Composable
private fun MetadataFilter(
    label: String,
    values: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf("") + values.filter { it.isNotBlank() }.distinct().sorted()).forEach { value ->
            FilterChip(selected == value, { onSelect(value) }, { Text(value.ifBlank { "Any" }) })
        }
    }
}

/** Quiet text tabs leave the shelf, rather than controls, as the visual focus. */
@Composable
fun LibraryShelfTabs(category: String, select: (String) -> Unit) {
    val options = listOf("Books", "Series", "Authors", "Collections")
    val state = rememberLazyListState()
    val reducedMotion = LocalReduceMotion.current
    LaunchedEffect(category) {
        val index = options.indexOf(category).coerceAtLeast(0)
        if (reducedMotion) state.scrollToItem(index) else state.animateScrollToItem(index)
    }
    LazyRow(
        state = state,
        modifier = Modifier.fillMaxWidth().testTag("library-tabs"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(options, key = { it }) { label ->
            val active = label == category
            Column(
                Modifier.width(IntrinsicSize.Max)
                    .clickable(role = Role.Tab) { select(label) }
                    .semantics { selected = active },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            if (active) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(2.dp)
                        .background(
                            if (active) MaterialTheme.colorScheme.primary
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun LibraryCoverTile(book: LibraryBook, click: () -> Unit) {
    Column(Modifier.clickable(onClick = click)) {
        Cover(book.book, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            shelfTitle(book.book.title),
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            book.book.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun LibraryBookRow(book: LibraryBook, onPlay: (() -> Unit)? = null, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Cover(book.book, Modifier.size(72.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                shelfTitle(book.book.title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                book.book.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.playbackFraction > 0 && !book.isFinished) {
                ShelfProgress(
                    book.playbackFraction,
                    Modifier.widthIn(max = 96.dp).fillMaxWidth().height(2.dp),
                )
            }
            Text(
                if (book.isFinished) "Finished"
                else if (book.playbackFraction > 0)
                    "${hours((book.durationMs - book.positionMs).coerceAtLeast(0))} left"
                else hours(book.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onPlay != null)
            IconButton(onPlay) { Icon(Icons.Rounded.PlayArrow, "Play ${book.book.title}") }
        else Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp))
    }
}

@Composable
private fun LibraryGroupDetail(
    name: String,
    books: List<LibraryBook>,
    back: () -> Unit,
    detail: (String) -> Unit,
    grid: Boolean,
    toggleGrid: () -> Unit,
    editSeries: (() -> Unit)? = null,
) {
    androidx.activity.compose.BackHandler(onBack = back)
    val ordered = orderedSeries(books)
    LazyVerticalGrid(
        if (grid) GridCells.Adaptive(148.dp) else GridCells.Fixed(1),
        Modifier.fillMaxSize().testTag("library-group-detail"),
        contentPadding = PaddingValues(AuralisSpace.lg),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to library") }
                Spacer(Modifier.weight(1f))
                IconButton(toggleGrid) {
                    Icon(
                        if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        if (grid) "Show list" else "Show grid",
                    )
                }
                if (editSeries != null) TextButton(onClick = editSeries) { Text("Edit series") }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                ordered.firstOrNull()?.let { Cover(it.book, Modifier.size(112.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        books.map { it.book.author }.distinct().joinToString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${books.size} books · ${hours(books.sumOf { it.durationMs })}",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("In reading order") }
        items(ordered, key = { it.book.id }) { book ->
            if (grid) LibraryCoverTile(book) { detail(book.book.id) }
            else LibraryBookRow(book) { detail(book.book.id) }
        }
    }
}

@Composable
fun DetailScreen(
    vm: AuralisViewModel,
    book: LibraryBook,
    back: () -> Unit,
    play: () -> Unit,
    library: List<LibraryBook>,
    openBook: (String) -> Unit,
    openPdf: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val collections by vm.collections.collectAsStateWithLifecycle()
    val memberships by vm.memberships.collectAsStateWithLifecycle()
    val marks by vm.bookmarks.collectAsStateWithLifecycle()
    var edit by remember { mutableStateOf(false) }
    var removeDialog by remember(book.book.id) { mutableStateOf(false) }
    var removing by remember(book.book.id) { mutableStateOf(false) }
    var addCollection by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var editingMark by remember { mutableStateOf<BookmarkEntity?>(null) }
    val relink =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            if (uris.isNotEmpty()) vm.relink(book, uris)
        }
    val cover =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                vm.task {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val file =
                            java.io
                                .File(context.filesDir, "covers")
                                .apply { mkdirs() }
                                .resolve("manual-${book.book.id}.jpg")
                        context.contentResolver.openInputStream(uri)!!.use { input ->
                            file.outputStream().use { out -> input.copyTo(out) }
                        }
                        vm.edit(book.book.copy(artwork = file.absolutePath))
                    }
                }
        }
    val companionPdfPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onSuccess {
                    vm.attachCompanionPdf(book.book.id, uri)
                }.onFailure {
                    vm.message.value = "Auralis couldn't keep access to that PDF. Choose it again from Files."
                }
            }
        }
    val seriesName = remember(book.book) { seriesInfo(book.book)?.name }
    val seriesBooks = remember(library, seriesName, book.book.id) {
        if (seriesName == null) emptyList()
        else orderedSeries(
            library.filter {
                it.book.id != book.book.id && seriesInfo(it.book)?.name == seriesName
            }
        )
    }
    val prefs by vm.preferences.collectAsStateWithLifecycle()
    val (catalogState, retryCatalog) = rememberBookCatalog(vm.audiobookCatalog, book, prefs.onlineBookDetails, vm::acceptCatalogMatch)
    BookDetailsContent(
        book,
        marks,
        back,
        play,
        { vm.favourite(book.book) },
        { edit = true },
        { removeDialog = true },
        { addCollection = true },
        { vm.mark(book.book, !book.book.manuallyCompleted) },
        { vm.reset(book.book) },
        { cover.launch(arrayOf("image/*")) },
        { relink.launch(arrayOf("audio/*", "application/octet-stream")) },
        { vm.loadAt(book.book.id, it) },
        { vm.loadAt(book.book.id, it.chapterIndex, it.positionMs) },
        { editingMark = it },
        { vm.deleteBookmark(it.id) },
        seriesBooks = seriesBooks,
        openBook = openBook,
        catalogState = catalogState,
        retryCatalog = retryCatalog,
        saveCatalogNarrator = { vm.saveCatalogNarrator(book.book, it) },
        attachPdf = { companionPdfPicker.launch(arrayOf("application/pdf")) },
        openPdf = { openPdf(book.book.id) },
        removePdf = { vm.attachCompanionPdf(book.book.id, null) },
    )
    if (removeDialog)
        RemoveBookDialog(book.book.title, removing, { removeDialog = false }) {
            removing = true
            vm.removeBook(
                book,
                {
                    removeDialog = false
                    back()
                },
                { removing = false },
            )
        }
    if (edit)
        MetadataDialog(book.book, { edit = false }) {
            vm.edit(it)
            edit = false
        }
    if (editingMark != null) {
        val mark = editingMark!!
        var title by remember(mark.id) { mutableStateOf(mark.title) }
        var note by remember(mark.id) { mutableStateOf(mark.note) }
        AlertDialog(
            onDismissRequest = { editingMark = null },
            title = { Text("Edit bookmark") },
            text = {
                Column {
                    OutlinedTextField(title, { title = it }, label = { Text("Title") })
                    OutlinedTextField(note, { note = it }, label = { Text("Note") })
                }
            },
            confirmButton = {
                TextButton({
                    vm.updateBookmark(mark.copy(title = title, note = note))
                    editingMark = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = { TextButton({ editingMark = null }) { Text("Cancel") } },
        )
    }
    if (addCollection)
        AlertDialog(
            onDismissRequest = { addCollection = false },
            title = { Text("Manage collections") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    collections.forEach { collection ->
                        val included = CollectionBookCrossRef(collection.id, book.book.id) in memberships
                        Row(
                            Modifier.fillMaxWidth().toggleable(included, role = Role.Checkbox) { checked ->
                                if (checked) vm.collect(collection.id, book.book.id)
                                else vm.removeFromCollection(collection.id, book.book.id)
                            }.heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(included, onCheckedChange = null)
                            Text(collection.name)
                        }
                    }
                    OutlinedTextField(name, { name = it }, label = { Text("New collection") })
                }
            },
            confirmButton = {
                TextButton({
                    if (name.isNotBlank()) {
                        vm.addCollection(name, book.book.id)
                        addCollection = false
                    }
                }) {
                    Text("Create")
                }
            },
            dismissButton = { TextButton({ addCollection = false }) { Text("Close") } },
        )
}

@Composable
private fun MetadataDialog(book: BookEntity, dismiss: () -> Unit, save: (BookEntity) -> Unit) {
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf(book.author) }
    var genre by rememberSaveable { mutableStateOf(book.genre) }
    var narrator by rememberSaveable { mutableStateOf(book.narrator) }
    var series by remember { mutableStateOf(seriesInfo(book)?.name.orEmpty()) }
    var number by remember {
        mutableStateOf(
            seriesInfo(book)
                ?.number
                ?.let { if (it % 1 == 0.0) it.toInt().toString() else it.toString() }
                .orEmpty()
        )
    }
    var edition by rememberSaveable { mutableStateOf(book.edition) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Make it yours") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") })
                OutlinedTextField(author, { author = it }, label = { Text("Author") })
                OutlinedTextField(genre, { genre = it }, label = { Text("Genre") })
                OutlinedTextField(narrator, { narrator = it }, label = { Text("Narrator") })
                OutlinedTextField(series, { series = it }, label = { Text("Series name") })
                OutlinedTextField(
                    number,
                    { number = it },
                    label = { Text("Book number (for example 2 or 2.5)") },
                    isError = number.isNotBlank() && number.toDoubleOrNull() == null,
                )
                OutlinedTextField(edition, { edition = it }, label = { Text("Edition") })
            }
        },
        confirmButton = {
            TextButton(
                {
                    save(
                        book.copy(
                            title = title,
                            author = author,
                            genre = genre,
                            narrator = narrator,
                            seriesName = series.trim(),
                            seriesNumber = number.toDoubleOrNull().takeIf { series.isNotBlank() },
                            edition = edition.trim(),
                            seriesInferenceDisabled = series.isBlank() &&
                                (seriesInfo(book) != null || book.seriesInferenceDisabled),
                        )
                    )
                },
                enabled =
                    title.isNotBlank() &&
                        (number.isBlank() ||
                            number.toDoubleOrNull()?.let { it >= 0 && it.isFinite() } == true),
            ) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } },
    )
}
