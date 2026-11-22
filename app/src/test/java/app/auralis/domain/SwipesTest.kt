package app.auralis.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipesTest {
    @Test
    fun locksToAnAxisOnlyOnceTheDragIsClear() {
        assertEquals(SwipeAxis.Undecided, swipeAxis(dx = 10f, dy = 2f, slop = 20f))
        assertEquals(SwipeAxis.Horizontal, swipeAxis(dx = -60f, dy = 10f, slop = 20f))
        assertEquals(SwipeAxis.Vertical, swipeAxis(dx = 8f, dy = 70f, slop = 20f))
        // A diagonal drag is neither, so scrolling a list never flips a tab.
        assertEquals(SwipeAxis.Undecided, swipeAxis(dx = 50f, dy = 45f, slop = 20f))
    }

    @Test
    fun commitsOnDistanceOrFlingAndPicksTheDirection() {
        assertEquals(1, swipeCommit(distance = -300f, velocity = 0f, extent = 1000f)) // left: next
        assertEquals(-1, swipeCommit(distance = 300f, velocity = 0f, extent = 1000f)) // right: back
        assertEquals(0, swipeCommit(distance = -100f, velocity = -200f, extent = 1000f))
        assertEquals(1, swipeCommit(distance = -100f, velocity = -2000f, extent = 1000f)) // quick flick
        // Flinging against the drag direction does not count.
        assertEquals(0, swipeCommit(distance = -100f, velocity = 2000f, extent = 1000f))
    }
}
