package app.auralis.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.auralis.domain.SwipeAxis
import app.auralis.domain.swipeAxis

/**
 * A swipe that follows the finger and then commits or springs back.
 *
 * - Listens after children, so a shelf, slider or list that already took the drag wins and this
 *   swipe stands down; nothing scrollable is ever hijacked.
 * - Ignores touches that start within [edge] of the left or right screen edge, which belong to
 *   Android's back gesture.
 * - Locks to one axis once the drag is clearly sideways or up/down; [axes] says which it handles.
 *
 * [onDrag] gets the live offset along the locked axis; [onRelease] gets the final offset, the
 * velocity (px/s) and the size along that axis.
 *
 * [first] makes this swipe see touches before the content underneath, for a whole-screen gesture
 * (pull the player down) that must win over a scroll container which cannot actually scroll.
 */
internal fun Modifier.liveSwipe(
    key: Any?,
    axes: Set<SwipeAxis>,
    edge: Dp = 0.dp,
    first: Boolean = false,
    onDrag: (axis: SwipeAxis, offset: Float) -> Unit,
    onRelease: (axis: SwipeAxis, offset: Float, velocity: Float, extent: Float) -> Unit,
): Modifier =
    pointerInput(key) {
        awaitEachGesture {
            val pass = if (first) PointerEventPass.Initial else PointerEventPass.Main
            val down = awaitFirstDown(requireUnconsumed = false, pass = pass)
            val edgePx = edge.toPx()
            if (down.position.x < edgePx || down.position.x > size.width - edgePx) return@awaitEachGesture
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
            var total = Offset.Zero
            var axis = SwipeAxis.Undecided
            while (true) {
                val event = awaitPointerEvent(pass)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                // A child (shelf, slider, list) already claimed this drag.
                if (axis == SwipeAxis.Undecided && change.isConsumed) return@awaitEachGesture
                total += change.positionChange()
                tracker.addPosition(change.uptimeMillis, change.position)
                if (axis == SwipeAxis.Undecided) {
                    val decided = swipeAxis(total.x, total.y, viewConfiguration.touchSlop)
                    if (decided != SwipeAxis.Undecided) {
                        if (decided !in axes) return@awaitEachGesture
                        axis = decided
                    }
                }
                if (axis != SwipeAxis.Undecided) {
                    change.consume()
                    onDrag(axis, if (axis == SwipeAxis.Horizontal) total.x else total.y)
                }
            }
            if (axis != SwipeAxis.Undecided) {
                val velocity = tracker.calculateVelocity()
                val horizontal = axis == SwipeAxis.Horizontal
                onRelease(
                    axis,
                    if (horizontal) total.x else total.y,
                    if (horizontal) velocity.x else velocity.y,
                    if (horizontal) size.width.toFloat() else size.height.toFloat(),
                )
            }
        }
    }
