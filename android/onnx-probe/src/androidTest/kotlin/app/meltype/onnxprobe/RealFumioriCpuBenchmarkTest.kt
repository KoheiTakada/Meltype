// SPDX-License-Identifier: GPL-3.0-or-later
package app.meltype.onnxprobe

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Expensive opt-in test: the CI can temporarily load the current real Fumiori
 * dual-encoder ONNX + matching tokenizer fixtures from its public Windows MSI.
 * This is NOT a baseline accuracy test or a phone-specific latency claim.
 */
class RealFumioriCpuBenchmarkTest {
    @Test
    fun realDualEncoderRunsOnAndroidCpu() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val testAssets = instrumentation.context.assets
        val modelName = "fumiori-dual-encoder-int8.onnx"
        val fixtureName = "fumiori-fixtures.json"
        // Normal local development does not require the 70M model. The dedicated
        // real-model CI checks that the file exists BEFORE running this test.
        assumeTrue(testAssets.list("")?.contains(modelName) == true)
        val model = File(instrumentation.targetContext.cacheDir, modelName)
        try {
            testAssets.open(modelName).use { input -> model.outputStream().use(input::copyTo) }
            val fixtures = JSONObject(testAssets.open(fixtureName).bufferedReader().use { it.readText() })
            fun sequence(name: String): TokenSequence {
                val value = fixtures.getJSONObject(name)
                fun longArray(key: String): LongArray {
                    val arr = value.getJSONArray(key)
                    return LongArray(arr.length()) { arr.getLong(it) }
                }
                return TokenSequence(longArray("input_ids"), longArray("attention_mask"))
            }
            val initStart = System.nanoTime()
            OnnxCpuEncoder(model, threads = 2).use { encoder ->
                val initMs = (System.nanoTime() - initStart) / 1_000_000.0
                val context = sequence("context")
                val flower = sequence("flower")
                val nose = sequence("nose")
                val first = encoder.encode(context)
                assertEquals(384, first.size)
                assertTrue(first.all { it.isFinite() })
                // Run Fumiori-style candidate ranking using REAL model embeddings.
                val ranked = EmbeddingReranker(encoder).rerank(
                    context, listOf(
                        CandidateTokens("花", flower),
                        CandidateTokens("鼻", nose),
                    )
                )
                assertEquals(setOf("花", "鼻"), ranked.toSet())
                repeat(2) { encoder.encode(context) }
                val elapsed = LongArray(7) {
                    val start = System.nanoTime()
                    encoder.encode(context)
                    System.nanoTime() - start
                }.sorted()
                val p50 = elapsed[3] / 1_000_000.0
                val p95 = elapsed[6] / 1_000_000.0
                Log.i(
                    "MeltypeONNX",
                    "REAL_FUMIORI_CPU environment=AndroidEmulator-x86_64" +
                        " model_bytes=${model.length()} init_ms=$initMs" +
                        " p50_ms=$p50 p95_ms=$p95 rank=$ranked"
                )
            }
        } finally {
            model.delete()
        }
    }
}
