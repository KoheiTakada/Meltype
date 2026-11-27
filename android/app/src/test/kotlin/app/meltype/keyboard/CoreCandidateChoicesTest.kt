// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.keyboard

import app.meltype.keyboard.core.CoreCandidateChoices
import app.meltype.keyboard.core.CoreChoice
import app.meltype.keyboard.core.ImeCandidates
import app.meltype.keyboard.core.ImeView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CoreCandidateChoicesTest {
    @Test fun preservesTheListAndIndexOfEveryDisplayedChoice() {
        val view = ImeView(
            text = "今日は雨",
            converting = false,
            candidates = ImeCandidates(listOf("今日は雨です"), selected = -1),
            typingCandidates = listOf("今日は", "", "京は"),
        )

        val (choices, selected) = CoreCandidateChoices.fromView(view)
        assertEquals(
            listOf(
                CoreChoice("今日は雨です", 0),
                CoreChoice("今日は", 0, fromTyping = true),
                CoreChoice("京は", 2, fromTyping = true),
            ),
            choices,
        )
        assertNull(selected)
    }
}
