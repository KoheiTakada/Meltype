// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app

import io.github.ninjin0802.keyboard.app.essentials.DictionaryAdapter
import io.github.ninjin0802.keyboard.app.essentials.PersonalCompleter
import io.github.ninjin0802.keyboard.app.essentials.PhoneFieldContinuation
import io.github.ninjin0802.keyboard.app.essentials.UserWord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DictionaryAdapterTest {
    @Test fun reuseNativeDictionaryTextFormat() {
        assertEquals(listOf(UserWord("めあど","kohei@example.com"), UserWord("090","09012345678")),
            DictionaryAdapter.parse("# Meltype\nめあど\tkohei@example.com\n090\t09012345678\n"))
    }
    @Test fun personalEmailAndPhone() {
        val list = listOf(UserWord("めあど","kohei@example.com"), UserWord("090","09012345678"))
        assertEquals("kohei@example.com", PersonalCompleter.suggest("kohei",list)?.replacement)
        assertEquals("09012345678", PersonalCompleter.suggest("090",list)?.replacement)
        assertNull(PersonalCompleter.suggest("09",list))
    }
    @Test fun addressFromTown() {
        val list = listOf(UserWord("じゅうしょ","埼玉県さいたま市架空町1-2-3"))
        assertEquals("架空町1-2-3", PersonalCompleter.suggest("架空町",list)?.replacement)
        assertEquals("埼玉県さいたま市架空町1-2-3", PersonalCompleter.suggest("埼玉県",list)?.replacement)
    }
    @Test fun ordinaryDictionaryIsNativeOnly() {
        assertNull(PersonalCompleter.suggest("めるたいぷ",listOf(UserWord("めるたいぷ","Meltype"))))
    }
    @Test fun immediateNextPhoneField() {
        val list = listOf(UserWord("090","09012345678"))
        val c = PhoneFieldContinuation()
        c.exited("090","app",list,1000)
        assertEquals("1234",c.next("app",2000,false))
        c.exited("1234","app",list,3000)
        assertEquals("5678",c.next("app",4000,false))
        assertNull(c.next("other",4000,false))
        assertNull(c.next("app",90000,false))
    }
}
