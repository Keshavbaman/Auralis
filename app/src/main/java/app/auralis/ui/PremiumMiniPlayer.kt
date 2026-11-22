package app.auralis.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.auralis.domain.SwipeAxis
import app.auralis.domain.swipeCommit
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.data.LibraryBook

@Composable
fun PremiumMiniPlayer(
    book: LibraryBook,
    state: PlayerSnapshot,
    reduced: Boolean,
    open: () -> Unit,
    toggle: () -> Unit,
    skip: () -> Unit,
    skipSeconds: Int = 30,
    skipBack: () -> Unit = {},
) {
    // Swipe up opens the player; swipe left skips forward, right skips back. The bar moves with the
    // finger and springs home.
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var settle by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    Surface(
        onClick = open,
        modifier =
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = dragX
                    translationY = dragY
                }
                .liveSwipe(
                    book.book.id,
                    setOf(SwipeAxis.Horizontal, SwipeAxis.Vertical),
                    onDrag = { axis, offset ->
                        settle?.cancel()
                        if (axis == SwipeAxis.Horizontal) dragX = offset * .35f
                        else dragY = minOf(0f, offset) * .4f
                    },
                    onRelease = { axis, offset, velocity, extent ->
                        val direction =
                            swipeCommit(offset, velocity, if (axis == SwipeAxis.Horizontal) extent else 240f, .3f)
                        if (direction != 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        when {
                            axis == SwipeAxis.Horizontal && direction == 1 -> skip()
                            axis == SwipeAxis.Horizontal && direction == -1 -> skipBack()
                            axis == SwipeAxis.Vertical && direction == 1 -> open()
                        }
                        settle =
                            scope.launch {
                                launch {
                                    animate(dragX, 0f, animationSpec = spring(dampingRatio = .6f, stiffness = Spring.StiffnessMedium)) { v, _ -> dragX = v }
                                }
                                animate(dragY, 0f, animationSpec = spring(dampingRatio = .7f, stiffness = Spring.StiffnessMedium)) { v, _ -> dragY = v }
                            }
                    },
                )
                .semantics { contentDescription = "Open player" },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 10.dp,
    ) {
        Column(
            Modifier.border(
                .5.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
                RoundedCornerShape(20.dp),
            )
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(
                    book.book,
                    Modifier.size(44.dp).sharedArtwork(book.book.id),
                    corner = 11.dp,
                )
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 6.dp)) {
                    Text(
                        shelfTitle(book.book.title),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${state.chapterIndex + 1}/${book.chapters.size} · ${book.orderedChapters.getOrNull(state.chapterIndex)?.title.orEmpty()}",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                IconButton(toggle) {
                    Crossfade(
                        state.playing,
                        animationSpec = tween(if (reduced) 0 else 140),
                        label = "Mini transport",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play",
                            Modifier.size(30.dp),
                        )
                    }
                }
                IconButton(skip) {
                    Text(
                        "+$skipSeconds",
                        style = MaterialTheme.typography.labelMedium,
                        modifier =
                            Modifier.semantics {
                                contentDescription = "Skip forward $skipSeconds seconds"
                            },
                    )
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 5.dp)) {
                ShelfProgress(
                    book.playbackFraction,
                    Modifier.fillMaxWidth().height(2.dp),
                )
            }
        }
    }
}
