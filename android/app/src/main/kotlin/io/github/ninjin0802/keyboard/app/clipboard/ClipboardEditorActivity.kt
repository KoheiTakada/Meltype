// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.ninjin0802.keyboard.app.clipboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Editing is intentionally a normal Activity, so text input never traps IME focus. */
class ClipboardEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        val store = ClipboardHistoryStore(this)
        val original = store.get(id)
        if (original == null || original.isMedia) {
            finish()
            return
        }
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density + 0.5f).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "クリップボードの内容を編集"
            textSize = 19f
        })
        val editor = EditText(this).apply {
            setText(original.text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setSelection(text.length)
        }
        root.addView(editor, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "キャンセル"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        buttons.addView(Button(this).apply {
            text = "保存"
            setOnClickListener {
                if (store.edit(id, editor.text.toString())) finish()
                else editor.error = "空文字列や重複する内容は保存できません"
            }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        root.addView(buttons)
        setContentView(root)
    }

    companion object { const val EXTRA_ID = "clip_id" }
}
