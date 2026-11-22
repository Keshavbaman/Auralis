package app.auralis.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class Backup(
    val version: Int = 1,
    val books: List<BookEntity>,
    val files: List<AudioFileEntity>,
    val chapters: List<ChapterEntity>,
    val progress: List<PlaybackProgressEntity>,
    val sessions: List<ListeningSessionEntity>,
    val segments: List<ListeningSegmentEntity>,
    val bookmarks: List<BookmarkEntity>,
    val achievements: List<UserAchievementEntity>,
    val completions: List<CompletionEntity>,
    val collections: List<CollectionEntity>,
    val memberships: List<CollectionBookCrossRef>,
    val sources: List<SourceEntity>,
    val preferences: Preferences,
    val challenges: List<ChallengeEntity>? = emptyList(),
)

class BackupManager
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AuralisDatabase,
    private val prefs: PreferenceStore,
) {
    suspend fun export(uri: Uri) =
        withContext(Dispatchers.IO) {
            val d = db.dao()
            val snapshot = db.withTransaction {
                val library = d.observeLibrary().first()
                Backup(
                    books = library.map { it.book.copy(artwork = null) },
                    files = library.flatMap { it.files },
                    chapters = library.flatMap { it.chapters },
                    progress = library.mapNotNull { it.progress },
                    sessions = d.sessions(),
                    segments = d.observeSegments().first(),
                    bookmarks = d.observeBookmarks().first(),
                    achievements = d.observeAchievements().first(),
                    completions = d.observeCompletions().first(),
                    collections = d.observeCollections().first(),
                    memberships = d.observeMemberships().first(),
                    sources = d.sources(),
                    preferences = prefs.flow.first(),
                    challenges = d.observeChallenges().first(),
                )
            }
            context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                Gson().toJson(snapshot, it)
            }
        }

    suspend fun restore(uri: Uri) =
        withContext(Dispatchers.IO) {
            val data =
                context.contentResolver.openInputStream(uri)!!.bufferedReader().use { reader ->
                    val text = reader.readTextLimited(64 * 1024 * 1024)
                    Gson().fromJson(text, Backup::class.java)
                }
            require(data.version == 1) { "Unsupported backup version ${data.version}" }
            require(
                data.books.size <= 100000 &&
                    data.segments.all {
                        it.elapsedMs >= 0 && it.contentMs >= 0 && it.toMs >= it.fromMs
                    }
            ) {
                "Invalid backup data"
            }
            val d = db.dao()
            db.withTransaction {
                // Restore merges immutable event IDs; retries cannot duplicate listening history.
                d.books(
                    data.books.map {
                        it.copy(
                            seriesName = it.seriesName.orEmpty(),
                            edition = it.edition.orEmpty(),
                        )
                    }
                )
                d.files(data.files)
                d.chapters(data.chapters)
                data.progress.forEach { d.progress(it) }
                data.sessions.forEach { d.session(it) }
                data.segments.forEach { d.segment(it) }
                data.bookmarks.forEach { d.bookmark(it) }
                data.achievements.forEach { d.unlock(it) }
                data.completions.forEach { d.complete(it) }
                data.challenges.orEmpty().forEach { d.challenge(it) }
                data.collections.forEach { d.collection(it) }
                data.memberships.forEach { d.membership(it) }
                data.sources.forEach { d.source(it) }
            }
            prefs.update(data.preferences)
        }

    private fun java.io.Reader.readTextLimited(limit: Int): String {
        val result = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            require(result.length + n <= limit) { "Backup exceeds 64 MiB safety limit" }
            result.append(buffer, 0, n)
        }
        return result.toString()
    }
}
