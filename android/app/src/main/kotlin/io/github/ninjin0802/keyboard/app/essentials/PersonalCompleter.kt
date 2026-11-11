// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.essentials

/** An additional Android-only suggestion; native prediction and conversion remain authoritative. */
data class PersonalCompletion(val display: String, val typed: String, val replacement: String)

object PersonalCompleter {
    private val prefectures = listOf("北海道","東京都","大阪府","京都府",
        "青森県","岩手県","宮城県","秋田県","山形県","福島県","茨城県","栃木県","群馬県",
        "埼玉県","千葉県","神奈川県","新潟県","富山県","石川県","福井県","山梨県","長野県",
        "岐阜県","静岡県","愛知県","三重県","滋賀県","兵庫県","奈良県","和歌山県",
        "鳥取県","島根県","岡山県","広島県","山口県","徳島県","香川県","愛媛県",
        "高知県","福岡県","佐賀県","長崎県","熊本県","大分県","宮崎県","鹿児島県","沖縄県")

    fun digits(text: String) = text.filter(Char::isDigit)
    private fun typeOf(word: String): String? {
        val d = digits(word)
        return when {
            word.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "email"
            d.length in 10..11 && d.startsWith("0") &&
                word.all { it.isDigit() || it == '-' || it == ' ' || it == '(' || it == ')' } -> "phone"
            prefectures.any { word.startsWith(it) } -> "address"
            else -> null
        }
    }

    fun suggest(before: String, dictionary: List<UserWord>): PersonalCompletion? {
        if (before.isBlank()) return null
        var result: PersonalCompletion? = null
        var score = -1
        for (entry in dictionary) {
            val kind = typeOf(entry.word) ?: continue
            val variants = if (kind == "address") addressSuffixes(entry.word) else listOf(entry.word)
            for (value in variants) {
                val max = minOf(before.length, value.length - 1, 72)
                for (size in max downTo 1) {
                    val typed = before.takeLast(size)
                    val isMatch = when (kind) {
                        "phone" -> size >= 3 && typed.all { it.isDigit() || it == '-' } &&
                            digits(typed).length >= 3 && digits(value).startsWith(digits(typed))
                        "email" -> size >= 3 && value.startsWith(typed, ignoreCase = true)
                        else -> size >= 2 && value.startsWith(typed)
                    }
                    if (!isMatch) continue
                    if (size * 10 > score) {
                        score = size * 10
                        result = PersonalCompletion(value, typed, value)
                    }
                    break
                }
            }
            // Readings continue working through the original Core; personal info gets a third-slot UI shortcut.
            if (entry.reading.length >= 2 && before.endsWith(entry.reading, ignoreCase = true) &&
                entry.reading.length * 10 + 100 > score) {
                score = entry.reading.length * 10 + 100
                result = PersonalCompletion(entry.word, entry.reading, entry.word)
            }
        }
        return result
    }

    fun addressSuffixes(word: String): List<String> {
        val suffixes = linkedSetOf(word)
        val prefix = prefectures.firstOrNull { word.startsWith(it) }
        var remaining = if (prefix == null) word else word.removePrefix(prefix)
        if (remaining != word && remaining.isNotEmpty()) suffixes += remaining
        repeat(2) {
            val point = remaining.indexOfFirst { it in "市区町村" }
            if (point < 0 || point == remaining.lastIndex) return@repeat
            remaining = remaining.substring(point + 1)
            if (remaining.length >= 2) suffixes += remaining
        }
        return suffixes.toList()
    }
}

/** Only the immediate next blank field in the same application, for up to 45 seconds. */
class PhoneFieldContinuation {
    private data class State(val app: String, val parts: List<String>, val next: Int, val at: Long)
    private var state: State? = null

    fun exited(value: String, app: String?, dictionary: List<UserWord>, now: Long) {
        if (app == null) { state = null; return }
        val text = PersonalCompleter.digits(value)
        val prior = state
        if (prior != null && prior.app == app && now - prior.at in 0..45_000 &&
            prior.next < 3 && prior.parts[prior.next] == text) {
            state = prior.copy(next = prior.next + 1, at = now)
            return
        }
        val matches = dictionary.map { PersonalCompleter.digits(it.word) }
            .filter { it.length == 11 && text.length == 3 && it.startsWith(text) }.distinct()
        state = if (matches.size == 1) {
            val number = matches.single()
            State(app, listOf(number.take(3), number.substring(3, 7), number.takeLast(4)), 1, now)
        } else null
    }

    fun next(app: String?, now: Long, restarting: Boolean): String? {
        val saved = state ?: return null
        if (restarting || saved.app != app || now - saved.at !in 0..45_000 ||
            saved.next !in 1..2) return null
        return saved.parts[saved.next]
    }
}
