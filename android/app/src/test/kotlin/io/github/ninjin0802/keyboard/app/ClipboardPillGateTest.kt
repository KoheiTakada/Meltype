// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import io.github.ninjin0802.keyboard.app.clipboard.ClipboardPillGate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardPillGateTest {
    @Test fun noChipAfterTypingOrConfirmingEvenIfEditorBrieflyLooksEmpty() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        assertTrue(gate.visible())
        gate.edited() // first key
        assertFalse(gate.visible())
        gate.edited() // Enter, space, live conversion and native candidate click
        assertFalse(gate.visible())
        gate.deleted(editorEmpty = false) // backspace did not clear all text
        assertFalse(gate.visible())
    }

    @Test fun onlyCompleteDeletionRearms() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        gate.edited()
        gate.deleted(editorEmpty = false)
        assertFalse(gate.visible())
        gate.deleted(editorEmpty = true)
        assertTrue(gate.visible())
        gate.edited() // paste the chip
        assertFalse(gate.visible())
    }

    @Test fun restartDoesNotTreatAnAlreadyTypedEditorAsFresh() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        gate.edited()
        gate.opened(restarting = true)
        assertFalse(gate.visible())
        gate.closed()
        gate.opened(restarting = false)
        assertTrue(gate.visible())
    }
}
