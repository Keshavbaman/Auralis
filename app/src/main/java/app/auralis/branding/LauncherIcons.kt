package app.auralis.branding

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.auralis.R
import app.auralis.data.PreferenceStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class AuralisIcon(
    val key: String,
    val title: String,
    val description: String,
    val artwork: Int,
    val launcher: Int,
) {
    Signature(
        "signature",
        "Auralis Signature",
        "A golden A shaped by an open book and a sound wave.",
        R.drawable.brand_signature_v3,
        R.mipmap.ic_launcher_signature,
    ),
    Classic(
        "classic",
        "Auralis Classic",
        "An open book. A world waiting to be heard.",
        R.drawable.brand_classic,
        R.mipmap.ic_launcher_classic,
    ),
    Horizon(
        "horizon",
        "Auralis Horizon",
        "Every book brings a new horizon.",
        R.drawable.brand_horizon,
        R.mipmap.ic_launcher_horizon,
    ),
    Pulse(
        "pulse",
        "Auralis Pulse",
        "The warm rhythm of your listening world.",
        R.drawable.brand_pulse,
        R.mipmap.ic_launcher_pulse,
    ),
    Quill(
        "quill",
        "Auralis Quill",
        "For the words and moments you keep.",
        R.drawable.brand_quill,
        R.mipmap.ic_launcher_quill,
    );

    fun component(context: Context) =
        ComponentName(context.packageName, "${context.packageName}.launcher.$name")

    companion object {
        fun fromKey(key: String?) = entries.find { it.key == key } ?: Signature
    }
}

/** All aliases resolve to the stable MainActivity; neither it nor the media service is disabled. */
@Singleton
class LauncherIcons
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: PreferenceStore,
) {
    private val mutex = Mutex()
    private val pm
        get() = context.packageManager

    fun enabled(): List<AuralisIcon> =
        AuralisIcon.entries.filter { icon ->
            when (pm.getComponentEnabledSetting(icon.component(context))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AuralisIcon.Signature
                else -> false
            }
        }

    suspend fun select(icon: AuralisIcon) = mutex.withLock {
        val previous = enabled().firstOrNull() ?: AuralisIcon.Signature
        try {
            apply(icon)
            preferences.setIcon(icon.key)
        } catch (e: Exception) {
            runCatching { apply(previous) }
            throw e
        }
    }

    suspend fun reconcile() = mutex.withLock {
        val wanted = AuralisIcon.fromKey(preferences.flow.first().appIcon)
        if (enabled() != listOf(wanted)) apply(wanted)
    }

    private fun apply(icon: AuralisIcon) {
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(
                AuralisIcon.entries.map { item ->
                    PackageManager.ComponentEnabledSetting(
                        item.component(context),
                        if (item == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            )
        } else applySequential(icon)
    }

    /** API 26–32: enable the new entry first so the app never has zero launchers. */
    internal fun applySequential(icon: AuralisIcon) {
        pm.setComponentEnabledSetting(
            icon.component(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        AuralisIcon.entries
            .filter { it != icon }
            .forEach {
                pm.setComponentEnabledSetting(
                    it.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
    }
}
