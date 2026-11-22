package app.auralis.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookCatalogTest {
    @Test
    fun cachesPositiveAndNegativeResults() {
        val gson = Gson()
        val details = CatalogDetails("https://books.apple.com/us/audiobook/dune/id1", "Summary")
        listOf(CachedDetails(12, details), CachedDetails(13, null)).forEach { cached ->
            assertEquals(cached, gson.fromJson(gson.toJson(cached), CachedDetails::class.java))
        }
    }

    @Test
    fun acceptsDifferencesInPunctuationAndEditionLabel() {
        assertTrue(matchesCatalogBook("The Hitchhiker’s Guide", "Douglas Adams", "The Hitchhiker's Guide (Unabridged)", "Douglas Adams"))
    }

    @Test
    fun acceptsNumberedSeriesSuffixAndSpacedInitials() {
        assertTrue(matchesCatalogBook(
            "The Horror at Chiller House", "R.L. Stine",
            "The Horror at Chiller House (Goosebumps HorrorLand #19)", "R. L. Stine",
        ))
    }

    @Test
    fun rejectsADifferentBookInTheSeriesOrADifferentAuthor() {
        assertFalse(matchesCatalogBook("Dune", "Frank Herbert", "Dune Messiah", "Frank Herbert"))
        assertFalse(matchesCatalogBook("Dune", "Frank Herbert", "Dune", "Brian Herbert"))
    }

    @Test
    fun readsTheFullNarratorListFromApplePageData() {
        val escaped =
            """{\"name\":\"Dune\",\"narrator\":\"Scott Brick\",\"narratorNames\":\"Scott Brick, Orlagh Cassidy, Euan Morton \u0026 Scott Sowers\",\"offers\":[]}"""
        assertEquals("Scott Brick, Orlagh Cassidy, Euan Morton & Scott Sowers", narratorNamesFrom(escaped))
        assertEquals("A Reader, B Reader", narratorNamesFrom("""{"narratorNames":"A Reader, B Reader"}"""))
        assertEquals("", narratorNamesFrom("<html>no narrator data</html>"))
    }
}
