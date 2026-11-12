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

    @Test fun offerExpiresExactlyFiveMinutesAfterCopy() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        assertTrue(gate.canOffer(1L, 1_000L, 1_000L))
        assertTrue(gate.canOffer(1L, 1_000L, 300_999L))
        assertFalse(gate.canOffer(1L, 1_000L, 301_000L))
        assertFalse(gate.canOffer(1L, 1_000L, 999L)) // Future timestamp
    }

    @Test fun successfulPasteHidesThatCopyEvenWhenEditorIsClearedOrReopened() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        assertTrue(gate.canOffer(14L, 2_000L, 3_000L))
        val token = gate.pasted(14L, 2_000L)
        gate.deleted(editorEmpty = true)
        assertFalse(gate.canOffer(14L, 2_000L, 4_000L))
        gate.closed()
        gate.opened(restarting = false)
        assertFalse(gate.canOffer(14L, 2_000L, 5_000L))

        // Persisted token survives service recreation, without persisting text.
        val restored = ClipboardPillGate()
        restored.restoreConsumedCopyToken(token)
        restored.opened(restarting = false)
        assertFalse(restored.canOffer(14L, 2_000L, 5_000L))
        assertTrue(restored.canOffer(14L, 3_000L, 5_000L)) // Recopied same text
        assertTrue(restored.canOffer(15L, 3_000L, 5_000L)) // Different copy
    }

    @Test fun newCopyCannotBypassTheExistingEditorEmptyRule() {
        val gate = ClipboardPillGate()
        gate.opened(restarting = false)
        gate.edited()
        assertFalse(gate.canOffer(16L, 5_000L, 5_001L))
        gate.deleted(editorEmpty = true)
        assertTrue(gate.canOffer(16L, 5_000L, 5_002L))
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
