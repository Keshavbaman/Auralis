package app.auralis.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import app.auralis.domain.ChapterSpan
import app.auralis.domain.mapFileCuesToBook
import app.auralis.domain.toWebVtt
import app.auralis.domain.CaptionTrack
import app.auralis.domain.parseCaptions
import java.io.File

/**
 * Imported caption files live in app-private storage, one per book. They are never uploaded and
 * are not part of the database, so importing or removing them cannot touch listening history.
 */
class CaptionStore(private val context: Context) {
    private val directory: File
        get() = File(context.filesDir, "captions").apply { mkdirs() }

    private fun fileFor(bookId: String): File = File(directory, "${safeName(bookId)}.vtt")

    /**
     * Per-chapter recognition output kept while a transcript is being generated. Versioned: v2
     * adds voice fingerprints, so older saved chapters are not reused.
     */
    fun partialDirectory(bookId: String): File =
        File(context.filesDir, "captions/partial-v2/${safeName(bookId)}")

    private fun voicesFile(bookId: String): File = File(directory, "${safeName(bookId)}.voices.json")

    /** Names the listener gave to detected voices, e.g. "Voice 3" → "Hagrid". */
    fun voiceNames(bookId: String): Map<String, String> {
        val file = voicesFile(bookId).takeIf { it.isFile } ?: return emptyMap()
        return runCatching {
                val json = org.json.JSONObject(file.readText())
                json.keys().asSequence().associateWith { json.getString(it) }
            }
            .getOrDefault(emptyMap())
    }

    /** Sets or, when [name] is blank, clears the listener's name for [voice]. */
    fun renameVoice(bookId: String, voice: String, name: String) {
        val names = voiceNames(bookId).toMutableMap()
        if (name.isBlank()) names.remove(voice) else names[voice] = name.trim().take(40)
        val json = org.json.JSONObject()
        names.forEach { (key, value) -> json.put(key, value) }
        val target = voicesFile(bookId)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(json.toString())
        check(temporary.renameTo(target)) { "Could not save the voice name." }
    }

    fun load(bookId: String): CaptionTrack? {
        val file = fileFor(bookId).takeIf { it.isFile } ?: return null
        val cues = parseCaptions(file.readText())
        return CaptionTrack(cues).takeIf { cues.isNotEmpty() }
    }

    /**
     * Copies [uri] into storage after confirming it contains at least one caption cue. The previous
     * file is replaced only once the new one is fully written and valid.
     *
     * @return number of caption lines imported
     */
    fun import(bookId: String, uri: Uri): Int {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readNBytes(MAX_BYTES + 1)
            } ?: throw IllegalArgumentException("Could not open that file.")
        require(bytes.size <= MAX_BYTES) { "Caption files must be 4 MB or smaller." }
        val text = bytes.toString(Charsets.UTF_8)
        val cues = parseCaptions(text)
        require(cues.isNotEmpty()) {
            "No captions found. Choose an .srt or .vtt file with timestamps."
        }
        save(bookId, text)
        return cues.size
    }

    /** Replaces the book's captions with [text]; the old file stays until the new one is complete. */
    fun save(bookId: String, text: String) {
        val target = fileFor(bookId)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeText(text, Charsets.UTF_8)
        check(temporary.renameTo(target)) { "Could not save the captions." }
    }

    /**
     * Picks up transcripts saved next to a book's audio, e.g. `Book.mp3` + `Book.vtt` made on a
     * computer. Each file's lines are moved onto the book timeline. A sidecar only replaces the
     * stored transcript when it is newer, so an import made later in the app is kept.
     *
     * @return true when the stored transcript was updated
     */
    fun syncSidecars(book: LibraryBook): Boolean {
        val stored = fileFor(book.book.id)
        val storedAt = if (stored.isFile) stored.lastModified() else 0L
        val ordered = book.orderedChapters
        val bookStarts = LongArray(ordered.size)
        ordered.indices.drop(1).forEach { bookStarts[it] = bookStarts[it - 1] + ordered[it - 1].durationMs }
        val found =
            book.files.mapNotNull { file ->
                val uri = sidecarUri(Uri.parse(file.uri)) ?: return@mapNotNull null
                val modified = lastModified(uri) ?: return@mapNotNull null
                Triple(file, uri, modified)
            }
        if (found.isEmpty() || found.maxOf { it.third } <= storedAt) return false
        val cues =
            found.flatMap { (file, uri, _) ->
                val text =
                    context.contentResolver.openInputStream(uri)?.use { it.readNBytes(MAX_BYTES + 1) }
                        ?.takeIf { it.size <= MAX_BYTES }
                        ?.toString(Charsets.UTF_8) ?: return@flatMap emptyList()
                val spans =
                    ordered.indices
                        .filter { ordered[it].fileId == file.id }
                        .map { ChapterSpan(ordered[it].startMs, ordered[it].endMs, bookStarts[it]) }
                mapFileCuesToBook(parseCaptions(text), spans)
            }
        if (cues.isEmpty()) return false
        save(book.book.id, toWebVtt(cues.sortedBy { it.startMs }))
        return true
    }

    /**
     * `name.vtt` beside an audio document, addressed through a folder the app already has access
     * to. Null for audio from providers without folders (e.g. a single picked file).
     */
    private fun sidecarUri(audio: Uri): Uri? {
        val documentId = runCatching { DocumentsContract.getDocumentId(audio) }.getOrNull() ?: return null
        val dot = documentId.lastIndexOf('.')
        if (dot <= documentId.lastIndexOf('/')) return null
        val sibling = documentId.substring(0, dot) + ".vtt"
        val tree =
            if (DocumentsContract.isTreeUri(audio)) audio
            else
                context.contentResolver.persistedUriPermissions
                    .map { it.uri }
                    .firstOrNull { granted ->
                        granted.authority == audio.authority &&
                            DocumentsContract.isTreeUri(granted) &&
                            DocumentsContract.getTreeDocumentId(granted).let { root ->
                                sibling.startsWith(if (root.endsWith(":")) root else "$root/")
                            }
                    } ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(tree, sibling)
    }

    private fun lastModified(uri: Uri): Long? =
        runCatching {
                context.contentResolver
                    .query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)
                    ?.use { if (it.moveToFirst()) it.getLong(0) else null }
            }
            .getOrNull()

    fun delete(bookId: String) {
        fileFor(bookId).delete()
        voicesFile(bookId).delete()
        partialDirectory(bookId).deleteRecursively()
        File(context.filesDir, "captions/partial/${safeName(bookId)}").deleteRecursively()
    }

    private fun safeName(bookId: String): String =
        bookId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "book" }

    private companion object {
        const val MAX_BYTES = 4 * 1024 * 1024
    }
}
