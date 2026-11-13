// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package io.github.ninjin0802.keyboard.app.clipboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.ninjin0802.keyboard.app.AppChrome
import io.github.ninjin0802.keyboard.app.R

/** Inline Meltype editing surface, with no system modal or default Android button chrome. */
class ClipboardEditorActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val clipId = intent.getLongExtra(EXTRA_ID, -1L)
        val store = ClipboardHistoryStore(this)
        val original = store.get(clipId)
        if (original == null || original.isMedia) { finish(); return }
        val colors = AppChrome.colors(this)
        fun dp(n: Int) = AppChrome.dp(this, n)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colors.background)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(12))
            addView(AppChrome.iconButton(this@ClipboardEditorActivity, R.drawable.ms_arrow_back, "戻る") {
                finish()
            }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(15) })
            addView(AppChrome.text(this@ClipboardEditorActivity, "クリップボードを編集",
                20f, strong = true))
        }
        root.addView(header)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        content.addView(AppChrome.text(this, "内容", 13f, muted = true))
        val editor = AppChrome.input(this, "テキストを入力", original.text, multiline = true).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            gravity = Gravity.TOP
            setHorizontallyScrolling(false)
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f).apply {
            topMargin = dp(10); bottomMargin = dp(15)
        })
        val error = AppChrome.text(this, "", 13f).apply {
            visibility = View.GONE
            setTextColor(colors.danger)
        }
        content.addView(error)
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(AppChrome.action(this@ClipboardEditorActivity, "キャンセル", filled = false) {
                finish()
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(8) })
            addView(AppChrome.action(this@ClipboardEditorActivity, "保存") {
                val saved = store.edit(clipId, editor.text.toString())
                if (saved) finish()
                else {
                    error.text = "空欄または重複する内容は保存できません。"
                    error.visibility = View.VISIBLE
                }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        content.addView(actions)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        AppChrome.protectBars(this, root)
        setContentView(root)
    }

    companion object { const val EXTRA_ID = "clip_id" }
}
