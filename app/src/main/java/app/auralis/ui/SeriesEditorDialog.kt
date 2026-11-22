package app.auralis.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.auralis.data.LibraryBook
import app.auralis.domain.seriesInfo

@Composable
internal fun SeriesEditorDialog(
    originalName: String?,
    currentMembers: List<LibraryBook>,
    books: List<LibraryBook>,
    dismiss: () -> Unit,
    save: (String, Set<String>, () -> Unit) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(originalName.orEmpty()) }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedIds by rememberSaveable {
        mutableStateOf(currentMembers.map { it.book.id })
    }
    var saving by remember { mutableStateOf(false) }
    val ordered = remember(books, currentMembers) {
        val currentIds = currentMembers.map { it.book.id }.toSet()
        books.sortedWith(compareBy<LibraryBook> { it.book.id !in currentIds }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.book.title })
    }
    val visible = ordered.filter {
        it.book.title.contains(search.trim(), ignoreCase = true) ||
            it.book.author.contains(search.trim(), ignoreCase = true)
    }
    val duplicateName = name.isNotBlank() && books.any {
        val existing = seriesInfo(it.book)?.name
        existing != originalName && existing?.equals(name.trim(), ignoreCase = true) == true
    }

    AlertDialog(
        onDismissRequest = { if (!saving) dismiss() },
        title = { Text(if (originalName == null) "New series" else "Edit series") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Series name") },
                    isError = duplicateName,
                    supportingText = if (duplicateName) ({ Text("This name is already in use.") }) else null,
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("series-editor-name"),
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Find books") },
                    enabled = !saving,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (originalName != null && selectedIds.isEmpty()) {
                        "Saving removes all books from this series."
                    } else {
                        "${selectedIds.size} selected"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 340.dp)) {
                    items(visible, key = { it.book.id }) { item ->
                        val book = item.book
                        val existing = seriesInfo(book)?.name
                        val checked = book.id in selectedIds
                        Row(
                            Modifier.fillMaxWidth().testTag("series-book-${book.id}")
                                .toggleable(value = checked, enabled = !saving, role = Role.Checkbox) { selected ->
                                    selectedIds = if (selected) selectedIds + book.id else selectedIds - book.id
                                }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, enabled = !saving)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(book.title, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (existing != null && existing != originalName) {
                                        if (checked) "Moves from $existing" else "In $existing"
                                    } else book.author,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (visible.isEmpty()) item { Text("No matching books") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && !duplicateName && name.isNotBlank() &&
                    (originalName != null || selectedIds.isNotEmpty()),
                onClick = {
                    saving = true
                    save(name, selectedIds.toSet()) { saving = false }
                },
            ) { Text("Save series") }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}
