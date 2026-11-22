package app.auralis.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import app.auralis.branding.*
import app.auralis.domain.*

@Composable
fun ProfileWorldContent(
    stats: ListeningStats,
    goalMinutes: Int,
    unlocked: Set<String>,
    challengeCount: Int,
    finishedCount: Int,
    navigate: (String) -> Unit,
) {
    val rank = levels.indexOfLast { stats.elapsed >= it.second * 3600000L }.coerceAtLeast(0)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxSize()) {
            val wide = maxWidth >= 720.dp
            LazyColumn(
                Modifier.fillMaxSize().testTag("world-scroll"),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "identity") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                BrandMark(AuralisIcon.Signature, Modifier.size(22.dp))
                                Text(
                                    "YOUR PERSONAL SPACE",
                                    style = MaterialTheme.typography.labelSmall,
                                    letterSpacing = 1.6.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                "Your world",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                            Text(
                                "A little more you, with every story.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton({ navigate("preferences") }) {
                            Icon(
                                Icons.Rounded.Settings,
                                "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item(key = "journey") {
                    if (wide)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            WorldRankCard(stats, rank, Modifier.weight(1.2f)) { navigate("ranks") }
                            WorldGoalCard(
                                stats.daily[rememberListeningDate()] ?: 0,
                                goalMinutes,
                                Modifier.weight(1f),
                            ) {
                                navigate("preferences/Goals")
                            }
                        }
                    else WorldRankCard(stats, rank) { navigate("ranks") }
                }
                item(key = "moments") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WorldMetric(
                                hours(stats.elapsed),
                                "Time well spent",
                                Modifier.weight(1f),
                            )
                            Box(
                                Modifier.width(1.dp)
                                    .height(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
                                    )
                            )
                            WorldMetric(
                                "${stats.streak.current}",
                                "Day streak",
                                Modifier.weight(1f),
                            )
                            Box(
                                Modifier.width(1.dp)
                                    .height(32.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f)
                                    )
                            )
                            WorldMetric("$finishedCount", "Books finished", Modifier.weight(1f))
                        }
                    }
                }
                if (!wide)
                    item(key = "daily-goal") {
                        WorldGoalCard(stats.daily[rememberListeningDate()] ?: 0, goalMinutes) {
                            navigate("preferences/Goals")
                        }
                    }
                item(key = "trophy-heading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Small moments. Lasting milestones.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Trophy room",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        TextButton({ navigate("achievements") }) { Text("View all") }
                    }
                    Text(
                        "${unlocked.size} of ${badges.size} earned",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                item(key = "medals") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 4.dp),
                    ) {
                        items(
                            badges.sortedByDescending { it.key in unlocked }.take(5),
                            key = { it.key },
                        ) { badge ->
                            val earned = badge.key in unlocked
                            Surface(
                                onClick = { navigate("achievements") },
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.width(112.dp),
                            ) {
                                Column(
                                    Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Medal(badge.key, earned, Modifier.size(66.dp))
                                    Text(
                                        badge.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.heightIn(min = 34.dp),
                                    )
                                    Text(
                                        if (earned) "Unlocked" else "To discover",
                                        style = MaterialTheme.typography.labelSmall,
                                        color =
                                            if (earned) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "essentials") {
                    Text(
                        "Made for your listening life",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        Column {
                            WorldAction(
                                Icons.Rounded.Flag,
                                "Personal challenges",
                                "$challengeCount goals on your journey",
                            ) {
                                navigate("preferences/Challenges")
                            }
                            HorizontalDivider(
                                Modifier.padding(start = 76.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f),
                            )
                            WorldAction(
                                Icons.Rounded.History,
                                "Listening history",
                                "${stats.sessions} sessions, one growing story",
                            ) {
                                navigate("history")
                            }
                            HorizontalDivider(
                                Modifier.padding(start = 76.dp, end = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .25f),
                            )
                            WorldAction(
                                Icons.Rounded.Tune,
                                "Settings",
                                "Playback, appearance & your library",
                            ) {
                                navigate("preferences")
                            }
                        }
                    }
                }
                item(key = "sign-off") {
                    Text(
                        "YOUR BOOKS. YOUR WORLD.",
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 2.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .75f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorldRankCard(
    stats: ListeningStats,
    rank: Int,
    modifier: Modifier = Modifier,
    click: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val next = levels.getOrNull(rank + 1)
    val lower = levels[rank].second * 3600000L
    val progress =
        if (next == null) 1f
        else ((stats.elapsed - lower).toFloat() / (next.second * 3600000L - lower)).coerceIn(0f, 1f)
    Surface(
        onClick = click,
        modifier = modifier.fillMaxWidth().testTag("world-rank"),
        shape = RoundedCornerShape(28.dp),
        color = colors.surfaceContainer,
        border = BorderStroke(.7.dp, colors.primary.copy(alpha = .22f)),
    ) {
        Column(
            Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            colors.primary.copy(alpha = .10f),
                            Color.Transparent,
                            colors.primary.copy(alpha = .025f),
                        )
                    )
                )
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                RankEmblem(rank, Modifier.size(80.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "YOUR LISTENING LEVEL",
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.3.sp,
                        color = colors.primary,
                    )
                    Text(
                        levels[rank].first,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Serif,
                    )
                    Text(
                        "Every hour opens another world.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            ShelfProgress(
                progress,
                Modifier.fillMaxWidth().height(3.dp).semantics {
                    contentDescription = "Progress to next listening level"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    next?.let {
                        "${hours((it.second*3600000L-stats.elapsed).coerceAtLeast(0))} to ${it.first}"
                    } ?: "Grandmaster · A lifetime of stories",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = colors.primary)
            }
        }
    }
}

@Composable
private fun WorldGoalCard(
    elapsed: Long,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
    click: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val goal = goalMinutes.coerceAtLeast(1)
    val progress = (elapsed / (goal * 60000f)).coerceIn(0f, 1f)
    val animated =
        animateFloatAsState(
            progress,
            if (LocalReduceMotion.current) snap() else tween(400),
            label = "daily-goal",
        )
    Surface(
        onClick = click,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerLow,
        border = BorderStroke(.7.dp, colors.outlineVariant.copy(alpha = .3f)),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Box(
                Modifier.size(68.dp).semantics {
                    contentDescription = "Daily listening goal, ${(progress*100).toInt()} percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    val size = Size(size.width - stroke, size.height - stroke)
                    drawArc(
                        colors.primary.copy(alpha = .12f),
                        -90f,
                        360f,
                        false,
                        Offset(stroke / 2, stroke / 2),
                        size,
                        style = Stroke(stroke),
                    )
                    drawArc(
                        colors.primary,
                        -90f,
                        360f * animated.value,
                        false,
                        Offset(stroke / 2, stroke / 2),
                        size,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                if (progress >= 1f) Icon(Icons.Rounded.Check, null, tint = colors.primary)
                else
                    Text(
                        "${(elapsed/60000).toInt()}",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.primary,
                    )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Your daily ritual", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (progress >= 1f) "Today’s goal, beautifully done."
                    else "${(goal-elapsed/60000).coerceAtLeast(0)} minutes to a little escape.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    "${elapsed/60000} / $goal min today",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun WorldMetric(value: String, label: String, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WorldAction(icon: ImageVector, title: String, subtitle: String, click: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clickable(onClick = click)
            .padding(horizontal = 18.dp, vertical = 16.dp),
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
                Modifier.padding(11.dp).size(20.dp),
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
