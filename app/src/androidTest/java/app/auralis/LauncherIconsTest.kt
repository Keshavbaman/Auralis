package app.auralis

import android.content.Intent
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.branding.*
import app.auralis.data.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class LauncherIconsTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun isolatedOnly() {
        Assume.assumeTrue(context.packageName.endsWith(".verification"))
    }

    @Test
    fun fiveAdaptiveIconsHaveMonochromeLayersAndOnePersistentLauncher() = runBlocking {
        val icons =
            EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java).icons()
        val store = PreferenceStore(context)
        val original = store.flow.first()
        val pid = android.os.Process.myPid()
        try {
            for (icon in AuralisIcon.entries) {
                val drawable = context.getDrawable(icon.launcher) as AdaptiveIconDrawable
                assertNotNull(drawable.foreground)
                assertNotNull(drawable.background)
                if (Build.VERSION.SDK_INT >= 33) assertNotNull(drawable.monochrome)
                icons.select(icon)
                assertEquals(listOf(icon), icons.enabled())
                assertEquals(icon.key, store.flow.first().appIcon)
                val available =
                    context.packageManager.queryIntentActivities(
                        Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setPackage(context.packageName),
                        0,
                    )
                assertEquals(1, available.size)
                assertEquals(
                    icon.component(context).className,
                    available.single().activityInfo.name,
                )
                assertEquals(pid, android.os.Process.myPid())
                assertTrue(
                    context.packageManager
                        .getActivityInfo(
                            android.content.ComponentName(context, MainActivity::class.java),
                            0,
                        )
                        .enabled
                )
            }
        } finally {
            icons.select(AuralisIcon.fromKey(original.appIcon))
        }
    }

    @Test
    fun legacyEnableFirstSequenceAlsoLeavesExactlyOneLauncher() = runBlocking {
        val icons =
            EntryPointAccessors.fromApplication(context, ApplicationGraph::class.java).icons()
        val store = PreferenceStore(context)
        val original = store.flow.first()
        try {
            store.setIcon(AuralisIcon.Classic.key)
            icons.applySequential(AuralisIcon.Classic)
            assertEquals(listOf(AuralisIcon.Classic), icons.enabled())
        } finally {
            icons.select(AuralisIcon.fromKey(original.appIcon))
        }
    }
}
