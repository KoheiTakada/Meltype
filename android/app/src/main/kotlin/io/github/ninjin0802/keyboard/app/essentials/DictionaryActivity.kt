// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.essentials

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** UI only. Writes the same userdict.txt consumed by Meltype.Core. */
class DictionaryActivity : Activity() {
    private val store by lazy { DictionaryAdapter(this) }
    private val d by lazy { resources.displayMetrics.density }
    private fun dp(n: Int) = (n * d + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Meltype ユーザー辞書"
        draw()
    }

    private fun draw() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }
        root.addView(TextView(this).apply { text = "ユーザー辞書"; textSize = 23f })
        root.addView(TextView(this).apply {
            text = "一般の語句は本家辞書を使用。メール・電話・住所は入力中に3番目の候補として補完します。"
            setPadding(0, dp(8), 0, dp(12))
        })
        root.addView(Button(this).apply {
            text = "＋ 単語を登録"
            setOnClickListener { edit(null) }
        })
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (entry in store.list()) {
            list.addView(TextView(this).apply {
                text = entry.reading + "  →  " + entry.word
                textSize = 16f
                setPadding(dp(8), dp(13), dp(8), dp(13))
                setOnClickListener { edit(entry) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@DictionaryActivity)
                        .setTitle("辞書から削除しますか？")
                        .setNegativeButton("キャンセル", null)
                        .setPositiveButton("削除") { _, _ -> store.delete(entry); draw() }
                        .show()
                    true
                }
            })
        }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun edit(old: UserWord?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(7), dp(20), 0)
        }
        val reading = EditText(this).apply {
            hint = "読み（例：めあど）"
            setSingleLine(true)
            setText(old?.reading ?: "")
        }
        val word = EditText(this).apply {
            hint = "単語・メール・電話・住所"
            setSingleLine(true)
            setText(old?.word ?: "")
        }
        container.addView(reading)
        container.addView(word)
        val dialog = AlertDialog.Builder(this).setTitle("辞書の登録")
            .setView(container).setNegativeButton("キャンセル", null)
            .setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val ok = if (old == null) store.add(reading.text.toString(), word.text.toString())
                    else store.edit(old, reading.text.toString(), word.text.toString())
                if (ok) { dialog.dismiss(); draw() }
                else word.error = "保存に失敗しました。入力を確認してください。"
            }
        }
        dialog.show()
    }
}
