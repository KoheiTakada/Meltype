// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import io.github.ninjin0802.keyboard.app.view.PersonalCandidateLines
import io.github.ninjin0802.keyboard.app.view.SuggestionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonalCandidateLinesTest {
    @Test fun emailBreaksAtAtMark() {
        assertEquals("kohei.t0312" to "@gmail.com",
            PersonalCandidateLines.split("kohei.t0312@gmail.com", SuggestionKind.EMAIL))
    }
    @Test fun addressBreaksBeforeTown() {
        assertEquals("埼玉県さいたま市" to "架空町1-2-3",
            PersonalCandidateLines.split("埼玉県さいたま市架空町1-2-3", SuggestionKind.ADDRESS))
        assertEquals("神奈川県横浜市港北区" to "菊名3-1",
            PersonalCandidateLines.split("神奈川県横浜市港北区菊名3-1", SuggestionKind.ADDRESS))
    }
    @Test fun townOnlyVariantSeparatesNumber() {
        assertEquals("架空町" to "1-2-3",
            PersonalCandidateLines.split("架空町1-2-3", SuggestionKind.ADDRESS))
    }
    @Test fun phonesUseFullSizeAndSingleLine() {
        assertEquals("09012345678" to null,
            PersonalCandidateLines.split("09012345678", SuggestionKind.PHONE))
        assertEquals("1234" to null,
            PersonalCandidateLines.split("1234", SuggestionKind.PHONE_SEGMENT))
    }
}
