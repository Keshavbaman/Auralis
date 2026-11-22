package app.auralis.branding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*

val BrandObsidian = Color(0xFF111113)
val BrandChampagne = Color(0xFFF5BE70)
val BrandIvory = Color(0xFFF5F0E6)
val LocalBrandIntroVisible = staticCompositionLocalOf { false }

@Composable
fun BrandMark(icon: AuralisIcon, modifier: Modifier = Modifier, description: String? = null) {
    Image(painterResource(icon.artwork), description, modifier)
}

/** The signature monogram and wordmark used on the opening screen. */
@Composable
fun BrandOpening(frame: BrandFrame, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFF111111)).testTag("brand-intro").semantics {
            contentDescription = "Auralis. Your books. Your world."
        },
        contentAlignment = Alignment.Center,
    ) {
        val landscape = maxWidth > maxHeight
        val emblem = minOf(if (landscape) maxHeight * .30f else maxWidth * .40f, 160.dp)
        val density = androidx.compose.ui.platform.LocalDensity.current
        // Keep the full wordmark readable at large accessibility font sizes.
        val identitySize = minOf(30f, (maxWidth.value - 64f) / (6.2f * density.fontScale)).sp
        Column(
            Modifier.safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .offset(y = if (landscape) (-12).dp else -maxHeight * .07f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BrandMark(
                AuralisIcon.Signature,
                Modifier.size(emblem).graphicsLayer {
                    alpha = frame.stroke
                    scaleX = .96f + .04f * frame.stroke
                    scaleY = scaleX
                },
            )
            Spacer(Modifier.height(if (landscape) 20.dp else 32.dp))
            Text(
                "AURALIS",
                color = Color(0xFFE5B66D),
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Light,
                fontSize = identitySize,
                letterSpacing = identitySize * .26f,
                modifier = Modifier.alpha(frame.identity),
                maxLines = 1,
            )
            Text(
                "Your books. Your world.",
                color = Color(0xFFB5ADA0),
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp).alpha(frame.tagline),
            )
        }
        Text(
            "A world of stories awaits.",
            color = Color(0xFF968A75),
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(horizontal = 24.dp, vertical = if (landscape) 16.dp else 36.dp)
                    .alpha(frame.tagline),
        )
    }
}

/** The app initializes underneath this short, skippable layer; the audio service is untouched. */
@Composable
fun BrandLaunchHost(
    cold: Boolean,
    preferencesReady: Boolean,
    reduced: Boolean,
    animationScale: Float,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(cold) }
    var elapsed by remember { mutableLongStateOf(0) }
    var contentReady by remember { mutableStateOf(!cold) }
    // Let the lightweight identity draw before constructing the database/controller UI.
    LaunchedEffect(Unit) {
        if (cold) {
            withFrameNanos {}
            withFrameNanos {}
            contentReady = true
        }
    }
    val duration = launchDurationMs(animationScale, reduced)
    LaunchedEffect(visible, preferencesReady, duration, contentReady) {
        if (!visible || !preferencesReady || !contentReady) return@LaunchedEffect
        if (duration == 0L) {
            withFrameNanos {}
            withFrameNanos {}
            visible = false
            return@LaunchedEffect
        }
        val start = withFrameNanos { it }
        while (visible) {
            val now = withFrameNanos { it }
            elapsed = ((now - start) / 1_000_000f / animationScale).toLong()
            if (elapsed >= 1500) {
                visible = false
                break
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(if (visible) Modifier.clearAndSetSemantics {} else Modifier) {
            if (contentReady || !visible)
                CompositionLocalProvider(LocalBrandIntroVisible provides visible) { content() }
        }
        if (visible) {
            val frame =
                if (!preferencesReady) brandFrame(0)
                else if (duration == 0L) brandFrame(1250) else brandFrame(elapsed)
            BrandOpening(
                frame,
                Modifier.alpha(1f - frame.exit).clickable(onClickLabel = "Enter Auralis") {
                    visible = false
                },
            )
            androidx.activity.compose.BackHandler { visible = false }
        }
    }
}
