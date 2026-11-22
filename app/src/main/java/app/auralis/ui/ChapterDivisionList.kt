package app.auralis.ui

import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.auralis.data.ChapterEntity

/**
 * Optional livelier look for the player's chapter sheet: the book's accent colour, an animated
 * equalizer on the current chapter while playing, and rows that slide in when the sheet opens.
 * The book page leaves this unset and keeps the plain style.
 */
internal data class ChapterListStyle(val accent: Color, val playing: Boolean, val openedAt: Long)

internal val LocalChapterListStyle = staticCompositionLocalOf<ChapterListStyle?> { null }

private data class ChapterSection(
    val heading: ChapterEntity?,
    val chapters: List<ChapterEntity>,
    val standalone: Boolean = false,
)

private val sectionTitle =
    Regex("^(?:book|part|volume|act)\\s+(?:[0-9]+|[a-z]+|[ivxlcdm]+)\\s*[:—–-].+", RegexOption.IGNORE_CASE)
private val creditTitle = Regex("^(?:opening|end|closing) credits$", RegexOption.IGNORE_CASE)

private fun groupChapters(chapters: List<ChapterEntity>): List<ChapterSection> {
    val result = mutableListOf<ChapterSection>()
    var heading: ChapterEntity? = null
    var tracks = mutableListOf<ChapterEntity>()

    fun flush() {
        if (heading != null || tracks.isNotEmpty()) {
            result += ChapterSection(heading, tracks.toList())
            heading = null
            tracks = mutableListOf()
        }
    }

    chapters.forEach { chapter ->
        when {
            sectionTitle.matches(chapter.title.trim()) -> {
                flush()
                heading = chapter
            }
            creditTitle.matches(chapter.title.trim()) -> {
                flush()
                result += ChapterSection(null, listOf(chapter), standalone = true)
            }
            else -> tracks += chapter
        }
    }
    flush()
    return result
}

/** Audible-style section headers are real chapter tracks: tapping one seeks to its audio. */
@Composable
internal fun ChapterDivisionColumn(
    chapters: List<ChapterEntity>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val sections = groupChapters(chapters)
    Column(Modifier.fillMaxWidth()) {
        sections.forEachIndexed { sectionIndex, section ->
            if (section.standalone) {
                val track = section.chapters.single()
                ChapterDivisionRow(
                    track = track,
                    selected = track.ordinal == selectedIndex,
                    first = true,
                    last = true,
                    sectionHeading = true,
                    onSelect = onSelect,
                )
            } else {
                section.heading?.let { heading ->
                    ChapterDivisionRow(
                        track = heading,
                        selected = heading.ordinal == selectedIndex,
                        first = true,
                        last = section.chapters.isEmpty(),
                        sectionHeading = true,
                        onSelect = onSelect,
                    )
                }
                section.chapters.forEachIndexed { index, chapter ->
                    ChapterDivisionRow(
                        track = chapter,
                        selected = chapter.ordinal == selectedIndex,
                        first = section.heading == null && index == 0,
                        last = index == section.chapters.lastIndex,
                        sectionHeading = false,
                        onSelect = onSelect,
                    )
                }
            }
            if (sectionIndex != sections.lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

/** Lazy version used by the player's chapter sheet so large books only compose visible rows. */
internal fun LazyListScope.chapterDivisionItems(
    chapters: List<ChapterEntity>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    isCompleted: (ChapterEntity) -> Boolean = { false },
) {
    val sections = groupChapters(chapters)
    sections.forEachIndexed { sectionIndex, section ->
        if (section.standalone) {
            val track = section.chapters.single()
            item(key = "chapter-${track.id}") {
                ChapterDivisionRow(
                    track, track.ordinal == selectedIndex, true, true, true, onSelect,
                    isCompleted(track),
                )
            }
        } else {
            section.heading?.let { heading ->
                item(key = "chapter-section-${heading.id}") {
                    ChapterDivisionRow(
                        heading, heading.ordinal == selectedIndex, true,
                        section.chapters.isEmpty(), true, onSelect, isCompleted(heading),
                    )
                }
            }
            items(section.chapters, key = { "chapter-${it.id}" }) { track ->
                val index = section.chapters.indexOf(track)
                ChapterDivisionRow(
                    track,
                    track.ordinal == selectedIndex,
                    section.heading == null && index == 0,
                    index == section.chapters.lastIndex,
                    false,
                    onSelect,
                    isCompleted(track),
                )
            }
        }
        if (sectionIndex != sections.lastIndex) {
            item(key = "chapter-section-space-$sectionIndex") { Spacer(Modifier.height(8.dp)) }
        }
    }
}

internal fun chapterDivisionLazyIndex(chapters: List<ChapterEntity>, ordinal: Int): Int {
    var position = 0
    val sections = groupChapters(chapters)
    sections.forEachIndexed { sectionIndex, section ->
        if (section.standalone) {
            if (section.chapters.single().ordinal == ordinal) return position
            position++
        } else {
            section.heading?.let {
                if (it.ordinal == ordinal) return position
                position++
            }
            section.chapters.forEach {
                if (it.ordinal == ordinal) return position
                position++
            }
        }
        if (sectionIndex != sections.lastIndex) position++
    }
    return position.coerceAtLeast(0)
}

@Composable
private fun ChapterDivisionRow(
    track: ChapterEntity,
    selected: Boolean,
    first: Boolean,
    last: Boolean,
    sectionHeading: Boolean,
    onSelect: (Int) -> Unit,
    completed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val style = LocalChapterListStyle.current
    val accent = style?.accent ?: colors.primary
    // Rows visible when the sheet opens slide in one after another; rows scrolled into view later
    // simply appear, so browsing stays calm.
    val appear =
        remember {
            Animatable(
                if (style == null || android.os.SystemClock.uptimeMillis() - style.openedAt > 700) 1f else 0f
            )
        }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay((track.ordinal % 10) * 30L)
            appear.animateTo(1f, spring(dampingRatio = .72f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val rowColor by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .12f) else colors.surfaceContainerLow,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "Chapter row selection",
    )
    Surface(
        onClick = { onSelect(track.ordinal) },
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
                .semantics { this.selected = selected }
                .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationX = (1f - appear.value) * 28.dp.toPx()
            },
        shape = RoundedCornerShape(12.dp),
        color = rowColor,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Surface(
                        Modifier.width(3.dp).height(24.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = accent,
                    ) {}
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.width(15.dp))
                }
                Text(
                    track.title.ifBlank { "Chapter ${track.ordinal + 1}" },
                    Modifier.weight(1f),
                    style = if (sectionHeading) MaterialTheme.typography.titleSmall
                    else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (sectionHeading) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sectionHeading) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    chapterTime(track.durationMs),
                    color = if (sectionHeading) colors.onSurface else colors.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (completed && !selected) {
                    Spacer(Modifier.width(9.dp))
                    Icon(
                        Icons.Rounded.CheckCircleOutline,
                        contentDescription = "Chapter listened",
                        modifier = Modifier.size(18.dp),
                        tint = if (style != null) accent.copy(alpha = .8f) else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun chapterTime(durationMs: Long): String {
    val seconds = durationMs.coerceAtLeast(0) / 1_000
    return if (seconds >= 3_600) {
        "%02d:%02d:%02d".format(seconds / 3_600, (seconds / 60) % 60, seconds % 60)
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

/** Three bars that bounce while the current chapter plays and rest when paused. */
@Composable
private fun EqualizerBars(color: Color, playing: Boolean) {
    val reduce = LocalReduceMotion.current
    val levels =
        if (playing && !reduce) {
            val transition = rememberInfiniteTransition(label = "Chapter equalizer")
            List(3) { i ->
                transition
                    .animateFloat(
                        .25f,
                        1f,
                        infiniteRepeatable(tween(420 + i * 130, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 110)),
                        label = "Bar $i",
                    )
                    .value
            }
        } else listOf(.45f, .8f, .6f)
    Row(
        Modifier.size(18.dp).semantics { contentDescription = "Current chapter" },
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        levels.forEach { level ->
            Box(
                Modifier.width(3.dp)
                    .fillMaxHeight(level)
                    .background(color, RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * The player's chapter list as separate rounded cards in the book's colours. Each card shows how
 * much of the chapter has been heard; tapping it expands it with its place in the book and a play
 * button, and the play icon jumps straight in. Section headings (Book One…) become banners.
 */
internal fun LazyListScope.chapterCardItems(
    chapters: List<ChapterEntity>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    heard: (ChapterEntity) -> Float,
    bookStartMs: (ChapterEntity) -> Long,
) {
    groupChapters(chapters).forEach { section ->
        val tracks = if (section.standalone) section.chapters else listOfNotNull(section.heading) + section.chapters
        tracks.forEach { track ->
            val heading = section.heading?.id == track.id
            item(key = "card-${track.id}") {
                if (heading) SectionBanner(track, track.ordinal == selectedIndex, onSelect)
                else ChapterCard(track, track.ordinal == selectedIndex, heard(track), bookStartMs(track), onSelect)
            }
        }
    }
}

@Composable
private fun SectionBanner(track: ChapterEntity, selected: Boolean, onSelect: (Int) -> Unit) {
    val accent = LocalChapterListStyle.current?.accent ?: MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(accent.copy(alpha = if (selected) .26f else .14f))
            .clickable { onSelect(track.ordinal) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            track.title.uppercase(),
            Modifier.weight(1f),
            color = accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(time(track.durationMs), color = accent.copy(alpha = .8f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ChapterCard(
    track: ChapterEntity,
    selected: Boolean,
    heard: Float,
    bookStartMs: Long,
    onSelect: (Int) -> Unit,
) {
    val style = LocalChapterListStyle.current
    val accent = style?.accent ?: MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    var open by remember { mutableStateOf(false) }
    val appear =
        remember {
            Animatable(if (style == null || android.os.SystemClock.uptimeMillis() - style.openedAt > 700) 1f else 0f)
        }
    LaunchedEffect(Unit) {
        if (appear.value < 1f) {
            delay((track.ordinal % 10) * 35L)
            appear.animateTo(1f, spring(dampingRatio = .7f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val glow =
        if (selected && style?.playing == true && !LocalReduceMotion.current)
            rememberInfiniteTransition(label = "Current chapter glow")
                .animateFloat(.35f, .9f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "Glow")
                .value
        else .6f
    Column(
        Modifier.fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationX = (1f - appear.value) * 32.dp.toPx()
                scaleX = if (selected) 1.015f else 1f
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = if (selected) .22f else .075f))
            .border(
                if (selected) 1.5.dp else 1.dp,
                accent.copy(alpha = if (selected) glow else .14f),
                RoundedCornerShape(22.dp),
            )
            .clickable(onClickLabel = if (open) "Collapse" else "Show chapter details") { open = !open }
            .animateContentSize(spring(dampingRatio = .75f, stiffness = Spring.StiffnessMediumLow))
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).background(accent.copy(alpha = if (selected) .9f else .18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected && style != null) EqualizerBars(MaterialTheme.colorScheme.onPrimary, style.playing)
                else if (heard >= .98f) Icon(Icons.Rounded.Check, "Chapter listened", tint = accent, modifier = Modifier.size(18.dp))
                else Text("${track.ordinal + 1}", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title.ifBlank { "Chapter ${track.ordinal + 1}" },
                    color = if (selected) accent else ink,
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = if (open) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = .16f))) {
                        Box(Modifier.fillMaxWidth(heard.coerceIn(0f, 1f)).fillMaxHeight().background(accent))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(time(track.durationMs), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
            IconButton({ onSelect(track.ordinal) }) {
                Icon(Icons.Rounded.PlayCircle, "Play chapter", tint = accent, modifier = Modifier.size(30.dp))
            }
        }
        if (open) {
            Column(Modifier.padding(top = 12.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Starts ${time(bookStartMs)} into the book · ${(heard * 100).toInt()}% heard",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
                Button(
                    { onSelect(track.ordinal) },
                    Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(50),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Play from the start", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
