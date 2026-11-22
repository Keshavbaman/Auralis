package app.auralis.importer

import app.auralis.domain.filenameBookCredits
import app.auralis.domain.needsBookAuthor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import app.auralis.data.*
import app.auralis.domain.naturalOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun stableId(text: String) =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") {
        "%02x".format(it)
    }

data class ImportFile(
    val uri: String,
    val name: String,
    val title: String,
    val author: String,
    val album: String,
    val genre: String,
    val duration: Long,
    val artwork: String?,
    val embedded: List<EmbeddedChapter>,
)

data class ImportGroup(
    val title: String,
    val files: List<ImportFile>,
    val existingBookId: String? = null,
)

data class ImportReview(
    val groups: List<ImportGroup>,
    val errors: List<String>,
    val duplicates: Int,
)

@Singleton
class ImportEngine
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val db: AuralisDatabase,
) {
    private val extensions = setOf("mp3", "m4b", "m4a", "aac", "flac", "ogg", "opus", "wav")

    suspend fun scan(uris: List<Uri>, tree: Boolean): ImportReview =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            var duplicates = 0
            val groups = mutableListOf<ImportGroup>()
            val dao = db.dao()
            suspend fun readGroup(label: String, documents: List<DocumentFile>) {
                val files = mutableListOf<ImportFile>()
                val existingIds = mutableSetOf<String>()
                for (d in
                    documents.sortedWith { a, b ->
                        naturalOrder.compare(a.name.orEmpty(), b.name.orEmpty())
                    }) {
                    val existingId = dao.bookIdForUri(d.uri.toString())
                    if (existingId != null) {
                        duplicates++
                        existingIds += existingId
                        continue
                    }
                    runCatching { metadata(d) }
                        .onSuccess { files += it }
                        .onFailure {
                            errors += "${d.name}: ${it.message?:"Could not read this audio file"}"
                        }
                }
                val existingBook = existingIds.singleOrNull()?.let { dao.book(it) }
                if (files.isNotEmpty())
                    groups +=
                        ImportGroup(
                            existingBook?.book?.title
                                ?: if (files.size == 1)
                                    files.first().album.ifBlank { files.first().title }
                                else files.first().album.ifBlank { label },
                            files,
                            existingBook?.book?.id,
                        )
            }
            suspend fun visit(dir: DocumentFile, depth: Int) {
                require(depth <= 24) { "Folder nesting limit reached" }
                val children = dir.listFiles()
                readGroup(
                    dir.name ?: "Audiobook",
                    children.filter {
                        it.isFile && it.name?.substringAfterLast('.')?.lowercase() in extensions
                    },
                )
                for (child in
                    children
                        .filter { it.isDirectory }
                        .sortedWith { a, b ->
                            naturalOrder.compare(a.name.orEmpty(), b.name.orEmpty())
                        }) visit(child, depth + 1)
            }
            if (tree)
                for (uri in uris) {
                    val dir = DocumentFile.fromTreeUri(context, uri)
                    if (dir == null || !dir.canRead())
                        errors += "Folder is unavailable. Select it again to relink."
                    else {
                        dao.source(SourceEntity(uri.toString(), dir.name ?: "Audiobooks"))
                        runCatching { visit(dir, 0) }.onFailure { errors += it.message.orEmpty() }
                    }
                }
            else
                readGroup(
                    "Imported audiobook",
                    uris.mapNotNull { DocumentFile.fromSingleUri(context, it) },
                )
            ImportReview(groups, errors, duplicates)
        }

    private fun metadata(file: DocumentFile): ImportFile {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, file.uri)
            fun tag(k: Int) = r.extractMetadata(k).orEmpty().trim()
            val duration = tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0
            require(duration > 0) { "Audio duration unavailable or format unsupported" }
            val artwork =
                r.embeddedPicture?.let { data ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    options.inSampleSize = 1
                    while (
                        maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 1024
                    ) options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    BitmapFactory.decodeByteArray(data, 0, data.size, options)?.let { bitmap ->
                        val target =
                            File(context.filesDir, "covers")
                                .apply { mkdirs() }
                                .resolve("${stableId(file.uri.toString())}.jpg")
                        target.outputStream().use {
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it)
                        }
                        bitmap.recycle()
                        target.absolutePath
                    }
                }
            val embedded =
                if (file.name?.substringAfterLast('.')?.lowercase() in setOf("m4b", "m4a"))
                    runCatching {
                            context.contentResolver.openFileDescriptor(file.uri, "r")!!.use { pfd ->
                                FileInputStream(pfd.fileDescriptor).use {
                                    Mp4Chapters.read(it.channel)
                                }
                            }
                        }
                        .getOrDefault(emptyList())
                else emptyList()
            return ImportFile(
                file.uri.toString(),
                file.name ?: "Audio",
                tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank {
                    file.name?.substringBeforeLast('.') ?: "Untitled"
                },
                tag(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    .ifBlank { tag(MediaMetadataRetriever.METADATA_KEY_ARTIST) }
                    .ifBlank { "Unknown author" },
                tag(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                duration,
                artwork,
                embedded,
            )
        } finally {
            r.release()
        }
    }

    suspend fun commit(groups: List<ImportGroup>, split: Boolean) =
        withContext(Dispatchers.IO) {
            val imported = mutableListOf<String>()
            for (group in
                groups.flatMap { g ->
                    if (split) g.files.map { ImportGroup(it.title, listOf(it)) } else listOf(g)
                }) db.withTransaction {
                val newFiles = group.files.filter { db.dao().duplicate(it.uri) == 0 }
                if (newFiles.isEmpty()) return@withTransaction
                val existing = group.existingBookId?.let { db.dao().book(it) }
                val id = existing?.book?.id ?: stableId(newFiles.first().uri)
                val first = newFiles.first()
                val title = group.title.ifBlank { first.title }
                val hint = if (needsBookAuthor(first.author)) filenameBookCredits(title) else null
                db.dao()
                    .books(
                        listOf(
                            existing?.book?.copy(title = group.title)
                                ?: BookEntity(
                                    id,
                                    hint?.title ?: title,
                                    hint?.author ?: first.author,
                                    first.genre,
                                    artwork = first.artwork,
                                )
                        )
                    )
                val chapters = mutableListOf<ChapterEntity>()
                db.dao()
                    .files(
                        newFiles.map {
                            AudioFileEntity(stableId(it.uri), id, it.uri, it.name, it.duration)
                        }
                    )
                for (f in newFiles) {
                    val points = f.embedded.filter { it.startMs < f.duration }.toMutableList()
                    if (points.isEmpty() || points.first().startMs > 0)
                        points.add(0, EmbeddedChapter(f.title, 0))
                    for ((i, ch) in points.withIndex()) {
                        val end = points.getOrNull(i + 1)?.startMs ?: f.duration
                        if (end > ch.startMs)
                            chapters +=
                                ChapterEntity(
                                    "${stableId(f.uri)}:${ch.startMs}",
                                    id,
                                    stableId(f.uri),
                                    ch.title,
                                    chapters.size,
                                    ch.startMs,
                                    end,
                                )
                    }
                }
                val oldChapter =
                    existing?.orderedChapters?.getOrNull(existing.progress?.chapterIndex ?: 0)?.id
                val allFiles =
                    (existing?.files.orEmpty() +
                            newFiles.map {
                                AudioFileEntity(stableId(it.uri), id, it.uri, it.name, it.duration)
                            })
                        .sortedWith { a, b -> naturalOrder.compare(a.name, b.name) }
                val allChapters =
                    (existing?.chapters.orEmpty() + chapters)
                        .sortedWith(
                            compareBy<ChapterEntity> { ch ->
                                    allFiles.indexOfFirst { it.id == ch.fileId }
                                }
                                .thenBy { it.startMs }
                        )
                        .mapIndexed { i, ch -> ch.copy(ordinal = i) }
                db.dao().chapters(allChapters)
                if (existing == null) imported += id
                if (oldChapter != null && existing?.progress != null) {
                    val index = allChapters.indexOfFirst { it.id == oldChapter }
                    db.dao().progress(existing.progress.copy(chapterIndex = index.coerceAtLeast(0)))
                }
            }
            imported
        }
}
