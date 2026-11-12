// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.clipboard

/**
 * Clipboard chips are an opening/clear action, not a composition idle action.
 * Never re-arm on Enter, conversion commits, caret changes, or a candidate refresh.
 */
internal class ClipboardPillGate {
    private var eligible = false
    private var consumedCopyToken: String? = null

    /** Store only an opaque database ID and copy time, never clipboard content. */
    fun restoreConsumedCopyToken(token: String?) {
        consumedCopyToken = token
    }

    fun canOffer(copyId: Long, copiedAtMs: Long, nowMs: Long): Boolean =
        eligible && copiedAtMs > 0L && nowMs >= copiedAtMs &&
            nowMs - copiedAtMs < MAX_OFFER_AGE_MS &&
            consumedCopyToken != copyToken(copyId, copiedAtMs)

    /** Called only after the input connection confirms a successful paste. */
    fun pasted(copyId: Long, copiedAtMs: Long): String {
        val token = copyToken(copyId, copiedAtMs)
        consumedCopyToken = token
        eligible = false
        return token
    }

    companion object {
        const val MAX_OFFER_AGE_MS = 5L * 60_000L
        private fun copyToken(id: Long, timestamp: Long): String = "$id:$timestamp"
    }

    fun opened(restarting: Boolean) {
        if (!restarting) eligible = true
    }

    fun edited() {
        eligible = false
    }

    /** Re-arm only after a real deletion emptied the whole editor. */
    fun deleted(editorEmpty: Boolean) {
        if (editorEmpty) eligible = true
    }

    fun closed() {
        eligible = false
    }

    fun visible(): Boolean = eligible
}
