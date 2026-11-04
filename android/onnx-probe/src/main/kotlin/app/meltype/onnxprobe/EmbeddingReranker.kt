// SPDX-License-Identifier: GPL-3.0-or-later
package app.meltype.onnxprobe

import kotlin.math.sqrt

/** Exact tokenizer IDs and masks, generated with the model's matching tokenizer. */
class TokenSequence(val inputIds: LongArray, val attentionMask: LongArray) {
    init {
        require(inputIds.isNotEmpty() && inputIds.size == attentionMask.size)
    }
}

data class CandidateTokens(val text: String, val tokens: TokenSequence)

/** Backend contract; intentionally independent of the Android ORT API for unit testing. */
fun interface TokenEmbeddingEncoder {
    fun encode(tokens: TokenSequence): FloatArray
}

/**
 * Experimental Fumiori-style dual-encoder ranker: one context embedding, cached
 * candidate embeddings, cosine similarity, and conservative Mozc-order fallback.
 *
 * It does NOT implement Fumiori's full lexical bonuses or multi-clause joint decoder.
 * Run only on a background worker. Never send input, candidates, or context off-device.
 */
class EmbeddingReranker(
    private val encoder: TokenEmbeddingEncoder,
    private val limit: Int = 10,
    private val minimumGain: Double = 0.04,
) {
    private val candidateCache = object : LinkedHashMap<String, FloatArray>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > 512
    }

    init {
        require(limit in 2..100)
        require(minimumGain >= 0.0)
    }

    @Synchronized
    fun rerank(context: TokenSequence, candidates: List<CandidateTokens>): List<String> {
        val baseline = candidates.map { it.text }
        if (candidates.size < 2) return baseline
        // ONNX/tokenizer failures must never interfere with normal Mozc conversion.
        return try {
            val contextVector = encoder.encode(context)
            val scores = candidates.take(limit).mapIndexed { index, candidate ->
                val candidateVector = candidateCache.getOrPut(candidate.text) {
                    encoder.encode(candidate.tokens)
                }
                Ranked(index, candidate.text, cosine(contextVector, candidateVector))
            }
            val best = scores.maxWithOrNull(compareBy<Ranked> { it.score }.thenBy { -it.index })
                ?: return baseline
            val first = scores.first()
            if (best.index == 0 || !best.score.isFinite() ||
                best.score - first.score < minimumGain
            ) return baseline
            val ordered = scores.sortedWith(compareByDescending<Ranked> { it.score }.thenBy { it.index })
            ordered.map { it.text } + baseline.drop(limit)
        } catch (_: Exception) {
            baseline
        }
    }

    @Synchronized
    fun clearCache() {
        candidateCache.clear()
    }

    private data class Ranked(val index: Int, val text: String, val score: Double)

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return Double.NEGATIVE_INFINITY
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na <= 0.0 || nb <= 0.0) return Double.NEGATIVE_INFINITY
        return dot / sqrt(na * nb)
    }
}
