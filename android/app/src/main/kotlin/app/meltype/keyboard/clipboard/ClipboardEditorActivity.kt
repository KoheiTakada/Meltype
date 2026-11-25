// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.keyboard.clipboard

import android.app.Activity
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import app.meltype.keyboard.padForSystemBars
import app.meltype.keyboard.view.Palette

/** クリップボードの履歴の文字を書き換える。キーボードの履歴の「編集」から開く。 */
class ClipboardEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clipId = intent.getLongExtra(EXTRA_ID, -1L)
        val store = ClipboardHistoryStore(this)
        val original = store.get(clipId)
        if (original == null || original.isMedia) { finish(); return }
        val palette = Palette.of(this)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density + .5f).toInt()
        fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(14).toFloat() }

        val editor = EditText(this).apply {
            setText(original.text)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setTextColor(palette.text)
            background = rounded(palette.key)
            setPadding(dp(16), dp(13), dp(16), dp(13))
        }
        val error = TextView(this).apply {
            text = "空欄または重複する内容は保存できません。"
            setTextColor(palette.subText)
            visibility = View.GONE
        }
        fun button(label: String, filled: Boolean, action: () -> Unit) = TextView(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            setTextColor(if (filled) palette.onAccent else palette.text)
            background = rounded(if (filled) palette.accent else palette.key)
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("キャンセル", filled = false) { finish() },
                LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
            addView(button("保存", filled = true) {
                if (store.edit(clipId, editor.text.toString())) finish() else error.visibility = View.VISIBLE
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.background)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(TextView(this@ClipboardEditorActivity).apply {
                text = "クリップボードを編集"
                textSize = 20f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(palette.text)
            })
            addView(editor, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16); bottomMargin = dp(12) })
            addView(error, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(actions)
        }
        setContentView(LinearLayout(this).apply {
            setBackgroundColor(palette.background)
            addView(root, LinearLayout.LayoutParams(-1, -1))
            padForSystemBars()
        })
    }

    companion object { const val EXTRA_ID = "clip_id" }
}
