package app.auralis.domain

import kotlin.math.abs

/** What a drag has turned out to be so far. */
enum class SwipeAxis {
    Undecided,
    Horizontal,
    Vertical,
}

/**
 * Locks a drag to one axis once it has clearly moved. A drag must travel past [slop] and be at
 * least [ratio] times longer along one axis than the other, so diagonal scrolling never counts as
 * a sideways swipe.
 */
fun swipeAxis(dx: Float, dy: Float, slop: Float, ratio: Float = 1.8f): SwipeAxis =
    when {
        abs(dx) > slop && abs(dx) > abs(dy) * ratio -> SwipeAxis.Horizontal
        abs(dy) > slop && abs(dy) > abs(dx) * ratio -> SwipeAxis.Vertical
        else -> SwipeAxis.Undecided
    }

/**
 * Whether a released swipe commits, and which way: -1 for a swipe towards the start (finger moved
 * right), +1 towards the end (finger moved left), 0 to spring back. It commits when dragged past
 * [distanceFraction] of [extent], or flung faster than [flingVelocity] in the same direction.
 */
fun swipeCommit(
    distance: Float,
    velocity: Float,
    extent: Float,
    distanceFraction: Float = .25f,
    flingVelocity: Float = 1_200f,
): Int {
    val far = abs(distance) > extent * distanceFraction
    val fast = abs(velocity) > flingVelocity && velocity * distance > 0
    if (!far && !fast) return 0
    // Finger moving left (negative distance) means "go to the next one".
    return if (distance < 0) 1 else -1
}
