package app.auralis.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.text.Html
import android.util.AtomicFile
import app.auralis.domain.bookDetailTitle
import app.auralis.domain.needsBookAuthor
import app.auralis.domain.filenameBookCredits
import kotlin.math.abs
import app.auralis.domain.seriesInfo
import app.auralis.importer.Mp4Chapters
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

data class CatalogDetails(
    @SerializedName("url") val url: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("released") val released: String = "",
    @SerializedName("genre") val genre: String = "",
    @SerializedName("publisher") val publisher: String = "",
    @SerializedName("language") val language: String = "",
    @SerializedName("narrator") val narrator: String = "",
    @SerializedName("rating") val rating: Double? = null,
    @SerializedName("ratingCount") val ratingCount: Int? = null,
    @SerializedName("narratorSource") val narratorSource: String? = null,
    @SerializedName("narratorDurationMs") val narratorDurationMs: Long? = null,
)

internal data class CachedDetails(
    @SerializedName("savedAt") val savedAt: Long,
    @SerializedName("details") val details: CatalogDetails?,
    @SerializedName("usedBookFallback") val usedBookFallback: Boolean = true,
)

@Singleton
class AudiobookCatalog @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val gson = Gson()
    // v2: narrators now include the full cast, so older single-narrator results are refetched.
    private val cache = File(context.filesDir, "catalog-v3")
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    fun online() = callbackFlow {
        fun update() {
            val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
            trySend(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true)
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = update()
            override fun onLost(network: Network) = update()
        }
        connectivity.registerDefaultNetworkCallback(callback)
        update()
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    suspend fun embeddedSummary(files: List<AudioFileEntity>): String = withContext(Dispatchers.IO) {
        files.asSequence()
            .filter { it.name.substringAfterLast('.').lowercase() in setOf("m4b", "m4a", "mp4") }
            .take(3)
            .mapNotNull { file ->
                runCatching {
                    context.contentResolver.openFileDescriptor(Uri.parse(file.uri), "r")?.use { pfd ->
                        FileInputStream(pfd.fileDescriptor).use { plainText(Mp4Chapters.summary(it.channel)) }
                    }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }.firstOrNull().orEmpty()
    }

    suspend fun cached(book: BookEntity, durationMs: Long = 0): CatalogDetails? = withContext(Dispatchers.IO) {
        read(book, durationMs)?.details ?: read(book)?.details
    }

    suspend fun lookup(book: BookEntity, refresh: Boolean = false, durationMs: Long = 0): CatalogDetails? =
        withContext(Dispatchers.IO) {
            val title = bookDetailTitle(book).replace(numberedTitle, "")
            val author = book.author
            if (title.isBlank() || needsBookAuthor(author)) {
                return@withContext null
            }

            if (!refresh) read(book, durationMs)?.let {
                val ttl = if (it.details == null) 86_400_000L else 30 * 86_400_000L
                if ((it.details?.summary?.isNotBlank() == true || it.usedBookFallback) &&
                    (System.currentTimeMillis() - it.savedAt) in 0 until ttl) {
                    return@withContext it.details
                }
            }

            val titles = seriesLookupTitles(book)
            val query = URLEncoder.encode("${titles.first().take(180)} ${author.take(120)}", "UTF-8")
            val search = "https://itunes.apple.com/search?term=$query&country=US&media=audiobook&entity=audiobook&limit=12"
            val audiobookSearch = runCatching { searchResults(search) }
            val matches = audiobookSearch.getOrDefault(emptyList())
            val match = matches.firstOrNull {
                it.text("wrapperType") == "audiobook" &&
                    titles.any { wanted -> matchesCatalogBook(wanted, author, it.text("collectionName"), it.text("artistName")) } &&
                    isAppleBooksUrl(it.text("collectionViewUrl"), "/audiobook/")
            }

            val details = match?.let { entry ->
                val url = entry.text("collectionViewUrl")
                val page = runCatching { request(url, 1_500_000) }.getOrDefault("")
                val schema = audiobookSchema(page)
                val rating = schema?.get("aggregateRating") as? JsonObject
                CatalogDetails(
                    url = url,
                    summary = plainText(entry.text("description").ifBlank { schema?.text("description").orEmpty() }),
                    released = entry.text("releaseDate").take(10),
                    genre = plainText(entry.text("primaryGenreName")),
                    publisher = plainText(schema?.text("provider").orEmpty()),
                    language = plainText(schema?.text("inLanguage").orEmpty().ifBlank {
                        languageBadge.find(page)?.groupValues?.get(1).orEmpty()
                    }),
                    narrator =
                        narratorNamesFrom(page).ifBlank {
                            plainText(schema?.let(::readByNames).orEmpty())
                        },
                    narratorSource = "Apple Books",
                    narratorDurationMs = entry.text("trackTimeMillis").toLongOrNull()
                        ?: runCatching { java.time.Duration.parse(schema?.text("duration").orEmpty()).toMillis() }.getOrNull(),
                    rating = rating?.text("ratingValue")?.toDoubleOrNull()?.takeIf { it in 1.0..5.0 },
                    ratingCount = rating?.text("reviewCount")?.toIntOrNull()?.takeIf { it > 0 },
                )
            }
            val result = if (details?.summary.isNullOrBlank()) {
                val ebook = "https://itunes.apple.com/search?term=$query&country=US&media=ebook&entity=ebook&limit=12"
                runCatching { searchResults(ebook) }.getOrDefault(emptyList()).firstNotNullOfOrNull { entry ->
                    val url = entry.text("trackViewUrl")
                    val summary = plainText(entry.text("description"))
                    if (summary.isNotBlank() && isAppleBooksUrl(url, "/book/") &&
                        titles.any { wanted -> matchesCatalogBook(wanted, author, entry.text("trackName"), entry.text("artistName")) }
                    ) CatalogDetails(
                        url, summary,
                        narrator = details?.narrator.orEmpty(),
                        narratorSource = details?.narratorSource,
                        narratorDurationMs = details?.narratorDurationMs,
                    ) else null
                } ?: details
            } else details
            // Apple often lacks an edition (e.g. Audible-exclusive full casts), so Audible's public
            // catalogue is asked for the cast too; whichever names more narrators wins.
            val cast = audibleCast(book, titles, durationMs)
            val appleFits = result?.matchesNarratorDuration(durationMs) == true
            val audibleFits = matchingRuntime(durationMs, cast?.durationMs)
            val enriched = when {
                cast == null -> result
                result == null -> CatalogDetails(
                    url = "https://www.audible.com/pd/${cast.asin}",
                    summary = "",
                    narrator = cast.names,
                    narratorSource = "Audible",
                    narratorDurationMs = cast.durationMs,
                )
                appleFits && audibleFits && !cast.names.equals(result.narrator, ignoreCase = true) ->
                    result.copy(narratorDurationMs = null)
                (audibleFits && !appleFits) ||
                    (!appleFits && narratorCount(cast.names) > narratorCount(result.narrator)) ->
                    result.copy(narrator = cast.names, narratorSource = "Audible", narratorDurationMs = cast.durationMs)
                else -> result
            }
            if (enriched == null) audiobookSearch.getOrThrow()
            write(book, CachedDetails(System.currentTimeMillis(), enriched), durationMs)
            enriched
        }

    /**
     * Narrators of the matching edition in Audible's public catalogue, with its ASIN. Editions are
     * matched by title and author, then ranked by known narrator credits and full-cast wording.
     */
    private data class CastMatch(val score: Int, val names: String, val asin: String, val durationMs: Long?)

    private fun audibleCast(book: BookEntity, titles: List<String>, durationMs: Long): CastMatch? {
        val fullCast = Regex("full[- ]?cast", RegexOption.IGNORE_CASE)
        val wantsFullCast = fullCast.containsMatchIn(book.title + " " + book.edition)
        val known = splitNames(book.narrator).map { it.lowercase() }.toSet()
        val queries = titles.flatMap { base ->
            if (wantsFullCast) listOf("$base Full-Cast ${book.author}", "$base ${book.author}")
            else listOf("$base ${book.author}")
        }
        var best: CastMatch? = null
        for (query in queries) {
            val url =
                "https://api.audible.com/1.0/catalog/products?num_results=10" +
                    "&response_groups=contributors,product_desc,product_attrs&keywords=" +
                    URLEncoder.encode(query.take(250), "UTF-8")
            val products =
                runCatching { Json.parseToJsonElement(request(url, 2_000_000)).jsonObject["products"] as? JsonArray }
                    .getOrNull() ?: continue
            for (item in products) {
                val product = item as? JsonObject ?: continue
                val authors = (product["authors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.text("name") }
                if (authors.none { sameName(it, book.author) }) continue
                val narrators =
                    (product["narrators"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.text("name")?.takeIf(String::isNotBlank) }
                if (narrators.isEmpty()) continue
                val overlap = narrators.count { it.lowercase() in known }
                val candidate = product.text("title")
                val titleMatch = titles.any { wanted ->
                    authors.any { matchesCatalogBook(wanted, book.author, candidate, it) }
                }
                if (!titleMatch) continue
                if (wantsFullCast && overlap == 0 && !fullCast.containsMatchIn(candidate)) continue
                val runtime = product.text("runtime_length_min").toLongOrNull()
                    ?.takeIf { it in 1..60_000 }?.times(60_000L)
                val score =
                    overlap * 10 + (if (matchingRuntime(durationMs, runtime)) 5 else 0) +
                        (if (fullCast.containsMatchIn(candidate) == wantsFullCast) 2 else 0)
                if (best == null || score > best.score)
                    best = CastMatch(score, narrators.joinToString(", "), product.text("asin"), runtime)
                else if (score == best.score && matchingRuntime(durationMs, runtime) &&
                    !best.names.equals(narrators.joinToString(", "), ignoreCase = true)
                ) best = best.copy(durationMs = null)
            }
            if (best != null && (wantsFullCast || known.isEmpty() || best.score >= 10)) break
        }
        return best
    }

    private fun cacheFile(book: BookEntity, durationMs: Long): AtomicFile {
        val titles = seriesLookupTitles(book)
        val identity =
            if (titles.size == 1) "${bookDetailTitle(book)}\u0000${book.author}"
            else "${titles.first()}\u0000${book.author}\u0000${book.edition}\u0000${book.title}"
        val key = MessageDigest.getInstance("SHA-256").digest((if (durationMs > 0) "$identity\u0000credits-1\u0000${book.edition}\u0000$durationMs" else identity).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(cache, "$key.json"))
    }

    @Synchronized
    private fun read(book: BookEntity, durationMs: Long = 0) = runCatching {
        gson.fromJson(cacheFile(book, durationMs).readFully().decodeToString(), CachedDetails::class.java)
    }.getOrNull()

    @Synchronized
    private fun write(book: BookEntity, details: CachedDetails, durationMs: Long) {
        cache.mkdirs()
        val file = cacheFile(book, durationMs)
        val encoded = gson.toJson(details).encodeToByteArray()
        val output = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            output.write(encoded)
            file.finishWrite(output)
        } catch (_: IOException) {
            file.failWrite(output)
        }
    }

    private fun searchResults(url: String): List<JsonObject> =
        Json.parseToJsonElement(request(url, 512_000)).jsonObject["results"]
            ?.jsonArray?.mapNotNull { it as? JsonObject } ?: throw IOException("Missing catalog results")

    private fun request(url: String, limit: Int): String {
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200) throw IOException("Catalog HTTP ${connection.responseCode}")
            connection.inputStream.reader(Charsets.UTF_8).use { reader ->
                val result = StringBuilder()
                val buffer = CharArray(4096)
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (result.length + read > limit) throw IOException("Catalog response too large")
                    result.append(buffer, 0, read)
                }
                result.toString()
            }
        } finally {
            connection.disconnect()
        }
    }
}

private val numberedTitle = Regex("""^\d+\.\s*""")

private val editionSuffix = Regex(
    """\s*\((?:unabridged|abridged|full.cast(?: edition)?|audiobook|[^()]+(?:#|book\s+)\d+)\)\s*$""",
    RegexOption.IGNORE_CASE,
)

private val harryPotterSubtitles = setOf(
    "philosopher's stone",
    "sorcerer's stone",
    "chamber of secrets",
    "prisoner of azkaban",
    "goblet of fire",
    "order of the phoenix",
    "half-blood prince",
    "deathly hallows",
)

internal fun seriesLookupTitles(book: BookEntity): List<String> {
    val title = bookDetailTitle(book).replace(numberedTitle, "").replace(editionSuffix, "").trim()
    val series = seriesInfo(book)?.name.orEmpty().substringBefore(" (").trim()
    val franchise = "Harry Potter"
    val subtitle = title.removePrefix("The ").removePrefix("the ")
    val shortHarryPotterTitle =
        sameName(book.author, "J.K. Rowling") &&
            subtitle.replace('’', '\'').lowercase(java.util.Locale.ROOT) in harryPotterSubtitles
    val fullHarryPotterTitle = title.startsWith("$franchise and the ", ignoreCase = true)
    if (!series.equals(franchise, ignoreCase = true) && !shortHarryPotterTitle && !fullHarryPotterTitle) {
        return listOf(title)
    }

    val fullTitle = if (fullHarryPotterTitle) title else "$franchise and the $subtitle"
    val aliases = listOf(fullTitle, title).distinct()
    // The US audiobook of the first book is listed under a different title.
    return if (fullTitle.replace('’', '\'').equals("Harry Potter and the Philosopher's Stone", ignoreCase = true)) {
        listOf("Harry Potter and the Sorcerer's Stone") + aliases
    } else aliases
}

internal fun matchesCatalogBook(title: String, author: String, candidateTitle: String, candidateAuthor: String): Boolean {
    fun normalized(value: String) =
        Normalizer.normalize(value.lowercase(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}|['’]"), "")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    val wanted = normalized(title.replace(numberedTitle, "").replace(editionSuffix, ""))
    val candidate = normalized(candidateTitle.replace(numberedTitle, "").replace(editionSuffix, ""))
    val writer = normalized(author).replace(" ", "")
    return wanted.isNotEmpty() && writer.isNotEmpty() && wanted == candidate &&
        writer == normalized(candidateAuthor).replace(" ", "")
}

private fun isAppleBooksUrl(url: String, pathSegment: String) = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "books.apple.com" &&
        uri.port == -1 && uri.userInfo == null && uri.path.contains(pathSegment)
}.getOrDefault(false)

private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()

/** `readBy` as one name, a person, or a list of people, joined into "A, B & C". */
private fun readByNames(schema: JsonObject): String =
    when (val value = schema["readBy"]) {
        is JsonPrimitive -> value.contentOrNull.orEmpty()
        is JsonObject -> value.text("name")
        is JsonArray ->
            value
                .mapNotNull { (it as? JsonObject)?.text("name") ?: (it as? JsonPrimitive)?.contentOrNull }
                .filter { it.isNotBlank() }
                .joinToString(", ")
        else -> ""
    }

private val narratorNamesField = Regex("""narratorNames\\?"\s*:\s*\\?"(.*?)\\?"""")
private val unicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")

/**
 * The full narrator list Apple embeds in an audiobook page's data (its visible badge shows only
 * the first narrator), e.g. "Scott Brick, Orlagh Cassidy … & Scott Sowers". Empty if absent.
 */
internal fun narratorNamesFrom(page: String): String {
    val raw = narratorNamesField.find(page)?.groupValues?.get(1) ?: return ""
    return unicodeEscape
        .replace(raw) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\/", "/")
        .trim()
        .take(2_000)
}

private fun plainText(value: String) =
    Html.fromHtml(value, Html.FROM_HTML_MODE_COMPACT).toString().trim().take(12_000)

private val schemaScript = Regex("""<script\b[^>]*\bname="schema:audiobook"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
private val languageBadge = Regex(
    """>LANGUAGE\s*</div>\s*<div[^>]*>[^<]*</div>\s*<div[^>]*>([^<]{1,80})</div>""",
)

private fun audiobookSchema(page: String): JsonObject? =
    schemaScript.find(page)?.groupValues?.get(1)?.let {
        runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
    }

/** "A, B & C and D" into individual names. */
internal fun splitNames(names: String): List<String> =
    names.split(Regex("""\s*(?:,|&|;|\band\b)\s*""")).map { it.trim() }.filter { it.isNotEmpty() }

private fun narratorCount(names: String) = splitNames(names).size

private fun sameName(a: String, b: String): Boolean {
    fun letters(value: String) = value.lowercase().filter(Char::isLetter)
    return letters(a).isNotEmpty() && letters(a) == letters(b)
}

internal fun CatalogDetails.matchesNarratorDuration(durationMs: Long): Boolean =
    narrator.isNotBlank() && matchingRuntime(durationMs, narratorDurationMs)

internal fun matchingRuntime(actualMs: Long, catalogMs: Long?): Boolean {
    if (actualMs <= 0 || catalogMs == null || catalogMs <= 0) return false
    val tolerance = (actualMs / 50).coerceIn(120_000, 480_000)
    return abs(actualMs - catalogMs) <= tolerance
}

internal fun BookEntity.withFilenameCredits(): BookEntity {
    if (!needsBookAuthor(author)) return this
    val hint = filenameBookCredits(title) ?: return this
    return copy(title = hint.title, author = hint.author)
}
