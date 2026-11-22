package app.auralis.cast

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.auralis.data.*
import java.io.File
import java.net.Socket
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalCastServerTest {
    @Test
    fun rangedSafAudioAndAccessIsolation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.packageName.endsWith(".verification"))
        val folder = File(context.filesDir, "test-fixtures").apply { mkdirs() }
        val file = File(folder, "cast-range.mp3")
        val bytes = ByteArray(8192) { (it % 251).toByte() }
        "ftyp".toByteArray().copyInto(bytes, 4) // MP4 despite misleading filename.
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.testfiles", file)
        val server = LocalCastServer(context)
        try {
            server.register(
                LibraryBook(
                    BookEntity("cast-fixture", "Range fixture"),
                    emptyList(),
                    listOf(
                        AudioFileEntity("range", "cast-fixture", uri.toString(), file.name, 10000)
                    ),
                    null,
                )
            )
            val url = server.url(uri)
            assertEquals("audio/mp4", server.mime(uri))
            fun request(
                path: String = url.encodedPath!!,
                range: String? = null,
                method: String = "GET",
            ): Pair<String, ByteArray> {
                Socket(url.host, url.port).use { socket ->
                    socket.soTimeout = 5000
                    val request =
                        "$method $path HTTP/1.1\r\nHost: ${url.host}\r\n" +
                            (range?.let { "Range: $it\r\n" } ?: "") +
                            "\r\n"
                    socket.getOutputStream().write(request.toByteArray())
                    val response = socket.getInputStream().readBytes()
                    val marker = "\r\n\r\n".toByteArray()
                    val split =
                        (0..response.size - 4).first { i ->
                            marker.indices.all { response[i + it] == marker[it] }
                        }
                    return String(response, 0, split, Charsets.US_ASCII) to
                        response.copyOfRange(split + 4, response.size)
                }
            }
            val full = request()
            assertTrue(full.first.startsWith("HTTP/1.1 200"))
            assertArrayEquals(bytes, full.second)
            val part = request(range = "bytes=1000-1099")
            assertTrue(part.first.contains("Content-Range: bytes 1000-1099/8192"))
            assertArrayEquals(bytes.copyOfRange(1000, 1100), part.second)
            assertArrayEquals(bytes.takeLast(32).toByteArray(), request(range = "bytes=-32").second)
            assertTrue(request(range = "bytes=8192-").first.startsWith("HTTP/1.1 416"))
            assertTrue(request(path = "/wrong/audio-range").first.startsWith("HTTP/1.1 404"))
            assertTrue(
                request(path = url.encodedPath!! + "/../secret").first.startsWith("HTTP/1.1 404")
            )
            assertEquals(0, request(method = "HEAD").second.size)
            val oldPort = url.port
            server.stop()
            assertTrue(runCatching { Socket(url.host, oldPort).close() }.isFailure)
            val replacement = server.url(uri)
            assertNotEquals(url.encodedPath, replacement.encodedPath)
        } finally {
            server.release()
            file.delete()
        }
    }
}
