package app.auralis.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptionsTest {
    @Test
    fun parsesWebVttWithVoiceTagsAndCueSettings() {
        val cues =
            parseCaptions(
                """
                WEBVTT

                NOTE exported by a test

                00:00:01.000 --> 00:00:03.500 align:start
                <v Narrator>It was a <i>dark</i> night.</v>

                00:01:02.250 --> 00:01:04.000
                <v Hermione>Tom &amp; Jerry&nbsp;are here.
                """
                    .trimIndent()
            )
        assertEquals(2, cues.size)
        assertEquals(Cue(1000, 3500, "Narrator", "It was a dark night."), cues[0])
        assertEquals(Cue(62250, 64000, "Hermione", "Tom & Jerry are here."), cues[1])
    }

    @Test
    fun parsesSubRipWithIndexesAndCommaMilliseconds() {
        val cues =
            parseCaptions(
                "1\r\n00:00:00,500 --> 00:00:02,000\r\nHello there.\r\n\r\n" +
                    "2\r\n00:00:02,000 --> 00:00:04,250\r\nSecond line\r\nover two rows.\r\n"
            )
        assertEquals(listOf(500L, 2000L), cues.map { it.startMs })
        assertEquals("Second line over two rows.", cues[1].text)
        assertNull(cues[0].speaker)
    }

    @Test
    fun skipsMalformedBlocksWithoutDroppingTheRest() {
        val cues =
            parseCaptions(
                """
                00:00:05.000 --> not-a-time
                broken block

                00:00:06.000 --> 00:00:07.000
                good line

                00:00:08.000 --> 00:00:09.000

                """
                    .trimIndent()
            )
        assertEquals(listOf("good line"), cues.map { it.text })
    }

    @Test
    fun sortsCuesByStartTime() {
        val cues =
            parseCaptions(
                "00:00:10.000 --> 00:00:11.000\nlater\n\n00:00:01.000 --> 00:00:02.000\nearlier"
            )
        assertEquals(listOf("earlier", "later"), cues.map { it.text })
    }

    @Test
    fun parsesShortTimestampsAndNormalisesFraction() {
        assertEquals(65_500L, parseTimestamp("01:05.5"))
        assertEquals(3_723_004L, parseTimestamp("01:02:03.004"))
        assertNull(parseTimestamp("12"))
        assertNull(parseTimestamp("aa:bb"))
    }

    @Test
    fun activeCueFollowsPlaybackAndHoldsAcrossGaps() {
        val track =
            CaptionTrack(
                listOf(
                    Cue(1_000, 2_000, null, "a"),
                    Cue(5_000, 6_000, null, "b"),
                    Cue(9_000, 10_000, null, "c"),
                )
            )
        assertEquals(-1, track.indexAt(0))
        assertEquals(0, track.indexAt(1_000))
        assertEquals(0, track.indexAt(3_000)) // gap keeps the previous line
        assertEquals(1, track.indexAt(5_000))
        assertEquals(2, track.indexAt(99_999)) // past the last cue stays on it
        assertEquals(-1, CaptionTrack(emptyList()).indexAt(10))
    }

    @Test
    fun readsKaraokeTimestampsAsWordTimings() {
        val cue =
            parseCaptions(
                    "WEBVTT\n\n00:00:01.000 --> 00:00:03.000\n<v Hagrid>Hello <00:00:01.600>big <00:00:02.200>world"
                )
                .single()
        assertEquals("Hagrid", cue.speaker)
        assertEquals("Hello big world", cue.text)
        assertEquals(listOf(1_000L, 1_600L, 2_200L), cue.words.map { it.startMs })
        assertEquals(listOf(1_600L, 2_200L, 3_000L), cue.words.map { it.endMs })
    }

    @Test
    fun estimatesWordTimingsWhenAFileHasNone() {
        val words = Cue(0, 1_000, null, "a bbb").timedWords()
        assertEquals(listOf("a", "bbb"), words.map { it.text })
        assertEquals(0L, words.first().startMs)
        assertEquals(333L, words[1].startMs) // "a " is 2 of 6 weighted characters
        assertEquals(1_000L, words.last().endMs) // the last word ends with the line
    }

    @Test
    fun mapsAFilesCuesOntoTheBookTimeline() {
        // This file holds chapters 3 and 4 of the book; chapter 3 starts 10 minutes into the book.
        val spans =
            listOf(
                ChapterSpan(fileStartMs = 0, fileEndMs = 60_000, bookStartMs = 600_000),
                ChapterSpan(fileStartMs = 60_000, fileEndMs = 120_000, bookStartMs = 660_000),
            )
        val cues =
            listOf(
                Cue(5_000, 6_000, null, "a b", listOf(CueWord("a", 5_000, 5_400), CueWord("b", 5_500, 6_000))),
                Cue(70_000, 71_000, null, "c"),
                Cue(130_000, 131_000, null, "past the last chapter"),
            )
        val mapped = mapFileCuesToBook(cues, spans)
        assertEquals(listOf(605_000L, 670_000L), mapped.map { it.startMs })
        assertEquals(listOf(605_000L, 605_500L), mapped.first().words.map { it.startMs })
    }
}
