package app.auralis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.auralis.data.BookEntity
import app.auralis.data.AudiobookCatalog
import app.auralis.data.CatalogDetails
import app.auralis.data.LibraryBook
import app.auralis.data.withFilenameCredits
import app.auralis.domain.bookDetailTitle
import app.auralis.domain.needsBookAuthor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest

enum class CatalogStatus { Disabled, Offline, Loading, Found, NoMatch, Failed }

data class BookCatalogState(
    val details: CatalogDetails? = null,
    val status: CatalogStatus = CatalogStatus.Disabled,
    val embeddedSummary: String = "",
)

@Composable
internal fun rememberBookCatalog(
    catalog: AudiobookCatalog,
    libraryBook: LibraryBook,
    enabled: Boolean,
    acceptMatch: suspend (LibraryBook, CatalogDetails) -> Unit,
): Pair<BookCatalogState, () -> Unit> {
    val book = libraryBook.book
    val lookupBook = book.withFilenameCredits()
    val durationMs = libraryBook.durationMs
    val title = bookDetailTitle(book)
    var state by remember(book.id, title, book.author, book.edition) { mutableStateOf(BookCatalogState()) }
    var retry by remember(book.id, title, book.author, book.edition) { mutableIntStateOf(0) }
    LaunchedEffect(catalog, book.id, title, book.author, book.edition, enabled, retry, durationMs) {
        var details = catalog.cached(lookupBook, durationMs)
        if (!enabled && retry == 0) {
            state = BookCatalogState(details)
            return@LaunchedEffect
        }
        var refresh = retry > 0
        catalog.online().collectLatest { online ->
            if (!online) {
                state = BookCatalogState(details, CatalogStatus.Offline)
                return@collectLatest
            }
            state = BookCatalogState(details, CatalogStatus.Loading)
            try {
                val match = catalog.lookup(lookupBook, refresh, durationMs)
                details = match
                refresh = false
                if (enabled && match != null) acceptMatch(libraryBook, match)
                state = BookCatalogState(match, if (match == null) CatalogStatus.NoMatch else CatalogStatus.Found)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                state = BookCatalogState(details, CatalogStatus.Failed)
            }
        }
    }
    val embedded by produceState("", libraryBook.files) {
        value = catalog.embeddedSummary(libraryBook.files)
    }
    return state.copy(embeddedSummary = embedded) to { retry++ }
}

@Composable
internal fun NarratorLookup(
    book: BookEntity,
    state: BookCatalogState,
    search: () -> Unit,
    save: (String) -> Unit,
) {
    val suggestion = state.details?.narrator.orEmpty().trim()
    var reviewing by remember(book.id, suggestion) { mutableStateOf(false) }
    if (suggestion.isNotBlank() && !suggestion.equals(book.narrator.trim(), ignoreCase = true)) {
        OutlinedButton({ reviewing = true }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Review narrator credits")
        }
    } else if (suggestion.isBlank()) {
        val status = when (state.status) {
            CatalogStatus.Loading -> "Looking up narrator credits…"
            CatalogStatus.Offline -> "Connect to the internet to find narrator credits."
            CatalogStatus.NoMatch -> if (needsBookAuthor(book.withFilenameCredits().author))
                "Add an author using Edit book metadata to improve the lookup."
                else "No matching audiobook found."
            CatalogStatus.Found -> "No narrator credits found for this listing."
            CatalogStatus.Failed -> "Narrator lookup is temporarily unavailable."
            CatalogStatus.Disabled -> null
        }
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall)
            if (state.status !in setOf(CatalogStatus.Loading, CatalogStatus.Offline)) {
                TextButton(search) { Text("Find narrator credits") }
            }
        }
    }
    if (reviewing) {
        AlertDialog(
            onDismissRequest = { reviewing = false },
            title = { Text("Save narrator credits?") },
            text = {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Check that these credits match your audiobook edition.")
                    if (book.narrator.isNotBlank()) InformationRow("Saved", book.narrator)
                    InformationRow(state.details?.narratorSource ?: "Suggested", suggestion)
                }
            },
            confirmButton = {
                TextButton({
                    reviewing = false
                    save(suggestion)
                }) { Text(if (book.narrator.isBlank()) "Save narrator" else "Replace narrator") }
            },
            dismissButton = { TextButton({ reviewing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
internal fun BookSummary(state: BookCatalogState, retry: () -> Unit) {
    val details = state.details
    val summary = state.embeddedSummary.ifBlank { details?.summary.orEmpty() }
    var expanded by rememberSaveable(summary) { mutableStateOf(false) }
    var overflows by remember(summary) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionTitle("Summary")
        if (summary.isNotBlank()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
            )
            Text(
                if (state.embeddedSummary.isNotBlank()) "From your audiobook file" else if (details?.url?.contains("audible.com") == true) "Source: Audible" else "Source: Apple Books",
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
            )
            if (expanded || overflows) {
                TextButton({ expanded = !expanded }) {
                    Text(if (expanded) "Read less" else "Read more")
                }
            }
        } else {
            Text(
                when (state.status) {
                    CatalogStatus.Disabled -> "Automatic lookup is off in Storage and imports."
                    CatalogStatus.Offline -> "A summary can be found when you connect to the internet."
                    CatalogStatus.Loading -> "Looking up this audiobook…"
                    CatalogStatus.Failed -> "The audiobook catalog is temporarily unavailable."
                    CatalogStatus.NoMatch -> "No matching audiobook listing was found."
                    CatalogStatus.Found -> "The catalog has no summary for this audiobook."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            if (state.status == CatalogStatus.Failed || state.status == CatalogStatus.NoMatch) {
                TextButton(retry) { Text("Try again") }
            }
        }
    }
}

@Composable
internal fun AudiobookInformation(book: LibraryBook, details: CatalogDetails?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionTitle("Book details")
        val local = book.book
        InformationRow("Duration", hours(book.durationMs))
        if (local.edition.isNotBlank()) InformationRow("Edition", local.edition)
        if (local.genre.isNotBlank()) InformationRow("Genre", local.genre)

        if (details != null) {
            HorizontalDivider(Modifier.padding(top = 4.dp))
            val ebook = "/book/" in details.url
            Text(
                when {
                    details.url.contains("audible.com") -> "Audible edition"
                    ebook -> "Apple Books · written edition"
                    else -> "Apple Books edition"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            val release = remember(details.released) {
                runCatching {
                    LocalDate.parse(details.released)
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
                }.getOrDefault("")
            }
            if (release.isNotBlank()) InformationRow("Release date", release)
            if (details.publisher.isNotBlank()) InformationRow("Publisher", details.publisher)
            if (details.language.isNotBlank()) InformationRow("Language", details.language)
            if (details.narrator.isNotBlank()) InformationRow("Catalog narrator", details.narrator)
            if (details.genre.isNotBlank() && local.genre.isBlank()) InformationRow("Catalog genre", details.genre)
            val rating = details.rating
            val count = details.ratingCount
            if (rating != null && count != null) {
                InformationRow("Catalog rating", "%.1f / 5 · %,d ratings".format(rating, count))
            }
            val uriHandler = LocalUriHandler.current
            TextButton({ uriHandler.openUri(details.url) }) { Text(if (details.url.contains("audible.com")) "View on Audible" else "View on Apple Books") }
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            label,
            Modifier.weight(.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, Modifier.weight(.62f), style = MaterialTheme.typography.bodyMedium)
    }
}
