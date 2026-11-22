package app.auralis.cast

import org.junit.Assert.*
import org.junit.Test

class CastRangeTest {
    @Test
    fun fullAndPartialRequests() {
        assertEquals(ByteRange(0, 999), parseByteRange(null, 1000))
        assertEquals(ByteRange(100, 199), parseByteRange("bytes=100-199", 1000))
        assertEquals(ByteRange(900, 999), parseByteRange("bytes=900-", 1000))
        assertEquals(ByteRange(900, 999), parseByteRange("bytes=-100", 1000))
        assertEquals(ByteRange(0, 999), parseByteRange("bytes=-2000", 1000))
        assertEquals(ByteRange(900, 999), parseByteRange("bytes=900-2000", 1000))
        assertEquals(1L, parseByteRange("bytes=999-999", 1000)!!.length)
    }

    @Test
    fun rejectsInvalidOrAmbiguousRanges() {
        listOf(
                "bytes=1000-",
                "bytes=200-100",
                "bytes=-0",
                "bytes=-",
                "bytes=0-1,2-3",
                "items=0-1",
                "bytes=foo-",
                "bytes=9223372036854775808-",
            )
            .forEach {
                assertNull(it, parseByteRange(it, 1000))
            }
        assertNull(parseByteRange(null, 0))
        assertNull(parseByteRange(null, -1))
    }

    @Test
    fun longAudiobooksDoNotOverflow() {
        val size = 12_000_000_000L
        assertEquals(ByteRange(9_000_000_000L, size - 1), parseByteRange("bytes=9000000000-", size))
        assertEquals(size, parseByteRange(null, size)!!.length)
    }

    @Test
    fun embeddedChapterCoordinates() {
        assertEquals(0L, chapterPosition(9_000, 10_000, 60_000))
        assertEquals(15_000L, chapterPosition(25_000, 10_000, 60_000))
        assertEquals(60_000L, chapterPosition(80_000, 10_000, 60_000))
        assertEquals(45_000L, chapterPosition(45_000, 0, 90_000))
    }
}
