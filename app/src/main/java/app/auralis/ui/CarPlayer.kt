package app.auralis.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.data.LibraryBook

/**
 * Glanceable driving player in the book's own colours: big type, a thick progress bar and
 * oversized controls that are easy to hit without looking.
 */
@Composable
fun CarPlayer(
    book: LibraryBook,
    state: PlayerSnapshot,
    toggle: () -> Unit,
    skip: (Boolean) -> Unit,
    chapter: (Int) -> Unit = {},
    close: () -> Unit,
) {
    BackHandler(onBack = close)
    val tint by animateColorAsState(artworkTint(book.book.artwork), label = "Car atmosphere")
    val accent by
        animateColorAsState(artworkAccent(book.book.artwork) ?: readableAccent(tint), label = "Car accent")
    val chapterTitle =
        book.orderedChapters.getOrNull(state.chapterIndex)?.title?.ifBlank { null }
            ?: "Chapter ${state.chapterIndex + 1}"
    Box(
        Modifier.fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to lerp(Color(0xFF121216), tint, .55f),
                    1f to lerp(PlayerScrim, tint, .12f),
                )
            )
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp)) {
            val wide = maxWidth > maxHeight
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        ExitButton(close)
                        Identity(book, chapterTitle, state, cover = 120.dp)
                        Timeline(state, accent)
                    }
                    Controls(state, accent, toggle, skip, chapter, book.chapters.lastIndex, Modifier.weight(1f))
                }
            } else {
                // Exit at the top, what is playing in the middle, controls at the bottom within
                // thumb reach. Scrolls only if large text makes it taller than the screen.
                val screen = maxHeight
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = screen),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        ExitButton(close)
                        Column(verticalArrangement = Arrangement.spacedBy(26.dp)) {
                            Identity(book, chapterTitle, state, cover = 150.dp)
                            Timeline(state, accent)
                        }
                        Controls(state, accent, toggle, skip, chapter, book.chapters.lastIndex, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitButton(close: () -> Unit) {
    FilledTonalButton(
        close,
        Modifier.height(56.dp),
        shape = RoundedCornerShape(50),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.Black.copy(alpha = .28f),
                contentColor = PlayerInk,
            ),
        contentPadding = PaddingValues(horizontal = 22.dp),
    ) {
        Icon(Icons.Rounded.Close, null, Modifier.size(24.dp))
        Spacer(Modifier.width(10.dp))
        Text("Exit car mode", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Identity(book: LibraryBook, chapterTitle: String, state: PlayerSnapshot, cover: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Cover(book.book, Modifier.size(cover).clip(RoundedCornerShape(16.dp)), corner = 16.dp)
        Column(Modifier.weight(1f)) {
            Text(
                "CHAPTER ${state.chapterIndex + 1} OF ${book.chapters.size}",
                color = PlayerInk.copy(alpha = .75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            )
            Text(
                chapterTitle,
                color = PlayerInk,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                shelfTitle(book.book.title),
                color = PlayerInk.copy(alpha = .75f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Timeline(state: PlayerSnapshot, accent: Color) {
    val fraction = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    Column {
        Canvas(
            Modifier.fillMaxWidth().height(10.dp).semantics { contentDescription = "Chapter progress" }
        ) {
            drawRoundRect(Color.White.copy(alpha = .18f), cornerRadius = CornerRadius(size.height / 2))
            drawRoundRect(
                accent,
                size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(size.height / 2),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(time(state.position), color = PlayerInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "−${time((state.duration - state.position).coerceAtLeast(0))}",
                color = PlayerInk.copy(alpha = .75f),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Controls(
    state: PlayerSnapshot,
    accent: Color,
    toggle: () -> Unit,
    skip: (Boolean) -> Unit,
    chapter: (Int) -> Unit,
    lastChapter: Int,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundControl(Icons.Rounded.Replay30, "Skip back 30 seconds", 96.dp, Color.Black.copy(alpha = .28f), PlayerInk) {
                skip(false)
            }
            RoundControl(
                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.playing) "Pause" else "Play",
                136.dp,
                accent,
                PlayerScrim,
                toggle,
            )
            RoundControl(Icons.Rounded.Forward30, "Skip forward 30 seconds", 96.dp, Color.Black.copy(alpha = .28f), PlayerInk) {
                skip(true)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            WideControl(
                Icons.Rounded.SkipPrevious,
                "Previous",
                enabled = state.chapterIndex > 0,
                modifier = Modifier.weight(1f),
            ) {
                chapter(state.chapterIndex - 1)
            }
            WideControl(
                Icons.Rounded.SkipNext,
                "Next",
                enabled = state.chapterIndex < lastChapter,
                modifier = Modifier.weight(1f),
            ) {
                chapter(state.chapterIndex + 1)
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    description: String,
    size: Dp,
    container: Color,
    content: Color,
    click: () -> Unit,
) {
    FilledIconButton(
        click,
        Modifier.size(size),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = content),
    ) {
        Icon(icon, description, Modifier.size(size * .5f))
    }
}

@Composable
private fun WideControl(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    modifier: Modifier,
    click: () -> Unit,
) {
    FilledTonalButton(
        click,
        modifier.height(68.dp).semantics { contentDescription = "$label chapter" },
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.Black.copy(alpha = .28f),
                contentColor = PlayerInk,
                disabledContainerColor = Color.Black.copy(alpha = .14f),
                disabledContentColor = PlayerInk.copy(alpha = .3f),
            ),
    ) {
        Icon(icon, null, Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
    }
}
