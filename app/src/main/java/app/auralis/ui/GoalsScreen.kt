package app.auralis.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.auralis.domain.DayMark
import app.auralis.domain.GoalJourney
import app.auralis.domain.GoalMood
import app.auralis.domain.goalJourney
import app.auralis.domain.statistics
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

private val Warm = Color(0xFFF5BE70)
private val Ember = Color(0xFFFF8A5B)
private val Mint = Color(0xFF7FD8BE)

/** A page to celebrate listening: today's ring, the streak, the week and how often goals are met. */
@Composable
fun GoalsScreen(
    vm: AuralisViewModel,
    back: () -> Unit,
    changeGoal: () -> Unit,
    keepListening: () -> Unit,
) {
    val segments by vm.segments.collectAsStateWithLifecycle()
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val today = rememberListeningDate()
    val journey =
        remember(segments, preferences.goalMinutes, preferences.streakMinutes, today) {
            goalJourney(
                statistics(segments, preferences.streakMinutes, today).daily,
                preferences.goalMinutes,
                preferences.streakMinutes,
                today,
            )
        }
    val reduce = preferences.reducedMotion
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFF2A1F14), .45f to Color(0xFF151416), 1f to Color(0xFF111113)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)
        ) {
            IconButton(back, Modifier.padding(top = 4.dp).background(Color.Black.copy(alpha = .25f), CircleShape)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = PlayerInk)
            }
            Hero(journey, reduce)
            Spacer(Modifier.height(22.dp))
            StreakCard(journey, reduce)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    "${(journey.successRate * 100).toInt()}%",
                    "of days met your goal",
                    Icons.Rounded.Verified,
                    Mint,
                    Modifier.weight(1f),
                )
                Tile(durationLabel(journey.monthMs), "listened this month", Icons.Rounded.Headphones, Warm, Modifier.weight(1f))
                Tile(
                    "${journey.bestStreak} ${if (journey.bestStreak == 1) "day" else "days"}",
                    "best streak",
                    Icons.Rounded.EmojiEvents,
                    Ember,
                    Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(22.dp))
            Button(
                keepListening,
                Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Warm, contentColor = Color(0xFF2A1F14)),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(if (journey.mood == GoalMood.Reached) "Keep the story going" else "Keep listening", fontWeight = FontWeight.SemiBold)
            }
            TextButton(changeGoal, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 28.dp)) {
                Text("Change goal · ${journey.goalMs / 60_000} min a day", color = PlayerMuted)
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun Hero(journey: GoalJourney, reduce: Boolean) {
    val fill = remember { Animatable(0f) }
    val minutes = remember { Animatable(0f) }
    LaunchedEffect(journey.fraction, journey.todayMs) {
        val spec = if (reduce) snap<Float>() else tween(1100, easing = FastOutSlowInEasing)
        fill.animateTo(journey.fraction, spec)
    }
    LaunchedEffect(journey.todayMs) {
        minutes.animateTo((journey.todayMs / 60_000f), if (reduce) snap() else tween(1100, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val stroke = 20.dp.toPx()
                drawArc(Color.White.copy(alpha = .08f), -90f, 360f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(
                    Brush.sweepGradient(listOf(Warm, Ember, Warm)),
                    -90f,
                    360f * fill.value,
                    false,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (journey.mood == GoalMood.Reached) TrophyBounce(reduce)
                Text(
                    "${minutes.value.toInt()}",
                    color = PlayerInk,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1.5).sp,
                )
                Text("of ${journey.goalMs / 60_000} min today", color = PlayerMuted, fontSize = 14.sp)
            }
            if (journey.mood == GoalMood.Reached && !reduce) Confetti(Modifier.fillMaxSize())
        }
        Text(
            when (journey.mood) {
                GoalMood.Reached -> "Goal reached!"
                GoalMood.Close -> "Almost there"
                GoalMood.KeepStreak -> "Keep the fire going"
                GoalMood.Begin -> "Your story is waiting"
            },
            color = PlayerInk,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            when (journey.mood) {
                GoalMood.Reached ->
                    if (journey.newBest) "And a new best streak: ${journey.streak} days in a row."
                    else "You gave yourself ${durationLabel(journey.todayMs)} of stories today."
                GoalMood.Close -> "Just ${durationLabel(journey.remainingMs)} to go. One more chapter does it."
                GoalMood.KeepStreak ->
                    "Your ${journey.streak}-day streak is waiting. ${journey.streakMs / 60_000} minutes keeps it alive."
                GoalMood.Begin -> "A few minutes is all it takes to start a streak."
            },
            color = PlayerMuted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
        )
    }
}

@Composable
private fun TrophyBounce(reduce: Boolean) {
    val pop = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduce) pop.animateTo(1f, spring(dampingRatio = .4f, stiffness = Spring.StiffnessLow)) }
    Icon(
        Icons.Rounded.EmojiEvents,
        null,
        tint = Warm,
        modifier = Modifier.size(34.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    )
}

/** A short burst of falling paper in warm colours, played once when the page opens. */
@Composable
private fun Confetti(modifier: Modifier) {
    val pieces = remember {
        val random = Random(7)
        List(70) {
            floatArrayOf(
                random.nextFloat(), // x
                .6f + random.nextFloat() * .8f, // fall speed
                random.nextFloat() * 360f, // spin
                random.nextFloat(), // colour pick
                random.nextFloat() * .35f, // start delay
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2_800, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    val colours = listOf(Warm, Ember, Mint, PlayerInk, Color(0xFFB89CFF))
    Canvas(modifier) {
        pieces.forEach { p ->
            val t = ((progress.value - p[4]) / (1f - p[4])).coerceIn(0f, 1f)
            if (t <= 0f) return@forEach
            val x = p[0] * size.width + sin((t * 6f + p[2]).toDouble()).toFloat() * 18f
            val y = -20f + t * p[1] * size.height * 1.3f
            rotate(p[2] + t * 540f, Offset(x, y)) {
                drawRect(
                    colours[(p[3] * colours.size).toInt().coerceAtMost(colours.lastIndex)].copy(alpha = 1f - t * .8f),
                    topLeft = Offset(x - 5f, y - 3f),
                    size = Size(10f, 6f),
                )
            }
        }
    }
}

@Composable
private fun StreakCard(journey: GoalJourney, reduce: Boolean) {
    val pulse =
        if (reduce || journey.streak == 0) 1f
        else
            rememberInfiniteTransition(label = "Flame")
                .animateFloat(.92f, 1.08f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Flame pulse")
                .value
    Column(
        Modifier.fillMaxWidth()
            .background(Color.White.copy(alpha = .05f), RoundedCornerShape(26.dp))
            .border(1.dp, Color.White.copy(alpha = .07f), RoundedCornerShape(26.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .background(Brush.radialGradient(listOf(Ember.copy(alpha = .45f), Color.Transparent)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, null, tint = if (journey.streak > 0) Ember else PlayerMuted, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    if (journey.streak > 0) "${journey.streak}-day streak" else "Start a streak today",
                    color = PlayerInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${journey.streakMs / 60_000} min a day counts · best ${journey.bestStreak}",
                    color = PlayerMuted,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            journey.week.forEachIndexed { index, day -> DayBubble(day, index, isToday = index == journey.week.lastIndex, reduce) }
        }
    }
}

@Composable
private fun DayBubble(day: DayMark, index: Int, isToday: Boolean, reduce: Boolean) {
    val pop = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduce) return@LaunchedEffect
        delay(120L + index * 70L)
        pop.animateTo(1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMediumLow))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(38.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .background(
                    when {
                        day.goalMet -> Warm
                        day.streakDay -> Ember.copy(alpha = .35f)
                        else -> Color.White.copy(alpha = .06f)
                    },
                    CircleShape,
                )
                .then(if (isToday) Modifier.border(2.dp, PlayerInk.copy(alpha = .85f), CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            when {
                day.goalMet -> Icon(Icons.Rounded.Check, null, tint = Color(0xFF2A1F14), modifier = Modifier.size(20.dp))
                day.streakDay -> Icon(Icons.Rounded.LocalFireDepartment, null, tint = Ember, modifier = Modifier.size(20.dp))
                else -> {}
            }
        }
        Text(
            day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
            color = if (isToday) PlayerInk else PlayerMuted,
            fontSize = 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Tile(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, modifier: Modifier) {
    Column(
        modifier
            .background(color.copy(alpha = .10f), RoundedCornerShape(22.dp))
            .border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(22.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(value, color = PlayerInk, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = PlayerMuted, fontSize = 12.sp, lineHeight = 15.sp)
    }
}
