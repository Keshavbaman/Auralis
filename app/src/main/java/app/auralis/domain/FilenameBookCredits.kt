package app.auralis.domain

internal data class FilenameBookCredits(val title: String, val author: String)

private val filenameAuthor = Regex(
    """^(.{2,180}?)\s+[-–—]\s*by[\s_:]+(.{3,120}?)$""",
    RegexOption.IGNORE_CASE,
)
private val audioHint = Regex(
    """\s*(?:[-–—]\s*)?(?:\(\s*)?(?:full\s+audio\s*book|unabridged\s+audiobook|audiobook)\s*\)?\s*$""",
    RegexOption.IGNORE_CASE,
)
private val audioExtension = Regex("""\.(?:m4b|m4a|mp3|aac|flac|ogg|opus|wav)$""", RegexOption.IGNORE_CASE)

internal fun needsBookAuthor(author: String): Boolean =
    author.isBlank() || author.equals("Unknown author", ignoreCase = true)

internal fun filenameBookCredits(value: String): FilenameBookCredits? {
    val cleaned = value.trim().replace(audioExtension, "").replace(audioHint, "").replace('_', ' ').trim()
    val match = filenameAuthor.matchEntire(cleaned) ?: return null
    val title = match.groupValues[1].trim()
    val author = match.groupValues[2].trim()
    if (title.length < 2 || !author.split(Regex("\\s+")).all { token -> token.any(Char::isLetter) } ||
        author.count(Char::isLetter) < 3 || author.any(Char::isDigit)
    ) return null
    return FilenameBookCredits(title, author)
}
