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
    private var startCursor: Int? = null

    fun typed(key: String, caret: Int = -1) {
        if (key.isEmpty()) return
        if (key.any { !(it.isLetterOrDigit() || it in "@._-") }) {
            clear()
            return
        }
        if (value.length + key.length > 96) clear()
        if (value.isEmpty()) startCursor = caret.takeIf { it >= 0 }
        value.append(key.lowercase())
    }

    fun deleted() {
        if (value.isNotEmpty()) value.deleteCharAt(value.lastIndex)
        if (value.isEmpty()) startCursor = null
    }

    fun clear() {
        value.setLength(0)
        startCursor = null
    }

    fun current(): String = value.toString()

    /**
     * In direct number/telephone/email/Latin editors, the Android editor reports
     * the caret movement after our commitText(). CaretWatch intentionally treats
     * non-Core edits as external to the Core, but the same update must NOT clear
     * these raw keystrokes used by the personal completion UI.
     *
     * Verify against the visible editor tail, not just the caret delta: genuine
     * cursor moves or app rewrites that remove the typed prefix still invalidate it.
     */
    fun isOwnDirectInput(editorBefore: CharSequence?, selectionCollapsed: Boolean): Boolean =
        selectionCollapsed && value.isNotEmpty() &&
            editorBefore?.toString()?.endsWith(value.toString(), ignoreCase = true) == true

    /**
     * Span written since the first key, including any text Core live-converted
     * and committed ahead of the still-composing segment. Never delete an
     * arbitrary large range or a range that precedes the original caret.
     */
    fun replacedUtf16Length(currentCaret: Int): Int? {
        val start = startCursor ?: return null
        return (currentCaret - start).takeIf { it in 1..160 && value.isNotEmpty() }
    }
}
