// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 KoheiTakada
package app.meltype.keyboard

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout

/** 端末の上のテストが、入れたキーボードで打つための入力欄 (debug の版だけ)。 */
class ImeSmokeActivity : Activity() {
    lateinit var japaneseEditor: EditText
        private set

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        // テストのあいだ、画面が消えてキーボードが閉じないようにする。
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        japaneseEditor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            isFocusableInTouchMode = true
            textSize = 20f
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 80, 24, 24)
            addView(japaneseEditor, LinearLayout.LayoutParams(-1, -2))
        })
    }
}
