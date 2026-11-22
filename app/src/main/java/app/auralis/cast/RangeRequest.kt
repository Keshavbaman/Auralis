package app.auralis.cast

/** One inclusive HTTP byte range. Malformed/multiple/unsatisfiable ranges are rejected. */
data class ByteRange(val start: Long, val end: Long) {
    val length: Long
        get() = end - start + 1
}

fun parseByteRange(header: String?, size: Long): ByteRange? {
    if (size <= 0) return null
    if (header == null) return ByteRange(0, size - 1)
    val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(header.trim()) ?: return null
    val (a, b) = match.destructured
    if (a.isEmpty()) {
        val suffix = b.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return ByteRange((size - suffix).coerceAtLeast(0), size - 1)
    }
    val start = a.toLongOrNull()?.takeIf { it in 0 until size } ?: return null
    val end =
        if (b.isEmpty()) size - 1
        else b.toLongOrNull()?.takeIf { it >= start }?.coerceAtMost(size - 1) ?: return null
    return ByteRange(start, end)
}
