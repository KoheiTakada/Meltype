// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.view

import android.graphics.Bitmap

/** Android-only presentation. Native candidate indexes are not modified. */
enum class SuggestionKind { EMAIL, ADDRESS, PHONE, PHONE_SEGMENT, CLIPBOARD_TEXT, CLIPBOARD_IMAGE }

data class BarSuggestion(
    val text: String,
    val kind: SuggestionKind,
    val thumbnail: Bitmap? = null,
) {
    val isClipboard: Boolean
        get() = kind == SuggestionKind.CLIPBOARD_TEXT || kind == SuggestionKind.CLIPBOARD_IMAGE
}

/** The second line is visually smaller; the full word is always committed on tap. */
object PersonalCandidateLines {
    fun split(value: String, kind: SuggestionKind): Pair<String, String?> = when (kind) {
        SuggestionKind.EMAIL -> {
            val at = value.indexOf('@')
            if (at > 0 && at < value.lastIndex) value.substring(0, at) to value.substring(at)
            else value to null
        }
        SuggestionKind.ADDRESS -> {
            val prefecture = Regex("^(?:北海道|東京都|大阪府|京都府|.{2,3}県)")
                .find(value)?.value.orEmpty()
            val town = value.substring(prefecture.length)
            val first = town.indexOfFirst { it in "市区町村" }
            var boundary = if (first >= 0) prefecture.length + first + 1 else -1
            // Example: 神奈川県横浜市港北区 -> split after the ward, not after 横浜市.
            if (boundary > 0 && value[boundary - 1] == '市') {
                val ward = value.substring(boundary).indexOf('区')
                if (ward in 1..8) boundary += ward + 1
            }
            if (boundary in 1 until value.length) {
                value.substring(0, boundary) to value.substring(boundary)
            } else {
                val number = Regex("[0-9０-９]").find(value)?.range?.first ?: -1
                if (number in 2 until value.length) value.substring(0, number) to value.substring(number)
                else value to null
            }
        }
        else -> value to null
    }
}
