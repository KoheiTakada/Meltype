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
        "saitama".forEach { keys.typed(it.toString()) }
        assertEquals("saitama", keys.current())
        val entries = listOf(UserWord("じゅうしょ", "埼玉県さいたま市架空町1-2-3"))
        assertEquals("埼玉県さいたま市架空町1-2-3",
            PersonalCompleter.suggest(keys.current(), entries)?.replacement)
        // A Core view that now shows kanji must not be used for matching.
        assertNull(PersonalCompleter.suggest("埼玉県", entries))
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

    @Test fun uppercaseIsNormalizedAndFieldSwitchClearsIt() {
        val keys = PersonalTypingBuffer()
        keys.typed("KoHEi")
        assertEquals("kohei", keys.current())
        keys.clear()
        assertEquals("", keys.current())
    }

    @Test fun distinguishKanaFromConvertedKanji() {
        val entries = listOf(UserWord("じゅうしょ", "埼玉県さいたま市架空町1-2-3"))
        assertEquals("埼玉県さいたま市架空町1-2-3",
            PersonalCompleter.suggest("さいたま", entries)?.replacement)
        assertNull(PersonalCompleter.suggest("じゅうしょ", entries))
        assertNull(PersonalCompleter.suggest("埼玉県", entries))
    }
}
