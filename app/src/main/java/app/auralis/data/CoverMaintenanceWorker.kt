package app.auralis.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Cleans only Auralis-generated cover caches, never user audio. */
class CoverMaintenanceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork() =
        withContext(Dispatchers.IO) {
            runCatching {
                val dao =
                    EntryPointAccessors.fromApplication(
                            applicationContext,
                            ApplicationGraph::class.java,
                        )
                        .database()
                        .dao()
                val used = dao.observeLibrary().first().mapNotNull { it.book.artwork }.toSet()
                File(applicationContext.filesDir, "covers")
                    .listFiles()
                    ?.filter {
                        it.absolutePath !in used &&
                            it.lastModified() < System.currentTimeMillis() - 7 * 86400000L
                    }
                    ?.forEach { it.delete() }
                Result.success()
            }
                .getOrElse { Result.retry() }
        }
}
