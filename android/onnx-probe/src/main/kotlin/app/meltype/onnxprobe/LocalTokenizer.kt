// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.onnxprobe

import java.io.Closeable
import java.io.File

/** The model's own Hugging Face tokenizer, loaded and run entirely on-device. */
class LocalTokenizer(file: File) : Closeable {
    private var handle: Long

    init {
        System.loadLibrary("meltype_tokenizer")
        require(file.isFile) { "Tokenizer file missing" }
        handle = nativeCreate(file.absolutePath)
        check(handle != 0L) { "Tokenizer initialization failed" }
    }

    @Synchronized
    fun encode(text: String): TokenSequence {
        check(handle != 0L) { "Tokenizer is closed" }
        val ids = nativeEncode(handle, text)
        check(ids.isNotEmpty()) { "Tokenizer returned no tokens" }
        return TokenSequence(ids, LongArray(ids.size) { 1L })
    }

    @Synchronized
    override fun close() {
        val oldHandle = handle
        handle = 0L
        if (oldHandle != 0L) nativeClose(oldHandle)
    }

    private external fun nativeCreate(path: String): Long
    private external fun nativeEncode(handle: Long, text: String): LongArray
    private external fun nativeClose(handle: Long)
}
