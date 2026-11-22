package app.auralis.importer

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

data class EmbeddedChapter(val title: String, val startMs: Long)

/** Bounded ISO-BMFF atom walk for Nero chpl. Audio payloads are skipped, never loaded. */
object Mp4Chapters {
    fun read(channel: FileChannel): List<EmbeddedChapter> = runCatching {
        walk(channel, 0, channel.size(), 0)
    }
        .getOrDefault(emptyList())

    /** Reads bounded iTunes synopsis/comment atoms without decoding audio. */
    fun summary(channel: FileChannel): String = runCatching {
        val tags = linkedMapOf<String, String>()
        readTextAtoms(channel, 0, channel.size(), 0, tags)
        listOf("ldes", "desc", "©cmt").firstNotNullOfOrNull(tags::get).orEmpty()
    }.getOrDefault("")

    private fun readTextAtoms(
        channel: FileChannel,
        start: Long,
        end: Long,
        depth: Int,
        tags: MutableMap<String, String>,
        tag: String? = null,
    ) {
        if (depth > 7) return
        var position = start
        while (position + 8 <= end) {
            val header = bytes(channel, position, 8)
            val small = header.int.toLong() and 0xffffffffL
            val type = ByteArray(4).also { header.get(it) }.toString(Charsets.ISO_8859_1)
            val headerSize = if (small == 1L) 16 else 8
            if (end - position < headerSize) return
            val size = when (small) {
                0L -> end - position
                1L -> bytes(channel, position + 8, 8).long
                else -> small
            }
            if (size < headerSize || size > end - position) return
            val payload = position + headerSize
            val next = position + size
            when (type) {
                "moov", "udta", "ilst" -> readTextAtoms(channel, payload, next, depth + 1, tags)
                "meta" -> readTextAtoms(channel, payload + 4, next, depth + 1, tags)
                "ldes", "desc", "©cmt" -> readTextAtoms(channel, payload, next, depth + 1, tags, type)
                "data" -> if (tag != null && next - payload in 9..65536) {
                    val data = bytes(channel, payload, (next - payload).toInt())
                    val encoding = data.int
                    data.int // Locale.
                    if (encoding == 1) {
                        val value = ByteArray(data.remaining()).also { data.get(it) }
                            .toString(Charsets.UTF_8).trim { it <= ' ' }
                        if (value.length >= 40) tags.putIfAbsent(tag, value)
                    }
                }
            }
            position = next
        }
    }

    private fun bytes(c: FileChannel, p: Long, n: Int): ByteBuffer {
        val b = ByteBuffer.allocate(n).order(ByteOrder.BIG_ENDIAN)
        var offset = p
        while (b.hasRemaining()) {
            val read = c.read(b, offset)
            require(read > 0)
            offset += read
        }
        b.flip()
        return b
    }

    private fun walk(c: FileChannel, start: Long, end: Long, depth: Int): List<EmbeddedChapter> {
        if (depth > 6) return emptyList()
        var p = start
        while (p + 8 <= end) {
            val header = bytes(c, p, 8)
            val small = header.int.toLong() and 0xffffffffL
            val type = ByteArray(4).also { header.get(it) }.toString(Charsets.US_ASCII)
            val headerSize = if (small == 1L) 16 else 8
            val size =
                when (small) {
                    0L -> end - p
                    1L -> bytes(c, p + 8, 8).long
                    else -> small
                }
            if (size < headerSize || size > end - p) break
            if (type == "chpl" && size <= 65536) {
                val b = bytes(c, p + headerSize, (size - headerSize).toInt())
                if (b.remaining() < 9) return emptyList()
                b.int
                b.int
                val count = b.get().toInt() and 255
                return (0 until count)
                    .map {
                        require(b.remaining() >= 9)
                        val time = b.long / 10000L
                        val length = b.get().toInt() and 255
                        require(b.remaining() >= length)
                        val title = ByteArray(length).also { b.get(it) }.toString(Charsets.UTF_8)
                        EmbeddedChapter(title, time)
                    }
                    .filter { it.startMs >= 0 }
                    .distinctBy { it.startMs }
                    .sortedBy { it.startMs }
            }
            if (type in setOf("moov", "udta")) {
                val found = walk(c, p + headerSize, p + size, depth + 1)
                if (found.isNotEmpty()) return found
            }
            p += size
        }
        return emptyList()
    }
}
