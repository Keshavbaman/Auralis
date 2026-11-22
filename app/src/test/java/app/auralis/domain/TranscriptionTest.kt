package app.auralis.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionTest {
    private fun word(text: String, start: Long, end: Long) = RecognizedWord(text, start, end)

    @Test
    fun breaksLinesOnPausesAndCapitalisesTheStart() {
        val cues =
            groupWords(
                listOf(
                    word("hello", 0, 400),
                    word("there", 450, 800),
                    word("again", 3_000, 3_400), // 2.2s pause starts a new line
                )
            )
        assertEquals(listOf("Hello there", "Again"), cues.map { it.text })
        assertEquals(3_000L, cues[1].startMs)
        assertEquals(3_400L, cues[1].endMs)
    }

    @Test
    fun breaksLinesAtMaximumWordCount() {
        val words = (0 until 20).map { word("w$it", it * 100L, it * 100L + 90) }
        val cues = groupWords(words, maxWords = 8)
        assertEquals(listOf(8, 8, 4), cues.map { it.text.split(' ').size })
    }

    @Test
    fun ignoresBlankTokens() {
        val cues = groupWords(listOf(word("", 0, 10), word("ok", 20, 100)))
        assertEquals(listOf("Ok"), cues.map { it.text })
    }

    @Test
    fun webVttRoundTripsThroughCaptionParser() {
        val cues =
            listOf(
                Cue(1_000, 2_500, null, "Tom & Jerry <b>say</b> hi"),
                Cue(65_250, 66_000, "Narrator", "Second line"),
            )
        val parsed = parseCaptions(toWebVtt(cues))
        assertEquals(
            listOf(
                Cue(1_000, 2_500, null, "Tom & Jerry <b>say</b> hi"),
                Cue(65_250, 66_000, "Narrator", "Second line"),
            ),
            parsed,
        )
    }

    @Test
    fun formatsHoursMinutesSecondsAndMilliseconds() {
        assertEquals("01:02:03.004", vttTimestamp(3_723_004))
        assertEquals("00:00:00.000", vttTimestamp(-5))
    }

    @Test
    fun groupedLinesKeepEachWordsTiming() {
        val cue = groupWords(listOf(word("hello", 0, 400), word("there", 450, 800))).single()
        assertEquals(listOf("Hello", "there"), cue.words.map { it.text })
        assertEquals(listOf(0L, 450L), cue.words.map { it.startMs })
    }

    @Test
    fun wordTimingsSurviveAWriteAndReadBack() {
        val cue =
            groupWords(
                    listOf(word("tom", 1_000, 1_300), word("&", 1_400, 1_500), word("jerry", 1_600, 2_100))
                )
                .single()
        val parsed = parseCaptions(toWebVtt(listOf(cue))).single()
        assertEquals("Tom & jerry", parsed.text)
        assertEquals(listOf("Tom", "&", "jerry"), parsed.words.map { it.text })
        assertEquals(listOf(1_000L, 1_400L, 1_600L), parsed.words.map { it.startMs })
    }

    @Test
    fun startsANewLineWhenTheVoiceChanges() {
        val cues =
            groupWords(
                listOf(
                    RecognizedWord("good", 0, 300, "Voice 1"),
                    RecognizedWord("evening", 350, 700, "Voice 1"),
                    RecognizedWord("professor", 750, 1_200, "Voice 2"),
                )
            )
        assertEquals(listOf("Good evening", "Professor"), cues.map { it.text })
        assertEquals(listOf("Voice 1", "Voice 2"), cues.map { it.speaker })
    }

    @Test
    fun transcribesTheCurrentChapterFirstThenOnward() {
        assertEquals(listOf(3, 4, 0, 1, 2), transcriptionOrder(5, 3))
        assertEquals(listOf(0, 1, 2), transcriptionOrder(3, -1))
        assertEquals(listOf(2, 0, 1), transcriptionOrder(3, 9))
        assertEquals(emptyList<Int>(), transcriptionOrder(0, 0))
    }

    @Test
    fun cutsLongChaptersIntoPiecesAndKeepsShortOnesWhole() {
        val slices = planSlices(listOf(560_000L, 90_000L), sliceMs = 240_000L)
        assertEquals(listOf(0 to 0L, 0 to 240_000L, 1 to 0L), slices.map { it.chapter to it.startMs })
        assertEquals(560_000L, slices[1].endMs) // the 80 s remainder joins the last piece
        assertEquals(90_000L, slices[2].endMs)
    }

    @Test
    fun startsWithThePieceAtTheListenersPosition() {
        val slices = planSlices(listOf(1_000_000L), sliceMs = 240_000L) // 4 pieces
        assertEquals(listOf(2, 3, 0, 1), sliceOrder(slices, chapter = 0, positionMs = 500_000L))
        assertEquals(listOf(0, 1, 2, 3), sliceOrder(slices, chapter = 0, positionMs = 0L))
    }

    @Test
    fun dropsAWordFragmentRepeatedAcrossAPieceBoundary() {
        val first = listOf(word("hello", 0, 400), word("wonderful", 239_800, 240_600))
        // The next piece starts at 240s and hears the tail of "wonderful" as a fragment.
        val second = listOf(word("ful", 240_000, 240_500), word("world", 240_700, 241_100))
        assertEquals(listOf("hello", "wonderful", "world"), joinSliceWords(listOf(first, second)).map { it.text })
    }
}
