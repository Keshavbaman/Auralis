package app.auralis.branding

import java.util.concurrent.atomic.AtomicBoolean

/** One opportunity per process; restoring an Activity or a service-first launch skips the intro. */
class LaunchPolicy {
    private val claimed = AtomicBoolean(false)

    fun claim(restoringActivity: Boolean, serviceAlreadyRunning: Boolean): Boolean =
        !claimed.getAndSet(true) && !restoringActivity && !serviceAlreadyRunning
}

data class BrandFrame(
    val stroke: Float,
    val artwork: Float,
    val identity: Float,
    val tagline: Float,
    val exit: Float,
)

fun brandFrame(elapsedMs: Long): BrandFrame {
    fun phase(start: Long, end: Long) =
        ((elapsedMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    return BrandFrame(
        phase(200, 650),
        phase(650, 950),
        phase(950, 1150),
        phase(1050, 1250),
        phase(1250, 1500),
    )
}

fun launchDurationMs(scale: Float, reduced: Boolean): Long =
    if (reduced || scale <= 0f || !scale.isFinite()) 0 else (1500 * scale).toLong()
