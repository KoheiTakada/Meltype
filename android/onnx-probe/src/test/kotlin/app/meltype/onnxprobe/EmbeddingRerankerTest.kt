// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.onnxprobe

import kotlin.test.Test
import kotlin.test.assertEquals

class EmbeddingRerankerTest {
    private fun seq(id: Long) = TokenSequence(longArrayOf(id), longArrayOf(1))

    @Test
    fun betterContextCandidateMovesToFirstPlace() {
        val encoder = TokenEmbeddingEncoder { tokens ->
            when (tokens.inputIds[0]) {
                1L, 3L -> floatArrayOf(1f, 0f)
                else -> floatArrayOf(0f, 1f)
            }
        }
        val ranker = EmbeddingReranker(encoder)
        assertEquals(
            listOf("鼻", "花"),
            ranker.rerank(seq(1), listOf(CandidateTokens("花", seq(2)), CandidateTokens("鼻", seq(3)))),
        )
    }

    @Test
    fun originalOrderIsPreservedBelowConfidenceMargin() {
        val ranker = EmbeddingReranker(
            TokenEmbeddingEncoder { floatArrayOf(1f, 0f) },
        )
        assertEquals(
            listOf("花", "鼻"),
            ranker.rerank(seq(1), listOf(CandidateTokens("花", seq(2)), CandidateTokens("鼻", seq(3)))),
        )
    }

    @Test
    fun modelFailureFallsBackToMozc() {
        val ranker = EmbeddingReranker(TokenEmbeddingEncoder { error("ORT failed") })
        assertEquals(
            listOf("花", "鼻"),
            ranker.rerank(seq(1), listOf(CandidateTokens("花", seq(2)), CandidateTokens("鼻", seq(3)))),
        )
    }

    @Test
    fun rerankingUsesCachedCandidateEmbeddingAndPreservesOverflow() {
        val calls = mutableListOf<Long>()
        val ranker = EmbeddingReranker(TokenEmbeddingEncoder {
            calls += it.inputIds[0]
            when (it.inputIds[0]) {
                1L, 3L -> floatArrayOf(1f, 0f)
                else -> floatArrayOf(0f, 1f)
            }
        }, limit = 2)
        val candidates = listOf(CandidateTokens("花", seq(2)), CandidateTokens("鼻", seq(3)), CandidateTokens("外", seq(4)))
        assertEquals(listOf("鼻", "花", "外"), ranker.rerank(seq(1), candidates))
        assertEquals(listOf("鼻", "花", "外"), ranker.rerank(seq(1), candidates))
        assertEquals(listOf(1L, 2L, 3L, 1L), calls)
    }
}
