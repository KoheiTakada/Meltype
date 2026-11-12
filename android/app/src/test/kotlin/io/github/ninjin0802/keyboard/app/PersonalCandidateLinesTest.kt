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
        assertEquals("東京都千代田区" to "千代田1-1",
            PersonalCandidateLines.split("東京都千代田区千代田1-1", SuggestionKind.ADDRESS))
        assertEquals("大阪府大阪市中央区" to "大阪城1-1",
            PersonalCandidateLines.split("大阪府大阪市中央区大阪城1-1", SuggestionKind.ADDRESS))
    }
    @Test fun townOnlyVariantSeparatesNumber() {
        assertEquals("千代田" to "1-2-3",
            PersonalCandidateLines.split("千代田1-1", SuggestionKind.ADDRESS))
    }
    @Test fun phonesUseFullSizeAndSingleLine() {
        assertEquals("09012345678" to null,
            PersonalCandidateLines.split("09012345678", SuggestionKind.PHONE))
        assertEquals("1234" to null,
            PersonalCandidateLines.split("1234", SuggestionKind.PHONE_SEGMENT))
    }
}
