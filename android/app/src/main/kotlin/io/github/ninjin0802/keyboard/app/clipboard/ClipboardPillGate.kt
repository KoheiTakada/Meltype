// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.clipboard

/**
 * Clipboard chips are an opening/clear action, not a composition idle action.
 * Never re-arm on Enter, conversion commits, caret changes, or a candidate refresh.
 */
internal class ClipboardPillGate {
    private var eligible = false

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
