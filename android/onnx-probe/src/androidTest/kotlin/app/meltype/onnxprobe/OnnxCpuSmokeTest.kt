// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.onnxprobe

import android.util.Base64
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Genuine Android ORT native execution smoke test. The embedded 275-byte model
 * only casts int64 inputs to float32 and masks them. It is NOT the Fumiori model,
 * and the latency figures must never be presented as 70M model benchmarks.
 */
class OnnxCpuSmokeTest {
    @Test
    fun onDeviceCpuRunsOnnxGraphAndReturnsEmbedding() {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val file = File(dir, "local-onnx-cpu-smoke.onnx")
        try {
            file.writeBytes(Base64.decode(MODEL_BASE64, Base64.DEFAULT))
            OnnxCpuEncoder(file).use { engine ->
                val input = TokenSequence(longArrayOf(1, 2, 3), longArrayOf(1, 1, 0))
                val actual = engine.encode(input)
                assertArrayEquals(floatArrayOf(1f, 2f, 0f), actual, 0.0001f)
                repeat(5) { engine.encode(input) }
                val times = LongArray(30) {
                    val start = System.nanoTime()
                    engine.encode(input)
                    System.nanoTime() - start
                }.sorted()
                val p50Ms = times[15] / 1_000_000.0
                val p95Ms = times[28] / 1_000_000.0
                Log.i("MeltypeONNX", "SMOKE_ONLY model=tiny-cast-multiply p50_ms=$p50Ms p95_ms=$p95Ms")
                assertTrue(p50Ms >= 0.0)
            }
        } finally {
            file.delete()
        }
    }

    companion object {
        private const val MODEL_BASE64 =
            "CAgSG01lbHR5cGUgT05OWCBDUFUgc21va2UgdGVzdDrtAQomCglpbnB1dF9pZHMSCGlkX2Zsb2F0IgRDYXN0KgkKAnRvGAGgAQIKLQoOYXR0ZW50aW9uX21hc2sSCm1hc2tfZmxvYXQiBENhc3QqCQoCdG8YAaABAgonCghpZF9mbG9hdAoKbWFza19mbG9hdBIKZW1iZWRkaW5ncyIDTXVsEg50aW55LW9ydC1zbW9rZVobCglpbnB1dF9pZHMSDgoMCAcSCAoCCAEKAggDWiAKDmF0dGVudGlvbl9tYXNrEg4KDAgHEggKAggBCgIIA2IcCgplbWJlZGRpbmdzEg4KDAgBEggKAggBCgIIA0ICEBI="
    }
}
