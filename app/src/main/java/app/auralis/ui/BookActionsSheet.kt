package app.auralis.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.data.LibraryBook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One entry in the book actions sheet. */
internal data class BookAction(
    val label: String,
    val detail: String,
    val icon: ImageVector,
    val warning: Boolean = false,
    val run: () -> Unit,
)

/**
 * Book actions as a sheet of large tiles in the cover's colour. Tiles pop in one after another
 * and squash when pressed; reduced motion shows them straight away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookActionsSheet(
    book: LibraryBook,
    title: String,
    actions: List<BookAction>,
    reduce: Boolean,
    dismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = artworkAccent(book.book.artwork) ?: colors.primary
    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(book.book, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)), corner = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "BOOK ACTIONS",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            actions.chunked(2).forEachIndexed { row, pair ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEachIndexed { column, action ->
                        ActionTile(
                            action = action,
                            accent = if (action.warning) colors.error else accent,
                            order = row * 2 + column,
                            reduce = reduce,
                            modifier = Modifier.weight(1f),
                        ) {
                            dismiss()
                            action.run()
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    action: BookAction,
    accent: Color,
    order: Int,
    reduce: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) {
    // Staggered entrance: each tile rises and settles with a little spring, slightly after the last.
    val entrance = remember { Animatable(if (reduce) 1f else 0f) }
    val iconPop = remember { Animatable(if (reduce) 1f else .6f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(70L * order)
        launch { entrance.animateTo(1f, spring(dampingRatio = .62f, stiffness = Spring.StiffnessMediumLow)) }
        delay(90)
        iconPop.animateTo(1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium))
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by
        animateFloatAsState(
            if (pressed) .94f else 1f,
            if (reduce) tween(0) else spring(dampingRatio = .55f, stiffness = Spring.StiffnessMedium),
            label = "Tile press",
        )
    Column(
        modifier
            .graphicsLayer {
                alpha = entrance.value.coerceIn(0f, 1f)
                translationY = (1f - entrance.value) * 36.dp.toPx()
                scaleX = press * (.92f + .08f * entrance.value)
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(24.dp))
            .background(accent.copy(alpha = .10f))
            .border(1.dp, accent.copy(alpha = .22f), RoundedCornerShape(24.dp))
            .clickable(interaction, indication = LocalIndication.current, role = Role.Button, onClick = click)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(44.dp)
                .graphicsLayer {
                    scaleX = iconPop.value
                    scaleY = iconPop.value
                }
                .background(accent.copy(alpha = .18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Text(action.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            action.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
