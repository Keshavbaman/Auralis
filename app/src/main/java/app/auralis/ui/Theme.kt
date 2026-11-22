package app.auralis.ui

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.repeatOnLifecycle
import app.auralis.data.*
import coil3.compose.AsyncImage
import java.io.File

val Gold = Color(0xFFF5BE70)
val Night = Color(0xFF111113)
val LocalReduceMotion = staticCompositionLocalOf { false }
private val dark =
    darkColorScheme(
        primary = Gold,
        onPrimary = Color(0xFF30220E),
        secondary = Color(0xFFB6CFC7),
        secondaryContainer = Color(0xFF4C3B25),
        onSecondaryContainer = Gold,
        background = Color(0xFF111113),
        surface = Color(0xFF1B1B1F),
        surfaceContainerLow = Color(0xFF161619),
        surfaceContainerHigh = Color(0xFF242428),
        surfaceContainerHighest = Color(0xFF303037),
        surfaceContainer = Color(0xFF202025),
        surfaceVariant = Color(0xFF282B32),
        onBackground = Color(0xFFF5F3EF),
        onSurface = Color(0xFFF5F3EF),
        onSurfaceVariant = Color(0xFFA5A5AA),
    )
private val light =
    lightColorScheme(
        primary = Color(0xFF80531B),
        secondary = Color(0xFF41675B),
        background = Color(0xFFFCF8F1),
        surface = Color(0xFFF4EFE7),
    )

@Composable
fun AuralisTheme(p: Preferences, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val introVisible = app.auralis.branding.LocalBrandIntroVisible.current
    SideEffect {
        (context as? android.app.Activity)?.window?.let { window ->
            val bars = androidx.core.view.WindowCompat.getInsetsController(window, view)
            bars.isAppearanceLightStatusBars = p.theme == "light" && !introVisible
            bars.isAppearanceLightNavigationBars = p.theme == "light" && !introVisible
        }
    }
    val selected =
        if (p.dynamic && Build.VERSION.SDK_INT >= 31) {
            if (p.theme == "light") dynamicLightColorScheme(context)
            else dynamicDarkColorScheme(context)
        } else if (p.theme == "light") light else dark
    val base = if (p.theme == "light") light else dark
    val colors =
        selected.copy(
            background = base.background,
            surface = base.surface,
            surfaceContainer = base.surfaceContainer,
            surfaceContainerLow = base.surfaceContainerLow,
            surfaceContainerHigh = base.surfaceContainerHigh,
            surfaceContainerHighest = base.surfaceContainerHighest,
            onBackground = base.onBackground,
            onSurface = base.onSurface,
            onSurfaceVariant = base.onSurfaceVariant,
        )
    CompositionLocalProvider(LocalReduceMotion provides p.reducedMotion) {
        MaterialTheme(
            colorScheme = colors,
            typography =
                Typography(
                    headlineLarge =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 30.sp,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1.1).sp,
                        ),
                    headlineMedium =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 27.sp,
                            lineHeight = 34.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-.7).sp,
                        ),
                    headlineSmall =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 23.sp,
                            lineHeight = 29.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.5).sp,
                        ),
                    titleLarge =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 21.sp,
                            lineHeight = 27.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-.35).sp,
                        ),
                    titleMedium =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    titleSmall =
                        androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    bodyLarge =
                        androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
                    bodyMedium =
                        androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
                    bodySmall =
                        androidx.compose.ui.text.TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
                ),
            content = content,
        )
    }
}

fun time(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
    else "%d:%02d".format(s / 60, s % 60)
}

fun hours(ms: Long) = app.auralis.domain.listeningDuration(ms)

@Composable
fun Cover(
    book: BookEntity,
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
) {
    val palette =
        listOf(
            Color(0xFF354A4C),
            Color(0xFF654337),
            Color(0xFF3E3C61),
            Color(0xFF514930),
            Color(0xFF4C3853),
        )
    val color = palette[(book.id.hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier
            .semantics { contentDescription = "Cover of ${book.title}" }
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(color, color.copy(alpha = .45f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (book.artwork != null)
            AsyncImage(
                File(book.artwork),
                null,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        else {
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..5) drawCircle(
                    Gold.copy(alpha = .08f),
                    size.width * (.2f + i * .14f),
                    Offset(size.width * .9f, size.height * .1f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                )
            }
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Headphones, null, tint = Gold, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    book.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Serif),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    book.author,
                    color = Color(0xFFDDD3C3),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (subtitle != null)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
    }
}

@Composable
fun Empty(title: String, description: String, action: String? = null, onAction: () -> Unit = {}) {
    BrandedEmpty(title, description, action, app.auralis.branding.AuralisIcon.Classic, onAction)
}

@Composable
fun BrandedEmpty(
    title: String,
    description: String,
    action: String?,
    brand: app.auralis.branding.AuralisIcon,
    onAction: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        app.auralis.branding.BrandMark(brand, Modifier.size(80.dp))
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            description,
            Modifier.padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) Button(onAction) { Text(action) }
    }
}

@Composable
fun GoalCard(elapsed: Long, goal: Int, streak: Int) {
    val target = (elapsed / (goal * 60000f)).coerceIn(0f, 1f)
    val fraction =
        if (LocalReduceMotion.current) target
        else
            androidx.compose.animation.core
                .animateFloatAsState(target, label = "Daily goal progress")
                .value
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier.size(76.dp).semantics {
                        contentDescription =
                            "Daily listening goal ${(fraction*100).toInt()} percent"
                    },
            ) {
                CircularProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 7.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${(elapsed/60000).toInt()}", style = MaterialTheme.typography.titleLarge)
                    Text("min / $goal", style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Your daily ritual", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (fraction >= 1) "Today's goal is complete. Enjoy your next chapter."
                    else "${(goal-elapsed/60000).coerceAtLeast(0)} min to your daily goal",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "$streak day streak",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
fun ShelfProgress(fraction: Float, modifier: Modifier = Modifier) {
    val value = fraction.coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = .10f)
    val fill = MaterialTheme.colorScheme.primary
    Canvas(modifier.semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) }) {
        drawRoundRect(
            track,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
        )
        if (value > 0)
            drawRoundRect(
                fill,
                size = androidx.compose.ui.geometry.Size(size.width * value, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2),
            )
    }
}

/** Refresh local calendar labels when foregrounded, including a paused player over midnight. */
@Composable
fun rememberListeningDate(): java.time.LocalDate {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    return produceState(java.time.LocalDate.now(), owner) {
            owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    value = java.time.LocalDate.now()
                    kotlinx.coroutines.delay(30000)
                }
            }
        }
        .value
}
