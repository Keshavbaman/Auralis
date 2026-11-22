package app.auralis

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import app.auralis.branding.*
import app.auralis.data.Preferences
import app.auralis.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class BrandingPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun introIsSkippableAndStaysGoneWhenContentRecomposes() {
        compose.mainClock.autoAdvance = false
        var generation by mutableIntStateOf(0)
        compose.setContent {
            val n = generation
            BrandLaunchHost(true, true, false, 1f) { androidx.compose.material3.Text("Library $n") }
        }
        compose.onNodeWithTag("brand-intro").assertIsDisplayed().performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("brand-intro").assertDoesNotExist()
        compose.runOnIdle { generation++ }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText("Library 1").assertExists()
        compose.onNodeWithTag("brand-intro").assertDoesNotExist()
    }

    @Test
    fun reducedMotionDoesNotHoldTheUserForTheFullSequence() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            BrandLaunchHost(true, true, true, 1f) { androidx.compose.material3.Text("Ready") }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("brand-intro").assertDoesNotExist()
        compose.onNodeWithText("Ready").assertExists()
    }

    @Test
    fun approvedIdentityFitsLandscapeAndLargeType() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(1400.dp, 876.dp)) then
                    DeviceConfigurationOverride.FontScale(1.5f)
            ) {
                BrandOpening(brandFrame(1250))
            }
        }
        compose.onNodeWithText("AURALIS").assertIsDisplayed()
        compose.onNodeWithText("Your books. Your world.").assertIsDisplayed()
    }

    @Test
    fun newOpeningFitsNarrowPhoneWithLargeText() {
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 700.dp)) then
                    DeviceConfigurationOverride.FontScale(1.8f)
            ) {
                BrandOpening(brandFrame(1250))
            }
        }
        compose.onNodeWithText("AURALIS").assertIsDisplayed()
        compose.onNodeWithText("Your books. Your world.").assertIsDisplayed()
        compose.onNodeWithText("A world of stories awaits.").assertIsDisplayed()
        val root = compose.onNodeWithTag("brand-intro").fetchSemanticsNode().boundsInRoot
        for (label in listOf("AURALIS", "Your books. Your world.", "A world of stories awaits.")) {
            val text = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
            assertTrue(text.left >= root.left && text.right <= root.right)
        }
    }

    @Test
    fun openingReferencePreview() {
        compose.setContent { BrandOpening(brandFrame(1250)) }
        compose.onNodeWithText("A world of stories awaits.").assertIsDisplayed()
        val context =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        if (context.packageName.endsWith(".verification")) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            java.io.File(context.cacheDir, "opening-reference.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun iconGalleryHasAllFiveChoicesAndAnAccessibleSelection() {
        var picked: AuralisIcon? = null
        compose.setContent {
            AuralisTheme(Preferences(reducedMotion = true)) {
                AppIconGallery(AuralisIcon.Signature, false, {}, { picked = it })
            }
        }
        compose.onNodeWithText("Auralis Signature").assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Auralis Quill"))
        compose.onNodeWithText("Auralis Quill").performClick()
        compose.runOnIdle { assertEquals(AuralisIcon.Quill, picked) }
    }
}
