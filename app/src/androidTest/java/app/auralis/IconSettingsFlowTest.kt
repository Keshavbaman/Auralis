package app.auralis

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.branding.*
import app.auralis.data.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class IconSettingsFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun isolatedOnly() {
        Assume.assumeTrue(context.packageName.endsWith(".verification"))
    }

    @Test
    fun appearanceGalleryChangesTheActualLauncherAndRestoresSelection() = runBlocking {
        val manager =
            EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java).icons()
        val original = PreferenceStore(context).flow.first()
        try {
            compose.onNodeWithTag("nav-settings").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithText("Appearance").performClick()
            compose.onNodeWithText("App icon").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("Auralis Quill"))
            compose.onNodeWithText("Auralis Quill").performClick()
            compose.waitUntil(5000) { manager.enabled() == listOf(AuralisIcon.Quill) }
            assertEquals(
                "quill",
                withTimeout(5000) {
                    PreferenceStore(context).flow.first { it.appIcon == "quill" }.appIcon
                },
            )
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            assertEquals(listOf(AuralisIcon.Quill), manager.enabled())
        } finally {
            manager.select(AuralisIcon.fromKey(original.appIcon))
        }
    }
}
