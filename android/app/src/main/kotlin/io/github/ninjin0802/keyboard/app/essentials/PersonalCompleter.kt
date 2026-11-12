// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.essentials

/** An additional Android-only suggestion; native prediction and conversion remain authoritative. */
data class PersonalCompletion(val display: String, val typed: String, val replacement: String, val kind: String)

object PersonalCompleter {
    /** The actual address reading is independent of the kanji shown by live conversion. */
    private val prefectureReadings = linkedMapOf(
        "北海道" to "ほっかいどう", "青森県" to "あおもりけん", "岩手県" to "いわてけん",
        "宮城県" to "みやぎけん", "秋田県" to "あきたけん", "山形県" to "やまがたけん",
        "福島県" to "ふくしまけん", "茨城県" to "いばらきけん", "栃木県" to "とちぎけん",
        "群馬県" to "ぐんまけん", "埼玉県" to "さいたまけん", "千葉県" to "ちばけん",
        "東京都" to "とうきょうと", "神奈川県" to "かながわけん", "新潟県" to "にいがたけん",
        "富山県" to "とやまけん", "石川県" to "いしかわけん", "福井県" to "ふくいけん",
        "山梨県" to "やまなしけん", "長野県" to "ながのけん", "岐阜県" to "ぎふけん",
        "静岡県" to "しずおかけん", "愛知県" to "あいちけん", "三重県" to "みえけん",
        "滋賀県" to "しがけん", "京都府" to "きょうとふ", "大阪府" to "おおさかふ",
        "兵庫県" to "ひょうごけん", "奈良県" to "ならけん", "和歌山県" to "わかやまけん",
        "鳥取県" to "とっとりけん", "島根県" to "しまねけん", "岡山県" to "おかやまけん",
        "広島県" to "ひろしまけん", "山口県" to "やまぐちけん", "徳島県" to "とくしまけん",
        "香川県" to "かがわけん", "愛媛県" to "えひめけん", "高知県" to "こうちけん",
        "福岡県" to "ふくおかけん", "佐賀県" to "さがけん", "長崎県" to "ながさきけん",
        "熊本県" to "くまもとけん", "大分県" to "おおいたけん", "宮崎県" to "みやざきけん",
        "鹿児島県" to "かごしまけん", "沖縄県" to "おきなわけん",
    )
    private val kana = arrayOf(
        "あ","い","う","え","お","か","き","く","け","こ","さ","し","す","せ","そ",
        "た","ち","つ","て","と","な","に","ぬ","ね","の","は","ひ","ふ","へ","ほ",
        "ま","み","む","め","も","や","ゆ","よ","ら","り","る","れ","ろ","わ","を",
        "ん","が","ぎ","ぐ","げ","ご","ざ","じ","ず","ぜ","ぞ","だ","ぢ","づ","で","ど",
        "ば","び","ぶ","べ","ぼ","ぱ","ぴ","ぷ","ぺ","ぽ","ゃ","ゅ","ょ","ぁ","ぃ","ぅ","ぇ","ぉ",
    )
    private val romaji = arrayOf(
        "a","i","u","e","o","ka","ki","ku","ke","ko","sa","shi","su","se","so",
        "ta","chi","tsu","te","to","na","ni","nu","ne","no","ha","hi","fu","he","ho",
        "ma","mi","mu","me","mo","ya","yu","yo","ra","ri","ru","re","ro","wa","wo",
        "n","ga","gi","gu","ge","go","za","ji","zu","ze","zo","da","ji","zu","de","do",
        "ba","bi","bu","be","bo","pa","pi","pu","pe","po","ya","yu","yo","a","i","u","e","o",
    )
    private val romanMap = kana.indices.associate { kana[it] to romaji[it] }
    private val kanaDigraphs = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
    )

    fun digits(text: String) = text.filter(Char::isDigit)
    fun kindOf(word: String): String? {
        val d = digits(word)
        return when {
            word.matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) -> "email"
            d.length in 10..11 && d.startsWith("0") &&
                word.all { it.isDigit() || it == '-' || it == ' ' || it == '(' || it == ')' } -> "phone"
            prefectureReadings.keys.any { word.startsWith(it) } -> "address"
            else -> null
        }
    }

    private fun hiragana(value: String): String = buildString {
        for (ch in value) append(if (ch in 'ァ'..'ヶ') (ch.code - 0x60).toChar() else ch)
    }

    fun romanize(value: String): String {
        val text = hiragana(value)
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val two = text.substring(i, minOf(i + 2, text.length))
            val digraph = kanaDigraphs[two]
            val one = romanMap[text[i].toString()]
            when {
                text[i] == 'っ' && i + 1 < text.length -> {
                    val next = kanaDigraphs[text.substring(i + 1, minOf(i + 3, text.length))]
                        ?: romanMap[text[i + 1].toString()].orEmpty()
                    if (next.isNotEmpty()) out.append(next[0])
                    i++
                }
                digraph != null -> { out.append(digraph); i += 2 }
                one != null -> { out.append(one); i++ }
                else -> { out.append(text[i]); i++ }
            }
        }
        return out.toString()
    }

    /** The prefecture reading is derived from the stored word, never guessed from kanji OCR. */
    fun prefectureReading(word: String): String? =
        prefectureReadings.entries.firstOrNull { word.startsWith(it.key) }?.value

    private data class Target(val reading: String, val text: String)

    /** Native dictionary reading can be used for an optional town phonetic alias. */
    private fun addressTargets(entry: UserWord): List<Target> {
        val prefecture = prefectureReading(entry.word) ?: return emptyList()
        val targets = mutableListOf(Target(prefecture, entry.word))
        val stored = hiragana(entry.reading)
        if (stored.length >= 2 && stored != "じゅうしょ" &&
            stored.all { it in 'ぁ'..'ん' || it == 'ー' }) {
            val suffix = if (stored.startsWith(prefecture)) entry.word
                else addressSuffixes(entry.word).drop(1).lastOrNull {
                    it.firstOrNull()?.isLetter() == true
                } ?: entry.word
            targets += Target(stored, suffix)
        }
        return targets
    }

    /** Match what the user actually typed. Conversion display is not used for lookups. */
    fun suggest(typed: String, dictionary: List<UserWord>): PersonalCompletion? {
        if (typed.isBlank()) return null
        val before = hiragana(typed).lowercase()
        val latin = before.all { it in 'a'..'z' || it in '0'..'9' || it in "@._-" }
        var best: PersonalCompletion? = null
        var bestScore = -1
        for (entry in dictionary) {
            val kind = kindOf(entry.word) ?: continue
            val targets = when (kind) {
                "address" -> addressTargets(entry)
                "email", "phone" -> listOf(Target(entry.word, entry.word))
                else -> emptyList()
            }
            for (target in targets) {
                val test = when {
                    kind == "phone" -> digits(target.reading)
                    latin && kind == "address" -> romanize(target.reading)
                    else -> target.reading.lowercase()
                }
                // Telephone fields often input unformatted digits even if the
                // stored phone number contains dashes or spaces.
                val prefix = if (kind == "phone") digits(before) else before
                if (prefix.length < (if (kind == "phone") 3 else if (latin) 3 else 2) ||
                    !test.startsWith(prefix)) continue
                val score = before.length * 10 + if (test.length > before.length) 1 else 0
                if (score > bestScore) {
                    bestScore = score
                    best = PersonalCompletion(target.text, typed, target.text, kind)
                }
            }
        }
        return best
    }

    fun addressSuffixes(word: String): List<String> {
        val suffixes = linkedSetOf(word)
        val prefix = prefectureReadings.keys.firstOrNull { word.startsWith(it) }
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
