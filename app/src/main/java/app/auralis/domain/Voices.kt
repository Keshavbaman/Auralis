package app.auralis.domain

import kotlin.math.sqrt

/**
 * Groups speaker fingerprints (one per recognised utterance) into voices, entirely on the device.
 *
 * Pass 1 assigns each fingerprint to the closest voice by cosine similarity, opening a new voice
 * below [threshold]. Pass 2 reassigns everything to the final voice centres, correcting early
 * choices made before the centres settled. Voices holding less than [minShare] of the utterances
 * are folded into their nearest larger voice, since stray one-offs are usually mixed or noisy
 * utterances rather than real characters.
 *
 * [threshold] 0.55 was tuned on a full-cast audiobook excerpt: it separated the narrator cleanly
 * and kept most of each character's lines together. Results remain approximate; voices are meant
 * to be named by the listener.
 *
 * @return a voice id per input (numbered 0, 1, 2… by first appearance), or -1 where the input
 *   had no fingerprint.
 */
fun clusterVoices(
    fingerprints: List<FloatArray?>,
    threshold: Float = .55f,
    minShare: Float = .03f,
    maxVoices: Int = 12,
): IntArray {
    val vectors = fingerprints.map { it?.let(::unit) }
    val sums = mutableListOf<FloatArray>()
    for (vector in vectors) {
        if (vector == null) continue
        var best = -1
        var bestSimilarity = -2f
        sums.forEachIndexed { index, sum ->
            val similarity = dot(vector, unit(sum))
            if (similarity > bestSimilarity) {
                best = index
                bestSimilarity = similarity
            }
        }
        if (best >= 0 && (bestSimilarity >= threshold || sums.size >= maxVoices)) {
            for (i in vector.indices) sums[best][i] += vector[i]
        } else {
            sums += vector.copyOf()
        }
    }
    if (sums.isEmpty()) return IntArray(vectors.size) { -1 }
    val centres = sums.map(::unit)
    fun nearest(vector: FloatArray, among: Collection<Int>) =
        among.maxBy { dot(vector, centres[it]) }

    val all = centres.indices.toList()
    val refined = vectors.map { v -> if (v == null) -1 else nearest(v, all) }
    val assigned = refined.count { it >= 0 }
    val sizes = refined.filter { it >= 0 }.groupingBy { it }.eachCount()
    val large =
        sizes.filterValues { it >= maxOf(2f, minShare * assigned) }.keys.ifEmpty {
            setOf(sizes.maxBy { it.value }.key)
        }
    val folded = vectors.map { v -> if (v == null) -1 else nearest(v, large) }

    val order = LinkedHashMap<Int, Int>()
    folded.forEach { if (it >= 0 && it !in order) order[it] = order.size }
    return IntArray(folded.size) { i -> folded[i].let { if (it < 0) -1 else order.getValue(it) } }
}

/** Display label for a voice id from [clusterVoices]. */
fun voiceLabel(id: Int): String = "Voice ${id + 1}"

private fun dot(a: FloatArray, b: FloatArray): Float {
    var total = 0f
    for (i in a.indices) total += a[i] * b[i]
    return total
}

private fun unit(vector: FloatArray): FloatArray {
    val length = sqrt(dot(vector, vector)).takeIf { it > 0f } ?: 1f
    return FloatArray(vector.size) { vector[it] / length }
}
