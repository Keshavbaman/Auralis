package app.auralis.transcription

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * An offline model pinned by name, URL and SHA-256, so a changed or tampered download is rejected
 * before anything is unpacked. [marker] is a file that exists only in a complete install.
 */
data class PinnedModel(
    val name: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val marker: String,
)

object SpeechModels {
    /** Small English speech recogniser: words and per-word timing. */
    val SPEECH =
        PinnedModel(
            name = "vosk-model-small-en-us-0.15",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
            sizeBytes = 41_205_931L,
            marker = "am/final.mdl",
        )

    /** Speaker fingerprints, used to group lines by voice. */
    val SPEAKER =
        PinnedModel(
            name = "vosk-model-spk-0.4",
            url = "https://alphacephei.com/vosk/models/vosk-model-spk-0.4.zip",
            sha256 = "a74d8f51144484813e16af689bb0f916b7a111e2347f467c4933c1166097b5a7",
            sizeBytes = 13_869_103L,
            marker = "final.ext.raw",
        )

    val ALL = listOf(SPEECH, SPEAKER)
}

class SpeechModelStore(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "speech-models")

    fun directory(model: PinnedModel): File = File(root, model.name)

    fun isInstalled(model: PinnedModel): Boolean = File(directory(model), model.marker).isFile

    /** True when every model needed for transcripts with voices is present. */
    fun allInstalled(): Boolean = SpeechModels.ALL.all(::isInstalled)

    /** Bytes still to download before a transcript can start; 0 when everything is installed. */
    fun pendingDownloadBytes(): Long =
        SpeechModels.ALL.filterNot(::isInstalled).sumOf { it.sizeBytes }

    /** Downloads, verifies and unpacks [model]. [onProgress] receives values from 0 to 1. */
    suspend fun install(model: PinnedModel, onProgress: (Float) -> Unit) =
        withContext(Dispatchers.IO) {
            if (isInstalled(model)) return@withContext
            root.mkdirs()
            val archive = File(context.cacheDir, "${model.name}.zip.part")
            try {
                download(model, archive, onProgress)
                check(sha256(archive).equals(model.sha256, ignoreCase = true)) {
                    "A speech model did not match its expected checksum. Try again later."
                }
                val staging = File(root, "${model.name}.staging")
                staging.deleteRecursively()
                staging.mkdirs()
                unzipInto(archive, staging)
                val extracted = File(staging, model.name)
                check(File(extracted, model.marker).isFile) { "Unexpected model layout." }
                directory(model).deleteRecursively()
                check(extracted.renameTo(directory(model))) { "Could not install a speech model." }
                staging.deleteRecursively()
            } finally {
                archive.delete()
            }
        }

    /** Stops promptly when the transcription is cancelled instead of finishing the download. */
    private suspend fun download(model: PinnedModel, target: File, onProgress: (Float) -> Unit) {
        val connection = URL(model.url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Could not download a speech model (HTTP ${connection.responseCode})."
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: model.sizeBytes
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        kotlin.coroutines.coroutineContext.ensureActive()
                        output.write(buffer, 0, read)
                        written += read
                        onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipInto(archive: File, destination: File) {
        val root = destination.canonicalFile
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                val target = File(root, entry.name).canonicalFile
                // Refuse entries that escape the destination folder.
                check(target.path.startsWith(root.path + File.separator)) { "Unsafe archive entry." }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { zip.copyTo(it) }
                }
            }
        }
    }
}
