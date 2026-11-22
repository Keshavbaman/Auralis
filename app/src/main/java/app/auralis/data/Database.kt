package app.auralis.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books", indices = [Index("addedAt")])
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String = "Unknown author",
    val genre: String = "",
    val narrator: String = "",
    val artwork: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val favourite: Boolean = false,
    val manuallyCompleted: Boolean = false,
    @ColumnInfo(defaultValue = "0") val progressResetAt: Long = 0,
    @ColumnInfo(defaultValue = "''") val seriesName: String = "",
    val seriesNumber: Double? = null,
    @ColumnInfo(defaultValue = "''") val edition: String = "",
    @ColumnInfo(defaultValue = "0") val seriesInferenceDisabled: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val companionPdfUri: String? = null,
    @ColumnInfo(defaultValue = "0") val companionPdfPage: Int = 0,
)

@Entity(
    tableName = "files",
    indices = [Index(value = ["uri"], unique = true), Index("bookId")],
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class AudioFileEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val uri: String,
    val name: String,
    val durationMs: Long,
)

@Entity(
    tableName = "chapters",
    indices = [Index("bookId"), Index("fileId")],
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = AudioFileEntity::class,
                parentColumns = ["id"],
                childColumns = ["fileId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class ChapterEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val fileId: String,
    val title: String,
    val ordinal: Int,
    val startMs: Long = 0,
    val endMs: Long,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0)
}

@Entity(
    tableName = "progress",
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class PlaybackProgressEntity(
    @PrimaryKey val bookId: String,
    val chapterIndex: Int = 0,
    val positionMs: Long = 0,
    val lastPlayed: Long = 0,
    val speed: Float = 1f,
)

@Entity(tableName = "sessions", indices = [Index("bookId"), Index("startedAt")])
data class ListeningSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val startedAt: Long,
    val endedAt: Long,
)

@Entity(
    tableName = "segments",
    indices = [Index("sessionId"), Index("date"), Index("bookId"), Index("chapterId")],
)
data class ListeningSegmentEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val bookId: String,
    val chapterId: String,
    val startedAt: Long,
    val endedAt: Long,
    val date: String,
    val zoneId: String,
    val elapsedMs: Long,
    val contentMs: Long,
    val fromMs: Long,
    val toMs: Long,
    val speed: Float,
)

@Entity(
    tableName = "bookmarks",
    indices = [Index("bookId")],
    foreignKeys =
        [
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val title: String = "Bookmark",
    val note: String = "",
)

@Entity(tableName = "collections")
data class CollectionEntity(@PrimaryKey val id: String, val name: String)

@Entity(
    tableName = "collection_books",
    primaryKeys = ["collectionId", "bookId"],
    indices = [Index("bookId")],
    foreignKeys =
        [
            ForeignKey(
                entity = CollectionEntity::class,
                parentColumns = ["id"],
                childColumns = ["collectionId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = BookEntity::class,
                parentColumns = ["id"],
                childColumns = ["bookId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
)
data class CollectionBookCrossRef(val collectionId: String, val bookId: String)

@Entity(tableName = "achievements")
data class UserAchievementEntity(@PrimaryKey val key: String, val unlockedAt: Long)

@Entity(tableName = "sources")
data class SourceEntity(@PrimaryKey val uri: String, val name: String)

@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val id: String,
    val title: String,
    val metric: String,
    val target: Long,
    val startsOn: String,
    val endsOn: String,
)

@Entity(tableName = "completed", indices = [Index("bookId")])
data class CompletionEntity(@PrimaryKey val bookId: String, val completedAt: Long)

data class LibraryBook(
    @Embedded val book: BookEntity,
    @Relation(parentColumn = "id", entityColumn = "bookId") val chapters: List<ChapterEntity>,
    @Relation(parentColumn = "id", entityColumn = "bookId") val files: List<AudioFileEntity>,
    @Relation(parentColumn = "id", entityColumn = "bookId") val progress: PlaybackProgressEntity?,
) {
    val orderedChapters
        get() = chapters.sortedBy { it.ordinal }

    val durationMs
        get() = chapters.sumOf { it.durationMs }

    val positionMs: Long
        get() {
            val ordered = orderedChapters
            val index =
                (progress?.chapterIndex ?: 0).coerceIn(0, (ordered.size - 1).coerceAtLeast(0))
            return ordered.take(index).sumOf { it.durationMs } +
                (progress?.positionMs ?: 0).coerceIn(0, ordered.getOrNull(index)?.durationMs ?: 0)
        }

    @Ignore var listenedFraction: Float? = null
    val coverageFraction
        get() = listenedFraction ?: 0f

    val playbackFraction
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val isFinished
        get() = book.manuallyCompleted || coverageFraction >= .98f

    // Shelf progress always means timeline position. Coverage remains a separate metric.
    val fraction
        get() = if (book.manuallyCompleted) 1f else playbackFraction

    // StateFlow equality must include the computed coverage as segments arrive.
    override fun equals(other: Any?) =
        other is LibraryBook &&
            book == other.book &&
            chapters == other.chapters &&
            files == other.files &&
            progress == other.progress &&
            listenedFraction == other.listenedFraction

    override fun hashCode() = listOf(book, chapters, files, progress, listenedFraction).hashCode()
}

@Dao
interface AuralisDao {
    @Transaction
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeLibrary(): Flow<List<LibraryBook>>

    @Transaction
    @Query("SELECT * FROM books WHERE id=:id")
    suspend fun book(id: String): LibraryBook?

    @Query("SELECT * FROM segments ORDER BY startedAt")
    fun observeSegments(): Flow<List<ListeningSegmentEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM achievements")
    fun observeAchievements(): Flow<List<UserAchievementEntity>>

    @Query("SELECT * FROM completed") fun observeCompletions(): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM collections") fun observeCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collection_books")
    fun observeMemberships(): Flow<List<CollectionBookCrossRef>>

    @Query("SELECT * FROM segments WHERE bookId=:bookId AND startedAt>=:since")
    suspend fun bookSegments(bookId: String, since: Long): List<ListeningSegmentEntity>

    @Query("SELECT * FROM challenges ORDER BY startsOn DESC")
    fun observeChallenges(): Flow<List<ChallengeEntity>>

    @Upsert suspend fun challenge(row: ChallengeEntity)

    @Query("SELECT * FROM sessions") suspend fun sessions(): List<ListeningSessionEntity>

    @Query("SELECT * FROM sources") suspend fun sources(): List<SourceEntity>

    @Query("SELECT bookId FROM files WHERE uri=:uri LIMIT 1")
    suspend fun bookIdForUri(uri: String): String?

    @Query("SELECT COUNT(*) FROM files WHERE uri=:uri") suspend fun duplicate(uri: String): Int

    @Query("SELECT * FROM books")
    suspend fun bookMetadata(): List<BookEntity>

    @Query("UPDATE books SET seriesName=:name, seriesNumber=:number, seriesInferenceDisabled=:disableInference WHERE id=:id")
    suspend fun setSeries(id: String, name: String, number: Double?, disableInference: Boolean)

    @Query("UPDATE books SET narrator=:narrator WHERE id=:id AND narrator=:previous")
    suspend fun setNarrator(id: String, previous: String, narrator: String): Int

    @Query("UPDATE books SET companionPdfUri=:uri, companionPdfPage=0 WHERE id=:id")
    suspend fun setCompanionPdf(id: String, uri: String?)

    @Query("UPDATE books SET companionPdfPage=:page WHERE id=:id")
    suspend fun setCompanionPdfPage(id: String, page: Int)

    @Query("""
        UPDATE books SET title=:title, author=:author, narrator=:narrator
        WHERE id=:id AND title=:oldTitle AND author=:oldAuthor
        AND narrator=:oldNarrator AND edition=:oldEdition
    """)
    suspend fun enrichBookCredits(
        id: String,
        oldTitle: String,
        oldAuthor: String,
        oldNarrator: String,
        oldEdition: String,
        title: String,
        author: String,
        narrator: String,
    ): Int

    @Upsert suspend fun books(rows: List<BookEntity>)

    @Upsert suspend fun files(rows: List<AudioFileEntity>)

    @Upsert suspend fun chapters(rows: List<ChapterEntity>)

    @Upsert suspend fun progress(row: PlaybackProgressEntity)

    @Upsert suspend fun session(row: ListeningSessionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun segment(row: ListeningSegmentEntity)

    @Upsert suspend fun bookmark(row: BookmarkEntity)

    @Upsert suspend fun source(row: SourceEntity)

    @Upsert suspend fun collection(row: CollectionEntity)

    @Query("DELETE FROM collection_books WHERE collectionId=:collectionId AND bookId=:bookId")
    suspend fun removeMembership(collectionId: String, bookId: String)

    @Upsert suspend fun membership(row: CollectionBookCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun unlock(row: UserAchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun complete(row: CompletionEntity)

    @Query("DELETE FROM bookmarks WHERE id=:id") suspend fun deleteBookmark(id: String)

    @Query("DELETE FROM books WHERE id=:id") suspend fun deleteBook(id: String)

    @Query("DELETE FROM progress WHERE bookId=:id") suspend fun resetProgress(id: String)
}

@Database(
    entities =
        [
            BookEntity::class,
            AudioFileEntity::class,
            ChapterEntity::class,
            PlaybackProgressEntity::class,
            ListeningSessionEntity::class,
            ListeningSegmentEntity::class,
            BookmarkEntity::class,
            CollectionEntity::class,
            CollectionBookCrossRef::class,
            UserAchievementEntity::class,
            SourceEntity::class,
            CompletionEntity::class,
            ChallengeEntity::class,
        ],
    version = 6,
    exportSchema = true,
)
abstract class AuralisDatabase : RoomDatabase() {
    abstract fun dao(): AuralisDao
}
