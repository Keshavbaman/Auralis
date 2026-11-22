package app.auralis.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object AuralisSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

object AuralisShape {
    val artwork = RoundedCornerShape(16.dp)
    val panel = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(28.dp)
}

val LocalArtworkTransition = staticCompositionLocalOf<SharedTransitionScope?> { null }
val LocalArtworkVisibility = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun Modifier.sharedArtwork(bookId: String): Modifier {
    val shared = LocalArtworkTransition.current ?: return this
    val visibility = LocalArtworkVisibility.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedArtwork.sharedElement(
            rememberSharedContentState("playing-cover-$bookId"),
            visibility,
            boundsTransform = { _, _ -> tween(450, easing = FastOutSlowInEasing) },
        )
    }
}

/** Keep single-column screens readable while letting their parent fill the window. */
@Composable
internal fun CenteredScreen(maxWidth: Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) {
            content()
        }
    }
}
