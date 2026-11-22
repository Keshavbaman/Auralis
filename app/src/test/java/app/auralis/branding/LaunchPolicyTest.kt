package app.auralis.branding

import org.junit.Assert.*
import org.junit.Test

class LaunchPolicyTest {
    @Test
    fun coldProcessClaimsTheFullIntroOnlyOnce() {
        val p = LaunchPolicy()
        assertTrue(p.claim(false, false))
        assertFalse(p.claim(false, false))
        assertFalse(p.claim(true, false))
    }

    @Test
    fun configurationRestoreAndPlaybackReopenDoNotReplayTheIntro() {
        assertFalse(LaunchPolicy().claim(true, false))
        val p = LaunchPolicy()
        assertFalse(p.claim(false, true))
        assertFalse(p.claim(false, false))
    }

    @Test
    fun prescribedPhasesHaveContinuousBoundaries() {
        assertEquals(0f, brandFrame(199).stroke, 0f)
        assertEquals(1f, brandFrame(650).stroke, 0f)
        assertEquals(0f, brandFrame(650).artwork, 0f)
        assertEquals(1f, brandFrame(950).artwork, 0f)
        assertEquals(0f, brandFrame(950).identity, 0f)
        assertEquals(1f, brandFrame(1250).tagline, 0f)
        assertEquals(0f, brandFrame(1250).exit, 0f)
        assertEquals(1f, brandFrame(1500).exit, 0f)
    }

    @Test
    fun reducedMotionAndSystemScaleAreHonoured() {
        assertEquals(1500L, launchDurationMs(1f, false))
        assertEquals(750L, launchDurationMs(.5f, false))
        assertEquals(3000L, launchDurationMs(2f, false))
        assertEquals(0L, launchDurationMs(0f, false))
        assertEquals(0L, launchDurationMs(1f, true))
        assertEquals(0L, launchDurationMs(Float.NaN, false))
    }

    @Test
    fun oldOrUnknownPreferencesUseSignature() {
        assertEquals(AuralisIcon.Signature, AuralisIcon.fromKey(null))
        assertEquals(AuralisIcon.Signature, AuralisIcon.fromKey("unknown"))
    }
}
