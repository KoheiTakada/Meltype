// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.essentials

import android.content.Context
import io.github.ninjin0802.keyboard.app.core.coreDataDirectory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** UserDictionary on all platforms uses exactly two tab-separated columns. */
data class UserWord(val reading: String, val word: String)

/** Android front-end to Meltype.Core's userdict.txt. No second dictionary or storage schema. */
class DictionaryAdapter(context: Context) {
    private val directory = coreDataDirectory(context.applicationContext)
    private val path = File(directory, "userdict.txt")

    fun list(): List<UserWord> = try {
        parse(if (path.isFile) path.readText(Charsets.UTF_8) else "")
    } catch (_: Exception) {
        emptyList()
    }

    @Synchronized fun add(reading: String, word: String): Boolean {
        val row = UserWord(reading.trim(), word.trim())
        if (!valid(row)) return false
        val existing = try { if (path.isFile) path.readText(Charsets.UTF_8) else "" } catch (_: Exception) { return false }
        if (row in parse(existing)) return true
        val header = if (existing.isBlank()) "# Meltype ユーザー辞書\n" else existing +
            (if (existing.endsWith("\n")) "" else "\n")
        return write(header + row.reading + "\t" + row.word + "\n")
    }

    @Synchronized fun edit(previous: UserWord, reading: String, word: String): Boolean {
        val changed = UserWord(reading.trim(), word.trim())
        if (!valid(changed)) return false
        val words = list()
        if (previous !in words || (previous != changed && changed in words)) return false
        return saveAll(words.map { if (it == previous) changed else it })
    }

    @Synchronized fun delete(word: UserWord): Boolean {
        val words = list()
        if (word !in words) return false
        return saveAll(words.filterNot { it == word })
    }

    private fun saveAll(words: List<UserWord>) =
        write("# Meltype ユーザー辞書: 読み<Tab>単語\n" +
            words.joinToString("") { it.reading + "\t" + it.word + "\n" })

    private fun write(content: String): Boolean {
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val temp = try { File.createTempFile("userdict.", ".tmp", directory) } catch (_: Exception) { return false }
        return try {
            temp.outputStream().use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
                if (stream is java.io.FileOutputStream) stream.fd.sync()
            }
            try {
                Files.move(temp.toPath(), path.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), path.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) { false } finally { temp.delete() }
    }

    companion object {
        fun parse(text: String): List<UserWord> = text.removePrefix("\uFEFF").lineSequence().mapNotNull {
            val line = it.trimEnd('\r')
            if (line.startsWith("#") || !line.contains('\t')) null
            else UserWord(line.substringBefore('\t').trim(), line.substringAfter('\t').substringBefore('\t').trim())
                .takeIf(::valid)
        }.distinct().toList()

        private fun valid(word: UserWord): Boolean =
            word.reading.isNotBlank() && word.word.isNotBlank() &&
            word.reading.length <= 150 && word.word.length <= 500 &&
            word.reading.none(Char::isISOControl) && word.word.none(Char::isISOControl)
    }
}
