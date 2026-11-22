package app.auralis.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*

@Composable
fun RefinedHeader(title: String, back: () -> Unit, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun RefinedField(
    value: String,
    change: (String) -> Unit,
    label: String,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
) {
    OutlinedTextField(
        value,
        change,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(18.dp),
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = .7f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .22f),
            ),
    )
}

@Composable
fun RefinedAction(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = click).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = .09f),
        ) {
            Icon(
                icon,
                null,
                Modifier.padding(10.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            null,
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun BookmarkEditor(save: (String, String) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        RefinedField(title, { title = it }, "Title (optional)", singleLine = true)
        RefinedField(note, { note = it }, "Your note", minLines = 3, maxLines = 6)
        Text(
            "A thought, a quote, a moment to return to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            { save(title, note) },
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.BookmarkAdd, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Save bookmark")
        }
    }
}
