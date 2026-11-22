package app.auralis.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map

private val Context.settings by preferencesDataStore("settings")

data class Preferences(
    val goalMinutes: Int = 30,
    val streakMinutes: Int = 10,
    val speed: Float = 1f,
    val skipSeconds: Int = 30,
    val theme: String = "dark",
    val dynamic: Boolean = false,
    val reducedMotion: Boolean = false,
    val lastBook: String = "",
    val fade: Boolean = true,
    val smartRewind: Boolean = true,
    val milestoneHaptics: Boolean = false,
    val appIcon: String = "signature",
    val onlineBookDetails: Boolean = true,
)

@Singleton
class PreferenceStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    val flow =
        context.settings.data.map { p ->
            Preferences(
                p[intPreferencesKey("goal")] ?: 30,
                p[intPreferencesKey("streak")] ?: 10,
                p[floatPreferencesKey("speed")] ?: 1f,
                p[intPreferencesKey("skip")] ?: 30,
                p[stringPreferencesKey("theme")] ?: "dark",
                p[booleanPreferencesKey("dynamic")] ?: false,
                p[booleanPreferencesKey("motion")] ?: false,
                p[stringPreferencesKey("last")] ?: "",
                p[booleanPreferencesKey("fade")] ?: true,
                p[booleanPreferencesKey("rewind")] ?: true,
                p[booleanPreferencesKey("haptics")] ?: false,
                p[stringPreferencesKey("app_icon")] ?: "signature",
                p[booleanPreferencesKey("online_book_details")] ?: true,
            )
        }

    suspend fun setIcon(key: String) {
        context.settings.edit { it[stringPreferencesKey("app_icon")] = key }
    }

    suspend fun setLastBook(id: String) {
        context.settings.edit { it[stringPreferencesKey("last")] = id }
    }

    suspend fun update(p: Preferences) {
        context.settings.edit {
            it[intPreferencesKey("goal")] = p.goalMinutes.coerceIn(1, 1440)
            it[intPreferencesKey("streak")] = p.streakMinutes.coerceIn(1, 1440)
            it[floatPreferencesKey("speed")] = p.speed.coerceIn(.5f, 3f)
            it[intPreferencesKey("skip")] = p.skipSeconds
            it[stringPreferencesKey("theme")] = p.theme
            it[booleanPreferencesKey("dynamic")] = p.dynamic
            it[booleanPreferencesKey("motion")] = p.reducedMotion
            it[stringPreferencesKey("last")] = p.lastBook
            it[booleanPreferencesKey("fade")] = p.fade
            it[booleanPreferencesKey("rewind")] = p.smartRewind
            it[booleanPreferencesKey("haptics")] = p.milestoneHaptics
            it[booleanPreferencesKey("online_book_details")] = p.onlineBookDetails
            it[stringPreferencesKey("app_icon")] =
                app.auralis.branding.AuralisIcon.fromKey(p.appIcon).key
        }
    }
}
