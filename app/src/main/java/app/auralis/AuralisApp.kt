package app.auralis

import android.app.Application
import android.content.Context
import androidx.room.Room
import app.auralis.branding.*
import app.auralis.data.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltAndroidApp
class AuralisApp : Application() {
    val launchPolicy = LaunchPolicy()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Inject lateinit var launcherIcons: LauncherIcons
    @Inject lateinit var preferences: PreferenceStore

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            preferences.flow
                .map { it.appIcon }
                .distinctUntilChanged()
                .collect { runCatching { launcherIcons.reconcile() } }
        }
        androidx.work.WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "cover-maintenance",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                androidx.work
                    .PeriodicWorkRequestBuilder<CoverMaintenanceWorker>(
                        7,
                        java.util.concurrent.TimeUnit.DAYS,
                    )
                    .setConstraints(
                        androidx.work.Constraints.Builder().setRequiresBatteryNotLow(true).build()
                    )
                    .build(),
            )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AuralisDatabase =
        Room.databaseBuilder(context, AuralisDatabase::class.java, "auralis.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
            .build()

    @Provides fun dao(database: AuralisDatabase): AuralisDao = database.dao()
}
