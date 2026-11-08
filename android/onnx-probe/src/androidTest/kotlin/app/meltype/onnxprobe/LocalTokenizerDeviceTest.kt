// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.onnxprobe

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class LocalTokenizerDeviceTest {
    @Test
    fun matchesReferenceTokenIdsForJapaneseAndMixedInput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        assumeTrue(assets.list("")?.contains("fumiori-tokenizer.json") == true)
        val file = File(instrumentation.targetContext.cacheDir, "fumiori-tokenizer.json")
        try {
            assets.open("fumiori-tokenizer.json").use { input -> file.outputStream().use(input::copyTo) }
            val fixtures = JSONObject(assets.open("fumiori-fixtures.json").bufferedReader().use { it.readText() })
            val tokenizer = LocalTokenizer(file)
            try {
                for (name in listOf("context", "flower", "nose", "mixed", "long")) {
                    val fixture = fixtures.getJSONObject(name)
                    val expected = fixture.getJSONArray("input_ids")
                    val ids = LongArray(expected.length()) { expected.getLong(it) }
                    assertArrayEquals(name, ids, tokenizer.encode(fixture.getString("text")).inputIds)
                }
            } finally {
                tokenizer.close()
            }
            tokenizer.close()
            assertThrows(IllegalStateException::class.java) { tokenizer.encode("花") }
        } finally {
            file.delete()
        }
    }
}
