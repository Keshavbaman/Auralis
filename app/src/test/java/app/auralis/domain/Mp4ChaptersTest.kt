package app.auralis.domain

import app.auralis.importer.Mp4Chapters
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Mp4ChaptersTest {
    @Test
    fun readsNestedNeroChaptersWithoutReadingMdat() {
        fun atom(type: String, data: ByteArray) =
            ByteBuffer.allocate(data.size + 8)
                .putInt(data.size + 8)
                .put(type.toByteArray())
                .put(data)
                .array()
        val content =
            ByteBuffer.allocate(9 + 9 + 5 + 9 + 6)
                .putInt(0x01000000)
                .putInt(0)
                .put(2)
                .putLong(0)
                .put(5)
                .put("Start".toByteArray())
                .putLong(600000000)
                .put(6)
                .put("Second".toByteArray())
                .array()
        val file = File.createTempFile("chapters", ".m4b")
        try {
            file.writeBytes(
                atom("mdat", ByteArray(1024)) + atom("moov", atom("udta", atom("chpl", content)))
            )
            val chapters = file.inputStream().channel.use { Mp4Chapters.read(it) }
            assertEquals(listOf("Start", "Second"), chapters.map { it.title })
            assertEquals(60000, chapters[1].startMs)
        } finally {
            file.delete()
        }
    }

    @Test
    fun readsEmbeddedSynopsis() {
        fun atom(type: String, payload: ByteArray) =
            ByteBuffer.allocate(payload.size + 8)
                .putInt(payload.size + 8).put(type.toByteArray(Charsets.ISO_8859_1))
                .put(payload).array()
        val summary = "This synopsis was included by the publisher in the audiobook file."
        val comment = atom("©cmt", atom("data", ByteBuffer.allocate(8).putInt(1).putInt(0).array() +
            summary.toByteArray()))
        val metadata = atom("moov", atom("udta", atom("meta", ByteArray(4) + atom("ilst", comment))))
        val file = File.createTempFile("synopsis", ".m4b")
        try {
            file.writeBytes(atom("mdat", ByteArray(1024)) + metadata)
            assertEquals(summary, file.inputStream().channel.use { Mp4Chapters.summary(it) })
        } finally {
            file.delete()
        }
    }

    @Test
    fun malformedAtomsReturnNoChapters() {
        val file = File.createTempFile("bad", ".m4b")
        try {
            file.writeBytes(
                ByteBuffer.allocate(8).putInt(Int.MAX_VALUE).put("moov".toByteArray()).array()
            )
            assertTrue(file.inputStream().channel.use { Mp4Chapters.read(it) }.isEmpty())
        } finally {
            file.delete()
        }
    }
}
