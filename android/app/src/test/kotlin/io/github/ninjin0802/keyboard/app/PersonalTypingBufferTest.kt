// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app

import io.github.ninjin0802.keyboard.app.essentials.PersonalTypingBuffer
import io.github.ninjin0802.keyboard.app.essentials.PersonalCompleter
import io.github.ninjin0802.keyboard.app.essentials.UserWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PersonalTypingBufferTest {
    @Test fun liveKanjiConversionDoesNotChangeRawPrefix() {
        val keys = PersonalTypingBuffer()
        "toukyou".forEach { keys.typed(it.toString()) }
        assertEquals("toukyou", keys.current())
        val entries = listOf(UserWord("じゅうしょ", "東京都千代田区千代田1-1"))
        assertEquals("東京都千代田区千代田1-1",
            PersonalCompleter.suggest(keys.current(), entries)?.replacement)
        // A Core view that now shows kanji must not be used for matching.
        assertNull(PersonalCompleter.suggest("東京都", entries))
    }

    @Test fun emailMatchesTypingAndDeleteChangesThePrefix() {
        val keys = PersonalTypingBuffer()
        "kohei".forEach { keys.typed(it.toString()) }
        val entries = listOf(UserWord("めあど", "kohei@example.com"))
        assertEquals("kohei@example.com",
            PersonalCompleter.suggest(keys.current(), entries)?.replacement)
        keys.deleted()
        assertEquals("kohe", keys.current())
        keys.typed(" ")
        assertEquals("", keys.current())
        assertNull(PersonalCompleter.suggest(keys.current(), entries))
    }

    @Test fun typedRegionLengthTracksKanjiAlreadyCommittedByCore() {
        val keys = PersonalTypingBuffer()
        keys.typed("s", 5)
        "aitamaken".forEach { keys.typed(it.toString(), 5) }
        // Core wrote live-converted two-character prefix plus trailing kana.
        assertEquals(4, keys.replacedUtf16Length(9))
        assertNull(keys.replacedUtf16Length(5))
        assertNull(keys.replacedUtf16Length(201))
        keys.clear()
        assertNull(keys.replacedUtf16Length(9))
    }

    @Test fun uppercaseIsNormalizedAndFieldSwitchClearsIt() {
        val keys = PersonalTypingBuffer()
        keys.typed("KoHEi")
        assertEquals("kohei", keys.current())
        keys.clear()
        assertEquals("", keys.current())
    }

    @Test fun forcedLatinAndNumericEditorCaretUpdatesPreserveTypedSuggestions() {
        val email = PersonalTypingBuffer()
        "kohei".forEach { email.typed(it.toString(), 0) }
        assertEquals(true, email.isOwnDirectInput("kohei", selectionCollapsed = true))
        assertEquals(true, email.isOwnDirectInput("prefix kohei", selectionCollapsed = true))
        assertEquals(false, email.isOwnDirectInput("kohe", selectionCollapsed = true))
        assertEquals(false, email.isOwnDirectInput("kohei", selectionCollapsed = false))

        val phone = PersonalTypingBuffer()
        "0901".forEach { phone.typed(it.toString(), 0) }
        assertEquals(true, phone.isOwnDirectInput("0901", selectionCollapsed = true))
        assertEquals(false, phone.isOwnDirectInput("0902", selectionCollapsed = true))
        assertEquals(false, phone.isOwnDirectInput(null, selectionCollapsed = true))
    }

    @Test fun phoneNumberMatchesDigitsEvenWhenStoredWithSeparators() {
        val entries = listOf(UserWord("でんわ", "090-1234-5678"))
        assertEquals("090-1234-5678", PersonalCompleter.suggest("0901", entries)?.replacement)
        assertEquals("090-1234-5678", PersonalCompleter.suggest("090", entries)?.replacement)
        assertNull(PersonalCompleter.suggest("0909", entries))
    }

    @Test fun distinguishKanaFromConvertedKanji() {
        val entries = listOf(UserWord("じゅうしょ", "東京都千代田区千代田1-1"))
        assertEquals("東京都千代田区千代田1-1",
            PersonalCompleter.suggest("とうきょう", entries)?.replacement)
        assertNull(PersonalCompleter.suggest("じゅうしょ", entries))
        assertNull(PersonalCompleter.suggest("東京都", entries))
    }
}
