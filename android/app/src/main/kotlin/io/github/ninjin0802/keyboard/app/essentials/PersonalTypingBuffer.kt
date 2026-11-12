// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.essentials

/**
 * Stores only the recent keys in memory, not editor text. This is important when Meltype Core
 * renders "さい" as "埼" by live conversion: completions compare with typed "sai", not kanji.
 * Never persisted, logged, uploaded, or sent to the native candidate ranking code.
 */
class PersonalTypingBuffer {
    private val value = StringBuilder()

    fun typed(key: String) {
        if (key.isEmpty()) return
        if (key.any { !(it.isLetterOrDigit() || it in "@._-") }) {
            clear()
            return
        }
        if (value.length + key.length > 96) clear()
        value.append(key.lowercase())
    }

    fun deleted() {
        if (value.isNotEmpty()) value.deleteCharAt(value.lastIndex)
    }

    fun clear() {
        value.setLength(0)
    }

    fun current(): String = value.toString()
}
