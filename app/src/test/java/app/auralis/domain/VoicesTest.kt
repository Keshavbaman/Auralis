package app.auralis.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VoicesTest {
    /** A fingerprint near [axis] with a small, deterministic wobble. */
    private fun near(axis: Int, wobble: Float = 0f) =
        FloatArray(8) { i -> if (i == axis) 1f else if (i == (axis + 1) % 8) wobble else 0f }

    @Test
    fun separatesDistinctVoicesAndNumbersThemByFirstAppearance() {
        val ids =
            clusterVoices(
                listOf(near(3), near(0), near(3, .1f), near(0, .1f), near(3, .05f), near(0, .05f))
            )
        assertArrayEquals(intArrayOf(0, 1, 0, 1, 0, 1), ids)
    }

    @Test
    fun keepsUtterancesWithoutAFingerprintUnassigned() {
        val ids = clusterVoices(listOf(near(1), null, near(1, .1f)))
        assertArrayEquals(intArrayOf(0, -1, 0), ids)
    }

    @Test
    fun foldsAOneOffIntoTheNearestRealVoice() {
        val fingerprints = MutableList<FloatArray?>(40) { near(0, it % 3 * .05f) }
        // A single stray utterance between two axes would otherwise become its own voice.
        fingerprints += FloatArray(8) { i -> if (i == 0) .3f else if (i == 5) .954f else 0f }
        val ids = clusterVoices(fingerprints)
        assertEquals(setOf(0), ids.toSet())
    }

    @Test
    fun handlesNoFingerprintsAtAll() {
        assertArrayEquals(intArrayOf(-1, -1), clusterVoices(listOf(null, null)))
        assertEquals("Voice 3", voiceLabel(2))
    }
}
